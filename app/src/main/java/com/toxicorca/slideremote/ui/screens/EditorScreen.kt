package com.toxicorca.slideremote.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toxicorca.slideremote.data.Deck
import com.toxicorca.slideremote.data.Slide
import com.toxicorca.slideremote.importer.DeckImporter
import com.toxicorca.slideremote.ui.components.BusyOverlay
import com.toxicorca.slideremote.ui.components.NumberPromptDialog
import com.toxicorca.slideremote.ui.components.SlideThumbnail
import com.toxicorca.slideremote.ui.components.SlideTile
import com.toxicorca.slideremote.ui.components.TextPromptDialog
import com.toxicorca.slideremote.ui.components.TileEmphasis
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    onPresent: (String) -> Unit,
    vm: EditorViewModel = viewModel(),
) {
    val deck by vm.deck.collectAsStateWithLifecycle()
    val arranging by vm.arranging.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var renamingDeck by remember { mutableStateOf(false) }
    var editingSlide by remember { mutableStateOf<Slide?>(null) }
    var askCount by remember { mutableStateOf(false) }
    var confirmRemovePictures by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.attachPdf(uri)
    }
    val pickPptx = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.refreshFromPptx(uri)
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    val d = deck
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column(Modifier.clickable(enabled = d != null) { renamingDeck = true }) {
                            Text(d?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "Tap to rename",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { d?.let { onPresent(it.id) } }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Present", tint = MaterialTheme.colorScheme.primary)
                        }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (d?.hasImages == true) "Replace pictures (PDF)" else "Add pictures (PDF)") },
                                    onClick = { menu = false; pickPdf.launch(arrayOf(DeckImporter.PDF_MIME)) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Update names from .pptx") },
                                    onClick = {
                                        menu = false
                                        pickPptx.launch(arrayOf(DeckImporter.PPTX_MIME, "application/octet-stream", "application/zip"))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Change slide count") },
                                    onClick = { menu = false; askCount = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Reset grid order") },
                                    onClick = { menu = false; vm.resetOrder() },
                                )
                                if (d?.hasImages == true) {
                                    DropdownMenuItem(
                                        text = { Text("Remove pictures") },
                                        onClick = { menu = false; confirmRemovePictures = true },
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
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
                EditorToolbar(
                    deck = d,
                    arranging = arranging,
                    onColumns = vm::setColumns,
                    onToggleArrange = vm::toggleArranging,
                    onCount = { askCount = true },
                    onPictures = { pickPdf.launch(arrayOf(DeckImporter.PDF_MIME)) },
                )
                Text(
                    if (arranging) {
                        if (selected == null) "Tap a slide, then tap where it should go to swap them."
                        else "Now tap the slide to swap with ${selected}."
                    } else {
                        "Tap a slide to rename it. Long-press to move it."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (arranging) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(d.columns),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(d.orderedSlides(), key = { it.number }) { slide ->
                        SlideTile(
                            slide = slide,
                            thumb = vm.thumb(slide),
                            imageVersion = d.imageVersion,
                            aspect = d.aspect,
                            compact = d.columns >= 4,
                            emphasis = if (arranging && selected == slide.number) TileEmphasis.SELECTED else TileEmphasis.NONE,
                            onClick = {
                                if (arranging) vm.tapInArrange(slide.number) else editingSlide = slide
                            },
                            onLongClick = {
                                if (!arranging) vm.toggleArranging()
                                vm.tapInArrange(slide.number)
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        TextButton(
                            onClick = { askCount = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Change slide count (${d.slideCount})")
                        }
                    }
                }
            }
        }

        busy?.let { BusyOverlay(it.message, it.progress) }
    }

    if (renamingDeck && d != null) {
        TextPromptDialog(
            title = "Rename deck",
            initial = d.name,
            label = "Deck name",
            onDismiss = { renamingDeck = false },
            onConfirm = {
                vm.renameDeck(it)
                renamingDeck = false
            },
        )
    }

    editingSlide?.let { slide ->
        val current = d?.slide(slide.number) ?: slide
        SlideEditDialog(
            slide = current,
            deck = d,
            thumb = vm.thumb(current),
            onDismiss = { editingSlide = null },
            onSave = { title ->
                vm.renameSlide(current.number, title)
                editingSlide = null
            },
            onMove = { delta -> vm.nudge(current.number, delta) },
        )
    }

    if (askCount && d != null) {
        NumberPromptDialog(
            title = "Number of slides",
            initial = d.slideCount,
            label = "Slides",
            range = 1..Deck.MAX_SLIDES,
            supporting = "Must match your PowerPoint file, hidden slides included.",
            onDismiss = { askCount = false },
            onConfirm = {
                vm.setSlideCount(it)
                askCount = false
            },
        )
    }

    if (confirmRemovePictures) {
        AlertDialog(
            onDismissRequest = { confirmRemovePictures = false },
            title = { Text("Remove pictures?") },
            text = { Text("Slide names and grid layout stay. You can add pictures again from a PDF.") },
            confirmButton = {
                TextButton(onClick = { vm.removePictures(); confirmRemovePictures = false }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemovePictures = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EditorToolbar(
    deck: Deck,
    arranging: Boolean,
    onColumns: (Int) -> Unit,
    onToggleArrange: () -> Unit,
    onCount: () -> Unit,
    onPictures: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            // Scrolls on narrow (360dp) screens instead of squeezing the last chip.
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Column stepper
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(colors.surfaceContainerHigh),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onColumns(deck.columns - 1) },
                enabled = deck.columns > Deck.MIN_COLUMNS,
            ) { Icon(Icons.Filled.Remove, contentDescription = "Fewer columns") }
            Text(
                "${deck.columns} wide",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(
                onClick = { onColumns(deck.columns + 1) },
                enabled = deck.columns < Deck.MAX_COLUMNS,
            ) { Icon(Icons.Filled.Add, contentDescription = "More columns") }
        }
        FilterChip(
            selected = arranging,
            onClick = onToggleArrange,
            label = { Text(if (arranging) "Done" else "Arrange") },
            leadingIcon = {
                Icon(if (arranging) Icons.Filled.Check else Icons.Filled.SwapHoriz, contentDescription = null)
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = colors.tertiaryContainer,
                selectedLabelColor = colors.onTertiaryContainer,
                selectedLeadingIconColor = colors.onTertiaryContainer,
            ),
        )
        if (!deck.hasImages) {
            AssistChip(
                onClick = onPictures,
                label = { Text("Pictures") },
                leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                colors = AssistChipDefaults.assistChipColors(leadingIconContentColor = colors.primary),
            )
        } else {
            AssistChip(
                onClick = onCount,
                label = { Text("${deck.slideCount} slides") },
            )
        }
    }
}

@Composable
private fun SlideEditDialog(
    slide: Slide,
    deck: Deck?,
    thumb: java.io.File,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onMove: (Int) -> Unit,
) {
    var title by remember(slide.number) { mutableStateOf(slide.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Slide ${slide.number}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (slide.hasImage && deck != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .aspectRatio(deck.aspect)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        SlideThumbnail(thumb, deck.imageVersion, Modifier.fillMaxSize()) {}
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    label = { Text("Name") },
                    placeholder = { Text("Slide ${slide.number}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (slide.hiddenInShow) {
                    Text(
                        "Hidden in PowerPoint. Next and Previous skip it, but tapping its tile still jumps to it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Grid position",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalIconButton(onClick = { onMove(-1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Move earlier")
                    }
                    FilledTonalIconButton(onClick = { onMove(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Move later")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
