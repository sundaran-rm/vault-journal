package com.example.data.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Compresses picked photos from Android Photo Picker into compact JPEG byte arrays (~80-220 KB)
 * so they can be AES-256-GCM encrypted and synced cleanly inside Firestore's 1MB document ceiling.
 */
object PhotoAttachmentCompressor {
    private const val MAX_DIMENSION_PX = 960
    private const val INITIAL_JPEG_QUALITY = 76
    private const val MAX_RAW_BYTES = 340_000

    suspend fun compressUriToJpegBytes(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return@withContext null

            var sampleSize = 1
            val longestSide = max(boundsOptions.outWidth, boundsOptions.outHeight)
            while (longestSide / sampleSize > MAX_DIMENSION_PX * 2) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext null

            val scale = minOf(
                1f,
                MAX_DIMENSION_PX.toFloat() / max(decoded.width, decoded.height).toFloat()
            )
            val scaledBitmap = if (scale < 1f) {
                val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
                val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
            } else {
                decoded
            }

            var quality = INITIAL_JPEG_QUALITY
            var outputBytes: ByteArray
            do {
                val out = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                outputBytes = out.toByteArray()
                quality -= 14
            } while (outputBytes.size > MAX_RAW_BYTES && quality >= 34)

            outputBytes
        } catch (e: Exception) {
            null
        }
    }
}
