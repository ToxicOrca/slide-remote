package com.toxicorca.slideremote.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** Stores each deck as decks/<id>/deck.json with slide images beside it. */
class DeckRepository(context: Context) {
    private val root = File(context.filesDir, "decks").apply { mkdirs() }
    private val mutex = Mutex()

    private val _decks = MutableStateFlow<List<Deck>>(emptyList())
    val decks: StateFlow<List<Deck>> = _decks.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    suspend fun load() = mutex.withLock {
        val list = withContext(Dispatchers.IO) { readAll() }
        // Merge: anything saved before load finished (e.g. an "Open with" import) wins.
        _decks.update { cur -> (cur + list).distinctBy { it.id }.sortedByDescending { it.updatedAt } }
        _loaded.value = true
    }

    private fun readAll(): List<Deck> =
        root.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                runCatching {
                    Deck.fromJson(JSONObject(File(dir, DECK_FILE).readText()))
                }.getOrNull()
            }
            .orEmpty()

    fun get(id: String): Deck? = _decks.value.firstOrNull { it.id == id }

    fun observe(id: String): Flow<Deck?> =
        decks.map { list -> list.firstOrNull { it.id == id } }.distinctUntilChanged()

    /**
     * Persist [deck]. [touch] moves it to the top of the list; pass false for
     * high-frequency updates like the current-slide position.
     */
    suspend fun save(deck: Deck, touch: Boolean = true): Deck = mutex.withLock { writeLocked(deck, touch) }

    /**
     * Read-modify-write against the latest stored copy, so rapid edits never
     * overwrite each other. Returns null if the deck no longer exists.
     */
    suspend fun update(id: String, touch: Boolean = true, transform: (Deck) -> Deck): Deck? = mutex.withLock {
        val latest = _decks.value.firstOrNull { it.id == id } ?: return@withLock null
        val next = transform(latest)
        if (next == latest) latest else writeLocked(next, touch)
    }

    private suspend fun writeLocked(deck: Deck, touch: Boolean): Deck {
        val d = if (touch) deck.copy(updatedAt = System.currentTimeMillis()) else deck
        withContext(Dispatchers.IO) {
            val dir = deckDir(d.id).apply { mkdirs() }
            val tmp = File(dir, "$DECK_FILE.tmp")
            tmp.writeText(d.toJson().toString())
            val target = File(dir, DECK_FILE)
            if (!tmp.renameTo(target)) {
                target.writeText(d.toJson().toString())
                tmp.delete()
            }
        }
        _decks.update { list ->
            (list.filterNot { it.id == d.id } + d).sortedByDescending { it.updatedAt }
        }
        return d
    }

    suspend fun delete(id: String) = mutex.withLock {
        withContext(Dispatchers.IO) { deckDir(id).deleteRecursively() }
        _decks.update { list -> list.filterNot { it.id == id } }
    }

    fun deckDir(id: String): File = File(root, id)

    fun thumbFile(deckId: String, slideNumber: Int): File =
        File(deckDir(deckId), "slide_$slideNumber.jpg")

    companion object {
        private const val DECK_FILE = "deck.json"
    }
}
