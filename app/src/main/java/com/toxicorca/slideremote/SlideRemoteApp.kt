package com.toxicorca.slideremote

import android.app.Application
import android.content.Context
import android.net.Uri
import com.toxicorca.slideremote.data.AppSettings
import com.toxicorca.slideremote.data.DeckRepository
import com.toxicorca.slideremote.hid.HidKeyboard
import com.toxicorca.slideremote.importer.DeckImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class SlideRemoteApp : Application() {
    lateinit var settings: AppSettings
        private set
    lateinit var decks: DeckRepository
        private set
    lateinit var hid: HidKeyboard
        private set
    lateinit var importer: DeckImporter
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        decks = DeckRepository(this)
        importer = DeckImporter(this, decks)
        hid = HidKeyboard(this, settings)
        appScope.launch { decks.load() }
        hid.start()
    }
}

val Context.app: SlideRemoteApp get() = applicationContext as SlideRemoteApp

/** Hardware volume buttons, forwarded by MainActivity while the presenter screen wants them. */
object VolumeKeys {
    /** Set by the presenter screen while it is visible and the setting is on. */
    @Volatile var active: Boolean = false

    private val _events = MutableSharedFlow<Int>(extraBufferCapacity = 8)

    /** +1 = next (volume down), -1 = previous (volume up). */
    val events: SharedFlow<Int> = _events.asSharedFlow()

    fun emit(direction: Int) {
        _events.tryEmit(direction)
    }
}

/** Files opened from other apps ("Open with"), waiting to be imported. */
object IncomingFiles {
    val pending = MutableStateFlow<Uri?>(null)
}
