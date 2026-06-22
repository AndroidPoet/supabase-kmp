package io.github.androidpoet.supabase.codegen.gradle

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SupabaseCodegenPluginTest {
    @Test
    fun registers_the_extension_and_task_with_conventions() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(SupabaseCodegenPlugin::class.java)

        val extension = project.extensions.findByType(SupabaseCodegenExtension::class.java)
        assertNotNull(extension, "supabaseCodegen extension should be registered")
        assertEquals("supabase.generated", extension.packageName.get())

        val task = project.tasks.findByName("generateSupabaseModels")
        assertNotNull(task, "generateSupabaseModels task should be registered")
        assertEquals("supabase", task.group)
    }
}
