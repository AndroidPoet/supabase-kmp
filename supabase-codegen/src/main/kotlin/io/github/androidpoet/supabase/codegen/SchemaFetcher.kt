package io.github.androidpoet.supabase.codegen

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Fetches a project's OpenAPI schema from `GET {projectUrl}/rest/v1/`. This is a
 * read-only request; it only needs an API key whose role can see the target tables
 * (use a service/secret key at build time so RLS doesn't hide tables from codegen).
 */
internal object SchemaFetcher {
    fun fetch(projectUrl: String, apiKey: String): String {
        val uri = URI.create("${projectUrl.trimEnd('/')}/rest/v1/")
        val request =
            HttpRequest
                .newBuilder(uri)
                .header("apikey", apiKey)
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/openapi+json")
                .GET()
                .build()
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() in 200..299) {
            "Failed to fetch schema: HTTP ${response.statusCode()} from $uri"
        }
        return response.body()
    }
}
