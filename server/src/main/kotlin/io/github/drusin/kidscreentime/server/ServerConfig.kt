package io.github.drusin.kidscreentime.server

import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.time.DateTimeException
import java.time.ZoneId

/** Thrown when required configuration is missing or invalid. The server must not start then. */
class ConfigurationException(val problems: List<String>) :
    RuntimeException("Ungültige Konfiguration:\n" + problems.joinToString("\n") { "- $it" })

/**
 * Server configuration, read from environment variables ([fromEnvironment]).
 *
 * Required: `ADMIN_PASSWORD`, `FAMILY_TIME_ZONE` (IANA id, independent of the container zone),
 * `PUBLIC_SERVER_URL`, `NTFY_PUBLIC_URL`, `NTFY_INTERNAL_URL`.
 * Optional: `DATABASE_PATH` (default `data/kid-screen-time.db`), `PORT` (default 8080),
 * `ADMIN_PORT` (default 8081).
 */
data class ServerConfig(
    val adminPassword: String,
    val familyZone: ZoneId,
    val publicServerUrl: String,
    val ntfyPublicUrl: String,
    val ntfyInternalUrl: String,
    val databasePath: Path,
    val port: Int = DEFAULT_PORT,
    val adminPort: Int = DEFAULT_ADMIN_PORT,
) {
    companion object {
        const val DEFAULT_PORT = 8080
        const val DEFAULT_ADMIN_PORT = 8081

        fun fromEnvironment(env: Map<String, String>): ServerConfig {
            val problems = mutableListOf<String>()

            fun required(name: String): String? =
                env[name]?.trim()?.takeIf { it.isNotEmpty() }.also { if (it == null) problems += "$name fehlt" }

            fun url(name: String): String? = required(name)?.let { value ->
                val uri = runCatching { URI(value) }.getOrNull()
                if (uri == null || uri.scheme !in setOf("http", "https") || uri.host == null) {
                    problems += "$name ist keine gültige http(s)-URL"
                    null
                } else {
                    value.trimEnd('/')
                }
            }

            fun port(name: String, default: Int): Int {
                val raw = env[name]?.trim()?.takeIf { it.isNotEmpty() } ?: return default
                val parsed = raw.toIntOrNull()?.takeIf { it in 1..65535 }
                if (parsed == null) problems += "$name ist kein gültiger Port"
                return parsed ?: default
            }

            val password = required("ADMIN_PASSWORD")
            val zone = required("FAMILY_TIME_ZONE")?.let {
                try {
                    ZoneId.of(it)
                } catch (_: DateTimeException) {
                    problems += "FAMILY_TIME_ZONE ist keine bekannte Zeitzone"
                    null
                }
            }
            val publicUrl = url("PUBLIC_SERVER_URL")
            val ntfyPublic = url("NTFY_PUBLIC_URL")
            val ntfyInternal = url("NTFY_INTERNAL_URL")
            val port = port("PORT", DEFAULT_PORT)
            val adminPort = port("ADMIN_PORT", DEFAULT_ADMIN_PORT)
            if (port == adminPort) problems += "PORT und ADMIN_PORT müssen verschieden sein"

            if (problems.isNotEmpty()) throw ConfigurationException(problems)
            return ServerConfig(
                adminPassword = password!!,
                familyZone = zone!!,
                publicServerUrl = publicUrl!!,
                ntfyPublicUrl = ntfyPublic!!,
                ntfyInternalUrl = ntfyInternal!!,
                databasePath = Paths.get(env["DATABASE_PATH"]?.takeIf { it.isNotBlank() } ?: "data/kid-screen-time.db"),
                port = port,
                adminPort = adminPort,
            )
        }
    }
}
