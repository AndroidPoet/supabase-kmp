package io.github.androidpoet.supabase.codegen

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import kotlinx.serialization.json.Json

/**
 * Generates Kotlin `@Serializable` data classes (one per table) and `enum class`es
 * (one per Postgres enum) from a PostgREST OpenAPI document. The database is the
 * source of truth — this only ever *reads* the schema and emits Kotlin source; it
 * never writes anything back to the database.
 */
public object SupabaseModelGenerator {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val serializable = ClassName("kotlinx.serialization", "Serializable")
    private val serialName = ClassName("kotlinx.serialization", "SerialName")
    private val jsonElement = ClassName("kotlinx.serialization.json", "JsonElement")

    /**
     * Parses the OpenAPI [specJson] served at `{projectUrl}/rest/v1/` and returns a
     * single Kotlin source file (named [fileName]) under [packageName] containing a
     * data class for every table and an enum class for every Postgres enum.
     */
    public fun generate(
        specJson: String,
        packageName: String,
        fileName: String = "SupabaseModels",
    ): String = generate(json.decodeFromString(OpenApiSpec.serializer(), specJson), packageName, fileName)

    internal fun generate(spec: OpenApiSpec, packageName: String, fileName: String): String {
        // Collected across all tables and de-duplicated by enum type name, since the
        // same Postgres enum can back columns on many tables.
        val enums = linkedMapOf<String, List<String>>()
        val tables = mutableListOf<TypeSpec>()
        // Two distinct tables can normalise to the same Kotlin class name (e.g. `user_profile`
        // and `userProfile`). That would emit duplicate declarations that don't compile, so we
        // fail fast with an actionable message instead.
        val claimedNames = mutableMapOf<String, String>()

        for ((tableName, definition) in spec.definitions) {
            if (definition.properties.isEmpty()) continue // a data class needs ≥1 property
            val className = Naming.pascal(tableName)
            val clash = claimedNames.put(className, tableName)
            check(clash == null) {
                "Tables `$clash` and `$tableName` both map to the Kotlin class `$className`. " +
                    "Rename one of the tables (or its exposed name) so the generated names don't collide."
            }
            tables += buildDataClass(className, tableName, definition, enums)
        }

        val file =
            FileSpec
                .builder(packageName, fileName)
                .addFileComment("Generated from the Supabase schema. Do not edit by hand.")
        enums.forEach { (name, values) -> file.addType(buildEnum(name, values)) }
        tables.forEach(file::addType)
        return file.build().toString()
    }

    private fun buildDataClass(
        className: String,
        tableName: String,
        definition: TableDefinition,
        enums: MutableMap<String, List<String>>,
    ): TypeSpec {
        val ctor = FunSpec.constructorBuilder()
        val typeBuilder =
            TypeSpec
                .classBuilder(className)
                .addModifiers(KModifier.DATA)
                .addAnnotation(serializable)

        // Two columns can normalise to the same Kotlin property (e.g. `user_id` and `userId`).
        // That would emit a data class with duplicate properties that doesn't compile, so we fail
        // fast with an actionable message — mirroring the table- and enum-name collision checks.
        val claimedProps = mutableMapOf<String, String>()

        for ((columnName, column) in definition.properties) {
            val nullable = columnName !in definition.required
            val type = kotlinType(tableName, columnName, column, enums).copy(nullable = nullable)
            val propName = Naming.camel(columnName)

            val clash = claimedProps.put(propName, columnName)
            check(clash == null) {
                "Columns `$clash` and `$columnName` in table `$tableName` both map to the Kotlin " +
                    "property `$propName`. Rename one of the columns (or its exposed name) so the " +
                    "generated names don't collide."
            }

            val prop = PropertySpec.builder(propName, type).initializer(propName)
            if (propName != columnName) prop.addAnnotation(serialNameOf(columnName))

            ctor.addParameter(propName, type)
            typeBuilder.addProperty(prop.build())
        }
        return typeBuilder.primaryConstructor(ctor.build()).build()
    }

    private fun buildEnum(name: String, values: List<String>): TypeSpec {
        val builder = TypeSpec.enumBuilder(name).addAnnotation(serializable)
        for (value in values) {
            builder.addEnumConstant(
                Naming.enumConstant(value),
                TypeSpec.anonymousClassBuilder().addAnnotation(serialNameOf(value)).build(),
            )
        }
        return builder.build()
    }

    private fun serialNameOf(value: String): AnnotationSpec =
        AnnotationSpec.builder(serialName).addMember("%S", value).build()

    /** Maps one column to its Kotlin type, registering any enum it introduces. */
    private fun kotlinType(
        tableName: String,
        columnName: String,
        column: ColumnProperty,
        enums: MutableMap<String, List<String>>,
    ): TypeName {
        if (column.enum.isNotEmpty()) {
            val enumName = Naming.enumName(column.format, tableName, columnName)
            registerEnum(enums, enumName, column.enum)
            return ClassName("", enumName)
        }
        if (column.type == "array") {
            val items = column.items
            val element =
                when {
                    items == null -> STRING
                    items.enum.isNotEmpty() -> {
                        val enumName = Naming.enumName(items.format, tableName, columnName)
                        registerEnum(enums, enumName, items.enum)
                        ClassName("", enumName)
                    }
                    // A nested array (e.g. int[][]) reports its element `type` as "array" with
                    // no scalar format. Keep it as raw JSON so deserialization can't fail trying
                    // to read an inner array into a String.
                    items.type == "array" -> jsonElement
                    // PostgREST gives array elements only a coarse Swagger type (no `format`),
                    // so this resolves "integer"/"number"/"string"/"boolean" via the same maps.
                    else -> scalarType(items.format ?: items.type)
                }
            return LIST.parameterizedBy(element)
        }
        return scalarType(column.format ?: column.type)
    }

    /** Registers an enum, failing if a different enum already claimed the same Kotlin name. */
    private fun registerEnum(
        enums: MutableMap<String, List<String>>,
        name: String,
        values: List<String>,
    ) {
        val existing = enums[name]
        check(existing == null || existing == values) {
            "Two Postgres enums map to the Kotlin enum `$name` with different values " +
                "($existing vs $values). Rename one of the enum types so they don't collide."
        }
        enums[name] = values
    }

    /** Maps a Postgres type name to a Kotlin scalar type. */
    private fun scalarType(format: String?): TypeName =
        when (format?.lowercase()?.trim()) {
            in INT_TYPES -> INT
            in LONG_TYPES -> LONG
            in DOUBLE_TYPES -> DOUBLE
            in BOOL_TYPES -> BOOLEAN
            in JSON_TYPES -> jsonElement
            // text, varchar, char, uuid, timestamp*, date, time, bytea, … → String (ISO-8601 for dates)
            else -> STRING
        }

    // PostgREST sets a column's `format` to its Postgres type name verbatim, EXCEPT integers,
    // which it normalises to Swagger's "int32"/"int64" (OpenAPI.hs#toSwaggerFormat) — so the
    // raw "int4"/"bigint" spellings rarely appear, but are kept for safety. Array elements
    // carry no `format` at all, only the coarse Swagger `type` ("integer"/"number"/"string"/
    // "boolean"), which is why those coarse names are mapped here too.
    private val INT_TYPES = setOf("int32", "integer", "int4", "smallint", "int2", "serial", "serial4", "smallserial")
    private val LONG_TYPES = setOf("int64", "bigint", "int8", "bigserial", "serial8")
    private val DOUBLE_TYPES =
        setOf("number", "double precision", "float8", "real", "float4", "numeric", "decimal", "money")
    private val BOOL_TYPES = setOf("boolean", "bool")
    private val JSON_TYPES = setOf("json", "jsonb")
}
