package com.earthvideo.app.ui.components

import android.os.Build
import android.text.Html

/**
 * Decode HTML entities from scraped data (fixes garbled text from external sources).
 * Handles &amp; &lt; &gt; &#x27; &quot; etc. from raw HTML.
 */
fun decodeHtml(text: String?): String {
    if (text.isNullOrEmpty()) return text ?: ""
    // Fast-path: no entities to decode
    if (!text.contains('&')) return text.trim()
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString().trim()
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(text).toString().trim()
        }
    } catch (_: Exception) { text.trim() }
}
