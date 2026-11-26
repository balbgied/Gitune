package com.gitune.prototype

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.WindowInsets as AndroidWindowInsets
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { PrototypeTheme { Prototype() } }
    }
}

private val Ink = Color.Black
private val Panel = Color(0xFF141D24)
private val Text = Color(0xFFF1F6F7)
private val Muted = Color(0xFFA2B3BC)
private val Mint = Color(0xFF85F3CE)
private val TunerYellow = Color(0xFFFFF000)
private val Rec = Color(0xFFBC223C)
private val RecText = Color(0xFFFF6C80)
private val notes = listOf("C", "C♯", "D", "E♭", "E", "F", "F♯", "G", "A♭", "A", "B♭", "B")
private val prominentNotes = listOf(4, 9, 7, 2, 10, 6, 1, 3)
private val accents = listOf(0xFFFF997D, 0xFFF7BE7A, 0xFFF3D773, 0xFFD5E97F, 0xFF9DEB86, 0xFF77E7AA,
    0xFF68E5CA, 0xFF6DD9F0, 0xFF80BCFA, 0xFFA4A7FF, 0xFFC494FA, 0xFFF397D4).map { Color(it) }

@Composable
private fun PrototypeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Mint, background = Ink, surface = Panel,
        onBackground = Text, onSurface = Text, onPrimary = Ink), content = content)
}

private enum class Screen { JAM, TUNER }

@Composable
private fun Prototype(initial: Screen = Screen.JAM, preview: Boolean = false) {
    val screenCount = Screen.entries.size
    // Start in the middle of repeated screens so both swipe directions wrap naturally.
    val middlePage = if (preview) 0 else 5_000 * screenCount
    val pageCount = if (preview) screenCount else 2 * middlePage
    val pager = rememberPagerState(initialPage = middlePage + initial.ordinal) { pageCount }
    val settledPage = pager.settledPage
    val tab = Screen.entries[settledPage % screenCount]
    LaunchedEffect(settledPage) {
        if (!preview && (settledPage < screenCount || settledPage >= pageCount - screenCount)) {
            pager.scrollToPage(middlePage + tab.ordinal)
        }
    }
    val context = LocalContext.current
    val prefs = remember(context, preview) { if (preview) null else context.getSharedPreferences("gtuner", 0) }
    val live = if (preview) remember { mutableStateOf(LiveReadout()) } else LiveAudio.live.collectAsState()
    val take = if (preview) TakeState(if (initial == Screen.TUNER) TakePhase.RECORDING else TakePhase.IDLE, 83)
        else LiveAudio.take.collectAsState().value
    val recording = take.phase != TakePhase.IDLE
    val elapsed = take.seconds
    var playing by rememberSaveable { mutableStateOf(false) }
    var position by rememberSaveable { mutableFloatStateOf(if (preview) 38f else 0f) }
    var a4 by rememberSaveable { mutableIntStateOf(prefs?.getInt("a4", 440) ?: 440) }
    var recordingFormatName by rememberSaveable { mutableStateOf(prefs?.getString("recordingFormat", null) ?: RecordingFormat.AAC.name) }
    val recordingFormat = RecordingFormat.entries.firstOrNull { it.name == recordingFormatName } ?: RecordingFormat.AAC
    var thickPitchLine by rememberSaveable { mutableStateOf(prefs?.getBoolean("thickPitchLine", true) ?: true) }
    var playlist by remember { mutableStateOf(prefs?.getString("playlist", "").orEmpty()) }
    var showPlaylist by rememberSaveable { mutableStateOf(prefs?.getBoolean("showPlaylist", true) ?: true) }
    var playlistDraft by rememberSaveable { mutableStateOf<String?>(null) }
    playlistDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = { playlistDraft = null },
            title = { Text("Playlist") },
            text = {
                OutlinedTextField(
                    value = draft, onValueChange = { playlistDraft = it },
                    label = { Text("Playlist text") },
                    supportingText = { Text("Paste one line per song. Save empty to hide the playlist.") },
                    modifier = Modifier.fillMaxWidth(), minLines = 4, maxLines = 8
                )
            },
            dismissButton = { TextButton(onClick = { playlistDraft = null }) { Text("Cancel") } },
            confirmButton = {
                TextButton(onClick = {
                    playlist = draft.trim()
                    prefs?.edit()?.putString("playlist", playlist)?.apply()
                    playlistDraft = null
                }) { Text("Save") }
            }
        )
    }
    var notice by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var fileName by rememberSaveable { mutableStateOf("") }
    var duration by remember { mutableFloatStateOf(120f) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var choosing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var importJob by remember { mutableStateOf<Job?>(null) }
    var deleteTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var copiedBytes by remember { mutableStateOf(0L) }
    var seekVersion by remember { mutableIntStateOf(0) }
    var reviewPoints by remember { mutableStateOf(emptyList<PitchPoint>()) }
    var reviewTempo by remember { mutableStateOf(emptyList<TempoPoint>()) }
    val playhead = remember { AtomicInteger() }
    val bookmarks = rememberSaveable { mutableStateOf(if (preview) arrayListOf(18f, 38f, 79f) else arrayListOf()) }
    fun openInReview(uri: Uri, name: String) {
        val key = uri.toString()
        playing = false
        position = 0f
        playhead.set(0)
        reviewPoints = emptyList()
        reviewTempo = emptyList()
        seekVersion++
        notice = ""
        if (selected == key) player?.seekTo(0L, MediaPlayer.SEEK_CLOSEST)
        bookmarks.value = ArrayList(prefs?.getString("bookmarks:$key", "").orEmpty().split(',').mapNotNull { it.toFloatOrNull() })
        fileName = name
        selected = key
    }
    var prefilledRecording by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(preview) {
        if (!preview) LiveAudio.latestRecording.collect { saved ->
            if (saved != null && saved.uri.toString() != prefilledRecording) {
                openInReview(saved.uri, saved.name)
                prefilledRecording = saved.uri.toString()
            }
        }
    }
    LaunchedEffect(selected, bookmarks.value) {
        selected?.let { prefs?.edit()?.putString("bookmarks:$it", bookmarks.value.joinToString(","))?.apply() }
    }
    val visible = remember { mutableStateOf(false) }
    var permitted by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var pendingRecord by remember { mutableStateOf(false) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permitted = it
        if (!it) pendingRecord = false
    }
    DisposableEffect(context, preview) {
        val lifecycle = (context as? ComponentActivity)?.lifecycle
        visible.value = lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    permitted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    visible.value = true
                }
                Lifecycle.Event.ON_STOP -> {
                    visible.value = false
                    playing = false
                    if (!preview) {
                        LiveAudio.stop("App no longer visible")
                        LiveAudio.configure(context, false, false)
                    }
                }
                else -> Unit
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    val isVisible = visible.value
    val monitorMic = !playing
    LaunchedEffect(isVisible, permitted, recording, pendingRecord, monitorMic, recordingFormat) {
        if (!preview && isVisible && visible.value && permitted) {
            LiveAudio.configure(context, recording || monitorMic, monitorMic)
            if (pendingRecord && monitorMic) {
                for (attempt in 1..30) {
                    if (LiveAudio.ready) break
                    delay(50)
                }
                pendingRecord = false
                LiveAudio.record(context, recordingFormat)
            }
        } else if (!preview) LiveAudio.configure(context, false, false)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        choosing = false
        val document = result.data
        document?.data?.let { uri ->
            if (recording || importJob != null) return@let
            if (document.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0) {
                runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            }
            copiedBytes = 0L
            notice = ""
            importJob = scope.launch {
                try {
                    val (copy, name) = withContext(Dispatchers.IO) {
                        val originalName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                            if (it.moveToFirst()) it.getString(0) else null
                        } ?: error("Could not read the filename.")
                        val importKey = "reviewCopy:$uri"
                        val previousCopy = prefs?.getString(importKey, null)?.let(Uri::parse)
                        val destination = ReviewAudio.savedCopy(context, previousCopy)
                            ?: ReviewAudio.copy(context, uri, originalName) { copiedBytes = it }.also {
                                prefs?.edit()?.putString(importKey, it.toString())?.apply()
                            }
                        val storedName = context.contentResolver.query(destination, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                            if (it.moveToFirst()) it.getString(0) else null
                        }
                        destination to (storedName ?: originalName)
                    }
                    openInReview(copy, name)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    notice = error.message?.takeIf { error is IllegalStateException } ?: "Could not import this audio file."
                } finally {
                    importJob = null
                }
            }
        }
    }
    DisposableEffect(selected) {
        val uri = selected
        val pending = if (uri == null || preview) null else MediaPlayer()
        player = null
        if (pending != null) {
            duration = 0f
            try {
                pending.setDataSource(context, Uri.parse(uri))
                pending.setOnPreparedListener {
                    duration = it.duration / 1000f
                    if (position > 0) it.seekTo((position.coerceAtMost(duration) * 1000).roundToInt())
                    player = it
                }
                pending.setOnCompletionListener { position = duration; playhead.set((duration * 1000).toInt()); playing = false }
                pending.setOnErrorListener { _, _, _ ->
                    playing = false
                    player = null
                    notice = "Could not play this audio file."
                    true
                }
                pending.prepareAsync()
            } catch (_: Exception) {
                notice = "Could not open this audio file."
            }
        }
        onDispose { pending?.release() }
    }
    LaunchedEffect(tab) {
        if (tab != Screen.JAM) playing = false
    }
    DisposableEffect(context, tab, preview) {
        val insets = if (preview) null else (context as? ComponentActivity)?.window?.insetsController
        if (tab == Screen.JAM) insets?.hide(AndroidWindowInsets.Type.statusBars())
        else insets?.show(AndroidWindowInsets.Type.statusBars())
        onDispose { if (tab == Screen.JAM) insets?.show(AndroidWindowInsets.Type.statusBars()) }
    }
    LaunchedEffect(playing, player) {
        val current = player
        if (current != null) {
            if (playing) {
                current.start()
                while (true) {
                    delay(33)
                    val millis = current.currentPosition
                    position = millis / 1000f
                    playhead.set(millis)
                }
            } else if (current.isPlaying) current.pause()
        } else if (preview && playing) {
            while (position < duration) { delay(33); position = (position + 0.033f).coerceAtMost(duration) }
            playing = false
        }
    }
    LaunchedEffect(selected, playing, seekVersion, isVisible, tab) {
        val uri = selected ?: return@LaunchedEffect
        if (!playing || !isVisible || tab != Screen.JAM) return@LaunchedEffect
        val start = (position - 10f).coerceAtLeast(0f)
        try {
            withContext(Dispatchers.IO) {
                ReviewAudio.plot(context, Uri.parse(uri), start, null, { playhead.get() / 1000f }) { pitch, tempo ->
                    if (pitch.isNotEmpty()) {
                        val first = pitch.first().seconds
                        reviewPoints = reviewPoints.filter { it.seconds >= first - 10f && it.seconds < first } + pitch
                    }
                    if (tempo.isNotEmpty()) {
                        val first = tempo.first().seconds
                        reviewTempo = reviewTempo.filter { it.seconds >= first - 30f && it.seconds < first } + tempo
                    }
                }
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            notice = "Pitch analysis is unavailable for this file."
        }
    }
    fun seek(seconds: Float) {
        reviewPoints = emptyList()
        reviewTempo = emptyList()
        position = seconds
        val millis = (seconds * 1000).roundToInt()
        playhead.set(millis)
        seekVersion++
        player?.seekTo(millis.toLong(), MediaPlayer.SEEK_CLOSEST)
    }

    val showFileAnalysis = tab == Screen.JAM && playing
    val headerBpm = if (preview) 124 else if (showFileAnalysis) reviewTempo.lastOrNull { it.seconds <= position }?.bpm else live.value.bpm
    deleteTarget?.takeIf { it == selected && !recording }?.let { target ->
        val sourceKey = remember(target) {
            prefs?.all?.entries?.singleOrNull { it.key.startsWith("reviewCopy:") && it.value == target }?.key
        }
        val source = sourceKey?.removePrefix("reviewCopy:")?.let(Uri::parse)
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete recording?") },
            text = {
                Text(if (source != null) "Permanently delete ‘$fileName’ and the original file it was imported from? This can’t be undone."
                    else "Permanently delete ‘$fileName’ from Music? This can’t be undone.")
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    deleting = true
                    playing = false
                    val uri = Uri.parse(target)
                    scope.launch {
                        val deleteFailure = if (source == null) "Could not delete this recording." else "Original removed, but the Music copy could not be deleted."
                        try {
                            if (source != null) {
                                val originalDeleted = withContext(Dispatchers.IO) { ReviewAudio.deleteOriginal(context, source) }
                                if (!originalDeleted) {
                                    notice = "Could not delete the original. Reopen it using File to renew access, or delete it in your file manager. The Music copy was kept."
                                    return@launch
                                }
                                prefs?.edit()?.remove(sourceKey)?.apply()
                            }
                            val deleted = withContext(Dispatchers.IO) { context.contentResolver.delete(uri, null, null) > 0 }
                            if (deleted) {
                                LiveAudio.forgetRecording(uri)
                                prefs?.edit()?.remove("bookmarks:$target")?.apply()
                                if (selected == target) {
                                    selected = null
                                    fileName = ""
                                    bookmarks.value = arrayListOf()
                                    reviewPoints = emptyList()
                                    reviewTempo = emptyList()
                                    position = 0f
                                    duration = 0f
                                    playhead.set(0)
                                    notice = ""
                                }
                            } else notice = deleteFailure
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            notice = deleteFailure
                        } finally {
                            deleting = false
                        }
                    }
                }, colors = ButtonDefaults.textButtonColors(contentColor = RecText)) { Text("Delete") }
            }
        )
    }
    val noteCounts by remember(live, a4, preview, showFileAnalysis) { derivedStateOf {
        if (preview) {
            val counts = IntArray(12)
            prominentNotes.forEachIndexed { index, note -> counts[note] = prominentNotes.size - index }
            counts.toList()
        } else {
            val end = if (showFileAnalysis) position else live.value.seconds
            val points = if (showFileAnalysis) reviewPoints else live.value.pitch
            countNotes(points, end, a4).toList()
        }
    } }
    val micMessage by remember(live) { derivedStateOf { live.value.message } }
    LaunchedEffect(notice) {
        if (!preview && notice.isNotEmpty()) {
            Toast.makeText(context, notice, Toast.LENGTH_LONG).show()
            notice = ""
        }
    }
    val audioMessage = take.message.ifEmpty { if (playing) "" else micMessage }
    LaunchedEffect(audioMessage) {
        if (!preview && audioMessage.isNotEmpty()) Toast.makeText(context, audioMessage, Toast.LENGTH_LONG).show()
    }
    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().background(Ink).safeDrawingPadding()) { page ->
        val screen = Screen.entries[page % screenCount]
        Column(Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = if (screen == Screen.JAM) 0.dp else 4.dp, bottom = 4.dp)) {
            val fileGraph = screen == Screen.JAM && playing
            LiveHeader(take.phase, elapsed, bpm = headerBpm,
                tempo = { if (fileGraph) reviewTempo else live.value.tempo },
                time = { if (fileGraph) position else live.value.seconds }, preview = preview) {
                if (!preview && !choosing && importJob == null) {
                    if (recording) LiveAudio.stop()
                    else {
                        playing = false
                        pendingRecord = true
                        if (!permitted) micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            }
            if (!preview) {
                if (!permitted && !fileGraph) TextButton(onClick = { micPermission.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Enable microphone") }
            }

            when (screen) {
                Screen.TUNER -> {
                    Tuner(Modifier.weight(1f), a4, if (preview) 41.30f else live.value.tunerHz)
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Thick pitch line", color = Text, fontSize = 14.sp)
                            Switch(checked = thickPitchLine, onCheckedChange = {
                                thickPitchLine = it
                                prefs?.edit()?.putBoolean("thickPitchLine", it)?.apply()
                            }, modifier = Modifier.semantics { contentDescription = "Thick pitch line" })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Recording format", color = Text, fontSize = 14.sp)
                            for (format in RecordingFormat.entries) {
                                FilterChip(selected = recordingFormat == format, enabled = !recording,
                                    onClick = {
                                        recordingFormatName = format.name
                                        prefs?.edit()?.putString("recordingFormat", format.name)?.apply()
                                    }, label = { Text(format.label) })
                            }
                        }
                        Text("A4 = $a4 Hz", color = Mint, fontSize = 19.sp, fontFamily = FontFamily.Monospace)
                        Slider(value = a4.toFloat(), onValueChange = { a4 = it.roundToInt() },
                            onValueChangeFinished = { prefs?.edit()?.putInt("a4", a4)?.apply() },
                            valueRange = 415f..466f, steps = 50,
                            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 12.dp)
                                .semantics { contentDescription = "A4 reference in hertz" })
                        FlowRow(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.Center,
                            itemVerticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = showPlaylist, onCheckedChange = {
                                showPlaylist = it
                                prefs?.edit()?.putBoolean("showPlaylist", it)?.apply()
                            }, modifier = Modifier.semantics { contentDescription = "Show playlist in Jam" })
                            TextButton(onClick = { playlistDraft = playlist }) { Text("Playlist") }
                            Text("Giedrius Balbieris <metagi@gmail.com>",
                                modifier = Modifier.padding(horizontal = 4.dp),
                                color = Muted, fontSize = 12.sp, lineHeight = 14.sp,
                                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Screen.JAM -> {
                    val canReview = !recording && !pendingRecord && !deleting && (preview || player != null)
                    PitchPlot(Modifier.weight(1f), { if (fileGraph) position else if (preview) 17f else live.value.seconds }, a4,
                        noteCounts = noteCounts, thickPitchLine = thickPitchLine, playlist = playlist.takeIf { showPlaylist }.orEmpty(),
                        preview = preview,
                        points = { if (fileGraph) reviewPoints else live.value.pitch })
                    Slider(value = position.coerceIn(0f, duration.coerceAtLeast(1f)), onValueChange = { seek(it) },
                         valueRange = 0f..duration.coerceAtLeast(1f), enabled = canReview,
                        modifier = Modifier.semantics { contentDescription = "Playback position" })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        bookmarks.value.forEach { mark ->
                            Surface(shape = RoundedCornerShape(8.dp), color = Panel) {
                                Text(clock(mark.toInt()), color = Mint, fontSize = 12.sp,
                                    modifier = Modifier.combinedClickable(enabled = canReview, onClick = { seek(mark) },
                                        onLongClick = { bookmarks.value = ArrayList(bookmarks.value.filter { it != mark }) },
                                        onLongClickLabel = "Delete bookmark").padding(10.dp))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = {
                            if (importJob != null) importJob?.cancel()
                            else if (recording) notice = "Stop recording before choosing a file."
                            else {
                                playing = false
                                choosing = true
                                picker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                     addCategory(Intent.CATEGORY_OPENABLE)
                                     type = "audio/*"
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                                    putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                                        DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Music"))
                                })
                            }
                        }, enabled = !choosing && !deleting, contentPadding = PaddingValues(horizontal = 12.dp)) {
                            Text(if (importJob != null) "Cancel" else "File")
                        }
                        val fileLabel = if (importJob != null) "Importing · ${copiedBytes / 1024} KiB copied"
                            else fileName.ifEmpty { if (preview) "Demo take" else "" }
                        Text(fileLabel, Modifier.weight(1f).padding(horizontal = 4.dp),
                            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, color = Muted)
                        IconButton(onClick = { deleteTarget = selected },
                            enabled = selected != null && !recording && !choosing && importJob == null && !deleting,
                            colors = IconButtonDefaults.iconButtonColors(contentColor = RecText)) {
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete current recording")
                        }
                        ReviewIconButton(if (playing) "Pause" else "Play", canReview, {
                            if (recording) notice = "Stop recording before playing a file."
                            else { if (!playing && position >= duration) seek(0f); playing = !playing; notice = "" }
                        }) { color ->
                            if (playing) {
                                drawRoundRect(color, Offset(size.width * 0.25f, size.height * 0.17f),
                                    androidx.compose.ui.geometry.Size(size.width * 0.17f, size.height * 0.66f))
                                drawRoundRect(color, Offset(size.width * 0.58f, size.height * 0.17f),
                                    androidx.compose.ui.geometry.Size(size.width * 0.17f, size.height * 0.66f))
                            } else drawPath(Path().apply {
                                moveTo(size.width * 0.28f, size.height * 0.13f)
                                lineTo(size.width * 0.85f, size.height * 0.5f)
                                lineTo(size.width * 0.28f, size.height * 0.87f)
                                close()
                            }, color)
                        }
                        ReviewIconButton("Add bookmark", canReview && bookmarks.value.size < 4, {
                            if (bookmarks.value.size < 4 && position.toInt().toFloat() !in bookmarks.value)
                                bookmarks.value = ArrayList((bookmarks.value + position.toInt().toFloat()).sorted())
                        }) { color ->
                            drawPath(Path().apply {
                                moveTo(size.width * 0.18f, size.height * 0.14f)
                                lineTo(size.width * 0.82f, size.height * 0.14f)
                                lineTo(size.width * 0.82f, size.height * 0.89f)
                                lineTo(size.width * 0.5f, size.height * 0.68f)
                                lineTo(size.width * 0.18f, size.height * 0.89f)
                                close()
                            }, color, style = Stroke(width = 1.8.dp.toPx()))
                            drawLine(color, Offset(size.width * 0.36f, size.height * 0.40f),
                                Offset(size.width * 0.64f, size.height * 0.40f), 1.8.dp.toPx())
                            drawLine(color, Offset(size.width * 0.5f, size.height * 0.26f),
                                Offset(size.width * 0.5f, size.height * 0.54f), 1.8.dp.toPx())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewIconButton(label: String, enabled: Boolean, onClick: () -> Unit,
                             icon: DrawScope.(Color) -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.semantics { contentDescription = label }) {
        Canvas(Modifier.size(21.dp)) { icon(if (enabled) Mint else Muted.copy(alpha = 0.45f)) }
    }
}

@Composable
private fun LiveHeader(phase: TakePhase, elapsed: Int, bpm: Int?,
                       tempo: () -> List<TempoPoint>, time: () -> Float, preview: Boolean, toggle: () -> Unit) {
    val recording = phase == TakePhase.RECORDING
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(bpm?.toString() ?: "—", Modifier.widthIn(min = 54.dp).semantics {
            contentDescription = bpm?.let { "$it beats per minute" } ?: "Tempo unavailable"
        },
            color = Color(0xFFFFD477), fontFamily = FontFamily.Monospace, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.weight(1f).height(48.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val color = Muted
                if (preview) {
                    fun y(j: Int) = size.height * (0.55f - 0.17f * sin(j * 0.25f) - 0.07f * sin(j * 0.8f))
                    for (i in 1..34) drawLine(color, Offset(size.width * (i - 1) / 34, y(i - 1)),
                        Offset(size.width * i / 34, y(i)), 1.5.dp.toPx(), StrokeCap.Round)
                } else {
                    val end = time()
                    var previous: Offset? = null
                    for (point in tempo()) {
                        if (point.seconds < end - 30f) continue
                        if (point.seconds > end) break
                        val next = point.bpm?.let { Offset(size.width * (1f - (end - point.seconds) / 30f),
                             size.height * (0.85f - ((it - 48) / 175f).coerceIn(0f, 1f) * 0.7f)) }
                        if (next != null) previous?.let { drawLine(color, it, next, 1.5.dp.toPx(), StrokeCap.Round) }
                        previous = next
                    }
                }
            }
        }
        OutlinedButton(onClick = toggle, enabled = phase == TakePhase.IDLE || recording,
            modifier = Modifier.width(108.dp).heightIn(min = 48.dp)
            .semantics { contentDescription = if (recording) "Stop recording, ${clock(elapsed)} elapsed" else "Start recording" },
            border = androidx.compose.foundation.BorderStroke(1.dp, if (recording) Rec else RecText),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (recording) Rec else Color.Transparent,
                contentColor = if (recording) Color.White else RecText), contentPadding = PaddingValues(horizontal = 8.dp),
            shape = RoundedCornerShape(11.dp)) {
            Text(when (phase) {
                TakePhase.STARTING -> "Starting"
                TakePhase.SAVING -> "Saving"
                TakePhase.RECORDING -> clock(elapsed)
                TakePhase.IDLE -> "REC"
            }, fontFamily = FontFamily.Monospace,
                fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PitchPlot(modifier: Modifier, time: () -> Float, a4: Int, noteCounts: List<Int> = emptyList(), thickPitchLine: Boolean,
                      playlist: String, preview: Boolean = false, points: () -> List<PitchPoint> = { emptyList() }) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
      val labelSize = (maxHeight.value / 12 * 0.79f).coerceIn(12f, 48f)
      Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
          val hasPlaylist = playlist.isNotBlank()
          if (hasPlaylist) {
              val rowHeight = maxHeight / 12
              PlaylistBackdrop(playlist, rowHeight)
          }
          Canvas(Modifier.matchParentSize().semantics { contentDescription = "Scrolling 12-note pitch detections" }) {
            val lane = size.height / 12
            val radius = (if (thickPitchLine) 6.dp else 2.dp).toPx()
            val end = time()
            val start = end - 8f
            fun y(pitch: Float) = size.height - lane * (pitch + 0.5f)
            fun drawDetection(seconds: Float, midiPitch: Double, estimated: Boolean = false) {
                val shifted = midiPitch + 0.5
                val pitch = (shifted - floor(shifted / 12) * 12 - 0.5).toFloat()
                drawCircle(accents[Math.floorMod(pitch.roundToInt(), 12)].copy(alpha = if (estimated) 0.65f else 1f), radius,
                    Offset((seconds - start) * size.width / 8f, y(pitch)))
            }
            repeat(12) { i ->
                val center = y(i.toFloat())
                drawLine(Color.White.copy(alpha = if (hasPlaylist) 0.22f else 1f),
                    Offset(0f, center), Offset(size.width, center), strokeWidth = 1f)
            }
            if (preview) {
                val referenceShift = 12 * log2(a4 / 440.0)
                for (i in 0..200) {
                    val seconds = start + i * 8f / 200
                    if (seconds < 0f) continue
                    val group = floor(seconds / 0.82f).toInt()
                    val note = demoMelody[group % demoMelody.size]
                    drawDetection(seconds, note + 0.11 * sin(seconds * 15f) - referenceShift)
                }
            } else {
                val data = points()
                var index = data.binarySearchBy(start) { it.seconds }.let { if (it < 0) -it - 1 else it }
                while (index > 0 && data[index - 1].seconds == start) index--
                for (i in index until data.size) {
                    val sample = data[i]
                    if (sample.seconds > end) break
                    sample.hz?.let { drawDetection(sample.seconds, midi(it, a4), sample.estimated) }
                }
            }
          }
        }
        Column(Modifier.fillMaxHeight().padding(start = 3.dp)) {
            val strongest = noteCounts.maxOrNull() ?: 0
            for (index in 11 downTo 0) Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                val count = noteCounts.getOrElse(index) { 0 }
                val brightness = when {
                    strongest == 0 || count == 0 -> 0f
                    else -> 0.12f + 0.88f * count / strongest
                }
                Text(notes[index], color = accents[index].copy(alpha = brightness),
                    fontFamily = FontFamily.Monospace, fontSize = labelSize.sp, lineHeight = labelSize.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
      }
    }
}

@Composable
private fun PlaylistBackdrop(playlist: String, rowHeight: Dp) {
    val lines = remember(playlist) { playlist.lines() }
    val fontSize = with(LocalDensity.current) { (rowHeight * 0.78f).toSp() }
    key(playlist) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 6.dp)) {
            items(lines) { line ->
                Box(Modifier.fillMaxWidth().height(rowHeight), contentAlignment = Alignment.CenterStart) {
                    Text(line, color = Color(0xFF757575).copy(alpha = 0.5f), fontSize = fontSize,
                        lineHeight = fontSize * 1.15f,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private val demoMelody = intArrayOf(4, 4, 7, 9, 9, 2, 4, 7)

@Composable
private fun Tuner(modifier: Modifier, a4: Int, measured: Float?) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        val pitch = measured?.let { midi(it, a4) } ?: 0.0
        val nearest = floor(pitch + 0.5).toInt()
        val relative = ((pitch - nearest) * 100).toFloat()
        val note = if (measured == null) "—" else "${notes[Math.floorMod(nearest, 12)]}${Math.floorDiv(nearest, 12) - 1}"
        Text(note, fontSize = 104.sp, lineHeight = 112.sp, fontWeight = FontWeight.Bold, color = Text)
        Text(measured?.let { String.format(Locale.ROOT, "%.2f Hz", it) } ?: "— Hz", color = Muted,
            fontSize = 25.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(24.dp))
        Canvas(Modifier.fillMaxWidth().height(62.dp)) {
            val left = size.width * 0.1f
            val span = size.width * 0.8f
            val y = size.height * 0.55f
            drawLine(Muted, Offset(left, y), Offset(left + span, y), 1.dp.toPx())
            for (i in 0..20) {
                val x = left + span * i / 20
                val tick = if (i == 10) 27.dp else if (i % 5 == 0) 14.dp else 5.dp
                drawLine(if (i == 10) Text else Muted, Offset(x, y - tick.toPx()),
                    Offset(x, y + if (i == 10) 22.dp.toPx() else 8.dp.toPx()),
                    if (i == 10) 4.dp.toPx() else 1.dp.toPx(), StrokeCap.Round)
            }
            val x = left + span * ((relative.coerceIn(-50f, 50f) + 50f) / 100f)
            if (measured != null) drawLine(TunerYellow, Offset(x, 4.dp.toPx()), Offset(x, size.height * 0.80f + 4.dp.toPx()),
                8.dp.toPx(), StrokeCap.Round)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            for (label in listOf("−50¢", "−25¢", "0", "+25¢", "+50¢")) Text(label,
                Modifier.weight(1f), textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace,
                color = if (label == "0") Text else Muted, fontSize = if (label == "0") 39.sp else 21.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
        Spacer(Modifier.height(22.dp))
        val status = if (abs(relative) <= 5) "IN TUNE" else if (relative < 0) "FLAT" else "SHARP"
        Text(if (measured == null) " " else String.format(Locale.ROOT, "%+.0f cents · %s", relative, status), color = Mint,
            fontSize = 23.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

private fun clock(seconds: Int) = String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)

@Preview(name = "Jam • phone", widthDp = 390, heightDp = 780)
@Composable private fun JamPreview() = PrototypeTheme { Prototype(preview = true) }

@Preview(name = "Tuner • bass + recording", widthDp = 390, heightDp = 780)
@Composable private fun TunerPreview() = PrototypeTheme { Prototype(initial = Screen.TUNER, preview = true) }

@Preview(name = "Jam • tablet", widthDp = 800, heightDp = 900)
@Composable private fun JamTabletPreview() = PrototypeTheme { Prototype(preview = true) }
