package io.github.drusin.kidscreentime.api

/**
 * Content of the QR code a Elterngerät shows for a Kopplungscode: `kidscreentime://pair?server=<URL>&code=<CODE>`.
 * Both values are percent-encoded. Pure Kotlin, so app and tests share one encoder and one parser.
 */
object PairingQr {
    private const val PREFIX = "kidscreentime://pair?"

    data class Content(val serverUrl: String, val code: String)

    fun encode(serverUrl: String, code: String): String = PREFIX + "server=${escape(serverUrl)}&code=${escape(code)}"

    /** Returns null when [raw] is not a Kopplungs-QR-Code of this app. */
    fun parse(raw: String): Content? {
        val text = raw.trim()
        if (!text.startsWith(PREFIX)) return null
        val parameters = text.removePrefix(PREFIX).split('&').mapNotNull { part ->
            val index = part.indexOf('=')
            if (index <= 0) null else part.substring(0, index) to unescape(part.substring(index + 1))
        }.toMap()
        val server = parameters["server"]?.takeIf { it.isNotBlank() } ?: return null
        val code = parameters["code"]?.takeIf { it.isNotBlank() } ?: return null
        return Content(server, code)
    }

    private fun escape(value: String): String = buildString {
        for (byte in value.encodeToByteArray()) {
            val c = byte.toInt() and 0xFF
            if (c < 128 && (c.toChar().isLetterOrDigit() || c.toChar() in "-._~")) append(c.toChar())
            else append('%').append(HEX[c shr 4]).append(HEX[c and 0xF])
        }
    }

    private fun unescape(value: String): String {
        val bytes = ArrayList<Byte>()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '%' && i + 2 < value.length) {
                val hex = value.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex != null) {
                    bytes += hex.toByte()
                    i += 3
                    continue
                }
            }
            bytes += c.toString().encodeToByteArray().toList()
            i++
        }
        return bytes.toByteArray().decodeToString()
    }

    private const val HEX = "0123456789ABCDEF"
}
