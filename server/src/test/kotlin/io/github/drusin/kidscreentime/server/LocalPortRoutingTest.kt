package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.api.ApiPaths
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.engine.embeddedServer
import io.ktor.server.testing.testApplication
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir

/**
 * Routes are chosen by the local port of the connection, never by the Host header (behind Traefik the Host
 * header carries no port). Admin routes stay reachable only on the admin port, whatever the Host header says.
 */
class LocalPortRoutingTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `Oeffentliche Routen antworten auch mit Host-Header ohne Port oder mit Port 443`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)

        for (host in listOf("kidscreentime.example.org", "kidscreentime.example.org:443")) {
            val response = client.get(TestServer.PUBLIC + ApiPaths.HEALTH) { header(HttpHeaders.Host, host) }
            assertEquals(HttpStatusCode.OK, response.status, "Host: $host")
        }
    }

    @Test
    fun `Admin ist auf dem oeffentlichen Port nicht erreichbar, auch nicht mit Admin-Port im Host-Header`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)

        for (host in listOf("x:8081", "localhost:8081", "x")) {
            val response = client.get(TestServer.PUBLIC + ADMIN_PATH) { header(HttpHeaders.Host, host) }
            assertEquals(HttpStatusCode.NotFound, response.status, "Host: $host")
        }
    }

    @Test
    fun `Admin antwortet auf dem Admin-Port unabhaengig vom Host-Header`() = testApplication {
        val server = TestServer(dir).also { it.install(this) }
        val client = server.client(this)

        for (host in listOf("x:8080", "kidscreentime.example.org", "x:8081")) {
            val response = client.get(TestServer.ADMIN + ADMIN_PATH) { header(HttpHeaders.Host, host) }
            assertEquals(HttpStatusCode.OK, response.status, "Host: $host")
        }
    }

    // testApplication has no real sockets, so the same rules are checked once more on a real CIO server.
    @Test
    fun `Echter Server trennt oeffentlich und Admin nach dem lokalen Port`() {
        val publicPort = freePort()
        val adminPort = freePort()
        val server = TestServer(dir, publicPort = publicPort, adminPort = adminPort)
        val engine = embeddedServer(CIO, configure = {
            connectors += EngineConnectorBuilder().apply { host = "127.0.0.1"; port = publicPort }
            connectors += EngineConnectorBuilder().apply { host = "127.0.0.1"; port = adminPort }
        }) {
            serverModule(server.config, server.clock, server.clock, server.services, runScheduler = false)
        }.start(wait = false)
        try {
            for (host in listOf("kidscreentime.example.org", "kidscreentime.example.org:443", "x:$adminPort")) {
                assertEquals(200, rawGet(publicPort, ApiPaths.HEALTH, host), "public /health, Host: $host")
                assertEquals(404, rawGet(publicPort, ADMIN_PATH, host), "public /admin, Host: $host")
            }
            for (host in listOf("kidscreentime.example.org", "x:$publicPort", "x:$adminPort")) {
                assertEquals(200, rawGet(adminPort, ADMIN_PATH, host), "admin /admin, Host: $host")
                assertEquals(404, rawGet(adminPort, ApiPaths.HEALTH, host), "admin /health, Host: $host")
            }
        } finally {
            engine.stop(0, 0)
        }
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    /** Plain HTTP/1.1 over a socket, so the Host header is exactly what the test says. Returns the status code. */
    private fun rawGet(port: Int, path: String, host: String): Int =
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 10_000
            socket.getOutputStream().write("GET $path HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n".toByteArray())
            socket.getOutputStream().flush()
            val statusLine = socket.getInputStream().bufferedReader().readLine()
            statusLine.split(" ")[1].toInt()
        }
}
