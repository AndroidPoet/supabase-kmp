// Generated from the Supabase schema. Do not edit by hand.
// To customise a model, add extension functions/properties in your OWN file —
// this file is regenerated (overwritten) on every run.
package io.github.androidpoet.supabase.sample.chat.generated.tables

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.String

@Serializable
public data class E2eMessages(
    public val id: String,
    public val body: String,
    @SerialName("created_at")
    public val createdAt: String,
)
