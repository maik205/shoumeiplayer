package com.maik205.shoumeiplayer.ui.screens.search

import androidx.compose.runtime.Immutable

/** Pure Kotlin on-screen keyboard grid model. No Compose UI / Android imports. */
sealed interface KeyAction {
    data class Char(val value: kotlin.Char) : KeyAction
    data object Space : KeyAction
    data object Delete : KeyAction
    data object Clear : KeyAction
}

@Immutable
data class KeyboardKey(val id: String, val label: String, val action: KeyAction, val span: Int = 1)

@Immutable
data class KeyboardCursor(val row: Int, val col: Int)

enum class KeyboardDirection { Up, Down, Left, Right }

@Immutable
data class KeyboardGrid(val rows: List<List<KeyboardKey>>) {

    fun keyAt(cursor: KeyboardCursor): KeyboardKey? {
        val row = rows.getOrNull(cursor.row) ?: return null
        return row.getOrNull(cursor.col)
    }

    /** Neighbour cursor, or [cursor] unchanged when the move would leave the grid. */
    fun move(cursor: KeyboardCursor, direction: KeyboardDirection): KeyboardCursor {
        if (rows.isEmpty()) return cursor
        return when (direction) {
            KeyboardDirection.Left -> {
                if (cursor.col <= 0) cursor else cursor.copy(col = cursor.col - 1)
            }
            KeyboardDirection.Right -> {
                val row = rows.getOrNull(cursor.row) ?: return cursor
                val lastCol = row.lastIndex
                if (cursor.col >= lastCol) cursor else cursor.copy(col = cursor.col + 1)
            }
            KeyboardDirection.Up -> {
                if (cursor.row <= 0) {
                    cursor
                } else {
                    val targetRow = cursor.row - 1
                    val lastCol = rows[targetRow].lastIndex.coerceAtLeast(0)
                    KeyboardCursor(targetRow, cursor.col.coerceAtMost(lastCol))
                }
            }
            KeyboardDirection.Down -> {
                if (cursor.row >= rows.lastIndex) {
                    cursor
                } else {
                    val targetRow = cursor.row + 1
                    val lastCol = rows[targetRow].lastIndex.coerceAtLeast(0)
                    KeyboardCursor(targetRow, cursor.col.coerceAtMost(lastCol))
                }
            }
        }
    }

    /** 7 rows: A-F / G-L / M-R / S-X / Y Z 0 1 2 3 / 4 5 6 7 8 9 / [Space 3][Delete 2][Clear 1]. */
    companion object {
        private fun charKey(c: kotlin.Char) = KeyboardKey(id = "char_$c", label = c.toString(), action = KeyAction.Char(c))

        private fun charRow(chars: String) = chars.map { charKey(it) }

        val Default: KeyboardGrid = KeyboardGrid(
            rows = listOf(
                charRow("ABCDEF"),
                charRow("GHIJKL"),
                charRow("MNOPQR"),
                charRow("STUVWX"),
                charRow("YZ0123"),
                charRow("456789"),
                listOf(
                    KeyboardKey(id = "space", label = "Space", action = KeyAction.Space, span = 3),
                    KeyboardKey(id = "delete", label = "Delete", action = KeyAction.Delete, span = 2),
                    KeyboardKey(id = "clear", label = "Clear", action = KeyAction.Clear, span = 1),
                ),
            ),
        )
    }
}

/** Pure reducer: the only place the query string is mutated. */
fun applyKey(query: String, action: KeyAction): String = when (action) {
    is KeyAction.Char -> query + action.value
    is KeyAction.Space -> if (query.isEmpty() || query.endsWith(' ')) query else query + ' '
    is KeyAction.Delete -> if (query.isEmpty()) query else query.dropLast(1)
    is KeyAction.Clear -> ""
}
