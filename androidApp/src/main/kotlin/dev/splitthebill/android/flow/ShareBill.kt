package dev.splitthebill.android.flow

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

/** Copies [text] and opens the share sheet with the same summary. */
fun shareBill(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText("Bill summary", text))
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share bill"))
}
