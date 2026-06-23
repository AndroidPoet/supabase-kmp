package io.github.androidpoet.supabase.e2e

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Base64

/**
 * SYNTHETIC test data.
 *
 * Everything here is generated from literals in this file — NOTHING is ever read
 * from disk, the network, or any real data source. This is the structural reason
 * the E2E suite can only ever upload fake data: there simply is no code path that
 * opens a real file. In particular [pngOnePixel] is the only image the suite ever
 * uploads, and it is a hardcoded constant, so a real image physically cannot
 * enter the upload path.
 */
internal object FakeData {
    /** A namespaced, obviously-fake e-mail address (e.g. `e2e-<run>-user@example.test`). */
    fun email(label: String = "user"): String = "${E2e.artifact(label)}@example.test"

    /** A throwaway password, unique per run. */
    val password: String = "Fake-Passw0rd-${E2e.runId}"

    /** A synthetic table row whose values are derived only from [E2e.runId]. */
    fun row(): JsonObject =
        buildJsonObject {
            put("name", E2e.artifact("row"))
            put("note", "synthetic e2e payload — safe to delete")
        }

    /**
     * A 1x1 transparent PNG, decoded from the Base64 literal below.
     *
     * This is the ONLY image the storage tests ever upload. It is a hardcoded
     * constant — not a file read — so no real image can ever be uploaded. You can
     * verify this by confirming there is no file/stream read anywhere in the e2e
     * source: `grep -rnE "File\\(|readBytes|FileInputStream|Paths\\.get" e2e-tests/src`.
     */
    val pngOnePixel: ByteArray =
        Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR4nGNgYGAAAAAEAAH2FzhVAAAAAElFTkSuQmCC",
        )
}
