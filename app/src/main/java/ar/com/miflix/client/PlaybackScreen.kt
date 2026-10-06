package ar.com.miflix.client

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import androidx.media3.ui.CaptionStyleCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import java.util.Locale

@Composable
internal fun PlaybackScreen(
    link: String,
    title: String,
    progressKey: String,
    startPosition: Long,
    progressStore: PlaybackProgressStore,
    previousEpisode: EpisodeNavigationTarget?,
    nextEpisode: EpisodeNavigationTarget?,
    onNavigateEpisode: (EpisodeNavigationTarget) -> Unit,
    fullscreen: Boolean,
    onFullscreenToggle: () -> Unit,
    onOpenTelegram: () -> Unit,
    onPlaybackFailure: (PlaybackIssue) -> Unit
) {
    val tag = "MiFlixPlayback"
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(view) {
        val previousKeepScreenOn = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previousKeepScreenOn }
    }
    val session = remember { TelegramSession.get(context) }
    val state by session.state.collectAsState()
    var video by remember(link) { mutableStateOf<TelegramVideo?>(null) }
    var failure by remember(link) { mutableStateOf<PlaybackIssue?>(null) }
    var downloading by remember(link) { mutableStateOf(false) }
    var attempt by remember(link) { mutableIntStateOf(0) }
    var resumePosition by rememberSaveable(link) { mutableStateOf(startPosition) }
    var resumePlay by rememberSaveable(link) { mutableStateOf(true) }
    var lastProgressWrite by remember(progressKey) { mutableLongStateOf(0L) }
    val owner = LocalLifecycleOwner.current
    var foreground by remember(owner) {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_START) {
                foreground = event == Lifecycle.Event.ON_START
                Log.d("MiFlixLifecycle", "SCREEN_LIFECYCLE event=$event telegramReady=${session.state.value is TelegramSession.State.Ready}")
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    fun saveProgress(position: Long, duration: Long, immediate: Boolean) {
        val elapsed = SystemClock.elapsedRealtime()
        if (immediate || elapsed - lastProgressWrite >= ProgressRules.SAVE_INTERVAL_MS) {
            lastProgressWrite = elapsed
            progressStore.save(progressKey, position, duration, System.currentTimeMillis())
        }
    }

    LaunchedEffect(link, state is TelegramSession.State.Ready, attempt, foreground) {
        Log.d(tag, "SCREEN_EFFECT_START telegramReady=${state is TelegramSession.State.Ready} attempt=$attempt videoId=${video?.fileId}")
        if (foreground && state is TelegramSession.State.Ready && video == null) {
            downloading = true
            failure = null
            try {
                // The old owner must have finished release → clear before resolving again.
                withFrameNanos { }
                if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@LaunchedEffect
                Log.d("MiFlixLifecycle", "RESOLVE_PLAYBACK position=$resumePosition playWhenReady=$resumePlay")
                val resolved = session.obtainVideo(link)
                if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) video = resolved
                else resolved.clear() // No Player has ever owned this result.
                Log.d(tag, "SCREEN_VIDEO_READY fileId=${video?.fileId} size=${video?.size}")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(tag, "SCREEN_RESOLVE_ERROR", error)
                if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@LaunchedEffect
                val issue = classifyPlaybackIssue(error)
                failure = issue
                onPlaybackFailure(issue)
            } finally {
                downloading = false
            }
        }
    }

    when {
        !foreground -> Box(Modifier.fillMaxSize())
        video != null -> VideoPlayer(video!!, title, fullscreen, onFullscreenToggle,
            resumePosition, resumePlay, previousEpisode, nextEpisode, onNavigateEpisode,
            onPlaybackFailure = onPlaybackFailure,
            onStopped = { video = null },
            onCheckpoint = { position, play ->
                resumePosition = position
                resumePlay = play
            },
            onProgress = { position, duration -> saveProgress(position, duration, false) },
            onBackground = { position, duration -> saveProgress(position, duration, true) },
            onExit = { position, duration -> progressStore.finish(progressKey, position, duration,
                System.currentTimeMillis()) },
            onCompleted = { progressStore.clear(progressKey) })
        state is TelegramSession.State.Ready -> {
            Column(Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (downloading) "Preparando video…" else "No se pudo reproducir",
                    style = MaterialTheme.typography.titleLarge)
                if (downloading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Conectando con Telegram. La reproducción empieza con el primer fragmento.")
                }
                if (failure != null) {
                    Text(failure!!.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { attempt++ }) { Text("Reintentar") }
                    TextButton(onClick = onOpenTelegram) { Text("Abrir publicación en Telegram") }
                }
            }
        }
        else -> TelegramLogin(
            session = session,
            state = state,
            onOpenTelegram = onOpenTelegram
        )
    }
}

@Composable
internal fun TelegramConnectScreen() {
    val context = LocalContext.current
    val session = remember { TelegramSession.get(context) }
    val state by session.state.collectAsState()
    if (state is TelegramSession.State.Ready) {
        Text("Cuenta Telegram conectada", modifier = Modifier.padding(24.dp))
    } else TelegramLogin(session, state, onOpenTelegram = null)
}

@Composable
private fun TelegramLogin(
    session: TelegramSession,
    state: TelegramSession.State,
    onOpenTelegram: (() -> Unit)?
) {
    val scope = rememberCoroutineScope()
    var input by remember(state) { mutableStateOf("") }
    var problem by remember(state) { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Conectar Telegram", style = MaterialTheme.typography.headlineSmall)
        Text("Iniciá sesión con una cuenta que pertenezca al canal. Esta sesión es independiente de la app oficial de Telegram.")
        when (state) {
            TelegramSession.State.NeedsApi -> {
                if (BuildConfig.TELEGRAM_API_ID > 0 && BuildConfig.TELEGRAM_API_HASH.isNotBlank()) {
                    CircularProgressIndicator()
                } else {
                    Text("Esta compilación no está preparada para conectar Telegram. Pedile al administrador la APK familiar configurada.")
                    Text("Diagnóstico de desarrollo: falta la configuración privada de Telegram durante la compilación.")
                }
            }
            TelegramSession.State.Starting -> CircularProgressIndicator()
            TelegramSession.State.Phone,
            TelegramSession.State.Email,
            TelegramSession.State.EmailCode,
            TelegramSession.State.Code,
            TelegramSession.State.Password -> {
                val label = when (state) {
                    TelegramSession.State.Phone -> "Número de teléfono"
                    TelegramSession.State.Email -> "Correo solicitado por Telegram"
                    TelegramSession.State.EmailCode -> "Código enviado por correo"
                    TelegramSession.State.Code -> "Código enviado por Telegram"
                    else -> "Contraseña de verificación en dos pasos"
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (state is TelegramSession.State.Phone) {
                        Text("+54", modifier = Modifier.padding(end = 12.dp))
                    }
                    OutlinedTextField(
                        value = input, onValueChange = {
                            if (state !is TelegramSession.State.Phone || isArgentinaPhoneInput(it)) {
                                input = if (state is TelegramSession.State.Phone)
                                    it.trimStart().removePrefix("+54") else it
                            }
                        },
                        label = { Text(label) },
                        singleLine = true,
                        visualTransformation = if (state is TelegramSession.State.Password)
                            PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (state is TelegramSession.State.Phone) KeyboardType.Phone
                            else KeyboardType.Text
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
                Button(onClick = {
                    submitting = true
                    problem = null
                    scope.launch {
                        try {
                            session.submit(input)
                            input = ""
                        } catch (error: Exception) {
                            problem = error.message ?: "Telegram rechazó el dato."
                        } finally {
                            submitting = false
                        }
                    }
                }, enabled = input.isNotBlank() && !submitting) { Text("Continuar") }
            }
            is TelegramSession.State.OtherDevice -> {
                Text("Telegram pide confirmar el inicio de sesión en otro dispositivo:")
                Text(state.link, color = MaterialTheme.colorScheme.primary)
                Text("Abrí Telegram en otro teléfono y confirmá la nueva sesión.")
            }
            is TelegramSession.State.Failed -> {
                Text(if (BuildConfig.TELEGRAM_API_ID > 0)
                    "No pudimos conectar con Telegram. Revisá tu conexión e intentá de nuevo."
                    else state.message, color = MaterialTheme.colorScheme.error)
                Button(onClick = { session.reset() }) {
                    Text("Reintentar")
                }
            }
            TelegramSession.State.Ready -> Unit
        }
        if (problem != null) Text(problem!!, color = MaterialTheme.colorScheme.error)
        if (onOpenTelegram != null) TextButton(onClick = onOpenTelegram) {
            Text("Abrir publicación en Telegram")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoPlayer(
    video: TelegramVideo,
    title: String,
    fullscreen: Boolean,
    onFullscreenToggle: () -> Unit,
    resumePosition: Long,
    resumePlay: Boolean,
    previousEpisode: EpisodeNavigationTarget?,
    nextEpisode: EpisodeNavigationTarget?,
    onNavigateEpisode: (EpisodeNavigationTarget) -> Unit,
    onPlaybackFailure: (PlaybackIssue) -> Unit,
    onStopped: () -> Unit,
    onCheckpoint: (Long, Boolean) -> Unit,
    onProgress: (Long, Long) -> Unit,
    onBackground: (Long, Long) -> Unit,
    onExit: (Long, Long) -> Unit,
    onCompleted: () -> Unit
) {
    val tag = "MiFlixPlayback"
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val subtitlePreferences = remember(context) {
        context.getSharedPreferences("miflix_client", android.content.Context.MODE_PRIVATE)
    }
    var subtitleSize by remember(subtitlePreferences) {
        mutableStateOf(SubtitleSize.fromStored(
            subtitlePreferences.getString(SubtitleSize.PREFERENCE_KEY, null)))
    }
    val player = remember(video) {
        Log.d("MiFlixLifecycle", "PLAYER_CREATE fileId=${video.fileId} position=$resumePosition playWhenReady=$resumePlay")
        Log.d(tag, "PLAYER_CREATE fileId=${video.fileId} size=${video.size} " +
            "resumePosition=$resumePosition playWhenReady=$resumePlay")
        ExoPlayer.Builder(context).build().apply {
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
            Log.d("MiFlixTracks", "DEFAULT_TEXT_DISABLED")
            setMediaSource(ProgressiveMediaSource.Factory(video.factory()).createMediaSource(
                MediaItem.fromUri(Uri.parse("miflix://telegram/${video.fileId}"))
            ))
            if (resumePosition > 0) seekTo(resumePosition)
            playWhenReady = resumePlay
            prepare()
            Log.d(tag, "PLAYER_PREPARE state=$playbackState duration=$duration")
        }
    }
    var released by remember(player) { mutableStateOf(false) }
    var availableTracks by remember(player) { mutableStateOf(player.currentTracks) }
    var showTracksPanel by remember(player) { mutableStateOf(false) }
    var defaultAudioApplied by remember(player) { mutableStateOf(false) }
    val trackOptions = remember(availableTracks) { selectableMediaTracks(availableTracks) }
    val audioTracks = trackOptions.filter { it.group.type == C.TRACK_TYPE_AUDIO }
    val textTracks = trackOptions.filter { it.group.type == C.TRACK_TYPE_TEXT }
    val hasTrackChoice = audioTracks.size > 1 || textTracks.isNotEmpty()
    var error by remember(video) { mutableStateOf<String?>(null) }
    var buffering by remember(video) { mutableStateOf(true) }
    var playing by remember(video) { mutableStateOf(player.isPlaying) }
    var currentPosition by remember(video) { mutableLongStateOf(player.currentPosition) }
    var duration by remember(video) { mutableLongStateOf(player.duration) }
    var controlsVisible by remember(video) { mutableStateOf(true) }
    var indication by remember(video) { mutableStateOf<String?>(null) }
    val scrub = remember(video) { ProgressScrub() }
    val progressInteractions = remember(video) { MutableInteractionSource() }
    val progressPressed by progressInteractions.collectIsPressedAsState()
    val progressDragged by progressInteractions.collectIsDraggedAsState()
    var brightnessTouching by remember(video) { mutableStateOf(false) }
    var volumeTouching by remember(video) { mutableStateOf(false) }
    val levels = rememberPlayerLevels(controlsVisible)
    var episodeNavigating by remember(video) { mutableStateOf(false) }
    val checkpoint by rememberUpdatedState(onCheckpoint)
    val progress by rememberUpdatedState(onProgress)
    val background by rememberUpdatedState(onBackground)
    val exitProgress by rememberUpdatedState(onExit)
    val completed by rememberUpdatedState(onCompleted)
    val navigateEpisode by rememberUpdatedState(onNavigateEpisode)
    val playbackFailure by rememberUpdatedState(onPlaybackFailure)
    val stopped by rememberUpdatedState(onStopped)

    fun selectTrack(option: PlayerTrackOption, automatic: Boolean = false) {
        if (released || (!automatic &&
            !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))) return
        val type = option.group.type
        val event = if (type == C.TRACK_TYPE_AUDIO) "AUDIO" else "TEXT"
        val groupIndex = player.currentTracks.groups.indexOfFirst {
            it.type == type && it.mediaTrackGroup == option.group.mediaTrackGroup
        }
        val group = player.currentTracks.groups.getOrNull(groupIndex)
        if (group == null || option.trackIndex !in 0 until group.length ||
            !group.isTrackSupported(option.trackIndex)) {
            Log.d("MiFlixTracks", "${event}_SELECTION_REJECTED reason=STALE_OR_UNSUPPORTED")
            return
        }
        if (type == C.TRACK_TYPE_AUDIO) defaultAudioApplied = true
        Log.d("MiFlixTracks", "${event}_SELECTION_REQUEST group=$groupIndex track=${option.trackIndex}")
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
            .build()
        // Submitted parameters; TRACKS_CHANGED confirms the actual renderer selection.
        Log.d("MiFlixTracks", "${event}_SELECTION_APPLIED group=$groupIndex track=${option.trackIndex}")
        if (automatic) Log.d("MiFlixTracks", "DEFAULT_AUDIO_SELECTED group=$groupIndex track=${option.trackIndex}")
        controlsVisible = true
    }

    fun disableText() {
        if (released || !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        Log.d("MiFlixTracks", "TEXT_DISABLED")
    }

    fun applyDefaultAudio(tracks: Tracks) {
        if (released || defaultAudioApplied) return
        val options = selectableMediaTracks(tracks).filter { it.group.type == C.TRACK_TYPE_AUDIO }
        if (options.none { it.group.isTrackSupported(it.trackIndex) }) return
        // Mark before assigning parameters: the resulting callback must not reset manual choices.
        defaultAudioApplied = true
        val preferred = preferredSpanishAudio(options.map {
            val format = it.group.getTrackFormat(it.trackIndex)
            AudioTrackMetadata(format.label, format.language,
                it.group.isTrackSupported(it.trackIndex), it.group.isTrackSelected(it.trackIndex))
        })
        if (preferred != null) selectTrack(options[preferred], automatic = true)
        else Log.d("MiFlixTracks", "DEFAULT_AUDIO_KEEP_MEDIA3")
    }

    fun switchEpisode(target: EpisodeNavigationTarget?) {
        if (target == null || episodeNavigating ||
            !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        episodeNavigating = true
        Log.d(tag, "EPISODE_NAV_TAP key=${target.progressKey} from=${player.currentPosition}")
        navigateEpisode(target)
    }

    fun seekToPosition(requested: Long, source: String) {
        val from = player.currentPosition
        val target = requested.coerceAtLeast(0L).let {
            if (player.duration > 0) it.coerceAtMost(player.duration) else it
        }
        Log.d(tag, "SEEK source=$source from=$from requested=$target")
        player.seekTo(target)
        currentPosition = target
        checkpoint(target, player.playWhenReady)
    }

    val finishScrub by rememberUpdatedState<() -> Unit> {
        scrub.finish(duration)?.let { seekToPosition(it, "slider") }
    }
    // Material3 1.2 keys its SliderState on this callback. Keep its identity stable
    // while reading the latest duration/seek callback, so recomposition cannot reset a gesture.
    val onScrubFinished = remember(video) { { finishScrub() } }

    LaunchedEffect(player) {
        while (!released) {
            currentPosition = player.currentPosition
            duration = player.duration
            checkpoint(currentPosition, player.playWhenReady)
            if (player.playbackState != Player.STATE_ENDED)
                progress(currentPosition, duration)
            delay(500)
        }
    }
    val controlInteracting = scrub.fraction != null || progressPressed || progressDragged ||
        brightnessTouching || volumeTouching
    LaunchedEffect(controlsVisible, playing, showTracksPanel, controlInteracting) {
        if (canAutoHideControls(controlsVisible, playing, showTracksPanel, controlInteracting)) {
            delay(3_000)
            if (canAutoHideControls(controlsVisible, playing, showTracksPanel,
                    scrub.fraction != null || progressPressed || progressDragged ||
                        brightnessTouching || volumeTouching))
                controlsVisible = false
        }
    }
    LaunchedEffect(indication) {
        if (indication != null) {
            delay(750)
            indication = null
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                availableTracks = tracks
                logMediaTracks(tracks, "TRACKS_CHANGED")
                applyDefaultAudio(tracks)
            }
            override fun onPlayerError(exception: PlaybackException) {
                Log.e(tag, "PLAYER_ERROR code=${exception.errorCode} name=${exception.errorCodeName} " +
                    "state=${player.playbackState} duration=${player.duration} position=${player.currentPosition}", exception)
                val issue = classifyPlaybackIssue(exception)
                error = issue.message
                playbackFailure(issue)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d(tag, "PLAYER_STATE state=$playbackState duration=${player.duration} " +
                    "position=${player.currentPosition} buffered=${player.bufferedPosition}")
                buffering = playbackState == Player.STATE_BUFFERING
                duration = player.duration
                if (playbackState == Player.STATE_ENDED) completed()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!released) checkpoint(player.currentPosition, playWhenReady)
            }
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                Log.d(tag, "PLAYER_TIMELINE reason=$reason windows=${timeline.windowCount} " +
                    "duration=${player.duration} seekable=${player.isCurrentMediaItemSeekable}")
            }
        }
        fun releasePlayer(backgrounded: Boolean) {
            if (released) return
            released = true
            val position = player.currentPosition
            val durationMs = player.duration
            // Capture user intent before release; background is not a user pause.
            checkpoint(position, player.playWhenReady)
            if (player.playbackState == Player.STATE_ENDED) completed()
            else if (backgrounded) background(position, durationMs)
            else exitProgress(position, durationMs)
            Log.d("MiFlixLifecycle", "PLAYER_RELEASE reason=${if (backgrounded) "ON_STOP" else "DISPOSE"} position=$position playWhenReady=${player.playWhenReady}")
            player.removeListener(listener)
            player.release()
            Log.d(tag, "PLAYER_RELEASED_CLEAR_VIDEO fileId=${video.fileId}")
            video.clear()
            Log.d("MiFlixLifecycle", "PLAYER_RELEASED_CLEAR_VIDEO fileId=${video.fileId}")
        }
        val observer = LifecycleEventObserver { _, event ->
            Log.d(tag, "PLAYER_LIFECYCLE event=$event state=${player.playbackState}")
            if (event == Lifecycle.Event.ON_STOP && !released) {
                releasePlayer(backgrounded = true)
                stopped()
            }
        }
        player.addListener(listener)
        availableTracks = player.currentTracks
        logMediaTracks(player.currentTracks, "LISTENER_ATTACHED")
        applyDefaultAudio(player.currentTracks)
        Log.d(tag, "PLAYER_LISTENER_ATTACHED state=${player.playbackState} duration=${player.duration}")
        owner.lifecycle.addObserver(observer)
        onDispose {
            Log.w(tag, "PLAYER_DISPOSE state=${player.playbackState} duration=${player.duration}",
                Throwable("Dispose caller stack"))
            owner.lifecycle.removeObserver(observer)
            releasePlayer(backgrounded = false)
        }
    }
    if (showTracksPanel && !released) {
        ModalBottomSheet(
            onDismissRequest = { showTracksPanel = false },
            containerColor = Color(0xFF18181C), contentColor = Color.White
        ) {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Audio y subtítulos", Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { showTracksPanel = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }
                Text("Audio", Modifier.padding(top = 16.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.titleMedium)
                audioTracks.forEach { option ->
                    val supported = option.group.isTrackSupported(option.trackIndex)
                    TrackChoiceRow(option.name, option.group.isTrackSelected(option.trackIndex),
                        enabled = supported && audioTracks.size > 1,
                        selectable = audioTracks.size > 1) { selectTrack(option) }
                }
                if (audioTracks.isEmpty()) Text("No disponible", color = Color.LightGray)
                Text("Subtítulos", Modifier.padding(top = 20.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.titleMedium)
                if (textTracks.isEmpty()) Text("No disponibles", color = Color.LightGray)
                else {
                    val textDisabled = C.TRACK_TYPE_TEXT in player.trackSelectionParameters.disabledTrackTypes
                    TrackChoiceRow("Desactivados", textDisabled) { disableText() }
                    textTracks.forEach { option ->
                        TrackChoiceRow(option.name,
                            !textDisabled && option.group.isTrackSelected(option.trackIndex),
                            enabled = option.group.isTrackSupported(option.trackIndex)) { selectTrack(option) }
                    }
                    Text("Tamaño del subtítulo", Modifier.padding(top = 20.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.titleMedium)
                    SubtitleSize.values().forEach { size ->
                        TrackChoiceRow(size.label, subtitleSize == size) {
                            if (subtitleSize != size) {
                                subtitleSize = size
                                subtitlePreferences.edit()
                                    .putString(SubtitleSize.PREFERENCE_KEY, size.name).apply()
                                Log.d("MiFlixTracks", "SUBTITLE_SIZE_CHANGED size=${size.name}")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        BoxWithConstraints(if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            val sideTrackHeight = if (fullscreen) (maxHeight - 190.dp).coerceIn(48.dp, 144.dp) else 48.dp
            val sideOffset = if (maxHeight < 300.dp) (-24).dp else 0.dp
            AndroidView(
                factory = { PlayerView(it).apply {
                    useController = false
                    this.player = player
                    subtitleView?.apply {
                        // Own presentation: embedded cue backgrounds/font sizes must not override it.
                        setApplyEmbeddedStyles(false)
                        setStyle(CaptionStyleCompat(
                            android.graphics.Color.WHITE,
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK,
                            null
                        ))
                        setFractionalTextSize(subtitleSize.fractionOfHeight)
                    }
                    this.tag = subtitleSize
                } },
                update = {
                    it.player = if (released) null else player
                    // Updating the existing View redraws current cues without touching the Player.
                    if (it.tag != subtitleSize) {
                        it.subtitleView?.setFractionalTextSize(subtitleSize.fractionOfHeight)
                        it.tag = subtitleSize
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.matchParentSize().pointerInput(player) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible },
                    onDoubleTap = { tap ->
                        val forward = tap.x >= size.width / 2f
                        val jump = if (forward) 10_000L else -10_000L
                        seekToPosition(player.currentPosition + jump,
                            if (forward) "double_right" else "double_left")
                        indication = if (forward) "10 s »" else "« 10 s"
                        controlsVisible = true
                    }
                )
            })
            if (indication != null) {
                Text(indication!!, Modifier.align(Alignment.Center)
                    .background(Color.Black.copy(alpha = .65f)).padding(16.dp),
                    color = Color.White, style = MaterialTheme.typography.headlineMedium)
            }
            if (buffering && error == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }
            if (controlsVisible) {
                VerticalPlayerControl(levels.brightness, levels.brightnessEnabled,
                    Icons.Default.Brightness6, "Brillo del reproductor", sideTrackHeight,
                    Modifier.align(Alignment.CenterStart).padding(start = 12.dp)
                        .offset(y = sideOffset),
                    onInteraction = { brightnessTouching = it }, onValueChange = levels.setBrightness)
                VerticalPlayerControl(levels.volume, levels.volumeEnabled,
                    Icons.Default.VolumeUp, "Volumen multimedia", sideTrackHeight,
                    Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)
                        .offset(y = sideOffset),
                    onInteraction = { volumeTouching = it }, onValueChange = levels.setVolume)
                if (hasTrackChoice) {
                    TextButton(onClick = { showTracksPanel = true }, enabled = !released,
                        modifier = Modifier.align(Alignment.TopEnd)
                            .background(Color.Black.copy(alpha = .6f))) {
                        Text("Audio y subtítulos", color = Color.White)
                    }
                }
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (fullscreen) 32.dp else 16.dp)) {
                    IconButton(onClick = { seekToPosition(player.currentPosition - 10_000L, "button_back") }) {
                        Icon(Icons.Default.Replay10, "Retroceder 10 segundos", tint = Color.White,
                            modifier = Modifier.size(32.dp))
                    }
                    IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() },
                        modifier = Modifier.size(56.dp).background(Color.Black.copy(alpha = .45f),
                            androidx.compose.foundation.shape.CircleShape)) {
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) "Pausar" else "Reproducir",
                            tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                    IconButton(onClick = { seekToPosition(player.currentPosition + 10_000L, "button_forward") }) {
                        Icon(Icons.Default.Forward10, "Adelantar 10 segundos", tint = Color.White,
                            modifier = Modifier.size(32.dp))
                    }
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Color.Black.copy(alpha = .6f)).padding(horizontal = 12.dp)) {
                    Slider(
                        value = scrub.fraction ?:
                            if (duration > 0) (currentPosition.toFloat() / duration).coerceIn(0f, 1f)
                            else 0f,
                        onValueChange = scrub::preview,
                        onValueChangeFinished = onScrubFinished,
                        interactionSource = progressInteractions,
                        enabled = duration > 0,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatPlaybackTime(scrub.fraction?.let { scrubPosition(it, duration) }
                            ?: currentPosition), color = Color.White,
                            style = MaterialTheme.typography.labelMedium)
                        Text(" / " + formatPlaybackTime(duration), color = Color.LightGray,
                            style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.weight(1f))
                        if (fullscreen && (previousEpisode != null || nextEpisode != null)) {
                            EpisodeControls(previousEpisode, nextEpisode, episodeNavigating, ::switchEpisode)
                        }
                        IconButton(onClick = onFullscreenToggle, modifier = Modifier.size(40.dp)) {
                            Icon(if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (fullscreen) "Salir de pantalla completa" else "Pantalla completa",
                                tint = Color.White)
                        }
                    }
                }
            }
        }
        if (!fullscreen) {
            if (controlsVisible && (previousEpisode != null || nextEpisode != null)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    EpisodeControls(previousEpisode, nextEpisode, episodeNavigating, ::switchEpisode)
                }
            }
            Text(title, Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
            if (error != null) Text(error!!, Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.error)
        } else if (error != null) {
            Text(error!!, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        }
    }
}

private data class PlayerTrackOption(
    val trackIndex: Int,
    val group: Tracks.Group,
    val name: String
)

private fun selectableMediaTracks(tracks: Tracks): List<PlayerTrackOption> = buildList {
    var audioCount = 0
    var textCount = 0
    tracks.groups.forEach { group ->
        if (group.type == C.TRACK_TYPE_AUDIO || group.type == C.TRACK_TYPE_TEXT) {
            repeat(group.length) { index ->
                val fallback = if (group.type == C.TRACK_TYPE_AUDIO) "Audio ${++audioCount}"
                    else "Subtítulo ${++textCount}"
                val format = group.getTrackFormat(index)
                add(PlayerTrackOption(index, group, mediaTrackName(format.label, format.language, fallback)))
            }
        }
    }
}

@Composable
private fun TrackChoiceRow(name: String, selected: Boolean, enabled: Boolean = true,
    selectable: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
        .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (selectable) RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(name, Modifier.weight(1f), color = if (enabled || !selectable) Color.White else Color.Gray)
    }
}

@Composable
private fun EpisodeControls(previous: EpisodeNavigationTarget?, next: EpisodeNavigationTarget?,
    navigating: Boolean, onNavigate: (EpisodeNavigationTarget?) -> Unit) {
    if (previous != null) TextButton(onClick = { onNavigate(previous) }, enabled = !navigating) {
        Icon(Icons.Default.SkipPrevious, "Episodio anterior", tint = Color.White)
        Text("Anterior", color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
    if (next != null) TextButton(onClick = { onNavigate(next) }, enabled = !navigating) {
        Text("Siguiente", color = Color.White, style = MaterialTheme.typography.labelMedium)
        Icon(Icons.Default.SkipNext, "Siguiente episodio", tint = Color.White)
    }
}

/** Read-only snapshots: no track preferences, IDs, URIs or player state are logged here. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun logMediaTracks(tracks: Tracks, event: String) {
    val tag = "MiFlixTracks"
    fun text(value: String?): String = value?.takeIf { it.isNotBlank() }
        ?.replace('\n', ' ')?.replace('\r', ' ')?.replace('\t', ' ') ?: "unknown"
    fun number(value: Int): String = if (value == Format.NO_VALUE) "unknown" else value.toString()
    Log.d(tag, "$event groups=${tracks.groups.size}")
    tracks.groups.forEachIndexed { groupIndex, group ->
        val type = when (group.type) {
            C.TRACK_TYPE_VIDEO -> "VIDEO"
            C.TRACK_TYPE_AUDIO -> "AUDIO"
            C.TRACK_TYPE_TEXT -> "TEXT/SUBTITLE"
            else -> "UNKNOWN"
        }
        repeat(group.length) { trackIndex ->
            val format = group.getTrackFormat(trackIndex)
            val support = group.getTrackSupport(trackIndex)
            val supportName = when (support) {
                C.FORMAT_HANDLED -> "HANDLED"
                C.FORMAT_EXCEEDS_CAPABILITIES -> "EXCEEDS_CAPABILITIES"
                C.FORMAT_UNSUPPORTED_DRM -> "UNSUPPORTED_DRM"
                C.FORMAT_UNSUPPORTED_SUBTYPE -> "UNSUPPORTED_SUBTYPE"
                C.FORMAT_UNSUPPORTED_TYPE -> "UNSUPPORTED_TYPE"
                else -> "UNKNOWN"
            }
            Log.d(tag, "$type group=$groupIndex track=$trackIndex " +
                "label=${text(format.label)} language=${text(format.language)} " +
                "sampleMimeType=${text(format.sampleMimeType)} codecs=${text(format.codecs)} " +
                "channelCount=${number(format.channelCount)} sampleRate=${number(format.sampleRate)} " +
                "bitrate=${number(format.bitrate)} selected=${group.isTrackSelected(trackIndex)} " +
                "supported=${group.isTrackSupported(trackIndex)} support=$supportName($support)")
        }
    }
}

private fun formatPlaybackTime(milliseconds: Long): String {
    if (milliseconds < 0) return "00:00"
    val seconds = milliseconds / 1_000
    return if (seconds >= 3_600) String.format(Locale.US, "%d:%02d:%02d",
        seconds / 3_600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
}
