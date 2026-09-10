package com.xtreamlytv.androidtv.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed as listItemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtreamlytv.androidtv.data.itemKey
import com.xtreamlytv.androidtv.model.CatalogItem
import com.xtreamlytv.androidtv.model.Category
import com.xtreamlytv.androidtv.model.ContentType
import com.xtreamlytv.androidtv.ui.theme.palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private suspend fun requestFocusAfterComposition(requester: FocusRequester) {
    repeat(4) {
        delay(16L)
        if (runCatching { requester.requestFocus() }.getOrDefault(false)) return
    }
}

@Composable
fun CatalogScreen(type: ContentType, state: AppUiState, viewModel: AppViewModel) {
    val selected = state.selectedCategories[type]
    val query = state.searchQuery.trim()
    val filtered = if (query.isBlank()) state.items else state.items.filter { it.name.contains(query, ignoreCase = true) }
    val categories = state.categories[type].orEmpty()
    val scope = selected?.let { catalogFocusScope(type, it.id) }
    val focusRequest = state.focusRequest?.takeIf { it.scope == scope }
    val firstCardFocus = remember(type, selected?.id) { FocusRequester() }
    val fallbackCategoryFocus = remember(type) { FocusRequester() }
    val selectedCategoryIndex = categories.indexOfFirst { it.id == selected?.id }
    val categoryFocusRequesters = remember(type, categories.map { it.id }) {
        List(categories.size) { FocusRequester() }
    }
    val selectedCategoryFocus = categoryFocusRequesters.getOrNull(selectedCategoryIndex) ?: fallbackCategoryFocus
    val categoryListState = rememberLazyListState(
        initialFirstVisibleItemIndex = pageStart(selectedCategoryIndex, pageSize = 10),
    )
    val gridState = rememberLazyGridState()
    val focusScope = rememberCoroutineScope()
    var pendingCategoryId by remember(type) { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingCategoryId, selected?.id, filtered.isNotEmpty()) {
        val pending = pendingCategoryId
        if (pending != null && pending == selected?.id && filtered.isNotEmpty()) {
            gridState.scrollToItem(0)
            requestFocusAfterComposition(firstCardFocus)
            pendingCategoryId = null
        }
    }

    val moveFromCategoriesToCards: () -> Unit = {
        if (filtered.isNotEmpty()) {
            focusScope.launch {
                // The first card may not be composed after the grid has been
                // scrolled. Bring it back before requesting focus so Right is
                // deterministic every time.
                gridState.scrollToItem(0)
                requestFocusAfterComposition(firstCardFocus)
            }
        }
    }
    val moveFromCardsToSelectedCategory: () -> Unit = {
        if (selectedCategoryIndex >= 0) {
            focusScope.launch {
                // Restore the selected category page before requesting focus.
                // This avoids targeting a disposed category after page browsing.
                categoryListState.scrollToItem(pageStart(selectedCategoryIndex, pageSize = 10))
                requestFocusAfterComposition(selectedCategoryFocus)
            }
        }
    }

    Row(Modifier.fillMaxSize()) {
        CategoryRail(
            categories = categories,
            selected = selected,
            loading = state.catalogsLoading,
            onSelect = { category ->
                pendingCategoryId = category.id
                viewModel.selectCategory(type, category)
            },
            onMoveRight = moveFromCategoriesToCards,
            focusRequesters = categoryFocusRequesters,
            listState = categoryListState,
            hasCards = filtered.isNotEmpty(),
            modifier = Modifier.width(174.dp).fillMaxHeight(),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(
                Modifier.fillMaxWidth().height(42.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchField(
                    value = state.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    placeholder = "Search loaded ${type.title().lowercase()}",
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "${filtered.size} ${if (filtered.size == 1) "item" else "items"}",
                    color = palette().muted,
                    fontSize = 10.sp,
                    modifier = Modifier.width(82.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            when {
                state.catalogsLoading && state.categories[type].isNullOrEmpty() ->
                    EmptyState("Loading categories…", "Your provider library will appear here in a moment.")
                selected == null ->
                    EmptyState("No categories", "This provider did not return any ${type.title().lowercase()} categories.")
                filtered.isEmpty() && query.isNotBlank() ->
                    EmptyState("No matches", "Try a different search term.")
                filtered.isEmpty() -> {
                    LaunchedEffect(selected.id, state.loading) {
                        if (!state.loading) moveFromCardsToSelectedCategory()
                    }
                    EmptyState("Nothing loaded", "Choose another category or try again later.")
                }
                else -> CatalogGrid(
                    type = type,
                    catalogItems = filtered,
                    state = state,
                    viewModel = viewModel,
                    scope = scope.orEmpty(),
                    focusRequest = focusRequest,
                    firstCardFocus = firstCardFocus,
                    onMoveLeftToCategories = moveFromCardsToSelectedCategory,
                    gridState = gridState,
                )
            }
        }
    }
}

@Composable
private fun CategoryRail(
    categories: List<Category>,
    selected: Category?,
    loading: Boolean,
    onSelect: (Category) -> Unit,
    onMoveRight: () -> Unit,
    focusRequesters: List<FocusRequester>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    hasCards: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = palette()
    val visibleRows = 10
    val focusScope = rememberCoroutineScope()
    var pageTransitioning by remember(categories) { mutableStateOf(false) }

    Column(
        modifier
            .background(colors.panel.copy(alpha = 0.78f), RoundedCornerShape(14.dp))
            .padding(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(42.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                "Categories",
                color = colors.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        if (categories.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (loading) "Loading…" else "No categories", color = colors.muted, fontSize = 10.sp)
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val gap = 4.dp
                val topPadding = 3.dp
                val bottomPadding = 3.dp
                val density = LocalDensity.current
                val rowHeight = with(density) {
                    val availablePx = (maxHeight - topPadding - bottomPadding - gap * (visibleRows - 1)).roundToPx()
                    (availablePx / visibleRows).toDp()
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 2.dp,
                        end = 2.dp,
                        top = topPadding,
                        bottom = bottomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(gap),
                    userScrollEnabled = false,
                ) {
                    listItemsIndexed(categories, key = { _, category -> category.id }) { index, category ->
                        val active = category.id == selected?.id
                        val keyRouting = Modifier.onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) {
                                return@onPreviewKeyEvent false
                            }

                            when (event.key) {
                                Key.DirectionRight -> {
                                    if (hasCards) onMoveRight()
                                    hasCards
                                }

                                Key.DirectionUp,
                                Key.DirectionDown -> {
                                    val direction = if (event.key == Key.DirectionUp) -1 else 1
                                    val targetIndex = index + direction
                                    if (targetIndex !in categories.indices) {
                                        true
                                    } else {
                                        val currentPage = pageStart(index, visibleRows)
                                        val targetPage = pageStart(targetIndex, visibleRows)
                                        if (targetPage == currentPage) {
                                            runCatching { focusRequesters[targetIndex].requestFocus() }
                                        } else if (!pageTransitioning) {
                                            pageTransitioning = true
                                            focusScope.launch {
                                                listState.scrollToItem(targetPage)
                                                requestFocusAfterComposition(focusRequesters[targetIndex])
                                                pageTransitioning = false
                                            }
                                        }
                                        true
                                    }
                                }

                                else -> false
                            }
                        }

                        TvSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowHeight)
                                .then(keyRouting),
                            onClick = { onSelect(category) },
                            active = active,
                            background = if (active) colors.accent.copy(alpha = 0.16f) else Color.Transparent,
                            radius = 9.dp,
                            focusRequester = focusRequesters.getOrNull(index),
                        ) {
                            Box(
                                Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    category.name,
                                    color = if (active) colors.accent else colors.muted,
                                    fontSize = 10.sp,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogGrid(
    type: ContentType,
    catalogItems: List<CatalogItem>,
    state: AppUiState,
    viewModel: AppViewModel,
    scope: String,
    focusRequest: FocusRequest?,
    firstCardFocus: FocusRequester,
    onMoveLeftToCategories: () -> Unit,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
) {
    val columns = if (type == ContentType.LIVE) 4 else 5
    val targetIndex = remember(catalogItems, focusRequest) {
        when {
            focusRequest == null -> -1
            focusRequest.firstItem -> 0
            focusRequest.itemKey != null -> catalogItems.indexOfFirst { itemKey(it) == focusRequest.itemKey }.coerceAtLeast(0)
            else -> 0
        }
    }
    val restoreFocus = remember(scope, focusRequest?.itemKey, focusRequest?.firstItem) { FocusRequester() }
    var focusedIndex by remember(scope) { mutableIntStateOf(-1) }

    LaunchedEffect(scope, targetIndex, focusRequest) {
        if (focusRequest != null && targetIndex in catalogItems.indices) {
            val initialIndex = if (type == ContentType.LIVE) {
                gridPageStart(targetIndex, columns, rows = 4)
            } else {
                gridRowStart(targetIndex, columns)
            }
            gridState.scrollToItem(initialIndex)
            delay(48L)
            runCatching { restoreFocus.requestFocus() }
            viewModel.consumeFocusRequest(scope)
        }
    }
    LaunchedEffect(focusedIndex, type) {
        if (focusedIndex >= 0) {
            if (type == ContentType.LIVE) {
                gridState.snapFocusedGridPage(focusedIndex, columns, rows = 4)
            } else {
                gridState.ensureFocusedRowVisible(focusedIndex, columns)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val visibleRows = if (type == ContentType.LIVE) 4 else 2
        val gap = 8.dp
        val topPadding = 2.dp
        val bottomPadding = 10.dp
        val density = LocalDensity.current
        val cardHeight = with(density) {
            val availablePx = (maxHeight - topPadding - bottomPadding - gap * (visibleRows - 1)).roundToPx()
            (availablePx / visibleRows).toDp()
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
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
            itemsIndexed(catalogItems, key = { _, item -> itemKey(item) }) { index, item ->
                val favorite = state.favorites.any { it.type == item.type && it.id == item.id }
                val leftLink = if (index % columns == 0) {
                    Modifier.onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                            onMoveLeftToCategories()
                            true
                        } else {
                            false
                        }
                    }
                } else {
                    Modifier
                }
                val cardModifier = Modifier
                    .fillMaxWidth()
                    .then(leftLink)
                val requester = when {
                    focusRequest != null && index == targetIndex -> restoreFocus
                    index == 0 -> firstCardFocus
                    else -> null
                }
                val onFocused = {
                    focusedIndex = index
                    viewModel.rememberFocusedItem(catalogArea(type), scope, itemKey(item))
                }

                if (type == ContentType.LIVE) {
                    LiveItemCard(
                        item = item,
                        favorite = favorite,
                        onClick = { viewModel.activate(item) },
                        modifier = cardModifier,
                        cardHeight = cardHeight,
                        focusRequester = requester,
                        onFocused = onFocused,
                    )
                } else {
                    PosterItemCard(
                        item = item,
                        favorite = favorite,
                        onClick = { viewModel.activate(item) },
                        modifier = cardModifier,
                        cardHeight = cardHeight,
                        focusRequester = requester,
                        onFocused = onFocused,
                    )
                }
            }
        }
    }
}
