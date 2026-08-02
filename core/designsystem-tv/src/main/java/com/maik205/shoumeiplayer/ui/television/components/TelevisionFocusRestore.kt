package com.maik205.shoumeiplayer.ui.television.components

/**
 * Decides which item in a rebuilt list should take focus.
 *
 * Every browse surface has the same problem: the viewer left from a card, the list was rebuilt
 * while they were away -- a refresh, a filter, a sort, a query edit, a Continue Watching update --
 * and the card they left from may no longer be there, or may have moved. Landing on the first item
 * is wrong (it is not where they were) and landing nowhere is worse (the remote stops responding).
 *
 * The rule: prefer the same item wherever it is now, and otherwise take whatever occupies the
 * position it used to hold, so a card that has genuinely gone leaves the viewer among its old
 * neighbours. [previousIndex] is clamped rather than validated, because a list that shrank is the
 * common case and the nearest surviving position is still a better answer than the start.
 *
 * Returns `null` when there is nothing to restore -- no remembered item, or an empty list -- which
 * callers read as "leave focus alone and let the screen's own fallback have it".
 */
fun televisionRestoreTarget(
    ids: List<String>,
    restoreId: String?,
    previousIndex: Int,
): Int? {
    if (restoreId == null || ids.isEmpty()) return null
    val exact = ids.indexOf(restoreId)
    if (exact >= 0) return exact
    return previousIndex.coerceIn(0, ids.lastIndex)
}
