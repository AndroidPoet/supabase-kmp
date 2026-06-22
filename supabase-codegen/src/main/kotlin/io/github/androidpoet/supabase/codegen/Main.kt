package io.github.androidpoet.supabase.codegen

import java.nio.file.Files
import java.nio.file.Path

/**
 * CLI entry point. Reads a Supabase project's schema (fetched live, or from a local OpenAPI JSON
 * file) and writes typed code. The output uses only multiplatform-common types, so it drops
 * straight into a `commonMain` source set.
 *
 * ```
 * supabase-codegen --url https://<ref>.supabase.co --key <api-key> \
 *   --package com.example.db --out src/commonMain/kotlin
 * ```
 * `--url`/`--key` fall back to the `SUPABASE_URL` / `SUPABASE_KEY` environment vars. Pass
 * `--spec <file.json>` instead to read the OpenAPI document from disk (no network, no key).
 *
 * `--format kotlin` (default) emits one Kotlin file per table (under `{package}.tables`) and per
 * enum (under `{package}.enums`). `--format sqldelight` emits one `.sq` file per table for
 * SQLDelight's plugin to compile; add `--adapters true` to *also* emit `SupabaseAdapters.kt`, the
 * glue that wires each table into an [offline-sync](https://github.com/AndroidPoet/offline-sync-kmp)
 * `LocalStore` (needs the offline-sync runtime on the classpath).
 */
public fun main(args: Array<String>) {
    val options = parseArgs(args)
    val packageName = options["--package"] ?: "supabase.generated"
    val outDir = options["--out"] ?: "build/generated/supabase"
    val format = options["--format"]?.lowercase() ?: "kotlin"
    val emitAdapters = options["--adapters"]?.toBooleanStrictOrNull() ?: false

    val spec = loadSpec(options)
    val packageDir = Path.of(outDir, *packageName.split('.').toTypedArray())

    // Generate, clearing prior output first so a dropped table/enum can't leave a stale file.
    val files =
        when (format) {
            "kotlin" -> {
                for (sub in SupabaseModelGenerator.generatedSubpackages) {
                    packageDir.resolve(sub).toFile().deleteRecursively()
                }
                SupabaseModelGenerator.generate(spec, packageName)
            }
            "sqldelight" -> {
                packageDir.toFile().listFiles { f -> f.extension == "sq" }?.forEach { it.delete() }
                SupabaseSqlDelightGenerator.generate(spec, packageName)
            }
            else -> error("Unknown --format '$format' (use 'kotlin' or 'sqldelight').")
        }

    val written = files.toMutableList()
    if (format == "sqldelight" && emitAdapters) {
        written += SupabaseSqlDelightAdapterGenerator.generate(spec, packageName)
    }

    for (file in written) {
        val target = Path.of(outDir, file.relativePath)
        Files.createDirectories(target.parent)
        Files.writeString(target, file.contents)
        println("✓ Wrote $target")
    }
    println("✓ Generated ${written.size} file(s) ($format${if (emitAdapters) " + adapters" else ""})")
}

/**
 * Loads the OpenAPI document (raw JSON) from `--spec <file>` if given, otherwise fetches it live
 * from `--url`/`--key`. The generators decode the JSON themselves.
 */
private fun loadSpec(options: Map<String, String>): String {
    val specFile = options["--spec"]
    if (specFile != null) {
        println("Reading schema from $specFile …")
        return Files.readString(Path.of(specFile))
    }
    val url = options["--url"] ?: System.getenv("SUPABASE_URL")
    val key = options["--key"] ?: System.getenv("SUPABASE_KEY")
    require(!url.isNullOrBlank() && !key.isNullOrBlank()) {
        "Provide --spec <file>, or --url and --key (or SUPABASE_URL / SUPABASE_KEY). " +
            "Use a key whose role can read the tables you want generated."
    }
    println("Fetching schema from ${url.trimEnd('/')}/rest/v1/ …")
    return SchemaFetcher.fetch(url, key)
}

private fun parseArgs(args: Array<String>): Map<String, String> {
    val options = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val arg = args[i]
        if (arg.startsWith("--") && i + 1 < args.size) {
            options[arg] = args[i + 1]
            i += 2
        } else {
            i += 1
        }
    }
    return options
}
