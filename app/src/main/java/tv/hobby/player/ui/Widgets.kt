package tv.hobby.player.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClassicCard
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import tv.hobby.player.data.CardModel

private val TileShape = RoundedCornerShape(14.dp)
private val ButtonShape = RoundedCornerShape(12.dp)

@Composable
fun PosterCard(
    card: CardModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ClassicCard(
        onClick = onClick,
        modifier = modifier,
        shape = CardDefaults.shape(TileShape),
        colors = CardDefaults.colors(
            containerColor = CardInk,
            contentColor = Paper,
            focusedContainerColor = CardInk,
            focusedContentColor = Paper,
        ),
        scale = CardDefaults.scale(focusedScale = 1.04f),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(4.dp, Gold),
                shape = TileShape,
            ),
        ),
        image = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .background(Ink),
            ) {
                if (!card.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = card.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                if (!card.badge.isNullOrBlank()) {
                    Text(
                        text = card.badge,
                        color = Paper,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Live)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        },
        title = {
            Text(
                text = card.title,
                color = Paper,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp),
            )
        },
        subtitle = {
            Text(
                text = card.subtitle,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 12.dp),
            )
        },
    )
}

@Composable
fun SourceCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(280.dp),
        shape = CardDefaults.shape(TileShape),
        colors = CardDefaults.colors(
            containerColor = CardInk,
            contentColor = Paper,
            focusedContainerColor = CardInk,
            focusedContentColor = Paper,
        ),
        scale = CardDefaults.scale(focusedScale = 1.04f),
        border = CardDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, Gold.copy(alpha = 0.25f)),
                shape = TileShape,
            ),
            focusedBorder = Border(
                border = BorderStroke(4.dp, Gold),
                shape = TileShape,
            ),
        ),
    ) {
        Column(modifier = Modifier.padding(28.dp)) {
            Text(text = title, color = Paper, fontSize = 36.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                color = Muted,
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = ButtonDefaults.shape(ButtonShape),
        scale = ButtonDefaults.scale(focusedScale = 1.06f),
        colors = ButtonDefaults.colors(
            containerColor = CardInk,
            contentColor = Paper,
            focusedContainerColor = Color(0xFF2C303C),
            focusedContentColor = Paper,
        ),
        border = ButtonDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, Gold.copy(alpha = 0.35f)),
                shape = ButtonShape,
            ),
            focusedBorder = Border(
                border = BorderStroke(4.dp, Gold),
                shape = ButtonShape,
            ),
        ),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Text(text = text, color = Paper, fontSize = 18.sp)
    }
}
