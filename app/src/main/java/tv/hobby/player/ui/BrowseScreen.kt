package tv.hobby.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.hobby.player.data.CardModel
import tv.hobby.player.data.CategoryChip
import tv.hobby.player.data.Rail
import tv.hobby.player.data.SourceId

@Composable
fun BrowseScreen(
    source: SourceId,
    studioRows: List<Rail>,
    studioLoading: Boolean,
    studioError: String?,
    onRefreshStudio: () -> Unit,
    categories: List<CategoryChip>,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    antonovCards: List<CardModel>,
    antonovLoading: Boolean,
    antonovError: String?,
    antonovHasMore: Boolean,
    onLoadMore: () -> Unit,
    onRetryAntonov: () -> Unit,
    accountName: String?,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
) {
    val back = remember { FocusRequester() }
    val first = remember { FocusRequester() }
    val title = if (source == SourceId.Studio) "36 Студия" else "Антонов Медиа"
    val error = if (source == SourceId.Studio) studioError else antonovError
    val loading = if (source == SourceId.Studio) studioLoading else antonovLoading
    val hasContent = if (source == SourceId.Studio) studioRows.isNotEmpty() else antonovCards.isNotEmpty()

    LaunchedEffect(source, hasContent, error) {
        delay(80)
        val target = if (error != null || !hasContent) back else first
        runCatching { target.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(ScreenPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TvButton(
                text = "Назад",
                onClick = onBack,
                modifier = Modifier.focusRequester(back),
            )
            Text(text = title, color = Paper, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
            if (source == SourceId.Studio) {
                TvButton(text = if (studioLoading) "Обновляем…" else "Обновить", onClick = onRefreshStudio)
            } else {
                Text(
                    text = accountName?.let { "Вы вошли: $it" } ?: "Вход не выполнен",
                    color = Muted,
                    modifier = Modifier.weight(1f),
                )
                TvButton(
                    text = if (accountName == null) "Войти" else "Выйти",
                    onClick = if (accountName == null) onLogin else onLogout,
                )
            }
        }

        if (error != null) {
            Text(text = error, color = Paper, fontSize = 20.sp, modifier = Modifier.padding(top = 28.dp))
            TvButton(
                text = "Повторить",
                onClick = if (source == SourceId.Studio) onRefreshStudio else onRetryAntonov,
                modifier = Modifier.padding(top = 16.dp).focusRequester(first),
            )
        } else if (!hasContent && loading) {
            Text(text = "Загрузка…", color = Muted, fontSize = 20.sp, modifier = Modifier.padding(top = 28.dp))
        } else if (source == SourceId.Studio) {
            StudioRails(rows = studioRows, first = first, back = back, onOpen = onOpen)
        } else {
            AntonovCatalog(
                categories = categories,
                selectedCategory = selectedCategory,
                onSelectCategory = onSelectCategory,
                cards = antonovCards,
                hasMore = antonovHasMore,
                loading = antonovLoading,
                first = first,
                back = back,
                onOpen = onOpen,
                onLoadMore = onLoadMore,
            )
        }
    }
}

@Composable
private fun StudioRails(
    rows: List<Rail>,
    first: FocusRequester,
    back: FocusRequester,
    onOpen: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        itemsIndexed(rows, key = { _, rail -> rail.title }) { rowIndex, rail ->
            Text(text = rail.title, color = Paper, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp),
                modifier = Modifier.height(292.dp).fillMaxWidth(),
            ) {
                itemsIndexed(rail.cards, key = { _, card -> card.id }) { index, card ->
                    val focus = if (rowIndex == 0 && index == 0) {
                        Modifier.focusRequester(first).focusProperties { up = back }
                    } else {
                        Modifier
                    }
                    PosterCard(
                        card = card,
                        onClick = { onOpen(card.id) },
                        modifier = Modifier.widthCard().then(focus),
                    )
                }
            }
        }
    }
}

@Composable
private fun AntonovCatalog(
    categories: List<CategoryChip>,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    cards: List<CardModel>,
    hasMore: Boolean,
    loading: Boolean,
    first: FocusRequester,
    back: FocusRequester,
    onOpen: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    val rows = cards.chunked(4)
    Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            item(key = "all") {
                TvButton(
                    text = if (selectedCategory == null) "Все ·" else "Все",
                    onClick = { onSelectCategory(null) },
                    modifier = Modifier.focusProperties { up = back },
                )
            }
            items(categories, key = { it.id }) { category ->
                val selected = category.id == selectedCategory
                TvButton(
                    text = if (selected) "${category.name} ·" else category.name,
                    onClick = { onSelectCategory(category.id) },
                    modifier = Modifier.focusProperties { up = back },
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            itemsIndexed(rows, key = { index, _ -> index }) { rowIndex, row ->
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.height(292.dp).fillMaxWidth(),
                ) {
                    itemsIndexed(row, key = { _, card -> card.id }) { index, card ->
                        val focus = if (rowIndex == 0 && index == 0) {
                            Modifier.focusRequester(first).focusProperties { up = back }
                        } else {
                            Modifier
                        }
                        PosterCard(
                            card = card,
                            onClick = { onOpen(card.id) },
                            modifier = Modifier.widthCard().then(focus),
                        )
                    }
                }
            }
            if (hasMore || loading) {
                item(key = "more") {
                    TvButton(
                        text = if (loading) "Загрузка…" else "Ещё",
                        onClick = onLoadMore,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

private fun Modifier.widthCard(): Modifier = width(300.dp)
