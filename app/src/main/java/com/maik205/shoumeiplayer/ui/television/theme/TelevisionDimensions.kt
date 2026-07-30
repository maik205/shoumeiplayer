package com.maik205.shoumeiplayer.ui.television.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Shapes

object TelevisionDimensions {
    val SafeHorizontal = 48.dp
    val SafeTop = 16.dp
    val SafeBottom = 17.dp
    val NavigationTop = 0.dp
    val NavigationHeight = 36.dp

    val FocusRadius = 6.dp
    val ActionHeight = 26.dp
    val ActionIcon = 12.dp
    val NavigationGap = 3.dp

    val ShelfGap = 27.dp
    val TileGap = 10.dp
    val HeaderGap = 9.dp

    val PosterWidth = 104.dp
    val PosterHeight = 156.dp
    val LandscapeWidth = 210.dp
    val LandscapeHeight = 118.dp
    val SquareSize = 116.dp
}

private val TelevisionRadius = RoundedCornerShape(TelevisionDimensions.FocusRadius)

val TelevisionShapes = Shapes(
    extraSmall = TelevisionRadius,
    small = TelevisionRadius,
    medium = TelevisionRadius,
    large = TelevisionRadius,
    extraLarge = TelevisionRadius,
)
