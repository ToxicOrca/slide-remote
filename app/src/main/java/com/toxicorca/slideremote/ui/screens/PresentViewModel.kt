package com.toxicorca.slideremote.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.toxicorca.slideremote.VolumeKeys
import com.toxicorca.slideremote.app
import com.toxicorca.slideremote.data.Deck
import com.toxicorca.slideremote.data.Settings
import com.toxicorca.slideremote.data.Slide
import com.toxicorca.slideremote.data.StepMode
import com.toxicorca.slideremote.hid.Commands
import com.toxicorca.slideremote.hid.HidAction
import com.toxicorca.slideremote.hid.HidStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface PresentEvent {
    data class Message(val text: String) : PresentEvent
    /** A command went out: give haptic feedback. */
    data object Sent : PresentEvent
    /** A guarded action was held back: ask whether the slideshow is running. */
    data class ConfirmShowRunning(val action: GuardedAction) : PresentEvent
}

/** Actions that type into PowerPoint's edit view if the slideshow isn't running. */
sealed interface GuardedAction {
    data class Jump(val slide: Int) : GuardedAction
    data object Next : GuardedAction
    data object Previous : GuardedAction
    data object Black : GuardedAction
    data object White : GuardedAction
}

class PresentViewModel(
    application: Application,
    savedState: SavedStateHandle,
) : AndroidViewModel(application) {
    private val app = application.app
    val deckId: String = checkNotNull(savedState["deckId"])

    val deck: StateFlow<Deck?> = app.decks.observe(deckId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, app.decks.get(deckId))

    val hidStatus: StateFlow<HidStatus> = app.hid.status
    val settings: StateFlow<Settings> = app.settings.state
    val loaded: StateFlow<Boolean> = app.decks.loaded

    /** The app's best guess of the slide on screen. */
    private val _current = MutableStateFlow(app.decks.get(deckId)?.currentSlide ?: 1)
    val current: StateFlow<Int> = _current.asStateFlow()

    /**
     * Whether the app believes the slideshow is running. The phone can't see PowerPoint,
     * so this only follows Start / From here / End and the user's own toggle.
     */
    private val _showRunning = MutableStateFlow(false)
    val showRunning: StateFlow<Boolean> = _showRunning.asStateFlow()

    private val _events = MutableSharedFlow<PresentEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private var persistJob: Job? = null

    init {
        viewModelScope.launch {
            VolumeKeys.events.collect { dir -> step(dir, fromVolumeKey = true) }
        }
        // Deck may not be loaded yet if the process was just restored: adopt the saved
        // position the first time it arrives, then only clamp.
        viewModelScope.launch {
            var adopted = app.decks.get(deckId) != null
            deck.collect { d ->
                if (d == null) return@collect
                if (!adopted) {
                    adopted = true
                    _current.value = d.currentSlide.coerceIn(1, d.slideCount)
                } else if (_current.value > d.slideCount) {
                    _current.value = d.slideCount
                }
            }
        }
    }

    fun thumb(slide: Slide): File = app.decks.thumbFile(deckId, slide.number)

    private fun slideCount(): Int = deck.value?.slideCount ?: 1

    private fun send(actions: List<HidAction>): Boolean {
        val ok = app.hid.send(actions)
        if (ok) {
            _events.tryEmit(PresentEvent.Sent)
        } else {
            _events.tryEmit(PresentEvent.Message("Not connected to a computer. Tap the status pill to connect."))
        }
        return ok
    }

    private fun setCurrent(n: Int) {
        val clamped = n.coerceIn(1, slideCount())
        _current.value = clamped
        // Persist without spamming disk on rapid taps.
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(400)
            app.decks.update(deckId, touch = false) { it.copy(currentSlide = clamped) }
        }
    }

    /**
     * Returns true if [action] may send now. Otherwise asks first (a dialog, or a snackbar
     * for volume keys) and returns false.
     */
    private fun guard(action: GuardedAction, fromVolumeKey: Boolean = false): Boolean {
        if (_showRunning.value || !settings.value.confirmBeforeShow) return true
        if (fromVolumeKey) {
            _events.tryEmit(PresentEvent.Message("Start the show first, or mark it running."))
        } else {
            _events.tryEmit(PresentEvent.ConfirmShowRunning(action))
        }
        return false
    }

    fun jumpTo(n: Int) {
        val target = n.coerceIn(1, slideCount())
        if (guard(GuardedAction.Jump(target))) sendJump(target)
    }

    private fun sendJump(target: Int) {
        if (send(Commands.jumpTo(target, settings.value.numpadDigits))) setCurrent(target)
    }

    /**
     * PowerPoint's own Next/Previous skip slides marked hidden, so ours do too.
     * Hidden slides are still reachable by tapping their tile.
     */
    private fun neighbor(from: Int, step: Int): Int? {
        val d = deck.value ?: return null
        var n = from + step
        while (n in 1..d.slideCount) {
            if (d.slide(n)?.hiddenInShow != true) return n
            n += step
        }
        return null
    }

    fun next() = step(1, fromVolumeKey = false)

    fun previous() = step(-1, fromVolumeKey = false)

    private fun step(dir: Int, fromVolumeKey: Boolean) {
        val forward = dir > 0
        val action = if (forward) GuardedAction.Next else GuardedAction.Previous
        val target = neighbor(_current.value, if (forward) 1 else -1)
        if (settings.value.stepMode == StepMode.ARROW) {
            if (!guard(action, fromVolumeKey)) return
            // Always send: the slide may still have animations to play, or tracking may have drifted.
            val keys = if (forward) Commands.nextArrow else Commands.prevArrow
            if (send(keys) && target != null) setCurrent(target)
            return
        }
        if (target == null) {
            _events.tryEmit(
                PresentEvent.Message(if (forward) "That's the last slide." else "Already on the first slide.")
            )
            return
        }
        if (guard(action, fromVolumeKey)) sendJump(target)
    }

    /** Title shown for the upcoming slide. */
    fun nextSlideNumber(from: Int): Int? = neighbor(from, 1)

    /** Correct the app's tracking without sending anything. */
    fun markCurrent(n: Int) {
        setCurrent(n)
        _events.tryEmit(PresentEvent.Message("Now tracking slide $n."))
    }

    fun startShow() {
        // F5 opens on the first slide that isn't hidden.
        if (send(Commands.startShow)) {
            setCurrent(neighbor(0, 1) ?: 1)
            _showRunning.value = true
        }
    }

    fun startAtCurrent() = startAt(_current.value)

    /** Start the slideshow (F5), then jump to [slide]. */
    fun startAt(slide: Int) {
        val s = settings.value
        val target = slide.coerceIn(1, slideCount())
        if (send(Commands.startAt(target, s.numpadDigits, s.showOpenDelayMs.toLong()))) {
            // From slide 1, startAt sends only F5, which opens on the first slide that isn't hidden.
            setCurrent(if (target <= 1) neighbor(0, 1) ?: 1 else target)
            _showRunning.value = true
        }
    }

    fun toggleBlack() {
        if (guard(GuardedAction.Black)) send(Commands.black)
    }

    fun toggleWhite() {
        if (guard(GuardedAction.White)) send(Commands.white)
    }

    fun endShow() {
        if (send(Commands.endShow)) _showRunning.value = false
    }

    /** Status chip: correct the app's belief about the slideshow without sending anything. */
    fun toggleShowRunning() {
        _showRunning.value = !_showRunning.value
    }

    /** "Start show here": start at the tapped slide, or at the current slide for other actions. */
    fun startShowFor(action: GuardedAction) {
        if (action is GuardedAction.Jump) startAt(action.slide) else startAtCurrent()
    }

    /** "It's already running": remember that, then do what was asked. */
    fun confirmRunningAndPerform(action: GuardedAction) {
        _showRunning.value = true
        when (action) {
            is GuardedAction.Jump -> jumpTo(action.slide)
            GuardedAction.Next -> next()
            GuardedAction.Previous -> previous()
            GuardedAction.Black -> toggleBlack()
            GuardedAction.White -> toggleWhite()
        }
    }

    override fun onCleared() {
        // Flush the latest position.
        val cur = _current.value
        if (deck.value?.currentSlide != cur) {
            app.appScope.launch { app.decks.update(deckId, touch = false) { it.copy(currentSlide = cur) } }
        }
        super.onCleared()
    }
}
