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
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import kotlinx.serialization.json.Json

/** One generated Kotlin source file: its path relative to the output root, and its contents. */
public data class GeneratedFile(
    public val relativePath: String,
    public val contents: String,
)

/**
 * Generates Kotlin `@Serializable` data classes (one file per table, under a `tables`
 * sub-package) and `enum class`es (one file per Postgres enum, under an `enums`
 * sub-package) from a PostgREST OpenAPI document. The database is the source of truth —
 * this only ever *reads* the schema and emits Kotlin source; it never writes anything
 * back to the database.
 *
 * The generated files are fully owned by the generator and overwritten on every run, so
 * never hand-edit them. To add behaviour, write extension functions/properties on the
 * generated types in your OWN file — that survives regeneration.
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

    // Sub-packages each kind of model is emitted into, so a large schema stays navigable and
    // a table and an enum that share a Kotlin name (e.g. `order_status`) land in different
    // packages instead of colliding. Mirrors jOOQ's `tables`/`enums` layout.
    private const val TABLES_SUBPACKAGE = "tables"
    private const val ENUMS_SUBPACKAGE = "enums"

    private val header =
        listOf(
            "Generated from the Supabase schema. Do not edit by hand.",
            "To customise a model, add extension functions/properties in your OWN file —",
            "this file is regenerated (overwritten) on every run.",
        )

    /**
     * Parses the OpenAPI [specJson] served at `{projectUrl}/rest/v1/` and returns one
     * [GeneratedFile] per table (under `{packageName}.tables`) and per Postgres enum
     * (under `{packageName}.enums`).
     */
    public fun generate(specJson: String, packageName: String): List<GeneratedFile> =
        generate(json.decodeFromString(OpenApiSpec.serializer(), specJson), packageName)

    internal fun generate(spec: OpenApiSpec, packageName: String): List<GeneratedFile> {
        val tablesPackage = "$packageName.$TABLES_SUBPACKAGE"
        val enumsPackage = "$packageName.$ENUMS_SUBPACKAGE"

        // Collected across all tables and de-duplicated by enum type name, since the
        // same Postgres enum can back columns on many tables.
        val enums = linkedMapOf<String, List<String>>()
        // Keyed by Kotlin class name so two tables that normalise to the same name (e.g.
        // `user_profile` and `userProfile`) are caught: they would emit two files with the
        // same class, so we fail fast with an actionable message instead.
        val tableTypes = linkedMapOf<String, TypeSpec>()
        val claimedTables = mutableMapOf<String, String>()

        for ((tableName, definition) in spec.definitions) {
            if (definition.properties.isEmpty()) continue // a data class needs ≥1 property
            val className = Naming.pascal(tableName)
            val clash = claimedTables.put(className, tableName)
            check(clash == null) {
                "Tables `$clash` and `$tableName` both map to the Kotlin class `$className`. " +
                    "Rename one of the tables (or its exposed name) so the generated names don't collide."
            }
            tableTypes[className] = buildDataClass(enumsPackage, className, tableName, definition, enums)
        }

        // Tables and enums live in separate sub-packages, so a table and an enum that share a
        // Kotlin name no longer collide — no cross-check needed (unlike the single-file layout).

        val files = mutableListOf<GeneratedFile>()
        enums.forEach { (name, values) -> files += fileOf(enumsPackage, name, buildEnum(name, values)) }
        tableTypes.forEach { (name, type) -> files += fileOf(tablesPackage, name, type) }
        return files
    }

    /** Wraps a single top-level [type] in its own file under [packageName]. */
    private fun fileOf(packageName: String, fileName: String, type: TypeSpec): GeneratedFile {
        val builder = FileSpec.builder(packageName, fileName)
        header.forEach(builder::addFileComment)
        val contents = builder.addType(type).build().toString()
        val relativePath = "${packageName.replace('.', '/')}/$fileName.kt"
        return GeneratedFile(relativePath, contents)
    }

    private fun buildDataClass(
        enumsPackage: String,
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
            val type = kotlinType(enumsPackage, tableName, columnName, column, enums).copy(nullable = nullable)
            val propName = Naming.camel(columnName)

            val clash = claimedProps.put(propName, columnName)
            check(clash == null) {
                "Columns `$clash` and `$columnName` in table `$tableName` both map to the Kotlin " +
                    "property `$propName`. Rename one of the columns (or its exposed name) so the " +
                    "generated names don't collide."
            }

            val prop = PropertySpec.builder(propName, type).initializer(propName)
            if (propName != columnName) prop.addAnnotation(serialNameOf(columnName))

            // A nullable column gets a `= null` default so it is OPTIONAL on the wire.
            // kotlinx.serialization treats a `T?` WITHOUT a default as required (the key
            // must be present, even if null), so a partial `select=...` that omits the
            // column — the common case — would throw MissingFieldException. Required
            // (NOT NULL) columns stay non-default so the row's presence is still enforced.
            val param = ParameterSpec.builder(propName, type)
            if (nullable) param.defaultValue("null")
            ctor.addParameter(param.build())
            typeBuilder.addProperty(prop.build())
        }
        return typeBuilder.primaryConstructor(ctor.build()).build()
    }

    private fun buildEnum(name: String, values: List<String>): TypeSpec {
        val builder = TypeSpec.enumBuilder(name).addAnnotation(serializable)
        // Distinct Postgres labels can normalise to the same Kotlin constant (e.g. `active` and
        // `Active`, or `in progress` and `in_progress`). KotlinPoet silently keeps only one, so a
        // label would be dropped and rows carrying it would fail to deserialize — fail fast with an
        // actionable message instead, mirroring the table/column/enum-name collision checks.
        val claimedConstants = mutableMapOf<String, String>()
        for (value in values) {
            val constant = Naming.enumConstant(value)
            val clash = claimedConstants.put(constant, value)
            check(clash == null) {
                "Enum values `$clash` and `$value` in enum `$name` both map to the Kotlin constant " +
                    "`$constant`. Rename one of the enum labels so the generated names don't collide."
            }
            builder.addEnumConstant(
                constant,
                TypeSpec.anonymousClassBuilder().addAnnotation(serialNameOf(value)).build(),
            )
        }
        return builder.build()
    }

    private fun serialNameOf(value: String): AnnotationSpec =
        AnnotationSpec.builder(serialName).addMember("%S", value).build()

    /** Maps one column to its Kotlin type, registering any enum it introduces. */
    private fun kotlinType(
        enumsPackage: String,
        tableName: String,
        columnName: String,
        column: ColumnProperty,
        enums: MutableMap<String, List<String>>,
    ): TypeName {
        if (column.enum.isNotEmpty()) {
            val enumName = Naming.enumName(column.format, tableName, columnName)
            registerEnum(enums, enumName, column.enum)
            // Enums live in their own `.enums` package; reference them by their full
            // package so KotlinPoet emits a correct `import {package}.enums.{Enum}`.
            return ClassName(enumsPackage, enumName)
        }
        if (column.type == "array") {
            val items = column.items
            val element =
                when {
                    items == null -> STRING
                    items.enum.isNotEmpty() -> {
                        val enumName = Naming.enumName(items.format, tableName, columnName)
                        registerEnum(enums, enumName, items.enum)
                        ClassName(enumsPackage, enumName)
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
            // text, varchar, char, uuid, timestamp*, date, time, bytea, money, … → String
            // (ISO-8601 for dates; money arrives locale-formatted, e.g. "$1,234.56")
            else -> STRING
        }

    // PostgREST sets a column's `format` to its Postgres type name verbatim, EXCEPT integers,
    // which it normalises to Swagger's "int32"/"int64" (OpenAPI.hs#toSwaggerFormat) — so the
    // raw "int4"/"bigint" spellings rarely appear, but are kept for safety. Array elements
    // carry no `format` at all, only the coarse Swagger `type` ("integer"/"number"/"string"/
    // "boolean"), which is why those coarse names are mapped here too.
    private val INT_TYPES = setOf("int32", "integer", "int4", "smallint", "int2", "serial", "serial4", "smallserial")
    private val LONG_TYPES = setOf("int64", "bigint", "int8", "bigserial", "serial8")

    // `money` is deliberately NOT here. Postgres `money` (CASHOID) is not a `JSONTYPE_NUMERIC` type,
    // so PostgREST (via `to_json`) emits it as a locale-formatted *string* — e.g. "$1,234.56" — not a
    // bare number. Mapping it to Double would throw at decode on every row; it falls through to String
    // below (consumers can parse it, or expose the column as `amount::numeric` to get a JSON number).
    // `numeric`/`decimal` ARE emitted as JSON numbers, so Double decodes them (with the usual IEEE-754
    // precision caveat for very large/high-scale values).
    private val DOUBLE_TYPES =
        setOf("number", "double precision", "float8", "real", "float4", "numeric", "decimal")
    private val BOOL_TYPES = setOf("boolean", "bool")
    private val JSON_TYPES = setOf("json", "jsonb")
}
