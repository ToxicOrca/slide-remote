package com.toxicorca.slideremote.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.toxicorca.slideremote.data.Slide
import java.io.File

enum class TileEmphasis { NONE, CURRENT, SELECTED }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SlideTile(
    slide: Slide,
    thumb: File?,
    imageVersion: Long,
    aspect: Float,
    modifier: Modifier = Modifier,
    emphasis: TileEmphasis = TileEmphasis.NONE,
    compact: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(if (compact) 10.dp else 14.dp)
    val border = when (emphasis) {
        TileEmphasis.CURRENT -> BorderStroke(3.dp, colors.primary)
        TileEmphasis.SELECTED -> BorderStroke(3.dp, colors.tertiary)
        TileEmphasis.NONE -> BorderStroke(1.dp, colors.outlineVariant)
    }
    val bg = if (emphasis == TileEmphasis.CURRENT) colors.primaryContainer else colors.surfaceContainerHigh

    Column(
        modifier
            .clip(shape)
            .background(bg)
            .border(border, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(if (compact) 4.dp else 6.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .clip(RoundedCornerShape(if (compact) 6.dp else 9.dp))
                .background(colors.surfaceContainerHighest)
        ) {
            SlideThumbnail(
                file = if (slide.hasImage) thumb else null,
                version = imageVersion,
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        slide.number.toString(),
                        style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NumberBadge(slide.number, highlighted = emphasis == TileEmphasis.CURRENT)
                if (slide.hiddenInShow) {
                    SmallBadge("HIDDEN", colors.tertiary)
                }
            }
        }
        Text(
            slide.displayTitle,
            modifier = Modifier
                .padding(top = 5.dp, start = 2.dp, end = 2.dp)
                .alpha(if (slide.title.isBlank()) 0.6f else 1f),
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            color = colors.onSurface,
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun NumberBadge(number: Int, highlighted: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (highlighted) colors.primary else Color(0xCC0B0E13))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(
            number.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (highlighted) colors.onPrimary else colors.onSurface,
        )
    }
}

@Composable
fun SmallBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xCC0B0E13))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
