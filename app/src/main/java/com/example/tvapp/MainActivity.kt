package com.example.tvapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) { App() }
        }
    }
}

data class TabDef(
    val name: String,
    val sections: List<Pair<String, List<Item>>>,
    val fit: Boolean,
    val emptyMsg: String
)

@Composable
fun App() {
    var live by remember { mutableStateOf<List<Item>?>(null) }
    var vod by remember { mutableStateOf<List<Item>?>(null) }
    var liveErr by remember { mutableStateOf<String?>(null) }
    var vodErr by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(0) }
    var playing by remember { mutableStateOf<Item?>(null) }

    LaunchedEffect(Unit) {
        live = try { Repo.load(Config.IPTV_URL) } catch (e: Exception) {
            liveErr = "直播加载失败：${e.message}"; emptyList()
        }
    }
    LaunchedEffect(Unit) {
        vod = try { Repo.load(Config.VOD_URL) } catch (e: Exception) {
            vodErr = "点播加载失败：${e.message}"; emptyList()
        }
    }

    val tabs = remember(live, vod, liveErr, vodErr) {
        val t = mutableListOf<TabDef>()
        val liveList = live
        t += TabDef(
            "直播",
            liveList.orEmpty().groupBy { it.group }.toList(),
            fit = true,
            emptyMsg = if (liveList == null) "加载中…" else (liveErr ?: "直播源没有内容")
        )
        val vodList = vod
        if (vodList.isNullOrEmpty()) {
            t += TabDef(
                "点播", emptyList(), fit = false,
                emptyMsg = if (vodList == null) "加载中…" else (vodErr ?: "点播源没有内容")
            )
        } else {
            vodList.groupBy { it.group }.entries.take(Config.MAX_VOD_TABS).forEach { (g, l) ->
                val sections = l.chunked(Config.SECTION_SIZE).mapIndexed { i, c ->
                    (if (i == 0) "${g}推荐" else "${g}更多 ${i + 1}") to c
                }
                t += TabDef(g, sections, fit = false, emptyMsg = "没有内容")
            }
        }
        t
    }
    val cur = tabs[tab.coerceIn(0, tabs.lastIndex)]

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF2A3FC8), Color(0xFF1A2A9A))))
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 20.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(tabs) { i, t ->
                    TopTab(t.name, selected = i == tab) { tab = i }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (cur.sections.isEmpty()) {
                Text(cur.emptyMsg, color = Color.White)
            } else {
                key(tab) { Sections(cur) { playing = it } }
            }
        }

        playing?.let { item ->
            BackHandler { playing = null }
            Player(item)
        }
    }
}

@Composable
fun TopTab(text: String, selected: Boolean, onFocus: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> Color(0xFF3D7BFF)
        selected -> Color(0x553D7BFF)
        else -> Color.Transparent
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable { onFocus() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            fontSize = 17.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = Color.White,
            maxLines = 1
        )
    }
}

@Composable
fun Sections(tab: TabDef, onPlay: (Item) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        items(tab.sections) { (title, list) -> Section(title, list, tab.fit, onPlay) }
    }
}

/** 一个区块：标题 + 2 张大卡片 + 一行小卡片（和截图布局一致） */
@Composable
fun Section(title: String, list: List<Item>, fit: Boolean, onPlay: (Item) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 12.dp
        val bigW = (maxWidth - gap) / 2
        val smallW = (maxWidth - gap * 3) / 4
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Row(
                horizontalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                list.take(2).forEach {
                    PosterCard(it, fit, Modifier.width(bigW).height(bigW * 0.46f), 15.sp, onPlay)
                }
            }
            if (list.size > 2) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    items(list.drop(2)) {
                        PosterCard(it, fit, Modifier.width(smallW).height(smallW * 0.52f), 11.sp, onPlay)
                    }
                }
            }
        }
    }
}

@Composable
fun PosterCard(
    item: Item,
    fit: Boolean,
    modifier: Modifier,
    titleSize: TextUnit,
    onPlay: (Item) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .scale(if (focused) 1.05f else 1f)
            .border(3.dp, if (focused) Color.White else Color.Transparent, shape)
            .clip(shape)
            .background(Color(0xFF101A55))
            .onFocusChanged { focused = it.isFocused }
            .clickable { onPlay(item) }
    ) {
        if (item.logo != null) {
            AsyncImage(
                model = item.logo,
                contentDescription = null,
                contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize().padding(if (fit) 10.dp else 0.dp)
            )
        }
        Row(
            Modifier.align(Alignment.TopEnd).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            badges(item.name).forEach { (label, color) ->
                Text(
                    label,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(color)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                item.name,
                fontSize = titleSize,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 只根据名称里真实出现的清晰度字样显示角标 */
private fun badges(name: String): List<Pair<String, Color>> {
    val n = name.uppercase()
    return when {
        "4K" in n -> listOf("4K" to Color(0xFFE91E63))
        "1080" in n || "FHD" in n -> listOf("1080P" to Color(0xFFE91E63))
        "720" in n || "HD" in n -> listOf("HD" to Color(0xFF2196F3))
        else -> emptyList()
    }
}

@OptIn(UnstableApi::class)
@Composable
fun Player(item: Item) {
    val ctx = LocalContext.current
    val player = remember(item) {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Config.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
    }
    DisposableEffect(player) {
        player.setMediaItem(MediaItem.fromUri(item.url))
        player.prepare()
        player.playWhenReady = true
        onDispose { player.release() }
    }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player; useController = true } },
        modifier = Modifier.fillMaxSize().background(Color.Black)
    )
}
