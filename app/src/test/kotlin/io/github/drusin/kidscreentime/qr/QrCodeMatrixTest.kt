package io.github.drusin.kidscreentime.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import io.github.drusin.kidscreentime.api.PairingQr
import kotlin.test.Test
import kotlin.test.assertEquals

class QrCodeMatrixTest {
    /** Draws the matrix like the screen does (dark on white, quiet zone) and lets a real QR reader decode it. */
    private fun decode(matrix: QrCodeMatrix): String {
        val quiet = 4
        val scale = 4
        val side = (matrix.size + 2 * quiet) * scale
        val pixels = IntArray(side * side) { 0xFFFFFFFF.toInt() }
        for (y in 0 until matrix.size) for (x in 0 until matrix.size) {
            if (matrix[x, y]) {
                for (dy in 0 until scale) for (dx in 0 until scale) {
                    pixels[((y + quiet) * scale + dy) * side + (x + quiet) * scale + dx] = 0xFF000000.toInt()
                }
            }
        }
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))).text
    }

    @Test
    fun `der QR-Code enthält Server-URL und Kopplungscode, so wie ein Scanner sie liest`() {
        val content = PairingQr.encode("https://kst.example.org", "ABCD-EFGH")

        val scanned = decode(QrCodeMatrix.encode(content))

        assertEquals(PairingQr.Content("https://kst.example.org", "ABCD-EFGH"), PairingQr.parse(scanned))
    }
}
