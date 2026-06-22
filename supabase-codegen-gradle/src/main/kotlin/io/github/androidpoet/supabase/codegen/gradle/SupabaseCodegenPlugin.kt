package io.github.androidpoet.supabase.codegen.gradle

import io.github.androidpoet.supabase.codegen.SchemaFetcher
import io.github.androidpoet.supabase.codegen.SupabaseModelGenerator
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Configures [the generate task][GenerateSupabaseModelsTask]. Add a `supabaseCodegen { }`
 * block to your build:
 *
 * ```kotlin
 * supabaseCodegen {
 *     url.set(providers.environmentVariable("SUPABASE_URL"))
 *     key.set(providers.environmentVariable("SUPABASE_KEY"))
 *     packageName.set("com.example.db")
 *     autoSync.set(true) // regenerate from the live schema on every build
 * }
 * ```
 *
 * `url`/`key` fall back to the `SUPABASE_URL` / `SUPABASE_KEY` environment variables.
 *
 * With [autoSync] on, the models are regenerated into [outputDir] (defaults to
 * `build/generated/supabase`, which you should gitignore) before every Kotlin compile, so they
 * always track the database — the SQLDelight/RevenueCat "generated code is build output, never
 * edited" model. Add the output as a source directory, e.g.
 * `kotlin.sourceSets.commonMain { kotlin.srcDir(layout.buildDirectory.dir("generated/supabase")) }`.
 */
public abstract class SupabaseCodegenExtension {
    /** Project URL, e.g. `https://<ref>.supabase.co`. Falls back to `SUPABASE_URL`. */
    public abstract val url: Property<String>

    /** API key whose role can read the target tables. Falls back to `SUPABASE_KEY`. */
    public abstract val key: Property<String>

    /** Package for the generated models. Defaults to `supabase.generated`. */
    public abstract val packageName: Property<String>

    /** Where to write the generated files. Defaults to `build/generated/supabase`. */
    public abstract val outputDir: DirectoryProperty

    /**
     * When `true`, regenerate the models from the live schema before every Kotlin compile so they
     * stay in sync with the database (output goes to [outputDir]; gitignore it and never hand-edit).
     * Requires network + a key on every build; if neither `url`/`key` nor the env vars are set, the
     * build skips codegen (with a warning) rather than failing, so it stays buildable offline/in CI
     * without credentials. A reachable-but-failing fetch still fails the build. Defaults to `false`
     * (the on-demand `generateSupabaseModels` task, committed like `supabase gen types`).
     */
    public abstract val autoSync: Property<Boolean>
}

/**
 * Fetches the Supabase schema and writes one Kotlin file per table/enum of `@Serializable` models.
 * By default this is an on-demand task; with `supabaseCodegen.autoSync = true` it is wired to run
 * before every Kotlin compile so the models always track the live schema.
 *
 * Everything is `@Internal`: this is a side-effecting command whose real input (the remote
 * schema) Gradle can't observe, so tracking inputs/outputs would only add footguns — notably
 * an `@OutputDirectory` pointed at a hand-written source dir would clash with the Kotlin
 * compile's source set. Keeping properties internal also lets the action raise its own clear
 * errors instead of Gradle's generic "no value" validation firing first.
 */
@DisableCachingByDefault(because = "Fetches a remote schema over the network; always regenerates")
public abstract class GenerateSupabaseModelsTask : DefaultTask() {
    @get:Internal
    public abstract val url: Property<String>

    @get:Internal
    public abstract val key: Property<String>

    @get:Internal
    public abstract val packageName: Property<String>

    @get:Internal
    public abstract val outputDir: DirectoryProperty

    /** Set by the plugin from `supabaseCodegen.autoSync`; controls the missing-credentials behaviour. */
    @get:Internal
    public abstract val autoSync: Property<Boolean>

    @TaskAction
    public fun generate() {
        val auto = autoSync.getOrElse(false)
        val projectUrl = url.orNull?.takeIf { it.isNotBlank() }
        val apiKey = key.orNull?.takeIf { it.isNotBlank() }

        // In auto-sync mode an unconfigured build (no url/key) must not fail — that would break
        // offline/CI builds and contributors who don't have a Supabase instance. Skip instead.
        // A configured-but-unreachable fetch below still throws and fails the build.
        if (projectUrl == null || apiKey == null) {
            if (auto) {
                logger.warn(
                    "supabaseCodegen: SUPABASE_URL/SUPABASE_KEY not set — skipping auto-sync codegen for this build.",
                )
                return
            }
            throw GradleException(
                when (projectUrl) {
                    null -> "supabaseCodegen.url is not set (or export SUPABASE_URL)."
                    else -> "supabaseCodegen.key is not set (or export SUPABASE_KEY). Use a key that can read your tables."
                },
            )
        }

        logger.lifecycle("Fetching Supabase schema from ${projectUrl.trimEnd('/')}/rest/v1/ …")
        val spec = SchemaFetcher.fetch(projectUrl, apiKey)
        val files = SupabaseModelGenerator.generate(spec, packageName.get())

        val root = outputDir.get().asFile
        for (file in files) {
            val target = root.resolve(file.relativePath)
            target.parentFile.mkdirs()
            target.writeText(file.contents)
            logger.lifecycle("✓ Wrote $target")
        }
        logger.lifecycle("✓ Generated ${files.size} file(s)")
    }
}

/** Registers the `supabaseCodegen { }` extension and the `generateSupabaseModels` task. */
public class SupabaseCodegenPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("supabaseCodegen", SupabaseCodegenExtension::class.java)
        extension.packageName.convention("supabase.generated")
        extension.outputDir.convention(project.layout.buildDirectory.dir("generated/supabase"))
        extension.autoSync.convention(false)

        val generateTask =
            project.tasks.register("generateSupabaseModels", GenerateSupabaseModelsTask::class.java) { task ->
                task.group = "supabase"
                task.description = "Generates Kotlin @Serializable models from your Supabase schema"
                task.url.set(extension.url.orElse(project.providers.environmentVariable("SUPABASE_URL")))
                task.key.set(extension.key.orElse(project.providers.environmentVariable("SUPABASE_KEY")))
                task.packageName.set(extension.packageName)
                task.outputDir.set(extension.outputDir)
                task.autoSync.set(extension.autoSync)
                // The remote schema can change without any local input changing, so never report
                // up-to-date — when this runs, it means it.
                task.outputs.upToDateWhen { false }
            }

        // Auto-sync: regenerate before every Kotlin compile so the models track the live schema.
        // Done in afterEvaluate so the Kotlin/Android compile tasks (and the user's autoSync choice)
        // are registered. The generated dir still has to be added as a source set by the consumer.
        project.afterEvaluate {
            if (extension.autoSync.getOrElse(false)) {
                project.tasks
                    .matching { it.name.startsWith("compile") && it.name.contains("Kotlin") }
                    .configureEach { it.dependsOn(generateTask) }
            }
        }
    }
}
