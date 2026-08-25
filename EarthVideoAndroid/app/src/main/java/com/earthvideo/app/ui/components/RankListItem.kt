package com.earthvideo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.earthvideo.app.data.model.Movie
import com.earthvideo.app.data.model.RankItem
import com.earthvideo.app.ui.theme.*
import com.earthvideo.app.ui.components.decodeHtml

@Composable
fun RankListItem(rankItem: RankItem, onClick: () -> Unit) {
    val movie = rankItem.movie
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank badge
        val rankColor = when (rankItem.rank) {
            1 -> Gold
            2 -> Orange
            3 -> HotRed
            else -> TextHint
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(rankColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("${rankItem.rank}", fontSize = 12.sp, color = White, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(8.dp))
        // Poster
        if (movie.posterUrl.isNotEmpty()) {
            AsyncImage(
                model = movie.posterUrl,
                contentDescription = movie.title,
                modifier = Modifier
                    .width(80.dp)
                    .height(108.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(108.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(PrimarySoft),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = movie.title.take(1),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary.copy(alpha = 0.3f)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(decodeHtml(movie.title), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${movie.year} ${decodeHtml(movie.type)} ${decodeHtml(movie.region)}",
                fontSize = 13.sp,
                color = TextHint,
                maxLines = 1
            )
            if (movie.director.isNotEmpty()) {
                Text("导演：${decodeHtml(movie.director)}", fontSize = 13.sp, color = TextHint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (movie.actors.isNotEmpty()) {
                Text("主演：${movie.actors.take(4).joinToString(" ")}", fontSize = 13.sp, color = TextHint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
