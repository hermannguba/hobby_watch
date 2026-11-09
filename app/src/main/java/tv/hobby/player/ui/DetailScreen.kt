package tv.hobby.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import tv.hobby.player.data.DetailUi
import tv.hobby.player.data.actionLabel
import tv.hobby.player.data.canOpen

@Composable
fun DetailScreen(
    detail: DetailUi?,
    error: String?,
    opening: Boolean,
    onPlay: () -> Unit,
    onAlt: () -> Unit,
    onLogin: () -> Unit,
    onBack: () -> Unit,
) {
    val back = remember { FocusRequester() }
    val primary = remember { FocusRequester() }
    val playable = detail?.primary?.canOpen() == true
    LaunchedEffect(detail?.id, playable, detail?.showLogin) {
        if (playable) primary.requestFocus() else back.requestFocus()
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(36.dp),
    ) {
        if (!detail?.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = detail?.imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .width(520.dp)
                    .height(292.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardInk),
                contentScale = ContentScale.Crop,
            )
        } else {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .width(520.dp)
                    .height(292.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardInk),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.weight(1f)) {
            TvButton(
                text = "Назад",
                onClick = onBack,
                modifier = Modifier.focusRequester(back),
            )
            Text(
                text = detail?.eyebrow.orEmpty(),
                color = Muted,
                fontSize = 16.sp,
            )
            Text(
                text = detail?.title ?: "Загрузка…",
                color = Paper,
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (!detail?.description.isNullOrBlank()) {
                Text(
                    text = detail?.description.orEmpty(),
                    color = Muted,
                    fontSize = 16.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val message = error ?: detail?.note
            if (!message.isNullOrBlank()) {
                Text(text = message, color = Paper, fontSize = 18.sp)
            }
            if (playable) {
                TvButton(
                    text = if (opening) "Открываем…" else detail?.primary?.actionLabel().orEmpty(),
                    onClick = onPlay,
                    modifier = Modifier
                        .focusRequester(primary)
                        .focusProperties { up = back },
                )
            }
            if (detail?.alt?.canOpen() == true) {
                TvButton(text = detail.alt?.actionLabel().orEmpty(), onClick = onAlt)
            }
            if (detail?.showLogin == true) {
                TvButton(
                    text = "Войти",
                    onClick = onLogin,
                    modifier = if (!playable) Modifier.focusRequester(primary).focusProperties { up = back } else Modifier,
                )
            }
        }
    }
}
