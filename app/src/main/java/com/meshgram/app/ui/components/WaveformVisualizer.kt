package com.meshgram.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WaveformPlayer(
    isPlaying: Boolean,
    waveforms: List<Int>,
    currentPosMs: Int,
    totalDurationMs: Int,
    playbackSpeed: Float,
    isSelf: Boolean,
    onPlayPauseClick: () -> Unit,
    onSpeedClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (totalDurationMs > 0) currentPosMs.toFloat() / totalDurationMs else 0f
    val bars = if (waveforms.isEmpty()) List(28) { 30 } else waveforms

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // دکمه پخش / مکث
        IconButton(
            onClick = onPlayPauseClick,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (isSelf) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = if (isSelf) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // میله‌های شکل موج صدا (Waveform Bars)
        Row(
            modifier = Modifier
                .weight(1f)
                .height(34.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            bars.forEachIndexed { index, amp ->
                val barProgress = index.toFloat() / bars.size
                val isPassed = barProgress <= progress
                val heightPercent = (amp / 100f).coerceIn(0.2f, 1f)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(heightPercent)
                        .clip(RoundedCornerShape(1.dp))
                        .background(
                            if (isPassed) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // دکمه کنترل سرعت پخش (1x, 1.5x, 2x)
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.clickable { onSpeedClick() }
        ) {
            Text(
                text = "${playbackSpeed}x",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}
