package com.xtreamlytv.androidtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtreamlytv.androidtv.data.itemKey
import com.xtreamlytv.androidtv.model.CatalogItem
import com.xtreamlytv.androidtv.model.ContentType
import com.xtreamlytv.androidtv.ui.theme.palette
import kotlinx.coroutines.delay

@Composable
fun DetailScreen(item: CatalogItem, state: AppUiState, viewModel: AppViewModel) {
    val colors = palette()
    val progress = state.progress[itemKey(item)]
    val favorite = state.favorites.any { it.type == item.type && it.id == item.id }
    val episodes = state.detailEpisodes
    val primaryActionFocus = remember(item.id) { FocusRequester() }
    val seasons = episodes.mapNotNull { it.season }.distinct().sorted()
    val selectedSeason = state.detailSelectedSeason ?: seasons.firstOrNull()
    val visibleEpisodes = if (selectedSeason == null) episodes else episodes.filter { it.season == selectedSeason }
    val detailScope = detailFocusScope(item.id, selectedSeason)
    val focusRequest = state.focusRequest?.takeIf { it.scope == detailScope }
    val targetIndex = when {
        focusRequest?.itemKey != null -> visibleEpisodes.indexOfFirst { itemKey(it) == focusRequest.itemKey }
        focusRequest?.firstItem == true -> 0
        else -> -1
    }
    val targetFocus = remember(detailScope, focusRequest?.itemKey, focusRequest?.firstItem) { FocusRequester() }
    val episodeGridState = rememberLazyGridState()
    val restoreFocusOnEntry = remember(item.id) {
        state.focusRequest?.scope?.startsWith("detail:${item.id}:") == true
    }
    var initialFocusHandled by remember(item.id) { mutableStateOf(false) }
    var focusedEpisodeIndex by remember(detailScope) { mutableIntStateOf(-1) }

    LaunchedEffect(item.id, restoreFocusOnEntry) {
        if (!initialFocusHandled) {
            initialFocusHandled = true
            if (!restoreFocusOnEntry) runCatching { primaryActionFocus.requestFocus() }
        }
    }

    LaunchedEffect(detailScope, targetIndex, visibleEpisodes.size) {
        if (targetIndex >= 0 && targetIndex < visibleEpisodes.size) {
            episodeGridState.scrollToItem(gridRowStart(targetIndex, 2))
            delay(40L)
            runCatching { targetFocus.requestFocus() }
            viewModel.consumeFocusRequest(detailScope)
        }
    }
    LaunchedEffect(focusedEpisodeIndex) {
        if (focusedEpisodeIndex >= 0) episodeGridState.ensureFocusedRowVisible(focusedEpisodeIndex, 2)
    }

    Column(Modifier.fillMaxSize()) {
        val series = item.type == ContentType.SERIES
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (series) 212.dp else 244.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(colors.panelStrong, colors.accent.copy(alpha = 0.18f), colors.panel.copy(alpha = 0.94f)),
                    ),
                    RoundedCornerShape(18.dp),
                )
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp)),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(0.55f)
                    .fillMaxHeight()
                    .background(Brush.radialGradient(listOf(colors.accent.copy(alpha = 0.24f), Color.Transparent))),
            )
            Row(
                Modifier.fillMaxSize().padding(if (series) 16.dp else 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    item = item,
                    modifier = when {
                        item.type == ContentType.LIVE -> Modifier.width(104.dp).height(104.dp)
                        series -> Modifier.width(96.dp).fillMaxHeight()
                        else -> Modifier.width(106.dp).fillMaxHeight()
                    },
                    live = item.type == ContentType.LIVE,
                )
                Spacer(Modifier.width(if (series) 16.dp else 20.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.type.title().uppercase(),
                            color = colors.accent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            item.name,
                            color = colors.text,
                            fontSize = when {
                                item.type == ContentType.LIVE -> 24.sp
                                series -> 20.sp
                                else -> 22.sp
                            },
                            lineHeight = if (series) 23.sp else 26.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(if (series) 5.dp else 7.dp))
                        MetadataRow(item)
                        Spacer(Modifier.height(if (series) 5.dp else 7.dp))
                        Text(
                            item.plot ?: if (item.type == ContentType.LIVE) {
                                "Live programming from your connected provider."
                            } else {
                                "No description supplied by this provider."
                            },
                            color = colors.muted,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            maxLines = if (series) 2 else 5,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(0.92f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!series) {
                            TvButton(
                                label = if (progress != null && progress.positionMs > 30_000L) {
                                    "Resume ${formatDuration(progress.positionMs)}"
                                } else if (item.type == ContentType.LIVE) {
                                    "Watch now"
                                } else {
                                    "Play"
                                },
                                leading = "▶",
                                onClick = { viewModel.play(item) },
                                modifier = Modifier.width(if (progress != null) 154.dp else 100.dp),
                                focusRequester = primaryActionFocus,
                            )
                        }
                        TvButton(
                            label = if (favorite) "Remove favorite" else "Add favorite",
                            leading = if (favorite) "♥" else "♡",
                            onClick = { viewModel.toggleFavorite(item) },
                            modifier = Modifier.width(142.dp),
                            style = TvButtonStyle.Secondary,
                            focusRequester = if (series) primaryActionFocus else null,
                        )
                        TvButton(
                            label = "Back",
                            onClick = viewModel::back,
                            modifier = Modifier.width(76.dp),
                            style = TvButtonStyle.Secondary,
                        )
                    }
                }
            }
        }

        if (item.type == ContentType.SERIES) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionHeader("Episodes", "${episodes.size} episodes", Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                LazyRow(
                    modifier = Modifier.height(38.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                ) {
                    items(seasons, key = { it }) { season ->
                        TvChip(
                            label = "Season $season",
                            selected = selectedSeason == season,
                            onClick = { viewModel.selectDetailSeason(item.id, season) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            if (visibleEpisodes.isEmpty()) {
                EmptyState("No episodes", "This provider did not return episode data for this series.", Modifier.weight(1f))
            } else {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val gap = 8.dp
                    val topPadding = 2.dp
                    val bottomPadding = 12.dp
                    val available = maxHeight - topPadding - bottomPadding
                    val desiredCardHeight = 68.dp
                    val visibleRows = ((available.value + gap.value) / (desiredCardHeight.value + gap.value))
                        .toInt()
                        .coerceIn(1, 3)
                    val density = LocalDensity.current
                    val cardHeight = with(density) {
                        val availablePx = (available - gap * (visibleRows - 1)).roundToPx()
                        (availablePx / visibleRows).toDp()
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = episodeGridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 2.dp,
                            end = 2.dp,
                            top = topPadding,
                            bottom = bottomPadding,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        itemsIndexed(visibleEpisodes, key = { _, episode -> "episode:${episode.id}" }) { index, episode ->
                            EpisodeCard(
                                episode = episode,
                                state = state,
                                viewModel = viewModel,
                                cardHeight = cardHeight,
                                focusRequester = targetFocus.takeIf { index == targetIndex },
                                onFocused = {
                                    focusedEpisodeIndex = index
                                    viewModel.rememberFocusedItem(
                                        area = detailArea(item.id),
                                        scope = detailScope,
                                        key = itemKey(episode),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(item: CatalogItem) {
    val colors = palette()
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        item.channelNumber?.let { MetadataChip("CH $it") }
        item.releaseDate?.take(4)?.takeIf { it.all(Char::isDigit) }?.let { MetadataChip(it) }
        item.genre?.takeIf { it.isNotBlank() }?.let { MetadataChip(it.take(24)) }
        item.rating?.let { MetadataChip("★ ${"%.1f".format(it)}") }
        if (item.type == ContentType.LIVE) Text("LIVE", color = colors.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MetadataChip(label: String) {
    val colors = palette()
    Text(
        label,
        color = colors.text.copy(alpha = 0.86f),
        fontSize = 8.sp,
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun EpisodeCard(
    episode: CatalogItem,
    state: AppUiState,
    viewModel: AppViewModel,
    cardHeight: Dp,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
) {
    val colors = palette()
    val progress = state.progress[itemKey(episode)]
    TvSurface(
        modifier = Modifier.fillMaxWidth().height(cardHeight),
        onClick = { viewModel.play(episode, state.detailEpisodes) },
        focusRequester = focusRequester,
        onFocused = onFocused,
    ) {
        Row(Modifier.fillMaxSize().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(episode, Modifier.width(76.dp).fillMaxHeight(), live = false)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "S${episode.season ?: 0} E${episode.episode ?: 0}",
                    color = colors.accent,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(episode.name, color = colors.text, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (progress != null) Text("Resume ${formatDuration(progress.positionMs)}", color = colors.muted, fontSize = 8.sp)
            }
            Text("▶", color = colors.accent, fontSize = 12.sp)
        }
    }
}
