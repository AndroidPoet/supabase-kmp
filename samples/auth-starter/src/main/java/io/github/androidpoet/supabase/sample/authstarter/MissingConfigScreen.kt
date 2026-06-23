package io.github.androidpoet.supabase.sample.authstarter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MissingConfigScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Supabase Auth Starter", style = MaterialTheme.typography.headlineSmall)
        Text("Missing Supabase config.")
        Text("Set these in ~/.gradle/gradle.properties or local gradle.properties:")
        Text("SUPABASE_URL=https://your-project.supabase.co")
        Text("SUPABASE_ANON_KEY=your-anon-key")
    }
}
