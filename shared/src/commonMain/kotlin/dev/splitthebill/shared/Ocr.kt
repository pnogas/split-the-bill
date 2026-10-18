package dev.splitthebill.shared

/**
 * A single text line from a receipt photo.
 *
 * [top] and [left] are distances, in pixels, from the top-left of the image.
 * Later parsing groups lines into items using that reading order.
 */
data class OcrLine(
    val text: String,
    val top: Double,
    val left: Double,
)

/**
 * Platform OCR entry point. Android (ML Kit) and iOS (Vision) will provide
 * implementations; the shared parser will consume [OcrLine]s.
 */
interface OcrEngine {
    suspend fun recognize(image: ByteArray): List<OcrLine>
}
