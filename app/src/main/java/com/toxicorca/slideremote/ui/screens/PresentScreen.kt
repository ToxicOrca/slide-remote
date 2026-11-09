package com.toxicorca.slideremote.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toxicorca.slideremote.VolumeKeys
import com.toxicorca.slideremote.data.Deck
import com.toxicorca.slideremote.data.Slide
import com.toxicorca.slideremote.hid.HidState
import com.toxicorca.slideremote.ui.components.ConnectionPill
import com.toxicorca.slideremote.ui.components.NumberBadge
import com.toxicorca.slideremote.ui.components.SlideThumbnail
import com.toxicorca.slideremote.ui.components.SlideTile
import com.toxicorca.slideremote.ui.components.TileEmphasis
import com.toxicorca.slideremote.ui.theme.Palette
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresentScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onConnect: () -> Unit,
    vm: PresentViewModel = viewModel(),
) {
    val deck by vm.deck.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    val status by vm.hidStatus.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val showRunning by vm.showRunning.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    var longPressed by remember { mutableStateOf<Slide?>(null) }
    var confirmAction by remember { mutableStateOf<GuardedAction?>(null) }

    // Keep the screen awake while presenting.
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Grab the volume buttons only while this screen is in front.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, settings.volumeKeys) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> VolumeKeys.active = settings.volumeKeys
                Lifecycle.Event.ON_PAUSE -> VolumeKeys.active = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            VolumeKeys.active = settings.volumeKeys
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            VolumeKeys.active = false
        }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                PresentEvent.Sent -> if (settings.haptics) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                is PresentEvent.Message -> scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(e.text, duration = SnackbarDuration.Short)
                }
                is PresentEvent.ConfirmShowRunning -> confirmAction = e.action
            }
        }
    }

    val d = deck
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(d?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (d != null) {
                            Text(
                                "Slide $current of ${d.slideCount}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    ConnectionPill(status, onClick = onConnect)
                    IconButton(onClick = { d?.let { onEdit(it.id) } }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit grid")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            ShowControls(
                onStart = vm::startShow,
                onStartHere = vm::startAtCurrent,
                onBlack = vm::toggleBlack,
                onWhite = vm::toggleWhite,
                onEnd = vm::endShow,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (d == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (loaded) {
                    Text("Deck not found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    CircularProgressIndicator()
                }
            }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (status.state != HidState.CONNECTED) {
                NotConnectedBanner(
                    connecting = status.state == HidState.CONNECTING,
                    onConnect = onConnect,
                )
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val landscape = maxWidth > maxHeight
                if (landscape) {
                    Row(Modifier.fillMaxSize()) {
                        NowPanel(
                            deck = d,
                            current = current,
                            nextNumber = vm.nextSlideNumber(current),
                            thumb = { vm.thumb(it) },
                            onPrev = vm::previous,
                            onNext = vm::next,
                            showRunning = showRunning,
                            onToggleShowRunning = vm::toggleShowRunning,
                            stacked = true,
                            modifier = Modifier
                                .weight(0.42f)
                                .fillMaxHeight()
                                .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 8.dp),
                        )
                        SlideGrid(
                            deck = d,
                            current = current,
                            thumb = { vm.thumb(it) },
                            onTap = { vm.jumpTo(it.number) },
                            onLongPress = { longPressed = it },
                            modifier = Modifier.weight(0.58f),
                        )
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        NowPanel(
                            deck = d,
                            current = current,
                            nextNumber = vm.nextSlideNumber(current),
                            thumb = { vm.thumb(it) },
                            onPrev = vm::previous,
                            onNext = vm::next,
                            showRunning = showRunning,
                            onToggleShowRunning = vm::toggleShowRunning,
                            stacked = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                        SlideGrid(
                            deck = d,
                            current = current,
                            thumb = { vm.thumb(it) },
                            onTap = { vm.jumpTo(it.number) },
                            onLongPress = { longPressed = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    longPressed?.let { slide ->
        AlertDialog(
            onDismissRequest = { longPressed = null },
            title = { Text("Slide ${slide.number}") },
            text = {
                Text(
                    slide.displayTitle + "\n\nIf the PC is already showing this slide (you used the mouse or a clicker), " +
                        "mark it as current so the app stays in sync."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.jumpTo(slide.number)
                    longPressed = null
                }) { Text("Jump here") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.markCurrent(slide.number)
                    longPressed = null
                }) { Text("Mark as current") }
            },
        )
    }

    confirmAction?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text("Is the slideshow running?") },
            text = { Text("If PowerPoint is in edit mode, these keys type into your slides and Enter adds blank slides.") },
            confirmButton = {
                // Stacked: three labels this long don't fit side by side on narrow phones.
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = {
                        confirmAction = null
                        vm.startShowFor(action)
                    }) { Text("Start show here") }
                    TextButton(onClick = {
                        confirmAction = null
                        vm.confirmRunningAndPerform(action)
                    }) { Text("It's already running") }
                    TextButton(onClick = { confirmAction = null }) { Text("Cancel") }
                }
            },
        )
    }
}

@Composable
private fun NotConnectedBanner(connecting: Boolean, onConnect: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.tertiaryContainer)
            .clickable(onClick = onConnect)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (connecting) "Connecting to your computer…" else "Not connected. Taps won't reach the PC.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onTertiaryContainer,
            modifier = Modifier.weight(1f),
        )
        Text("Connect", style = MaterialTheme.typography.labelLarge, color = colors.tertiary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NowPanel(
    deck: Deck,
    current: Int,
    nextNumber: Int?,
    thumb: (Slide) -> java.io.File,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    showRunning: Boolean,
    onToggleShowRunning: () -> Unit,
    stacked: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val slide = deck.slide(current)
    val nextSlide = nextNumber?.let { deck.slide(it) }

    @Composable
    fun Preview(mod: Modifier) {
        Box(
            mod
                .aspectRatio(deck.aspect)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceContainerHighest)
        ) {
            SlideThumbnail(
                file = if (slide?.hasImage == true) thumb(slide) else null,
                version = deck.imageVersion,
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        current.toString(),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                    )
                }
            }
            NumberBadge(current, highlighted = true, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
        }
    }

    @Composable
    fun Info(mod: Modifier) {
        Column(mod, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ShowStatusChip(showRunning, onToggleShowRunning)
            Spacer(Modifier.height(4.dp))
            Text("NOW", style = MaterialTheme.typography.labelSmall, color = colors.primary)
            Text(
                slide?.displayTitle ?: "Slide $current",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text("NEXT", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Text(
                nextSlide?.let { "${it.number} · ${it.displayTitle}" } ?: "End of deck",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    @Composable
    fun StepButtons(mod: Modifier) {
        Row(mod, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(
                onClick = onPrev,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, modifier = Modifier.size(28.dp))
                Text("Prev")
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1.4f).height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
            ) {
                Text("Next", fontWeight = FontWeight.Bold)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(28.dp))
            }
        }
    }

    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceContainer)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (stacked) {
            // Preview shrinks to fit so the Prev/Next buttons are never squeezed out.
            Preview(Modifier.weight(1f, fill = false).fillMaxWidth())
            Info(Modifier.fillMaxWidth())
            StepButtons(Modifier.fillMaxWidth())
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Preview(Modifier.weight(0.5f))
                Spacer(Modifier.width(12.dp))
                Info(Modifier.weight(0.5f))
            }
            StepButtons(Modifier.fillMaxWidth())
        }
    }
}

/** What the app believes about the slideshow. Tapping it corrects that without sending anything. */
@Composable
private fun ShowStatusChip(running: Boolean, onToggle: () -> Unit) {
    val tint = if (running) MaterialTheme.colorScheme.primary else Palette.Amber
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.15f))
            .clickable(onClickLabel = if (running) "Mark show not started" else "Mark show running", onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(tint)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (running) "Show running" else "Show not started",
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            maxLines = 1,
        )
    }
}

@Composable
private fun SlideGrid(
    deck: Deck,
    current: Int,
    thumb: (Slide) -> java.io.File,
    onTap: (Slide) -> Unit,
    onLongPress: (Slide) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ordered = remember(deck) { deck.orderedSlides() }
    val state = rememberLazyGridState()

    // Keep the current slide's tile in view when it changes.
    LaunchedEffect(current, ordered) {
        val index = ordered.indexOfFirst { it.number == current }
        if (index >= 0) {
            val visible = state.layoutInfo.visibleItemsInfo.map { it.index }
            if (index !in visible) state.animateScrollToItem(index)
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(deck.columns),
        state = state,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(ordered, key = { it.number }) { slide ->
            SlideTile(
                slide = slide,
                thumb = thumb(slide),
                imageVersion = deck.imageVersion,
                aspect = deck.aspect,
                compact = deck.columns >= 4,
                emphasis = if (slide.number == current) TileEmphasis.CURRENT else TileEmphasis.NONE,
                onClick = { onTap(slide) },
                onLongClick = { onLongPress(slide) },
            )
        }
    }
}

@Composable
private fun ShowControls(
    onStart: () -> Unit,
    onStartHere: () -> Unit,
    onBlack: () -> Unit,
    onWhite: () -> Unit,
    onEnd: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ControlButton(Icons.Filled.PlayArrow, "Start", onStart)
            ControlButton(Icons.Filled.Slideshow, "From here", onStartHere)
            ControlButton(Icons.Filled.DarkMode, "Black", onBlack)
            ControlButton(Icons.Filled.LightMode, "White", onWhite)
            ControlButton(Icons.Filled.Stop, "End", onEnd)
        }
    }
}

@Composable
private fun ControlButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
