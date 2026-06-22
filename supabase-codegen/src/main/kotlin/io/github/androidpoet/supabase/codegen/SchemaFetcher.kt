package io.github.androidpoet.supabase.codegen

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Fetches a project's OpenAPI schema from `GET {projectUrl}/rest/v1/`. This is a
 * read-only request; it only needs an API key whose role can see the target tables
 * (use a service/secret key at build time so RLS doesn't hide tables from codegen).
 */
public object SchemaFetcher {
    private val client =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()

    public fun fetch(projectUrl: String, apiKey: String): String {
        val uri = URI.create("${projectUrl.trimEnd('/')}/rest/v1/")
        val request =
            HttpRequest
                .newBuilder(uri)
                // Bound the whole request so a stalled connection fails the build instead of
                // hanging it forever (this runs inside the Gradle/compile pipeline).
                .timeout(Duration.ofSeconds(60))
                .header("apikey", apiKey)
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/openapi+json")
                .GET()
                .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() in 200..299) {
            "Failed to fetch schema: HTTP ${response.statusCode()} from $uri"
        }
        return response.body()
    }
}
