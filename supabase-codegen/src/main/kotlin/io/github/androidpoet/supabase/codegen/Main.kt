package io.github.androidpoet.supabase.codegen

import java.nio.file.Files
import java.nio.file.Path

/**
 * CLI entry point. Fetches a Supabase project's schema and writes one Kotlin file of
 * `@Serializable` models. The output uses only multiplatform-common types, so drop it
 * straight into a `commonMain` source set.
 *
 * ```
 * supabase-codegen --url https://<ref>.supabase.co --key <api-key> \
 *   --package com.example.db --out src/commonMain/kotlin
 * ```
 * `--url`/`--key` fall back to the `SUPABASE_URL` / `SUPABASE_KEY` environment vars.
 *
 * `--format kotlin` (default) emits one Kotlin file per table (under `{package}.tables`) and per
 * enum (under `{package}.enums`). `--format sqldelight` emits one `.sq` file per table for
 * SQLDelight's plugin to compile.
 */
public fun main(args: Array<String>) {
    val options = parseArgs(args)
    val url = options["--url"] ?: System.getenv("SUPABASE_URL")
    val key = options["--key"] ?: System.getenv("SUPABASE_KEY")
    require(!url.isNullOrBlank() && !key.isNullOrBlank()) {
        "Provide --url and --key (or SUPABASE_URL / SUPABASE_KEY). " +
            "Use a key whose role can read the tables you want generated."
    }
    val packageName = options["--package"] ?: "supabase.generated"
    val outDir = options["--out"] ?: "build/generated/supabase"
    val format = options["--format"]?.lowercase() ?: "kotlin"

    println("Fetching schema from ${url.trimEnd('/')}/rest/v1/ …")
    val spec = SchemaFetcher.fetch(url, key)
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

    for (file in files) {
        val target = Path.of(outDir, file.relativePath)
        Files.createDirectories(target.parent)
        Files.writeString(target, file.contents)
        println("✓ Wrote $target")
    }
    println("✓ Generated ${files.size} $format file(s)")
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
