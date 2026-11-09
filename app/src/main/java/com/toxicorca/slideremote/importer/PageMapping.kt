package com.toxicorca.slideremote.importer

import com.toxicorca.slideremote.data.Slide

data class PageMapping(
    /** PDF page index (0-based) -> slide number (1-based). */
    val pageToSlide: Map<Int, Int>,
    /** Explanation shown to the user when the match wasn't exact. */
    val note: String?,
)

object PageMappings {
    /**
     * PowerPoint leaves hidden slides out of PDF exports by default, so a PDF can have
     * fewer pages than the deck has slides. When the page count matches the number of
     * visible slides, map pages onto visible slides only so images stay aligned.
     */
    fun compute(pageCount: Int, slides: List<Slide>): PageMapping {
        val sorted = slides.sortedBy { it.number }
        if (pageCount == sorted.size) {
            return PageMapping(sorted.indices.associateWith { sorted[it].number }, null)
        }
        val visible = sorted.filterNot { it.hiddenInShow }
        if (visible.size != sorted.size && pageCount == visible.size) {
            return PageMapping(
                visible.indices.associateWith { visible[it].number },
                "The PDF skipped hidden slides, so pictures were matched to the visible ones."
            )
        }
        val n = minOf(pageCount, sorted.size)
        return PageMapping(
            (0 until n).associateWith { sorted[it].number },
            "The PDF has $pageCount pages but the deck has ${sorted.size} slides. " +
                "Pictures were matched in order. Check that they line up."
        )
    }
}
