package io.github.androidpoet.supabase.realtime
public sealed interface ConnectionState {
    public data object Disconnected : ConnectionState
    public data object Connecting : ConnectionState
    public data object Connected : ConnectionState
    public data class Reconnecting(
        public val attempt: Int,
        public val nextRetryMs: Long,
    ) : ConnectionState
    public data class Failed(
        public val reason: String,
        public val attempts: Int,
    ) : ConnectionState
}
