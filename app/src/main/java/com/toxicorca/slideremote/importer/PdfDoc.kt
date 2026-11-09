package com.toxicorca.slideremote.importer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File

/** Thin wrapper over the platform [PdfRenderer] that renders pages to white-backed bitmaps. */
class PdfDoc private constructor(
    private val fd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {

    val pageCount: Int get() = renderer.pageCount

    /** Width / height of the first page, or 16:9 if unknown. */
    fun aspect(): Float {
        if (pageCount == 0) return 16f / 9f
        renderer.openPage(0).use { page ->
            if (page.height <= 0) return 16f / 9f
            return page.width.toFloat() / page.height.toFloat()
        }
    }

    fun render(index: Int, targetWidth: Int): Bitmap {
        renderer.openPage(index).use { page ->
            val w = targetWidth.coerceAtLeast(64)
            val h = if (page.width > 0) {
                (w.toLong() * page.height / page.width).toInt().coerceIn(32, 4096)
            } else {
                w * 9 / 16
            }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE) // PDF pages render transparent by default
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bmp
        }
    }

    override fun close() {
        try {
            renderer.close()
        } catch (_: Throwable) {
        }
        try {
            fd.close()
        } catch (_: Throwable) {
        }
    }

    companion object {
        fun open(file: File): PdfDoc {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = try {
                PdfRenderer(fd)
            } catch (e: SecurityException) {
                fd.close()
                throw ImportException("That PDF is password-protected. Export it again without a password.")
            } catch (e: Exception) {
                fd.close()
                throw ImportException("Couldn't read that PDF.")
            }
            return PdfDoc(fd, renderer)
        }
    }
}
