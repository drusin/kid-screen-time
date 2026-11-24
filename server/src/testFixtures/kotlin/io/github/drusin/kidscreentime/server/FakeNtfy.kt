package io.github.drusin.kidscreentime.server

import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/** One request the fake ntfy received: the topic URL path with query, the headers and the (encrypted) body. */
class NtfyRequest(val method: String, val pathAndQuery: String, val headers: Map<String, String>, val body: ByteArray)

/**
 * A real HTTP endpoint standing in for the internal ntfy (the server publishes to it over the network). Start it, pass
 * [url] as `ntfyInternalUrl` to [TestServer], read [requests] after `services.push.flush()`.
 */
class FakeNtfy : AutoCloseable {
    val requests = CopyOnWriteArrayList<NtfyRequest>()

    /** The status ntfy answers with. `410` simulates a topic without subscriber. */
    @Volatile
    var status: Int = 200

    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        createContext("/") { exchange ->
            val body = exchange.requestBody.readBytes()
            val headers = exchange.requestHeaders.entries.associate { it.key.lowercase() to it.value.joinToString(",") }
            val uri = exchange.requestURI
            requests += NtfyRequest(exchange.requestMethod, uri.rawPath + (uri.rawQuery?.let { "?$it" } ?: ""), headers, body)
            exchange.sendResponseHeaders(status, -1)
            exchange.close()
        }
        start()
    }

    val url: String get() = "http://127.0.0.1:${server.address.port}"

    override fun close() = server.stop(0)
}
