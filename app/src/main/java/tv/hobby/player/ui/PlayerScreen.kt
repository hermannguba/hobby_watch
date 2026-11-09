package tv.hobby.player.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.hobby.player.data.BROWSER_UA
import tv.hobby.player.data.PlayerRequest
import tv.hobby.player.data.SessionCookies

@Composable
fun PlayerScreen(request: PlayerRequest, onBack: () -> Unit) {
    when {
        !request.directUrl.isNullOrBlank() -> DirectPlayer(request, onBack)
        !request.externalUrl.isNullOrBlank() -> ExternalHandoff(request, onBack)
        else -> MessageScreen("Этот ролик нечем открыть.", onBack)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun DirectPlayer(request: PlayerRequest, onBack: () -> Unit) {
    val context = LocalContext.current
    val url = request.directUrl.orEmpty()
    val timeline = remember { FocusRequester() }
    val playButton = remember { FocusRequester() }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var controls by remember { mutableStateOf(true) }
    var tick by remember { mutableStateOf(0) }
    var failed by remember { mutableStateOf(false) }

    val player = remember(url) {
        val headers = linkedMapOf<String, String>()
        request.referer?.let { referer ->
            headers["Referer"] = referer
            headers["Origin"] = referer.trimEnd('/')
        }
        val cookies = SessionCookies.header(url)
        if (cookies.isNotBlank()) headers["Cookie"] = cookies
        val dataSource = DefaultHttpDataSource.Factory()
            .setUserAgent(BROWSER_UA)
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY || state == Player.STATE_ENDED) buffering = false
            }

            override fun onPlayerError(error: PlaybackException) {
                failed = true
                controls = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            playing = player.isPlaying
            delay(250)
        }
    }

    LaunchedEffect(tick, playing, controls) {
        if (controls && playing && !failed) {
            delay(5_000)
            controls = false
        }
    }

    LaunchedEffect(controls, failed) {
        if (controls && !failed) timeline.requestFocus()
    }

    fun reveal() {
        controls = true
        tick += 1
    }

    fun toggle() {
        if (player.isPlaying) player.pause() else player.play()
        playing = player.isPlaying
        reveal()
    }

    fun seekBy(deltaMs: Long) {
        val length = player.duration
        val target = (player.currentPosition + deltaMs).coerceAtLeast(0L)
        player.seekTo(if (length > 0) target.coerceAtMost(length) else target)
        position = player.currentPosition
        reveal()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.MediaPlayPause -> {
                        toggle()
                        true
                    }
                    Key.MediaRewind -> {
                        seekBy(-10_000)
                        true
                    }
                    Key.MediaFastForward -> {
                        seekBy(10_000)
                        true
                    }
                    else -> {
                        if (!controls) {
                            reveal()
                            true
                        } else {
                            false
                        }
                    }
                }
            },
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    keepScreenOn = true
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        if (buffering && !failed) {
            CircularProgressIndicator(color = Gold, modifier = Modifier.align(Alignment.Center))
        }

        if (controls || failed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent, Color.Black.copy(alpha = 0.82f)),
                        ),
                    ),
            )
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 36.dp)) {
                Text(
                    text = request.title,
                    color = Paper,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (request.trailer) {
                    Text(text = "Трейлер", color = Gold, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
                }
                Box(modifier = Modifier.weight(1f))
                if (failed) {
                    Text(text = "Не удалось воспроизвести поток.", color = Paper, fontSize = 20.sp)
                    TvButton(text = "Назад", onClick = onBack, modifier = Modifier.padding(top = 16.dp))
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        TvButton(
                            text = if (playing) "Пауза" else "Смотреть",
                            onClick = ::toggle,
                            modifier = Modifier
                                .focusRequester(playButton)
                                .focusProperties { right = timeline },
                        )
                        Text(text = clock(position), color = Paper, fontSize = 18.sp)
                        Timeline(
                            position = position,
                            duration = duration,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(timeline)
                                .focusProperties { left = playButton },
                            onSeek = ::seekBy,
                            onToggle = ::toggle,
                        )
                        Text(text = clock(duration), color = Muted, fontSize = 18.sp)
                    }
                    Text(
                        text = "Влево и вправо перематывают на 10 секунд. Центр — пауза.",
                        color = Muted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Timeline(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .height(56.dp)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        onSeek(-10_000)
                        true
                    }
                    Key.DirectionRight -> {
                        onSeek(10_000)
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        onToggle()
                        true
                    }
                    else -> false
                }
            }
            .focusable(interactionSource = interaction)
            .border(
                width = if (focused) 4.dp else 2.dp,
                color = if (focused) Gold else Color.White.copy(alpha = 0.45f),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(Color.White.copy(alpha = 0.28f), RoundedCornerShape(4.dp)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(8.dp)
                .background(if (focused) Gold else Color.White, RoundedCornerShape(4.dp)),
        )
    }
}

@Composable
private fun ExternalHandoff(request: PlayerRequest, onBack: () -> Unit) {
    val context = LocalContext.current
    val open = remember { FocusRequester() }
    val url = request.externalUrl.orEmpty()
    val label = request.externalLabel ?: "плеер"
    var missing by remember { mutableStateOf(false) }
    LaunchedEffect(url) {
        delay(700)
        missing = !openExternal(context, url)
    }
    LaunchedEffect(Unit) { open.requestFocus() }

    Column(
        modifier = Modifier.fillMaxSize().padding(72.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Переход", color = Muted, fontSize = 18.sp)
        Text(
            text = label,
            color = Gold,
            fontSize = 42.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = request.title,
            color = Paper,
            fontSize = 28.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = if (missing) {
                "На устройстве нет приложения, которое открывает эту ссылку."
            } else {
                "Ролик откроется в плеере $label."
            },
            color = Muted,
            fontSize = 18.sp,
            modifier = Modifier.padding(top = 12.dp, bottom = 28.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TvButton(
                text = "Открыть",
                onClick = { missing = !openExternal(context, url) },
                modifier = Modifier.focusRequester(open),
            )
            TvButton(text = "Назад", onClick = onBack)
        }
    }
}

@Composable
private fun MessageScreen(text: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(72.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = text, color = Paper, fontSize = 24.sp)
        TvButton(text = "Назад", onClick = onBack, modifier = Modifier.padding(top = 20.dp))
    }
}

private fun openExternal(context: android.content.Context, url: String): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

private fun clock(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val total = ms / 1000
    val seconds = total % 60
    val minutes = (total / 60) % 60
    val hours = total / 3600
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
