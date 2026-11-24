package io.github.drusin.kidscreentime.qr

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

sealed interface QrScanResult {
    data class Scanned(val text: String) : QrScanResult

    /** The person closed the scanner. */
    data object Cancelled : QrScanResult

    /** The scanner could not run (no Google Play services, module not installed yet, no camera). */
    data object Unavailable : QrScanResult
}

/** Scans one QR code. A seam so ViewModels and tests do not need Google Play services. */
fun interface QrScanner {
    suspend fun scan(): QrScanResult
}

/**
 * Google Code Scanner (Google Play services): the scanner is its own screen of Play services, so this app needs no
 * camera permission and no camera code. Pass an Activity context; the module downloads itself on first use.
 */
class GmsQrScanner(private val context: Context) : QrScanner {
    override suspend fun scan(): QrScanResult = suspendCancellableCoroutine { continuation ->
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { barcode ->
                val text = barcode.rawValue
                continuation.resume(if (text.isNullOrBlank()) QrScanResult.Unavailable else QrScanResult.Scanned(text))
            }
            .addOnCanceledListener { continuation.resume(QrScanResult.Cancelled) }
            .addOnFailureListener { continuation.resume(QrScanResult.Unavailable) }
    }
}
