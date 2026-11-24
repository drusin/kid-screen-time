package io.github.drusin.kidscreentime.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import io.github.drusin.kidscreentime.qr.QrCodeMatrix

/** A QR code, dark on white with a quiet zone, whatever the theme is (scanners need the contrast). */
@Composable
fun QrCode(content: String, modifier: Modifier = Modifier) {
    val matrix = remember(content) { QrCodeMatrix.encode(content) }
    Canvas(modifier = modifier.fillMaxWidth().aspectRatio(1f)) {
        drawRect(Color.White)
        val quiet = 4
        val module = size.width / (matrix.size + 2 * quiet)
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (matrix[x, y]) {
                    drawRect(Color.Black, Offset((x + quiet) * module, (y + quiet) * module), Size(module + 0.5f, module + 0.5f))
                }
            }
        }
    }
}
