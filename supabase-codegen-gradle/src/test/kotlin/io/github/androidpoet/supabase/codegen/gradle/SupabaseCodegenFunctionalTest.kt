package io.github.androidpoet.supabase.codegen.gradle

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Runs a real Gradle build that applies the plugin through its id and configures it via the
 * `supabaseCodegen { }` DSL. This is the only test that exercises the path that actually
 * matters for consumers: Gradle's embedded Kotlin compiling a buildscript against the plugin's
 * compiled metadata. Using a blank url keeps it deterministic and offline (it fails before any
 * network call).
 */
class SupabaseCodegenFunctionalTest {
    @Test
    fun applies_via_dsl_and_fails_with_a_friendly_message_when_url_is_blank() {
        val dir = createTempDirectory("codegen-ft").toFile()
        File(dir, "settings.gradle.kts").writeText("""rootProject.name = "ft"""")
        File(dir, "build.gradle.kts").writeText(
            """
            plugins { id("io.github.androidpoet.supabase.codegen") }

            supabaseCodegen {
                url.set("")
                key.set("")
                packageName.set("com.example.db")
            }
            """.trimIndent(),
        )

        val result =
            GradleRunner
                .create()
                .withProjectDir(dir)
                .withPluginClasspath()
                // --configuration-cache also proves the task holds no non-serializable state
                // (e.g. a captured Project) at execution time.
                .withArguments("generateSupabaseModels", "--configuration-cache")
                .buildAndFail()

        assertTrue(
            "url is not set" in result.output || "SUPABASE_URL" in result.output,
            "expected a friendly missing-url failure, got:\n${result.output}",
        )
        assertTrue(
            "problems were found storing the configuration cache" !in result.output,
            "task must be configuration-cache compatible, got:\n${result.output}",
        )
    }
}
