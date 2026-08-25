package com.earthvideo.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earthvideo.app.data.model.HotSearchItem
import com.earthvideo.app.data.repository.MovieRepository
import com.earthvideo.app.ui.theme.*

@Composable
fun SearchScreen(
    repository: MovieRepository,
    onBack: () -> Unit,
    onSearch: (String) -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    var historyKeywords by remember { mutableStateOf(listOf<String>()) }
    var hotSearches by remember { mutableStateOf(listOf<HotSearchItem>()) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            historyKeywords = repository.getSearchHistory().keywords
            hotSearches = repository.getHotSearch()
        } catch (_: Exception) {}
        focusRequester.requestFocus()
    }

    Column(modifier = Modifier.fillMaxSize().background(PageBg)) {
        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(Primary)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = White.copy(alpha = 0.95f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = TextHint,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 14.sp,
                            color = TextPrimary
                        ),
                        cursorBrush = SolidColor(Primary),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (keyword.isNotBlank()) onSearch(keyword.trim())
                            }
                        ),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (keyword.isEmpty()) {
                                    Text(
                                        "片名 / 演员 / 导演",
                                        fontSize = 14.sp,
                                        color = TextHint
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    if (keyword.isNotEmpty()) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "清除",
                            tint = TextHint,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { keyword = "" }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "取消",
                fontSize = 15.sp,
                color = White,
                modifier = Modifier.clickable { onBack() }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            // Search History
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "搜索历史",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (historyKeywords.isNotEmpty()) {
                        IconButton(onClick = { historyKeywords = emptyList() }) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "清除",
                                tint = TextHint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                if (historyKeywords.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowingTagsRow(
                        items = historyKeywords,
                        onTagClick = onSearch
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "暂无搜索历史",
                        fontSize = 13.sp,
                        color = TextHint
                    )
                }
            }

            // Hot Search Title
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Whatshot,
                        contentDescription = null,
                        tint = HotRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "热门搜索",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Hot Search Items
            items(hotSearches.size) { index ->
                HotSearchRow(
                    index = index + 1,
                    item = hotSearches[index],
                    onClick = { onSearch(hotSearches[index].keyword) }
                )
            }
        }
    }
}

@Composable
private fun FlowingTagsRow(items: List<String>, onTagClick: (String) -> Unit) {
    // Wrap tags into rows of up to 4 to keep the layout tidy.
    val rows = remember(items) {
        val r = mutableListOf<List<String>>()
        var current: MutableList<String> = mutableListOf()
        items.forEach {
            val tentative: List<String> = current + it
            if (tentative.size > 4) {
                r.add(current)
                current = mutableListOf<String>().also { c -> c.add(it) }
            } else {
                current = tentative.toMutableList()
            }
        }
        if (current.isNotEmpty()) r.add(current)
        r
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { tag ->
                    Surface(
                        color = TabBg,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.clickable { onTagClick(tag) }
                    ) {
                        Text(
                            tag,
                            fontSize = 13.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HotSearchRow(index: Int, item: HotSearchItem, onClick: () -> Unit) {
    val indexColor = when (index) {
        1 -> HotRed
        2 -> Orange
        3 -> Gold
        else -> TextHint
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(indexColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$index",
                fontSize = 12.sp,
                color = indexColor,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            item.keyword,
            fontSize = 15.sp,
            color = TextPrimary,
            fontWeight = if (index <= 3) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (item.tag.isNotEmpty()) {
            val tagBg = when (item.tag) {
                "热" -> HotRed
                "荐" -> Primary
                else -> PrimaryLight
            }
            Surface(
                color = tagBg.copy(alpha = 0.12f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    item.tag,
                    fontSize = 10.sp,
                    color = tagBg,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
        if (item.description.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                item.description,
                fontSize = 12.sp,
                color = TextHint,
                maxLines = 1
            )
        }
    }
}
