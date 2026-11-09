package com.toxicorca.slideremote.ui.screens

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.toxicorca.slideremote.IncomingFiles
import com.toxicorca.slideremote.app
import com.toxicorca.slideremote.data.Deck
import com.toxicorca.slideremote.importer.FileKind
import com.toxicorca.slideremote.importer.ImportException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class Busy(val message: String, val progress: Float? = null)

sealed interface DeckListEvent {
    data class Message(val text: String) : DeckListEvent
    data class OpenEditor(val deckId: String) : DeckListEvent
    /** A .pptx was imported; offer to add pictures from a PDF export. */
    data class OfferPictures(val deckId: String, val note: String?) : DeckListEvent
    /** Show [note] before opening the editor (e.g. the hidden-slides warning after a PDF import). */
    data class NoticeThenEdit(val deckId: String, val note: String) : DeckListEvent
}

class DeckListViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.app
    val decks: StateFlow<List<Deck>> = app.decks.decks
    val loaded: StateFlow<Boolean> = app.decks.loaded
    val hidStatus = app.hid.status

    private val _busy = MutableStateFlow<Busy?>(null)
    val busy: StateFlow<Busy?> = _busy.asStateFlow()

    // Buffered so events raised while the list screen isn't showing (e.g. an
    // "Open with" import arriving on another screen) are delivered when it returns.
    private val _events = Channel<DeckListEvent>(Channel.BUFFERED)
    val events: Flow<DeckListEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            IncomingFiles.pending.filterNotNull().collect { uri ->
                IncomingFiles.pending.value = null
                importAny(uri)
            }
        }
    }

    fun thumb(deck: Deck) = app.decks.thumbFile(deck.id, 1)

    fun createBlank(name: String, count: Int) = viewModelScope.launch {
        val deck = app.decks.save(Deck.blank(name.ifBlank { "Presentation" }, count))
        _events.send(DeckListEvent.OpenEditor(deck.id))
    }

    fun importAny(uri: Uri) = viewModelScope.launch {
        val kind = try {
            app.importer.kindOf(uri)
        } catch (e: ImportException) {
            _events.send(DeckListEvent.Message(e.message ?: "Can't import that file."))
            return@launch
        }
        when (kind) {
            FileKind.PDF -> doImportPdf(uri)
            // Unknown types: try as pptx (zip) since pickers often report octet-stream.
            FileKind.PPTX, FileKind.UNKNOWN -> doImportPptx(uri)
        }
    }

    fun importPptx(uri: Uri) = viewModelScope.launch { doImportPptx(uri) }

    fun importPdf(uri: Uri) = viewModelScope.launch { doImportPdf(uri) }

    private suspend fun doImportPptx(uri: Uri) {
        _busy.value = Busy("Reading slides…")
        try {
            val outcome = app.importer.importPptx(uri)
            _events.send(DeckListEvent.OfferPictures(outcome.deck.id, outcome.note))
        } catch (e: ImportException) {
            _events.send(DeckListEvent.Message(e.message ?: "Import failed."))
        } catch (e: Exception) {
            _events.send(DeckListEvent.Message("Import failed: ${e.message ?: e.javaClass.simpleName}"))
        } finally {
            _busy.value = null
        }
    }

    private suspend fun doImportPdf(uri: Uri) {
        _busy.value = Busy("Rendering slides…", 0f)
        try {
            val outcome = app.importer.importPdf(uri) { p -> _busy.value = Busy("Rendering slides…", p) }
            val note = outcome.note
            _events.send(
                if (note != null) DeckListEvent.NoticeThenEdit(outcome.deck.id, note)
                else DeckListEvent.OpenEditor(outcome.deck.id)
            )
        } catch (e: ImportException) {
            _events.send(DeckListEvent.Message(e.message ?: "Import failed."))
        } catch (e: Exception) {
            _events.send(DeckListEvent.Message("Import failed: ${e.message ?: e.javaClass.simpleName}"))
        } finally {
            _busy.value = null
        }
    }

    /** Add pictures to the deck just imported from .pptx. */
    fun attachPdf(deckId: String, uri: Uri) = viewModelScope.launch {
        val deck = app.decks.get(deckId) ?: return@launch
        _busy.value = Busy("Adding pictures…", 0f)
        try {
            val outcome = app.importer.attachPdf(deck, uri) { p -> _busy.value = Busy("Adding pictures…", p) }
            outcome.note?.let { _events.send(DeckListEvent.Message(it)) }
        } catch (e: ImportException) {
            _events.send(DeckListEvent.Message(e.message ?: "Couldn't add pictures."))
        } catch (e: Exception) {
            _events.send(DeckListEvent.Message("Couldn't add pictures: ${e.message ?: e.javaClass.simpleName}"))
        } finally {
            _busy.value = null
        }
        _events.send(DeckListEvent.OpenEditor(deckId))
    }

    fun openEditor(deckId: String) = viewModelScope.launch { _events.send(DeckListEvent.OpenEditor(deckId)) }

    fun rename(deck: Deck, name: String) = viewModelScope.launch {
        if (name.isNotBlank()) app.decks.save(deck.copy(name = name))
    }

    fun delete(deck: Deck) = viewModelScope.launch { app.decks.delete(deck.id) }

    fun duplicate(deck: Deck) = viewModelScope.launch {
        val copy = deck.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = "${deck.name} (copy)",
            createdAt = System.currentTimeMillis(),
        )
        // Copy pictures alongside.
        val srcDir = app.decks.deckDir(deck.id)
        val dstDir = app.decks.deckDir(copy.id)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            dstDir.mkdirs()
            srcDir.listFiles()?.filter { it.name.endsWith(".jpg") }?.forEach { f ->
                f.copyTo(java.io.File(dstDir, f.name), overwrite = true)
            }
        }
        app.decks.save(copy)
    }
}
