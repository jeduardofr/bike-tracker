package com.biketracker.data.remote

import com.biketracker.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class TursoStatement(val sql: String, val args: List<Any?> = emptyList())

/**
 * Minimal client for Turso's Hrana-over-HTTP pipeline API (POST /v2/pipeline).
 * Statements run in a single request; any statement error fails the whole call.
 */
@Singleton
class TursoClient @Inject constructor() {

    private val baseUrl = BuildConfig.TURSO_DATABASE_URL
        .replaceFirst("libsql://", "https://")
        .trimEnd('/')
    private val token = BuildConfig.TURSO_AUTH_TOKEN

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && token.isNotBlank()

    @Throws(IOException::class)
    fun execute(statements: List<TursoStatement>) {
        if (!isConfigured) throw IOException("Turso is not configured")

        val requests = JSONArray()
        statements.forEach { stmt ->
            requests.put(
                JSONObject().put("type", "execute").put(
                    "stmt", JSONObject()
                        .put("sql", stmt.sql)
                        .put("args", JSONArray().apply { stmt.args.forEach { put(encodeArg(it)) } })
                )
            )
        }
        requests.put(JSONObject().put("type", "close"))
        val body = JSONObject().put("requests", requests).toString()

        val connection = URL("$baseUrl/v2/pipeline").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IOException("Turso request failed: HTTP $code $response")
            }

            val results = JSONObject(response).optJSONArray("results") ?: JSONArray()
            for (i in 0 until results.length()) {
                val result = results.getJSONObject(i)
                if (result.optString("type") == "error") {
                    val message = result.optJSONObject("error")?.optString("message") ?: "unknown"
                    throw IOException("Turso statement failed: $message")
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun encodeArg(value: Any?): JSONObject = when (value) {
        null -> JSONObject().put("type", "null")
        is String -> JSONObject().put("type", "text").put("value", value)
        is Boolean -> JSONObject().put("type", "integer").put("value", if (value) "1" else "0")
        is Int, is Long, is Short -> JSONObject().put("type", "integer").put("value", value.toString())
        is Float -> JSONObject().put("type", "float").put("value", value.toDouble())
        is Double -> JSONObject().put("type", "float").put("value", value)
        else -> throw IllegalArgumentException("Unsupported Turso arg type: ${value::class}")
    }
}
