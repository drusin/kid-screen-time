package io.github.drusin.kidscreentime.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** The modules of a QR code: `true` is a dark module. Always square, without the quiet zone. */
class QrCodeMatrix(val size: Int, private val dark: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = dark[y * size + x]

    companion object {
        fun encode(content: String): QrCodeMatrix {
            val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0, EncodeHintType.CHARACTER_SET to "UTF-8")
            val bits = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
            val dark = BooleanArray(bits.width * bits.height) { bits[it % bits.width, it / bits.width] }
            return QrCodeMatrix(bits.width, dark)
        }
    }
}
