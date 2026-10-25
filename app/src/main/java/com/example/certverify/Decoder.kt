package com.example.certverify

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.tasks.await
import java.util.zip.ZipInputStream

object Decoder {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )

    suspend fun decodeBitmap(bitmap: Bitmap): String? {
        val image = InputImage.fromBitmap(bitmap, 0)
        val codes = scanner.process(image).await()
        return codes.firstOrNull()?.rawValue
    }

    suspend fun extractQr(context: Context, uri: Uri, filename: String): String? {
        val ext = filename.substringAfterLast('.', "").lowercase()
        val bitmaps = when (ext) {
            "pdf"  -> pdfToBitmaps(context, uri)
            "docx" -> docxToBitmaps(context, uri)
            else   -> imageToBitmaps(context, uri)
        }
        for (b in bitmaps) {
            val qr = decodeBitmap(b)
            if (qr != null) return qr
        }
        return null
    }

    private fun imageToBitmaps(context: Context, uri: Uri): List<Bitmap> {
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return emptyList()
        return listOf(bmp)
    }

    private fun pdfToBitmaps(context: Context, uri: Uri): List<Bitmap> {
        val out = mutableListOf<Bitmap>()
        val pfd: ParcelFileDescriptor =
            context.contentResolver.openFileDescriptor(uri, "r") ?: return out
        PdfRenderer(pfd).use { renderer ->
            for (i in 0 until renderer.pageCount) {
                renderer.openPage(i).use { page ->
                    val scale = 3f // 3× ≈ 216 DPI — critical for small QR codes
                    val bmp = Bitmap.createBitmap(
                        (page.width * scale).toInt(),
                        (page.height * scale).toInt(),
                        Bitmap.Config.ARGB_8888
                    )
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    out.add(bmp)
                }
            }
        }
        pfd.close()
        return out
    }

    private fun docxToBitmaps(context: Context, uri: Uri): List<Bitmap> {
        val out = mutableListOf<Bitmap>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name.startsWith("word/media/") &&
                        Regex(".*\\.(png|jpe?g|gif|bmp|webp)$", RegexOption.IGNORE_CASE)
                            .matches(name)) {
                        val bytes = zip.readBytes()
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { out.add(it) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return out
    }
}