package com.toxicorca.slideremote.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toxicorca.slideremote.data.Deck
import com.toxicorca.slideremote.importer.DeckImporter
import com.toxicorca.slideremote.ui.components.BusyOverlay
import com.toxicorca.slideremote.ui.components.ConnectionPill
import com.toxicorca.slideremote.ui.components.NumberPromptDialog
import com.toxicorca.slideremote.ui.components.SlideThumbnail
import com.toxicorca.slideremote.ui.components.TextPromptDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(
    onPresent: (String) -> Unit,
    onEdit: (String) -> Unit,
    onConnect: () -> Unit,
    onSettings: () -> Unit,
    vm: DeckListViewModel = viewModel(),
) {
    val decks by vm.decks.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val status by vm.hidStatus.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showNewSheet by remember { mutableStateOf(false) }
    // Saveable: these must survive activity recreation while a file picker is open.
    var askBlankName by rememberSaveable { mutableStateOf(false) }
    var blankName by rememberSaveable { mutableStateOf("") }
    var askBlankCount by rememberSaveable { mutableStateOf(false) }
    var offerDeckId by rememberSaveable { mutableStateOf<String?>(null) }
    var offerNote by rememberSaveable { mutableStateOf<String?>(null) }
    var pdfTargetDeck by rememberSaveable { mutableStateOf<String?>(null) }
    var noticeDeckId by rememberSaveable { mutableStateOf<String?>(null) }
    var noticeText by rememberSaveable { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Deck?>(null) }
    var deleting by remember { mutableStateOf<Deck?>(null) }

    val pickPptx = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importPptx(uri)
    }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importPdf(uri)
    }
    val pickAttachPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = pdfTargetDeck
        pdfTargetDeck = null
        if (target != null) {
            if (uri != null) vm.attachPdf(target, uri) else vm.openEditor(target)
        }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is DeckListEvent.Message -> scope.launch { snackbar.showSnackbar(e.text) }
                is DeckListEvent.OpenEditor -> onEdit(e.deckId)
                is DeckListEvent.OfferPictures -> {
                    offerNote = e.note
                    offerDeckId = e.deckId
                }
                is DeckListEvent.NoticeThenEdit -> {
                    noticeText = e.note
                    noticeDeckId = e.deckId
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("Slide Remote") },
                    actions = {
                        ConnectionPill(status, onClick = onConnect)
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showNewSheet = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New deck") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            if (loaded && decks.isEmpty()) {
                EmptyState(Modifier.padding(padding))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp,
                        top = padding.calculateTopPadding() + 4.dp,
                        bottom = padding.calculateBottomPadding() + 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(decks, key = { it.id }) { deck ->
                        DeckRow(
                            deck = deck,
                            thumb = vm.thumb(deck),
                            onPresent = { onPresent(deck.id) },
                            onEdit = { onEdit(deck.id) },
                            onRename = { renaming = deck },
                            onDuplicate = { vm.duplicate(deck) },
                            onDelete = { deleting = deck },
                        )
                    }
                }
            }
        }

        busy?.let { BusyOverlay(it.message, it.progress) }
    }

    if (showNewSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNewSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(bottom = 16.dp)) {
                Text(
                    "New deck",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                NewOption(
                    icon = Icons.Filled.Description,
                    title = "Import PowerPoint (.pptx)",
                    subtitle = "Gets the slide count and titles automatically",
                ) {
                    showNewSheet = false
                    pickPptx.launch(arrayOf(DeckImporter.PPTX_MIME, "application/octet-stream", "application/zip"))
                }
                NewOption(
                    icon = Icons.Filled.PictureAsPdf,
                    title = "Import PDF export",
                    subtitle = "One tile per page, with slide pictures",
                ) {
                    showNewSheet = false
                    pickPdf.launch(arrayOf(DeckImporter.PDF_MIME))
                }
                NewOption(
                    icon = Icons.Filled.GridView,
                    title = "Start blank",
                    subtitle = "Enter how many slides and name them yourself",
                ) {
                    showNewSheet = false
                    blankName = ""
                    askBlankName = true
                }
            }
        }
    }

    if (askBlankName) {
        TextPromptDialog(
            title = "Name this deck",
            initial = blankName,
            label = "Deck name",
            placeholder = "Quarterly review",
            confirmText = "Next",
            onDismiss = { askBlankName = false },
            onConfirm = {
                blankName = it
                askBlankName = false
                askBlankCount = true
            },
        )
    }

    if (askBlankCount) {
        NumberPromptDialog(
            title = "How many slides?",
            initial = 10,
            label = "Slides",
            range = 1..Deck.MAX_SLIDES,
            supporting = "You can change this later.",
            onDismiss = { askBlankCount = false },
            onConfirm = {
                askBlankCount = false
                vm.createBlank(blankName, it)
            },
        )
    }

    offerDeckId?.let { offerId ->
        AlertDialog(
            onDismissRequest = {
                offerDeckId = null
                vm.openEditor(offerId)
            },
            title = { Text("Add slide pictures?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    offerNote?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }
                    Text(
                        "Phones can't draw .pptx slides, but a PDF works. In PowerPoint: " +
                            "File → Export → Create PDF/XPS, then pick that PDF here."
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    offerDeckId = null
                    pdfTargetDeck = offerId
                    pickAttachPdf.launch(arrayOf(DeckImporter.PDF_MIME))
                }) { Text("Pick PDF") }
            },
            dismissButton = {
                TextButton(onClick = {
                    offerDeckId = null
                    vm.openEditor(offerId)
                }) { Text("Later") }
            },
        )
    }

    noticeDeckId?.let { id ->
        val close = {
            noticeDeckId = null
            vm.openEditor(id)
        }
        AlertDialog(
            onDismissRequest = { close() },
            title = { Text("Check for hidden slides") },
            text = { Text(noticeText) },
            confirmButton = { TextButton(onClick = { close() }) { Text("OK") } },
        )
    }

    renaming?.let { deck ->
        TextPromptDialog(
            title = "Rename deck",
            initial = deck.name,
            label = "Deck name",
            onDismiss = { renaming = null },
            onConfirm = {
                vm.rename(deck, it)
                renaming = null
            },
        )
    }

    deleting?.let { deck ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${deck.name}\"?") },
            text = { Text("This removes the deck and its pictures from this phone. Your PowerPoint file isn't touched.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(deck)
                    deleting = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NewOption(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun DeckRow(
    deck: Deck,
    thumb: java.io.File,
    onPresent: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceContainerHigh)
            .clickable(onClick = onPresent)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(112.dp)
                .aspectRatio(deck.aspect)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceContainerHighest)
        ) {
            val first = deck.slide(1)
            SlideThumbnail(
                file = if (first?.hasImage == true) thumb else null,
                version = deck.imageVersion,
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.GridView, contentDescription = null, tint = colors.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                deck.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            val details = buildString {
                append("${deck.slideCount} slide")
                if (deck.slideCount != 1) append("s")
                if (deck.hasImages) append(" · pictures")
                if (deck.currentSlide > 1) append(" · on ${deck.currentSlide}")
            }
            Text(details, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        IconButton(onClick = onPresent) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Present", tint = colors.primary)
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Edit grid") }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
                DropdownMenuItem(
                    text = { Text("Delete", color = colors.error) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.GridView,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("No decks yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Import a PowerPoint file or its PDF export, or start blank. " +
                "Then pair your phone with the PC as a Bluetooth keyboard and tap any slide to jump to it.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
