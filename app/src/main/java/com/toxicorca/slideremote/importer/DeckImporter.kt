package com.toxicorca.slideremote.importer

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.toxicorca.slideremote.data.Deck
import com.toxicorca.slideremote.data.DeckRepository
import com.toxicorca.slideremote.data.Slide
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class FileKind { PPTX, PDF, UNKNOWN }

data class ImportOutcome(val deck: Deck, val note: String? = null)

class DeckImporter(
    private val context: Context,
    private val repo: DeckRepository,
) {
    private val resolver get() = context.contentResolver

    fun kindOf(uri: Uri): FileKind {
        val type = runCatching { resolver.getType(uri) }.getOrNull().orEmpty()
        val name = displayName(uri).orEmpty().lowercase()
        return when {
            type == PPTX_MIME || name.endsWith(".pptx") || name.endsWith(".ppsx") -> FileKind.PPTX
            type == PDF_MIME || name.endsWith(".pdf") -> FileKind.PDF
            name.endsWith(".ppt") -> throw ImportException(
                "Old .ppt files aren't supported. Save it as .pptx in PowerPoint first."
            )
            else -> FileKind.UNKNOWN
        }
    }

    /** New deck from a .pptx: slide count, titles and hidden flags. No images. */
    suspend fun importPptx(uri: Uri): ImportOutcome = withContext(Dispatchers.IO) {
        val info = readPptx(uri)
        // The file name is usually better than core.xml's title, which is often stale template text.
        val name = baseName(uri) ?: info.title ?: "Presentation"
        val slides = info.slides.mapIndexed { i, s ->
            Slide(number = i + 1, title = s.title, hiddenInShow = s.hidden)
        }
        val deck = repo.save(Deck(name = name, slides = slides))
        val hiddenCount = slides.count { it.hiddenInShow }
        ImportOutcome(
            deck,
            if (hiddenCount > 0) "$hiddenCount hidden slide(s) found. They're still on the grid and you can jump to them." else null
        )
    }

    /** New deck from a PDF export: one slide per page, with pictures. */
    suspend fun importPdf(uri: Uri, onProgress: (Float) -> Unit = {}): ImportOutcome =
        withContext(Dispatchers.IO) {
            val tmp = copyToCache(uri)
            try {
                PdfDoc.open(tmp).use { doc ->
                    if (doc.pageCount == 0) throw ImportException("That PDF has no pages.")
                    val count = doc.pageCount.coerceAtMost(Deck.MAX_SLIDES)
                    var deck = Deck.blank(baseName(uri) ?: "Presentation", count)
                        .copy(aspect = doc.aspect())
                    repo.deckDir(deck.id).mkdirs()
                    for (i in 0 until count) {
                        val bmp = doc.render(i, THUMB_WIDTH)
                        writeJpeg(bmp, repo.thumbFile(deck.id, i + 1))
                        bmp.recycle()
                        onProgress((i + 1f) / count)
                    }
                    deck = deck.copy(
                        slides = deck.slides.map { it.copy(hasImage = true) },
                        imageVersion = System.currentTimeMillis(),
                    )
                    ImportOutcome(repo.save(deck), PDF_ONLY_NOTE)
                }
            } finally {
                tmp.delete()
            }
        }

    /** Add or replace slide pictures on an existing deck from a PDF export. */
    suspend fun attachPdf(deck: Deck, uri: Uri, onProgress: (Float) -> Unit = {}): ImportOutcome =
        withContext(Dispatchers.IO) {
            val tmp = copyToCache(uri)
            try {
                PdfDoc.open(tmp).use { doc ->
                    if (doc.pageCount == 0) throw ImportException("That PDF has no pages.")
                    val mapping = PageMappings.compute(doc.pageCount, deck.slides)
                    deck.slides.forEach { repo.thumbFile(deck.id, it.number).delete() }
                    repo.deckDir(deck.id).mkdirs()
                    val withImage = HashSet<Int>()
                    val total = mapping.pageToSlide.size.coerceAtLeast(1)
                    var done = 0
                    // Old pictures are already deleted, so a failure part-way must still save
                    // which slides actually got images (and bust the cache).
                    val failure = try {
                        for ((page, slideNumber) in mapping.pageToSlide.toSortedMap()) {
                            val bmp = doc.render(page, THUMB_WIDTH)
                            writeJpeg(bmp, repo.thumbFile(deck.id, slideNumber))
                            bmp.recycle()
                            withImage += slideNumber
                            done++
                            onProgress(done.toFloat() / total)
                        }
                        null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        e
                    }
                    val updated = deck.copy(
                        slides = deck.slides.map { it.copy(hasImage = it.number in withImage) },
                        aspect = doc.aspect(),
                        imageVersion = System.currentTimeMillis(),
                    )
                    val saved = repo.save(updated)
                    if (failure != null) {
                        throw ImportException(
                            "Stopped after $done of ${mapping.pageToSlide.size} pictures. Try exporting the PDF again."
                        )
                    }
                    ImportOutcome(saved, mapping.note)
                }
            } finally {
                tmp.delete()
            }
        }

    /**
     * Refresh names / hidden flags / slide count from an updated .pptx, keeping the
     * existing grid layout and pictures where slide numbers still exist.
     */
    suspend fun refreshFromPptx(deck: Deck, uri: Uri): ImportOutcome = withContext(Dispatchers.IO) {
        val info = readPptx(uri)
        val resized = deck.withSlideCount(info.slides.size)
        val slides = resized.slides.map { s ->
            val src = info.slides[s.number - 1]
            s.copy(title = src.title, hiddenInShow = src.hidden)
        }
        val changedCount = info.slides.size != deck.slideCount
        ImportOutcome(
            repo.save(resized.copy(slides = slides)),
            if (changedCount) "Slide count changed from ${deck.slideCount} to ${info.slides.size}. Re-add pictures if they're out of date." else null
        )
    }

    // ------------------------------------------------------------------ helpers

    private fun readPptx(uri: Uri): PptxInfo {
        val input = resolver.openInputStream(uri) ?: throw ImportException("Couldn't open that file.")
        return try {
            input.use { PptxReader.read(it) }
        } catch (e: ImportException) {
            throw e
        } catch (e: Exception) {
            throw ImportException("Couldn't read that PowerPoint file.")
        }
    }

    /** PdfRenderer needs a seekable file descriptor, which some providers don't give. */
    private fun copyToCache(uri: Uri): File {
        val out = File.createTempFile("import", ".pdf", context.cacheDir)
        try {
            val input = resolver.openInputStream(uri) ?: throw ImportException("Couldn't open that file.")
            input.use { i -> FileOutputStream(out).use { o -> i.copyTo(o) } }
        } catch (e: ImportException) {
            out.delete()
            throw e
        } catch (e: Exception) {
            out.delete()
            throw ImportException("Couldn't copy that file.")
        }
        return out
    }

    private fun writeJpeg(bmp: Bitmap, file: File) {
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    }

    fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun baseName(uri: Uri): String? =
        displayName(uri)?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }

    companion object {
        const val PPTX_MIME = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        const val PDF_MIME = "application/pdf"
        private const val THUMB_WIDTH = 720

        /** A PDF alone can't tell us about hidden slides, which shift every later slide number. */
        private const val PDF_ONLY_NOTE =
            "PowerPoint leaves hidden slides out of PDFs by default. If this presentation has hidden slides, " +
                "tile numbers after them will be off and taps will jump to the wrong slide.\n\n" +
                "To fix it, import the .pptx too (then add this PDF to it for pictures), or export the PDF again " +
                "with \"Include hidden slides\" turned on (File → Export → Create PDF/XPS → Options)."
    }
}
