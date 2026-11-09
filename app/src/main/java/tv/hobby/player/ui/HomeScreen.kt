package tv.hobby.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.hobby.player.data.SourceId

@Composable
fun HomeScreen(
    accountName: String?,
    onOpen: (SourceId) -> Unit,
) {
    val studio = remember { FocusRequester() }
    val antonov = remember { FocusRequester() }
    LaunchedEffect(Unit) { studio.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 72.dp, vertical = 64.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Хобби ТВ", color = Paper, fontSize = 52.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = "Влево и вправо выбирают источник.",
            color = Muted,
            fontSize = 20.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 36.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.fillMaxWidth()) {
            SourceCard(
                title = "36 Студия",
                subtitle = "Трансляции без входа. Свой поток — здесь, VK и Rutube — в их плеере.",
                onClick = { onOpen(SourceId.Studio) },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(studio)
                    .focusProperties { right = antonov },
            )
            SourceCard(
                title = "Антонов Медиа",
                subtitle = if (accountName == null) "Вход не выполнен" else "Вы вошли: $accountName",
                onClick = { onOpen(SourceId.Antonov) },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(antonov)
                    .focusProperties { left = studio },
            )
        }
    }
}
