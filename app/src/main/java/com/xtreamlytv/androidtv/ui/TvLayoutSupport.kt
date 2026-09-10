package com.xtreamlytv.androidtv.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import kotlinx.coroutines.delay

internal fun pageStart(index: Int, pageSize: Int): Int {
    if (index < 0 || pageSize <= 0) return 0
    return (index / pageSize) * pageSize
}

internal fun gridRowStart(index: Int, columns: Int): Int {
    if (index < 0 || columns <= 0) return 0
    return (index / columns) * columns
}

internal fun gridPageStart(index: Int, columns: Int, rows: Int): Int {
    if (columns <= 0 || rows <= 0) return 0
    return pageStart(index, columns * rows)
}

/**
 * Foundation lazy containers only scroll enough to make a newly focused item
 * intersect the viewport. On TV that can leave the focus ring and part of the
 * card clipped. Align the row only when an item is not fully visible.
 */
internal suspend fun LazyGridState.ensureFocusedRowVisible(index: Int, columns: Int) {
    if (index < 0 || columns <= 0) return
    delay(24L)

    val layout = layoutInfo
    val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
    val fullyVisible = item != null &&
        item.offset.y >= layout.viewportStartOffset &&
        item.offset.y + item.size.height <= layout.viewportEndOffset

    if (!fullyVisible) {
        scrollToItem(gridRowStart(index, columns))
    }
}

/**
 * Live TV is presented as fixed pages so the viewport always contains a
 * complete 4x4 set rather than partial rows above or below the focus.
 */
internal suspend fun LazyGridState.snapFocusedGridPage(index: Int, columns: Int, rows: Int) {
    if (index < 0 || columns <= 0 || rows <= 0) return
    delay(24L)

    val target = gridPageStart(index, columns, rows)
    if (firstVisibleItemIndex != target || firstVisibleItemScrollOffset != 0) {
        scrollToItem(target)
    }
}

/**
 * Category rails use fixed ten-button pages. Focus may move within a page
 * without shifting the rail; crossing a page boundary snaps to the next ten.
 */
internal suspend fun LazyListState.snapFocusedListPage(index: Int, pageSize: Int) {
    if (index < 0 || pageSize <= 0) return
    delay(24L)

    val target = pageStart(index, pageSize)
    if (firstVisibleItemIndex != target || firstVisibleItemScrollOffset != 0) {
        scrollToItem(target)
    }
}

internal suspend fun LazyListState.ensureFocusedItemVisible(index: Int) {
    if (index < 0) return
    delay(24L)

    val layout = layoutInfo
    val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
    val fullyVisible = item != null &&
        item.offset >= layout.viewportStartOffset &&
        item.offset + item.size <= layout.viewportEndOffset

    if (!fullyVisible) scrollToItem(index)
}
