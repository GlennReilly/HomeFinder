package au.homefinder.web

import kotlinx.browser.document
import kotlinx.browser.window
import org.khronos.webgl.Int8Array
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import kotlin.js.Date

/** How long the file's blob address is kept after the click, so the browser has started the download before it is let go. */
private const val REVOKE_DELAY_MILLIS = 60_000

/** JavaScript counts months from 0. */
private const val FIRST_MONTH = 1
private const val TWO_DIGITS = 2

/**
 * Saves [bytes] as a download named [fileName] (the PDF export, ADR 0080): a blob address and a click on a temporary link, so nothing leaves the
 * browser again and no page opens. A Kotlin ByteArray is an Int8Array in the browser, so it goes into the Blob as it is.
 */
fun downloadFile(bytes: ByteArray, fileName: String, contentType: String) {
    val blob = Blob(arrayOf(bytes.unsafeCast<Int8Array>()), BlobPropertyBag(type = contentType))
    val url = URL.createObjectURL(blob)
    val anchor = document.createElement("a") as HTMLAnchorElement
    anchor.href = url
    anchor.download = fileName
    anchor.hidden = true
    document.body?.appendChild(anchor)
    anchor.click()
    anchor.remove()
    window.setTimeout({ URL.revokeObjectURL(url) }, REVOKE_DELAY_MILLIS)
}

/** Today in this browser as "2026-11-09", for the file name. */
fun todayIsoDate(): String {
    val now = Date()
    fun two(value: Int) = value.toString().padStart(TWO_DIGITS, '0')
    return "${now.getFullYear()}-${two(now.getMonth() + FIRST_MONTH)}-${two(now.getDate())}"
}
