package io.github.androidpoet.supabase.sample.chat

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
        Text("Supabase Chat Sample", style = MaterialTheme.typography.headlineSmall)
        Text("Missing Supabase config.")
        Text("Set SUPABASE_URL and SUPABASE_ANON_KEY in ~/.gradle/gradle.properties or local gradle.properties.")
        Text("Example:")
        Text("SUPABASE_URL=https://your-project.supabase.co")
        Text("SUPABASE_ANON_KEY=your-anon-key")
    }
}
