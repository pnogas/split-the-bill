package dev.splitthebill.android.ocr

import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.splitthebill.shared.OcrEngine
import dev.splitthebill.shared.OcrLine
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * On-device Latin text recognition. Lines come back in reading order, with
 * [OcrLine.top] and [OcrLine.left] measured in pixels from the image's top left.
 */
class MlKitOcrEngine : OcrEngine {
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognize(image: ByteArray): List<OcrLine> {
        val bitmap = decode(image) ?: throw IllegalArgumentException("Couldn't open that photo.")
        val rotation = rotationDegrees(image)
        val input = InputImage.fromBitmap(bitmap, rotation)
        return suspendCancellableCoroutine { continuation ->
            recognizer.process(input)
                .addOnSuccessListener { result ->
                    bitmap.recycle()
                    if (!continuation.isActive) return@addOnSuccessListener
                    val lines = result.textBlocks
                        .flatMap { block -> block.lines }
                        .map { line ->
                            val box = line.boundingBox
                            OcrLine(
                                text = line.text,
                                top = box?.top?.toDouble() ?: 0.0,
                                left = box?.left?.toDouble() ?: 0.0,
                            )
                        }
                        .sortedWith(compareBy({ it.top }, { it.left }))
                    continuation.resume(lines)
                }
                .addOnFailureListener { error ->
                    bitmap.recycle()
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
        }
    }
}

private fun decode(bytes: ByteArray): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 2_000 || bounds.outHeight / sample > 2_000) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

private fun rotationDegrees(bytes: ByteArray): Int {
    val exif = ExifInterface(ByteArrayInputStream(bytes))
    return when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
}
