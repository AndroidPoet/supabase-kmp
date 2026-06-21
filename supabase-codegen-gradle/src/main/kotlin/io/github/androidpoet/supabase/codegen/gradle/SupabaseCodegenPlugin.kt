package io.github.androidpoet.supabase.codegen.gradle

import io.github.androidpoet.supabase.codegen.SchemaFetcher
import io.github.androidpoet.supabase.codegen.SupabaseModelGenerator
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Configures [the generate task][GenerateSupabaseModelsTask]. Add a `supabaseCodegen { }`
 * block to your build:
 *
 * ```kotlin
 * supabaseCodegen {
 *     url.set(providers.environmentVariable("SUPABASE_URL"))
 *     key.set(providers.environmentVariable("SUPABASE_KEY"))
 *     packageName.set("com.example.db")
 *     outputDir.set(layout.projectDirectory.dir("src/commonMain/kotlin"))
 * }
 * ```
 *
 * `url`/`key` fall back to the `SUPABASE_URL` / `SUPABASE_KEY` environment variables.
 */
public abstract class SupabaseCodegenExtension {
    /** Project URL, e.g. `https://<ref>.supabase.co`. Falls back to `SUPABASE_URL`. */
    public abstract val url: Property<String>

    /** API key whose role can read the target tables. Falls back to `SUPABASE_KEY`. */
    public abstract val key: Property<String>

    /** Package for the generated file. Defaults to `supabase.generated`. */
    public abstract val packageName: Property<String>

    /** Generated file name (without extension). Defaults to `SupabaseModels`. */
    public abstract val fileName: Property<String>

    /** Where to write the generated file. Defaults to `build/generated/supabase`. */
    public abstract val outputDir: DirectoryProperty
}

/**
 * Fetches the Supabase schema and writes one Kotlin file of `@Serializable` models. This is
 * an on-demand task — it is deliberately NOT wired into `compileKotlin`, because the schema
 * lives behind the network and needs a key, and you don't want either on every build. Run it
 * when your schema changes (and commit the result, the way `supabase gen types` is used).
 */
public abstract class GenerateSupabaseModelsTask : DefaultTask() {
    @get:Input
    public abstract val url: Property<String>

    // The key is a secret, so it is kept out of the task input snapshot (no caching to disk).
    @get:Internal
    public abstract val key: Property<String>

    @get:Input
    public abstract val packageName: Property<String>

    @get:Input
    public abstract val fileName: Property<String>

    @get:OutputDirectory
    public abstract val outputDir: DirectoryProperty

    @TaskAction
    public fun generate() {
        val projectUrl =
            url.orNull?.takeIf { it.isNotBlank() }
                ?: error("supabaseCodegen.url is not set (or export SUPABASE_URL).")
        val apiKey =
            key.orNull?.takeIf { it.isNotBlank() }
                ?: error("supabaseCodegen.key is not set (or export SUPABASE_KEY). Use a key that can read your tables.")
        val pkg = packageName.get()
        val file = fileName.get()

        logger.lifecycle("Fetching Supabase schema from ${projectUrl.trimEnd('/')}/rest/v1/ …")
        val spec = SchemaFetcher.fetch(projectUrl, apiKey)
        val code = SupabaseModelGenerator.generate(spec, pkg, file)

        val packageDir = outputDir.get().asFile.resolve(pkg.replace('.', '/'))
        packageDir.mkdirs()
        val target = packageDir.resolve("$file.kt")
        target.writeText(code)
        logger.lifecycle("✓ Wrote $target")
    }
}

/** Registers the `supabaseCodegen { }` extension and the `generateSupabaseModels` task. */
public class SupabaseCodegenPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("supabaseCodegen", SupabaseCodegenExtension::class.java)
        extension.packageName.convention("supabase.generated")
        extension.fileName.convention("SupabaseModels")
        extension.outputDir.convention(project.layout.buildDirectory.dir("generated/supabase"))

        project.tasks.register("generateSupabaseModels", GenerateSupabaseModelsTask::class.java) { task ->
            task.group = "supabase"
            task.description = "Generates Kotlin @Serializable models from your Supabase schema"
            task.url.set(extension.url.orElse(project.providers.environmentVariable("SUPABASE_URL")))
            task.key.set(extension.key.orElse(project.providers.environmentVariable("SUPABASE_KEY")))
            task.packageName.set(extension.packageName)
            task.fileName.set(extension.fileName)
            task.outputDir.set(extension.outputDir)
            // The remote schema can change without any local input changing, so never report
            // up-to-date — when you run the task, you mean it.
            task.outputs.upToDateWhen { false }
        }
    }
}
