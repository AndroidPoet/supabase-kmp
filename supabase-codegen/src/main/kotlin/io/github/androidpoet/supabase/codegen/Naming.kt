package io.github.androidpoet.supabase.codegen

/** Converts Postgres identifiers (snake_case) into idiomatic Kotlin names. */
internal object Naming {
    private val separators = Regex("[^A-Za-z0-9]")

    /** `order_status` → `OrderStatus`, `todos` → `Todos`. */
    fun pascal(raw: String): String =
        raw
            .split(separators)
            .filter { it.isNotEmpty() }
            .joinToString("") { part -> part.replaceFirstChar(Char::uppercaseChar) }
            .ifEmpty { "Generated" }

    /** `created_at` → `createdAt`, `id` → `id`. */
    fun camel(raw: String): String = pascal(raw).replaceFirstChar(Char::lowercaseChar)

    /** `in_progress` → `IN_PROGRESS`, `owner` → `OWNER`. */
    fun enumConstant(value: String): String {
        val sanitized = value.uppercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
        return when {
            sanitized.isEmpty() -> "UNKNOWN"
            sanitized.first().isDigit() -> "_$sanitized"
            else -> sanitized
        }
    }

    /**
     * Names an enum class from its Postgres type. PostgREST reports the enum's
     * `format` as `schema.type_name` (e.g. `public.order_status` → `OrderStatus`);
     * falls back to `<table>_<column>` when no usable format is present.
     */
    fun enumName(format: String?, table: String, column: String): String {
        val base =
            when {
                format != null && format.contains('.') -> format.substringAfterLast('.')
                !format.isNullOrBlank() -> format
                else -> "${table}_$column"
            }
        return pascal(base)
    }
}
