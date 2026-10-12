package dev.splitthebill.shared

/** Present so androidMain and iosMain each have a real actual to compile. */
expect fun platformName(): String

object AppInfo {
    const val tagline = "Photograph a receipt, then split it across the table."
}
