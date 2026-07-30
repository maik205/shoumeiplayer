package com.maik205.shoumeiplayer.ui.television.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.maik205.shoumeiplayer.R

/**
 * Static branded splash shown while the real startup destination resolves underneath it.
 * Visibility and the single fade-out transition are owned by the caller.
 */
@Composable
fun TelevisionBrandSplash(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.shoumei_logomark_white),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(96.dp)
                    .height(93.dp),
            )

            Image(
                painter = painterResource(R.drawable.shoumei_textmark_white),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(330.dp)
                    .height(157.dp),
            )
        }
    }
}
