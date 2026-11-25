package com.mediaplay.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.sin
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlinx.coroutines.delay

// ---------- Início: Mais tocadas + álbuns sanfonados ----------
@Composable
fun Tile(t: Track, rank: Int, mod: Modifier, onClick: () -> Unit) {
    Box(mod.aspectRatio(1f).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)) {
        Cover(t.uri, Modifier.fillMaxSize(), 256, t.seed)
        Text("$rank", Modifier.padding(6.dp).glass(11).padding(8.dp, 2.dp), fontSize = 11.sp)
        Text(
            t.title,
            Modifier.align(Alignment.BottomCenter).padding(6.dp).fillMaxWidth().glass(12).padding(8.dp, 5.dp),
            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun HomeScreen(tracks: List<Track>, plays: Map<Long, Int>, onPlay: (List<Track>, Int) -> Unit, onConnected: () -> Unit) {
    if (tracks.isEmpty()) {
        Text("Nenhuma música encontrada neste aparelho.", Modifier.padding(20.dp), color = Color.White.copy(.7f))
        return
    }
    var n by remember { mutableIntStateOf(10) }
    var sel by remember { mutableIntStateOf(0) }
    val top = remember(tracks, plays, n) {
        tracks.filter { (plays[it.id] ?: 0) > 0 }.sortedByDescending { plays[it.id] ?: 0 }.take(n)
    }
    val albums = remember(tracks) { tracks.groupBy { it.album }.toList().filter { it.first.isNotBlank() }.take(6) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 200.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp, 8.dp, 20.dp, 10.dp),
            Arrangement.SpaceBetween, Alignment.CenterVertically
        ) {
            Text("Mais tocadas", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(10, 20).forEach { v ->
                    Text(
                        "$v",
                        Modifier.clip(RoundedCornerShape(12.dp)).then(if (n == v) Modifier.glass(12) else Modifier)
                            .clickable { n = v }.padding(12.dp, 4.dp),
                        fontSize = 12.sp, color = if (n == v) Color.White else Color.White.copy(.6f)
                    )
                }
            }
        }
        if (top.isEmpty()) {
            Text(
                "Ouça algumas músicas (mais de 30 segundos) e as mais tocadas aparecem aqui.",
                Modifier.padding(horizontal = 20.dp), color = Color.White.copy(.6f)
            )
        } else {
            val pages = top.chunked(9)
            val pager = rememberPagerState(pageCount = { pages.size })
            HorizontalPager(state = pager, contentPadding = PaddingValues(horizontal = 20.dp), pageSpacing = 12.dp) { p ->
                val page = pages.getOrNull(p) ?: emptyList()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    page.chunked(3).forEachIndexed { r, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEachIndexed { c, t ->
                                val idx = p * 9 + r * 3 + c
                                Tile(t, idx + 1, Modifier.weight(1f)) { onPlay(top, idx) }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), Arrangement.Center) {
                repeat(pages.size) { i ->
                    val w by animateDpAsState(if (pager.currentPage == i) 20.dp else 8.dp, label = "dot")
                    Box(
                        Modifier.padding(horizontal = 4.dp).height(8.dp).width(w).clip(CircleShape)
                            .background(if (pager.currentPage == i) Color.White else Color.White.copy(.3f))
                    )
                }
            }
        }
        if (albums.isNotEmpty()) {
            Text("Seus álbuns", Modifier.padding(20.dp, 24.dp, 20.dp, 10.dp), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(270.dp), Arrangement.spacedBy(8.dp)) {
                albums.forEachIndexed { i, (name, list) ->
                    val w by animateFloatAsState(if (i == sel) 5f else 1f, tween(450), label = "w")
                    Box(
                        Modifier.weight(w).fillMaxHeight().clip(RoundedCornerShape(28.dp))
                            .clickable { if (sel == i) onPlay(list, 0) else sel = i }
                    ) {
                        Cover(list.first().uri, Modifier.fillMaxSize(), 512, list.first().seed)
                        if (i == sel) {
                            Column(Modifier.align(Alignment.BottomCenter).padding(8.dp).fillMaxWidth().glass(20).padding(12.dp, 10.dp)) {
                                Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(list.first().artist, fontSize = 12.sp, color = Color.White.copy(.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        Column(
            Modifier.padding(20.dp, 24.dp, 20.dp, 0.dp).fillMaxWidth().glass(22).clickable(onClick = onConnected).padding(16.dp)
        ) {
            Text("Modo conectado", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Controle YouTube Music, Deezer, Amazon Music e Spotify com o visual do Media Play",
                Modifier.padding(top = 4.dp), fontSize = 13.sp, color = Color.White.copy(.65f)
            )
        }
    }
}

// ---------- Música: pesquisa + Faixas / Álbuns (discos) / Artistas / Favoritos ----------
/** Une variações do mesmo artista (maiúsculas, espaços) em uma só "pasta". */
fun artistKey(a: String): String = a.trim().lowercase().replace(Regex("\\s+"), " ")

@Composable
fun TrackRow(t: Track, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color.White.copy(.10f) else Color.Transparent)
            .clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(t.uri, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)), 128, t.seed)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.artist, fontSize = 12.sp, color = Color.White.copy(.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(fmt(t.dur), fontSize = 12.sp, color = Color.White.copy(.6f))
    }
}

/** Lista de músicas de uma pasta ou de um artista, com botão de voltar. */
@Composable
fun GroupDetail(title: String, list: List<Track>, curId: Long?, onBack: () -> Unit, onPlay: (List<Track>, Int) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(20.dp, 0.dp, 20.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹ Voltar", Modifier.clickable(onClick = onBack).padding(end = 16.dp, top = 4.dp, bottom = 4.dp), color = Color.White.copy(.75f))
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LazyColumn(contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 200.dp)) {
            items(list.size, key = { list[it].id }) { i ->
                TrackRow(list[i], list[i].id == curId) { onPlay(list, i) }
            }
        }
    }
}

@Composable
fun MusicScreen(tracks: List<Track>, curId: Long?, favs: Set<Long>, onPlay: (List<Track>, Int) -> Unit) {
    var q by remember { mutableStateOf("") }
    var mode by remember { mutableIntStateOf(1) }
    var openArtist by remember { mutableStateOf<String?>(null) }
    val list = remember(tracks, q) {
        if (q.isBlank()) tracks else tracks.filter { (it.title + " " + it.artist + " " + it.album).contains(q, ignoreCase = true) }
    }
    BackHandler(openArtist != null) { openArtist = null }
    val opened = openArtist
    if (opened != null && mode == 2) {
        val mine = tracks.filter { artistKey(it.artist) == opened }
        GroupDetail(mine.firstOrNull()?.artist ?: "", mine, curId, { openArtist = null }, onPlay)
        return
    }
    Column(Modifier.fillMaxSize()) {
        TextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            placeholder = { Text("Pesquisar músicas, artistas, álbuns…") },
            modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().glass(16),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
            )
        )
        Row(Modifier.padding(20.dp, 12.dp, 20.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf("Faixas", "Álbuns", "Artistas", "Favoritos").forEachIndexed { i, l ->
                Text(
                    l, Modifier.clickable { mode = i }.padding(vertical = 4.dp),
                    color = if (mode == i) Color.White else Color.White.copy(.55f),
                    fontWeight = if (mode == i) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
        val shown = if (mode == 3) list.filter { favs.contains(it.id) } else list
        if (shown.isEmpty()) {
            Text(
                if (mode == 3 && q.isBlank()) "Toque na estrela na tela de reprodução para favoritar uma música." else "Nada encontrado.",
                Modifier.padding(20.dp), color = Color.White.copy(.6f)
            )
        } else when (mode) {
            0, 3 -> LazyColumn(contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 200.dp)) {
                items(shown.size, key = { shown[it].id }) { i ->
                    TrackRow(shown[i], shown[i].id == curId) { onPlay(shown, i) }
                }
            }
            1 -> {
                val groups = remember(shown) { shown.groupBy { it.album }.toList() }
                LazyVerticalGrid(
                    GridCells.Fixed(2), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 200.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(groups) { (name, tr) ->
                        Column(Modifier.clickable { onPlay(tr, 0) }) {
                            Sleeve(tr.first().uri, false, Modifier.fillMaxWidth().aspectRatio(1.27f), tr.first().seed)
                            Text(name.ifBlank { "Sem álbum" }, Modifier.padding(top = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(tr.first().artist, fontSize = 12.sp, color = Color.White.copy(.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            else -> {
                val artists = remember(shown) {
                    shown.groupBy { artistKey(it.artist) }.toList().sortedBy { it.second.first().artist.lowercase() }
                }
                LazyColumn(contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 200.dp)) {
                    items(artists.size) { i ->
                        val (key, tr) = artists[i]
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { openArtist = key }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Cover(tr.first().uri, Modifier.size(52.dp).clip(CircleShape), 128, tr.first().artist)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(tr.first().artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${tr.size} faixas", fontSize = 12.sp, color = Color.White.copy(.6f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Barra de progresso: linha fina com bolinha, igual à do Xperia ----------
@Composable
fun ProgressLine(fraction: Float, onChange: (Float) -> Unit, onFinish: (Float) -> Unit, modifier: Modifier = Modifier) {
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onFinish)
    androidx.compose.foundation.Canvas(
        modifier.fillMaxWidth().height(36.dp)
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val k = 7.dp.toPx()
                    val f = ((o.x - k) / (size.width - 2 * k)).coerceIn(0f, 1f)
                    change(f)
                    finish(f)
                }
            }
            .pointerInput(Unit) {
                var last = 0f
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        val k = 7.dp.toPx()
                        last = ((o.x - k) / (size.width - 2 * k)).coerceIn(0f, 1f)
                        change(last)
                    },
                    onDragEnd = { finish(last) },
                    onDragCancel = { finish(last) },
                    onHorizontalDrag = { ch, _ ->
                        val k = 7.dp.toPx()
                        last = ((ch.position.x - k) / (size.width - 2 * k)).coerceIn(0f, 1f)
                        change(last)
                    }
                )
            }
    ) {
        val w = size.width
        val cy = size.height / 2
        val knob = 7.dp.toPx()
        val line = 3.dp.toPx()
        val x = knob + (w - knob * 2) * fraction.coerceIn(0f, 1f)
        drawLine(Color.White.copy(alpha = 0.28f), Offset(knob, cy), Offset(w - knob, cy), strokeWidth = line, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(knob, cy), Offset(x, cy), strokeWidth = line, cap = StrokeCap.Round)
        drawCircle(Color.White.copy(alpha = 0.25f), knob * 1.7f, Offset(x, cy))
        drawCircle(Color.White, knob, Offset(x, cy))
    }
}

// ---------- Tela de reprodução ----------
@Composable
fun NowPlaying(
    t: Track, tracks: List<Track>, n: Now, p: Player, favs: Set<Long>, onFav: () -> Unit,
    c1: Color, c2: Color, close: () -> Unit
) {
    var pos by remember { mutableLongStateOf(0L) }
    var drag by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(t.id) {
        pos = 0L
        while (true) { pos = p.currentPosition; delay(300) }
    }
    // próxima música da fila (capa que aparece atrás)
    val next = remember(t.id, n.shuffle, n.index) {
        runCatching {
            val i = p.nextMediaItemIndex
            if (i >= 0) tracks.firstOrNull { it.id.toString() == p.getMediaItemAt(i).mediaId } else null
        }.getOrNull()
    }
    // direção da animação: avançar entra pela direita, voltar entra pela esquerda
    val hold = remember { intArrayOf(n.index, 1) }
    if (n.index != hold[0]) {
        hold[1] = if (n.index == hold[0] - 1) -1 else 1
        hold[0] = n.index
    }
    val fav = favs.contains(t.id)
    val dur = t.dur.toFloat().coerceAtLeast(1f)

    Column(
        Modifier.fillMaxSize().aurora(c1, c2).statusBarsPadding().navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Round("▾", 44, onClick = close)
            Text("Tocando agora", Modifier.padding(start = 14.dp), fontSize = 13.sp, color = Color.White.copy(.7f))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 14.dp)) {
            Text(t.title, fontSize = 30.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.artist, fontSize = 18.sp, color = Color.White.copy(.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (t.album.isNotBlank()) Text(t.album, fontSize = 14.sp, color = Color.White.copy(.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), Alignment.CenterStart) {
            AnimatedContent(
                targetState = t,
                transitionSpec = {
                    val ms = 500
                    if (hold[1] >= 0) {
                        // avançar: a capa de trás cresce e vem para a frente; a que tocava sai para a esquerda
                        (slideInHorizontally(tween(ms)) { w -> (w * 0.59f).toInt() } + scaleIn(tween(ms), initialScale = 0.75f) + fadeIn(tween(ms), initialAlpha = 0.6f)) togetherWith
                            (slideOutHorizontally(tween(ms)) { w -> -w } + fadeOut(tween(ms - 150)))
                    } else {
                        // voltar: a capa anterior entra pela esquerda e a atual vai para trás
                        (slideInHorizontally(tween(ms)) { w -> -w } + fadeIn(tween(ms))) togetherWith
                            (slideOutHorizontally(tween(ms)) { w -> (w * 0.59f).toInt() } + scaleOut(tween(ms), targetScale = 0.75f) + fadeOut(tween(ms)))
                    }
                },
                label = "sleeve"
            ) { shown ->
                Sleeve(
                    shown.uri, n.playing && shown.id == t.id, Modifier.padding(start = 16.dp).fillMaxWidth().aspectRatio(1f + 0.37f + (if (next != null) 0.10f else 0f)), shown.seed,
                    peek = 0.37f, animateIn = true, nextUri = next?.uri, nextSeed = next?.seed ?: ""
                )
            }
        }
        ProgressLine(
            fraction = (drag ?: pos.toFloat()) / dur,
            onChange = { drag = it * dur },
            onFinish = { p.seekTo((it * dur).toLong()); drag = null },
            modifier = Modifier.padding(horizontal = 22.dp).padding(top = 6.dp)
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), Arrangement.SpaceBetween) {
            Text(fmt(drag?.toLong() ?: pos), fontSize = 12.sp, color = Color.White.copy(.6f))
            Text(fmt(t.dur), fontSize = 12.sp, color = Color.White.copy(.6f))
        }
        Row(
            Modifier.padding(horizontal = 22.dp).fillMaxWidth().padding(top = 14.dp).glass(40).padding(10.dp),
            Arrangement.SpaceBetween, Alignment.CenterVertically
        ) {
            Round(if (fav) "★" else "☆", 48, active = fav) { onFav() }
            Round("⏮", 48) { p.seekToPreviousMediaItem() }
            Orb(n.playing, 76.dp) { if (n.playing) p.pause() else p.play() }
            Round("⏭", 48) { p.seekToNextMediaItem() }
            Round("⇄", 48, active = n.shuffle) { p.shuffleModeEnabled = !n.shuffle }
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ---------- Biblioteca: pastas do aparelho e pastas de artistas ----------
fun folderName(path: String): String = path.trimEnd('/').substringAfterLast('/').ifBlank { "Armazenamento interno" }

@Composable
fun LibraryScreen(tracks: List<Track>, curId: Long?, onPlay: (List<Track>, Int) -> Unit) {
    var mode by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<String?>(null) }
    val folders = remember(tracks) { tracks.groupBy { it.folder }.toList().sortedBy { it.first.lowercase() } }
    val artists = remember(tracks) {
        tracks.groupBy { artistKey(it.artist) }.toList().sortedBy { it.second.first().artist.lowercase() }
    }
    BackHandler(open != null) { open = null }
    if (tracks.isEmpty()) {
        Text("Nenhuma música encontrada neste aparelho.", Modifier.padding(20.dp), color = Color.White.copy(.7f))
        return
    }
    val current = open
    if (current != null) {
        val groups = if (mode == 0) folders else artists
        val list = groups.firstOrNull { it.first == current }?.second ?: emptyList()
        val title = if (mode == 0) folderName(current) else list.firstOrNull()?.artist ?: ""
        GroupDetail(title, list, curId, { open = null }, onPlay)
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(20.dp, 0.dp, 20.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            listOf("Pastas do aparelho", "Pastas de artistas").forEachIndexed { i, l ->
                Text(
                    l, Modifier.clickable { mode = i }.padding(vertical = 4.dp),
                    color = if (mode == i) Color.White else Color.White.copy(.55f),
                    fontWeight = if (mode == i) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
        LazyColumn(contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 200.dp)) {
            if (mode == 0) {
                items(folders.size) { i ->
                    val (path, list) = folders[i]
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { open = path }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Cover(list.first().uri, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)), 128, path)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(folderName(path), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${list.size} faixas · $path", fontSize = 12.sp, color = Color.White.copy(.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            } else {
                items(artists.size) { i ->
                    val (key, list) = artists[i]
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { open = key }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Cover(list.first().uri, Modifier.size(52.dp).clip(CircleShape), 128, list.first().artist)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(list.first().artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${list.size} faixas", fontSize = 12.sp, color = Color.White.copy(.6f))
                        }
                    }
                }
            }
        }
    }
}
