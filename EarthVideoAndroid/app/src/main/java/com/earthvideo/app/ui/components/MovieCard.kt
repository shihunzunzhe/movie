package com.earthvideo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.earthvideo.app.data.model.Movie
import com.earthvideo.app.ui.theme.*
import com.earthvideo.app.ui.components.decodeHtml

@Composable
fun MovieCard(movie: Movie, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var imageFailed by remember(movie.posterUrl) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(Dimens.cardRadius),
                clip = false
            )
            .clip(RoundedCornerShape(Dimens.cardRadius))
            .background(CardBg)
            .clickable(onClick = onClick)
    ) {
        Box {
            if (movie.posterUrl.isNotEmpty() && !imageFailed) {
                AsyncImage(
                    model = movie.posterUrl,
                    contentDescription = movie.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.72f)
                        .clip(RoundedCornerShape(Dimens.cardPosterRadius)),
                    contentScale = ContentScale.Crop,
                    onState = { state ->
                        if (state is AsyncImagePainter.State.Error) {
                            imageFailed = true
                        }
                    }
                )
            }
            if (movie.posterUrl.isEmpty() || imageFailed) {
                // Placeholder for movies without poster
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.72f)
                        .clip(RoundedCornerShape(Dimens.cardPosterRadius))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(PrimaryLight.copy(alpha = 0.3f), PrimarySoft),
                                startY = 0f
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = movie.title.take(2),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Primary.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = movie.type.ifEmpty { "视频" },
                            fontSize = 10.sp,
                            color = TextHint
                        )
                    }
                }
            }
            // Top-left to bottom-right gradient overlay for badge readability
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(Dimens.cardPosterRadius))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Transparent, SemiBlack),
                            startY = 0f
                        )
                    )
            )
            // Source badge (top-left)
            if (movie.source.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(
                            color = SourceBadgeBg,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        decodeHtml(movie.source),
                        fontSize = 10.sp,
                        color = White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            // Hot tag
            if (movie.hotTag) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(
                            Brush.horizontalGradient(listOf(HotRed, HotRedDark)),
                            RoundedCornerShape(topEnd = 8.dp, bottomStart = 8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        "热播",
                        fontSize = 10.sp,
                        color = White,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
            // Episode tag
            if (movie.episodeTag.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(PlayerOverlayLight, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        decodeHtml(movie.episodeTag),
                        fontSize = 10.sp,
                        color = White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            // Rating pill
            if (movie.rating > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(PlayerOverlayLight, RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "★",
                        fontSize = 10.sp,
                        color = Gold
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "%.1f".format(movie.rating),
                        fontSize = 10.sp,
                        color = White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = movie.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                val date = movie.publishDate?.takeLast(5) // "MM-DD"
                if (date != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = date,
                        fontSize = 10.sp,
                        color = TextHint
                    )
                }
            }
            if (movie.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = decodeHtml(movie.description),
                    fontSize = 12.sp,
                    color = TextHint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
