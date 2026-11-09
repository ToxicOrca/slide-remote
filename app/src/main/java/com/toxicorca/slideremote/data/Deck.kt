package com.toxicorca.slideremote.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Slide(
    /** 1-based position in the PowerPoint file; this is what gets typed to jump. */
    val number: Int,
    val title: String = "",
    /** Marked hidden in PowerPoint (still reachable by number). */
    val hiddenInShow: Boolean = false,
    val hasImage: Boolean = false,
) {
    val displayTitle: String get() = title.ifBlank { "Slide $number" }
}

data class Deck(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val slides: List<Slide>,
    val columns: Int = 3,
    /** Grid order: slide numbers in the order the tiles are laid out. */
    val order: List<Int> = slides.map { it.number },
    /** Last known slide on screen (the app's best guess). */
    val currentSlide: Int = 1,
    /** Width / height of the slide images. */
    val aspect: Float = 16f / 9f,
    /** Bumped whenever images change, to bust the thumbnail cache. */
    val imageVersion: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val slideCount: Int get() = slides.size
    val hasImages: Boolean get() = slides.any { it.hasImage }

    fun slide(number: Int): Slide? = slides.getOrNull(number - 1)?.takeIf { it.number == number }
        ?: slides.firstOrNull { it.number == number }

    /** Slides in grid order. Repairs the order list if it drifted out of sync. */
    fun orderedSlides(): List<Slide> = normalizedOrder().mapNotNull { slide(it) }

    fun normalizedOrder(): List<Int> {
        val valid = slides.map { it.number }.toSet()
        val kept = order.filter { it in valid }.distinct()
        val missing = slides.map { it.number }.filter { it !in kept }
        return kept + missing
    }

    /** Grow or shrink to [count] slides, keeping names and grid order for slides that remain. */
    fun withSlideCount(count: Int): Deck {
        val n = count.coerceIn(1, MAX_SLIDES)
        val newSlides = (1..n).map { num -> slides.firstOrNull { it.number == num } ?: Slide(num) }
        val newOrder = order.filter { it <= n } + (1..n).filter { it !in order }
        return copy(
            slides = newSlides,
            order = newOrder.distinct(),
            currentSlide = currentSlide.coerceIn(1, n),
        )
    }

    fun withSlide(updated: Slide): Deck =
        copy(slides = slides.map { if (it.number == updated.number) updated else it })

    fun swap(a: Int, b: Int): Deck {
        val o = normalizedOrder().toMutableList()
        val ia = o.indexOf(a)
        val ib = o.indexOf(b)
        if (ia < 0 || ib < 0 || ia == ib) return this
        o[ia] = b
        o[ib] = a
        return copy(order = o)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("version", 1)
        put("id", id)
        put("name", name)
        put("columns", columns)
        put("currentSlide", currentSlide)
        put("aspect", aspect.toDouble())
        put("imageVersion", imageVersion)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("order", JSONArray().apply { order.forEach { put(it) } })
        put("slides", JSONArray().apply {
            slides.forEach { s ->
                put(JSONObject().apply {
                    put("number", s.number)
                    put("title", s.title)
                    put("hiddenInShow", s.hiddenInShow)
                    put("hasImage", s.hasImage)
                })
            }
        })
    }

    companion object {
        const val MAX_SLIDES = 999
        const val MIN_COLUMNS = 1
        const val MAX_COLUMNS = 6

        fun blank(name: String, count: Int): Deck {
            val n = count.coerceIn(1, MAX_SLIDES)
            return Deck(name = name, slides = (1..n).map { Slide(it) })
        }

        fun fromJson(o: JSONObject): Deck {
            val slidesJson = o.getJSONArray("slides")
            val slides = (0 until slidesJson.length()).map { i ->
                val s = slidesJson.getJSONObject(i)
                Slide(
                    number = s.getInt("number"),
                    title = s.optString("title", ""),
                    hiddenInShow = s.optBoolean("hiddenInShow", false),
                    hasImage = s.optBoolean("hasImage", false),
                )
            }.sortedBy { it.number }
            val orderJson = o.optJSONArray("order")
            val order = if (orderJson != null) {
                (0 until orderJson.length()).map { orderJson.getInt(it) }
            } else {
                slides.map { it.number }
            }
            return Deck(
                id = o.getString("id"),
                name = o.optString("name", "Untitled"),
                slides = slides,
                columns = o.optInt("columns", 3).coerceIn(MIN_COLUMNS, MAX_COLUMNS),
                order = order,
                currentSlide = o.optInt("currentSlide", 1),
                aspect = o.optDouble("aspect", 16.0 / 9.0).toFloat().takeIf { it in 0.3f..4f } ?: (16f / 9f),
                imageVersion = o.optLong("imageVersion", 0),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
            )
        }
    }
}
