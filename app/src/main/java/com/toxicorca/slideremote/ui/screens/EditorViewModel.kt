package com.toxicorca.slideremote.ui.screens

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.toxicorca.slideremote.app
import com.toxicorca.slideremote.data.Deck
import com.toxicorca.slideremote.data.Slide
import com.toxicorca.slideremote.importer.ImportException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class EditorViewModel(
    application: Application,
    savedState: SavedStateHandle,
) : AndroidViewModel(application) {
    private val app = application.app
    val deckId: String = checkNotNull(savedState["deckId"])

    val deck: StateFlow<Deck?> = app.decks.observe(deckId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, app.decks.get(deckId))

    val loaded: StateFlow<Boolean> = app.decks.loaded

    /** Arrange mode: first tapped tile waiting for a swap partner. */
    private val _selected = MutableStateFlow<Int?>(null)
    val selected: StateFlow<Int?> = _selected.asStateFlow()

    private val _arranging = MutableStateFlow(false)
    val arranging: StateFlow<Boolean> = _arranging.asStateFlow()

    private val _busy = MutableStateFlow<Busy?>(null)
    val busy: StateFlow<Busy?> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    fun thumb(slide: Slide): File = app.decks.thumbFile(deckId, slide.number)

    private fun edit(transform: (Deck) -> Deck) = viewModelScope.launch {
        app.decks.update(deckId) { transform(it) }
    }

    fun renameDeck(name: String) {
        if (name.isNotBlank()) edit { it.copy(name = name) }
    }

    fun renameSlide(number: Int, title: String) = edit { d ->
        val s = d.slide(number) ?: return@edit d
        d.withSlide(s.copy(title = title))
    }

    fun setColumns(columns: Int) = edit {
        it.copy(columns = columns.coerceIn(Deck.MIN_COLUMNS, Deck.MAX_COLUMNS))
    }

    fun setSlideCount(count: Int) = edit { it.withSlideCount(count) }

    fun toggleArranging() {
        _arranging.value = !_arranging.value
        _selected.value = null
    }

    fun tapInArrange(number: Int) {
        val first = _selected.value
        when {
            first == null -> _selected.value = number
            first == number -> _selected.value = null
            else -> {
                edit { it.swap(first, number) }
                _selected.value = null
            }
        }
    }

    fun resetOrder() = edit { d -> d.copy(order = d.slides.map { it.number }) }

    /** Move a tile one place earlier / later in the grid. */
    fun nudge(number: Int, delta: Int) = edit { d ->
        val o = d.normalizedOrder().toMutableList()
        val i = o.indexOf(number)
        val j = i + delta
        if (i < 0 || j !in o.indices) return@edit d
        o[i] = o[j]
        o[j] = number
        d.copy(order = o)
    }

    fun attachPdf(uri: Uri) = viewModelScope.launch {
        val d = deck.value ?: return@launch
        _busy.value = Busy("Adding pictures…", 0f)
        try {
            val out = app.importer.attachPdf(d, uri) { p -> _busy.value = Busy("Adding pictures…", p) }
            _messages.emit(out.note ?: "Pictures added.")
        } catch (e: ImportException) {
            _messages.emit(e.message ?: "Couldn't add pictures.")
        } catch (e: Exception) {
            _messages.emit("Couldn't add pictures: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            _busy.value = null
        }
    }

    fun refreshFromPptx(uri: Uri) = viewModelScope.launch {
        val d = deck.value ?: return@launch
        _busy.value = Busy("Reading slides…")
        try {
            val out = app.importer.refreshFromPptx(d, uri)
            _messages.emit(out.note ?: "Slide names updated.")
        } catch (e: ImportException) {
            _messages.emit(e.message ?: "Couldn't read that file.")
        } catch (e: Exception) {
            _messages.emit("Couldn't read that file: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            _busy.value = null
        }
    }

    fun removePictures() = viewModelScope.launch {
        val d = deck.value ?: return@launch
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            d.slides.forEach { app.decks.thumbFile(d.id, it.number).delete() }
        }
        app.decks.update(deckId) { latest ->
            latest.copy(
                slides = latest.slides.map { it.copy(hasImage = false) },
                imageVersion = System.currentTimeMillis(),
            )
        }
    }
}
