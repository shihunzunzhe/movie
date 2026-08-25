package com.earthvideo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.earthvideo.app.data.model.Movie
import com.earthvideo.app.ui.theme.*
import com.earthvideo.app.ui.components.decodeHtml

@Composable
fun SearchResultItem(movie: Movie, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Poster
        Box {
            if (movie.posterUrl.isNotEmpty()) {
                AsyncImage(
                    model = movie.posterUrl,
                    contentDescription = movie.title,
                    modifier = Modifier
                        .width(100.dp)
                        .height(135.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(135.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PrimarySoft),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = movie.title.take(1),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary.copy(alpha = 0.3f)
                    )
                }
            }
            if (movie.hotTag) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(
                            color = HotRed,
                            shape = RoundedCornerShape(topEnd = 8.dp, bottomStart = 4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        "热播",
                        fontSize = 9.sp,
                        color = White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (movie.rating > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(
                            color = PlayerOverlayLight,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text("★", fontSize = 9.sp, color = Gold)
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        "%.1f".format(movie.rating),
                        fontSize = 9.sp,
                        color = White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildHighlightedTitle(movie.highlightTitle ?: movie.title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (movie.source.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .background(SourceBadgeBg, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(decodeHtml(movie.source), fontSize = 11.sp, color = White, fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (movie.type.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .background(ChipBg, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(decodeHtml(movie.type), fontSize = 11.sp, color = Primary)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (movie.region.isNotEmpty()) {
                    Text(decodeHtml(movie.region), fontSize = 12.sp, color = TextHint)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (movie.year > 0) {
                    Text("${movie.year}", fontSize = 12.sp, color = TextHint)
                }
            }
            if (movie.director.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "导演：${movie.director}",
                    fontSize = 12.sp,
                    color = TextHint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (movie.actors.isNotEmpty()) {
                val actorsStr = movie.actors.take(5).joinToString(" ")
                Text(
                    "主演：$actorsStr",
                    fontSize = 12.sp,
                    color = TextHint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (movie.episodeTag.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    decodeHtml(movie.episodeTag),
                    fontSize = 12.sp,
                    color = Primary,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onClick,
                modifier = Modifier.height(32.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text("立即播放", fontSize = 13.sp, color = White, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/**
 * Build an AnnotatedString with <em>...</em> segments highlighted in the brand color.
 * If no <em> tags are present, returns plain title text.
 */
private fun buildHighlightedTitle(raw: String): AnnotatedString {
    if (raw.isEmpty()) return AnnotatedString("")
    if (!raw.contains("<em>", ignoreCase = true) && !raw.contains("</em>", ignoreCase = true)) {
        return AnnotatedString(raw)
    }
    return buildAnnotatedString {
        var cursor = 0
        val lower = raw.lowercase()
        val openTag = "<em>"
        val closeTag = "</em>"
        while (cursor < raw.length) {
            val openIdx = lower.indexOf(openTag, cursor)
            if (openIdx < 0) {
                append(raw.substring(cursor))
                break
            }
            // Append text before the opening tag
            if (openIdx > cursor) append(raw.substring(cursor, openIdx))
            val contentStart = openIdx + openTag.length
            val closeIdx = lower.indexOf(closeTag, contentStart)
            if (closeIdx < 0) {
                // No closing tag - treat the rest as highlighted
                withStyle(SpanStyle(color = Primary, fontWeight = FontWeight.Bold)) {
                    append(raw.substring(contentStart))
                }
                break
            }
            val highlight = raw.substring(contentStart, closeIdx)
            withStyle(SpanStyle(color = Primary, fontWeight = FontWeight.Bold)) {
                append(highlight)
            }
            cursor = closeIdx + closeTag.length
        }
    }
}
