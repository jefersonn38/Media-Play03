package com.mediaplay.app

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media as AudioCol
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { App() } }
    }
}

// ---------- Dados ----------
data class Track(val id: Long, val title: String, val artist: String, val album: String, val dur: Long, val uri: Uri, val folder: String = "")

/** Semente para a capa gerada: músicas do mesmo álbum ganham a mesma capa. */
val Track.seed: String get() = album.ifBlank { title }

object Repo {
    fun load(c: Context): List<Track> {
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val out = mutableListOf<Track>()
        val proj = arrayOf(AudioCol._ID, AudioCol.TITLE, AudioCol.ARTIST, AudioCol.ALBUM, AudioCol.DURATION, AudioCol.RELATIVE_PATH)
        val sel = AudioCol.IS_MUSIC + " != 0 AND " + AudioCol.DURATION + " > 30000"
        c.contentResolver.query(base, proj, sel, null, AudioCol.TITLE + " COLLATE NOCASE")?.use { q ->
            while (q.moveToNext()) {
                val id = q.getLong(0)
                out += Track(
                    id, q.getString(1) ?: "Sem título",
                    q.getString(2)?.takeIf { it != "<unknown>" } ?: "Artista desconhecido",
                    q.getString(3) ?: "", q.getLong(4), ContentUris.withAppendedId(base, id), q.getString(5) ?: ""
                )
            }
        }
        return out
    }
}

/** Contagem de "ouvidas" guardada só no aparelho. */
object Plays {
    private fun sp(c: Context) = c.getSharedPreferences("plays", Context.MODE_PRIVATE)
    fun all(c: Context): Map<Long, Int> {
        val out = HashMap<Long, Int>()
        for ((k, v) in sp(c).all) {
            val id = k.toLongOrNull()
            if (id != null && v is Int) out[id] = v
        }
        return out
    }
    fun inc(c: Context, id: Long) {
        val s = sp(c)
        s.edit().putInt(id.toString(), s.getInt(id.toString(), 0) + 1).apply()
    }
}

/** Favoritos guardados só no aparelho. */
object Favs {
    private fun sp(c: Context) = c.getSharedPreferences("favs", Context.MODE_PRIVATE)
    fun all(c: Context): Set<Long> =
        (sp(c).getStringSet("ids", emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()
    fun toggle(c: Context, id: Long) {
        val cur = HashSet(sp(c).getStringSet("ids", emptySet()) ?: emptySet())
        val k = id.toString()
        if (!cur.remove(k)) cur.add(k)
        sp(c).edit().putStringSet("ids", cur).apply()
    }
}

fun item(t: Track) = MediaItem.Builder().setMediaId(t.id.toString()).setUri(t.uri)
    .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(t.uri).build())
    .setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album).build())
    .build()

fun fmt(ms: Long) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)

class Now(private val p: Player) : Player.Listener {
    var playing by mutableStateOf(p.isPlaying)
    var id by mutableStateOf(p.currentMediaItem?.mediaId)
    var index by mutableStateOf(p.currentMediaItemIndex)
    var shuffle by mutableStateOf(p.shuffleModeEnabled)
    init { p.addListener(this) }
    override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { id = mediaItem?.mediaId; index = p.currentMediaItemIndex }
    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { shuffle = shuffleModeEnabled }
    fun release() = p.removeListener(this)
}

/** Duas cores da capa (cantos opostos), usadas no degradê do fundo. */
suspend fun coverColors(ctx: Context, uri: Uri): Pair<Color, Color>? = withContext(Dispatchers.IO) {
    runCatching {
        val b = ctx.contentResolver.loadThumbnail(uri, Size(32, 32), null)
        val s = Bitmap.createScaledBitmap(b, 2, 2, true)
        Color(s.getPixel(0, 0)) to Color(s.getPixel(1, 1))
    }.getOrNull()
}

// ---------- Visual "liquid glass" ----------
val Ink = Color(0xFF0B0D1A)
val Accent = Color(0xFFFF7A3D)
val Rainbow = Brush.linearGradient(listOf(Color(0xFF6AA8FF), Color(0xFFA56BFF), Color(0xFFFF6BD5)))

fun Modifier.glass(r: Int = 24): Modifier {
    val s = RoundedCornerShape(r.dp)
    return clip(s)
        .background(Brush.verticalGradient(listOf(Color.White.copy(.18f), Color.White.copy(.06f))))
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(.5f), Color.White.copy(.08f))), s)
}

/** Vidro mais firme, usado na barra de baixo para o texto da lista não aparecer por trás. */
fun Modifier.dock(r: Int): Modifier {
    val s = RoundedCornerShape(r.dp)
    return clip(s)
        .background(Brush.verticalGradient(listOf(Color(0xE0322F5E), Color(0xE0191736))))
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(.45f), Color.White.copy(.08f))), s)
}

/** Fundo em degradê com as cores do álbum. */
fun Modifier.aurora(c1: Color, c2: Color): Modifier = background(Ink).drawBehind {
    drawRect(Brush.radialGradient(listOf(c1.copy(alpha = .75f), Color.Transparent), Offset(size.width * .15f, 0f), size.width * 1.1f))
    drawRect(Brush.radialGradient(listOf(c2.copy(alpha = .6f), Color.Transparent), Offset(size.width, size.height * .2f), size.width))
}

/** Cache de capas na memória (até ~24 MB) para a rolagem ficar fluida. */
object Thumbs {
    val cache = object : android.util.LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }
}

@Composable
fun Cover(uri: Uri?, mod: Modifier, px: Int = 512, seed: String = "") {
    val ctx = LocalContext.current
    val key = "$uri@$px"
    var bmp by remember(key) { mutableStateOf<ImageBitmap?>(Thumbs.cache.get(key)) }
    LaunchedEffect(key) {
        if (bmp == null && uri != null) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.loadThumbnail(uri, Size(px, px), null).asImageBitmap() }.getOrNull()
            }
            if (loaded != null) Thumbs.cache.put(key, loaded)
            bmp = loaded
        }
    }
    Box(mod) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else GenCover(seed.ifBlank { uri?.toString() ?: "?" }, px >= 512, Modifier.fillMaxSize())
    }
}

private val PALETTE = listOf(
    Color(0xFF3A2A8F) to Color(0xFFD04FD0), Color(0xFFA8321B) to Color(0xFF1A0F12), Color(0xFF4B79B8) to Color(0xFFCDD7E8),
    Color(0xFFE0A44A) to Color(0xFF2B1A12), Color(0xFF7A6DFF) to Color(0xFF7DE3D0), Color(0xFFC43BD0) to Color(0xFF2D1560),
    Color(0xFF2B3A66) to Color(0xFF8AA0D8), Color(0xFF2F6B4F) to Color(0xFFD9C26A), Color(0xFF1F4E79) to Color(0xFF9AD1D4),
    Color(0xFFC4572B) to Color(0xFFF2C14E)
)

/** Capa colorida gerada quando o arquivo não tem imagem (sempre a mesma para o mesmo álbum). */
@Composable
fun GenCover(seed: String, big: Boolean, mod: Modifier) {
    val h = seed.hashCode() and 0x7fffffff
    val (a, b) = PALETTE[h % PALETTE.size]
    val style = (h / 7) % 6
    Box(
        mod.background(Brush.linearGradient(listOf(a, b))).drawBehind {
            val w = size.width
            val hh = size.height
            val white = Color.White
            when (style) {
                0 -> drawCircle(white.copy(alpha = .85f), w * .16f, Offset(w * .5f, hh * .62f))
                1 -> {
                    var x = 0f
                    while (x < w) {
                        drawRect(white.copy(alpha = .16f), Offset(x, 0f), androidx.compose.ui.geometry.Size(w * .06f, hh))
                        x += w * .15f
                    }
                }
                2 -> {
                    drawCircle(white.copy(alpha = .35f), w * .3f, Offset(w / 2, hh / 2), style = Stroke(width = w * .03f))
                    drawCircle(white.copy(alpha = .25f), w * .18f, Offset(w / 2, hh / 2), style = Stroke(width = w * .03f))
                }
                3 -> drawCircle(white.copy(alpha = .22f), w * .45f, Offset(w * .2f, hh * .2f))
                4 -> {
                    drawCircle(white.copy(alpha = .45f), w * .1f, Offset(w * .3f, hh * .3f))
                    drawCircle(Color.Black.copy(alpha = .3f), w * .18f, Offset(w * .7f, hh * .66f))
                }
                else -> drawLine(white.copy(alpha = .28f), Offset(0f, hh * .8f), Offset(w, hh * .35f), strokeWidth = w * .1f)
            }
        },
        Alignment.Center
    ) { Text("♪", color = Color.White.copy(.55f), fontSize = if (big) 48.sp else 18.sp) }
}

/** Capa quadrada com o disco de vinil saindo pelo lado direito (e, opcionalmente, a capa da próxima música atrás). */
@Composable
fun Sleeve(
    uri: Uri?, spinning: Boolean, modifier: Modifier = Modifier, seed: String = "",
    peek: Float = 0.27f, animateIn: Boolean = false, nextUri: Uri? = null, nextSeed: String = ""
) {
    val rot: Float = if (spinning) {
        val tr = rememberInfiniteTransition(label = "disc")
        val r by tr.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "r")
        r
    } else 0f
    val reveal = remember { Animatable(if (animateIn) 0f else 1f) }
    LaunchedEffect(Unit) { if (animateIn) reveal.animateTo(1f, tween(800, delayMillis = 250)) }
    BoxWithConstraints(modifier.clipToBounds()) {
        val total = 1f + peek + (if (nextUri != null) 0.10f else 0f)
        val sv = maxWidth / total
        if (nextUri != null) {
            Cover(
                nextUri,
                Modifier.offset(x = sv * 1.05f, y = sv * 0.125f).size(sv * 0.75f)
                    .clip(RoundedCornerShape(10.dp)).graphicsLayer { alpha = 0.6f },
                256, nextSeed
            )
        }
        Box(
            Modifier.offset(x = sv * (0.08f + peek * reveal.value), y = sv * 0.04f).size(sv * 0.92f)
                .graphicsLayer { rotationZ = rot + reveal.value * 270f }
                .clip(CircleShape).background(Color(0xFF0A0A10))
                .drawBehind {
                    val rad = size.minDimension / 2
                    var k = 0.5f
                    while (k < 0.98f) {
                        drawCircle(Color.White.copy(alpha = .07f), radius = rad * k, style = Stroke(width = 1.2f))
                        k += 0.06f
                    }
                },
            Alignment.Center
        ) {
            Cover(uri, Modifier.size(sv * 0.42f).clip(CircleShape), 256, seed)
            Box(Modifier.size(8.dp).clip(CircleShape).background(Ink))
        }
        Cover(uri, Modifier.size(sv).clip(RoundedCornerShape(10.dp)), 512, seed)
    }
}

@Composable
fun Round(t: String, s: Int, active: Boolean = false, ring: Boolean = false, onClick: () -> Unit) {
    val br: Brush = if (ring) Rainbow else SolidColor(Color.White.copy(if (active) .9f else .35f))
    Box(
        Modifier.size(s.dp).clip(CircleShape).border(if (ring) 2.dp else 1.5.dp, br, CircleShape).clickable(onClick = onClick),
        Alignment.Center
    ) { if (t == "▶" || t == "⏸" || t == "⏮" || t == "⏭") Glyph(t, (s * .4f).dp) else Text(t, color = Color.White, fontSize = (s * .38f).sp) }
}

/** Botão play/pause estilo "Siri": esfera de vidro com cores girando por dentro. */
@Composable
fun Orb(playing: Boolean, size: Dp, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "orb")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(if (playing) 3000 else 9000, easing = LinearEasing)), label = "spin")
    val pulse by t.animateFloat(1f, if (playing) 1.08f else 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    Box(
        Modifier.size(size).graphicsLayer { scaleX = pulse; scaleY = pulse }.clip(CircleShape).clickable(onClick = onClick),
        Alignment.Center
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { rotationZ = spin }.background(
                Brush.sweepGradient(
                    listOf(Color(0xFF6AA8FF), Color(0xFFA56BFF), Color(0xFFFF6BD5), Color(0xFFFF7A3D), Color(0xFF5CF2D6), Color(0xFF6AA8FF))
                )
            )
        )
        Box(
            Modifier.fillMaxSize().padding(size * 0.06f).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color.White.copy(.5f), Color.White.copy(.08f))))
        )
        Glyph(if (playing) "⏸" else "▶", size * 0.4f)
    }
}

/** Ícones da barra de baixo: início, música, biblioteca e ajustes. */
@Composable
fun NavIcon(k: Int, dp: Dp, color: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(dp)) {
        val w = size.width
        val h = size.height
        val sw = w * 0.09f
        val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (k) {
            0 -> drawPath(
                Path().apply {
                    moveTo(w * .12f, h * .5f); lineTo(w * .5f, h * .14f); lineTo(w * .88f, h * .5f)
                    moveTo(w * .24f, h * .44f); lineTo(w * .24f, h * .86f); lineTo(w * .76f, h * .86f); lineTo(w * .76f, h * .44f)
                },
                color, style = st
            )
            1 -> {
                drawCircle(color, w * .14f, Offset(w * .3f, h * .76f))
                drawLine(color, Offset(w * .43f, h * .74f), Offset(w * .43f, h * .2f), strokeWidth = sw)
                drawPath(
                    Path().apply { moveTo(w * .43f, h * .2f); cubicTo(w * .6f, h * .3f, w * .82f, h * .3f, w * .82f, h * .52f) },
                    color, style = st
                )
            }
            2 -> {
                drawRoundRect(color, Offset(w * .14f, h * .2f), androidx.compose.ui.geometry.Size(w * .72f, h * .6f), CornerRadius(w * .1f), style = st)
                for (y in listOf(.38f, .5f, .62f)) drawLine(color, Offset(w * .3f, h * y), Offset(w * .7f, h * y), strokeWidth = sw * .8f)
            }
            else -> {
                val c = Offset(w / 2, h / 2)
                drawCircle(color, w * .24f, c, style = st)
                drawCircle(color, w * .1f, c, style = st)
                for (a in 0 until 8) {
                    val ang = a * Math.PI / 4
                    val dx = cos(ang).toFloat()
                    val dy = sin(ang).toFloat()
                    drawLine(color, Offset(c.x + dx * w * .3f, c.y + dy * w * .3f), Offset(c.x + dx * w * .42f, c.y + dy * w * .42f), strokeWidth = sw * 1.4f)
                }
            }
        }
    }
}

@Composable
fun Glyph(k: String, dp: Dp) {
    androidx.compose.foundation.Canvas(Modifier.size(dp)) {
        val w = size.width
        val h = size.height
        val c = Color.White
        fun tri(l: Float, r: Float, right: Boolean) = Path().apply {
            if (right) { moveTo(l, h * .1f); lineTo(r, h / 2); lineTo(l, h * .9f) }
            else { moveTo(r, h * .1f); lineTo(l, h / 2); lineTo(r, h * .9f) }
            close()
        }
        val bar = androidx.compose.ui.geometry.Size(w * .14f, h * .8f)
        when (k) {
            "▶" -> drawPath(tri(w * .2f, w * .95f, true), c)
            "⏸" -> {
                val b = androidx.compose.ui.geometry.Size(w * .26f, h * .84f)
                drawRoundRect(c, Offset(w * .15f, h * .08f), b, CornerRadius(w * .06f))
                drawRoundRect(c, Offset(w * .59f, h * .08f), b, CornerRadius(w * .06f))
            }
            "⏭" -> { drawPath(tri(w * .05f, w * .7f, true), c); drawRect(c, Offset(w * .78f, h * .1f), bar) }
            "⏮" -> { drawPath(tri(w * .3f, w * .95f, false), c); drawRect(c, Offset(w * .08f, h * .1f), bar) }
        }
    }
}

// ---------- App ----------
@Composable
fun App() {
    val ctx = LocalContext.current
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    fun has() = ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
    var ok by remember { mutableStateOf(has()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { ok = has() }
    var tracks by remember { mutableStateOf(emptyList<Track>()) }
    LaunchedEffect(ok) { if (ok) tracks = withContext(Dispatchers.IO) { Repo.load(ctx) } }

    var ctl by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(Unit) {
        val f = MediaController.Builder(ctx, SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))).buildAsync()
        f.addListener({ runCatching { ctl = f.get() } }, ContextCompat.getMainExecutor(ctx))
        onDispose { MediaController.releaseFuture(f); ctl = null }
    }
    val now = remember(ctl) { ctl?.let { Now(it) } }
    DisposableEffect(now) { onDispose { now?.release() } }

    var tab by remember { mutableIntStateOf(0) }
    var full by remember { mutableStateOf(false) }
    var showConn by remember { mutableStateOf(false) }
    var plays by remember { mutableStateOf(Plays.all(ctx)) }
    var favs by remember { mutableStateOf(Favs.all(ctx)) }
    val cur = tracks.firstOrNull { it.id.toString() == now?.id }

    // Degradê do fundo com as cores da capa que está tocando
    var bg1 by remember { mutableStateOf(Color(0xFF3A2A8F)) }
    var bg2 by remember { mutableStateOf(Color(0xFFD04FD0)) }
    val a1 by animateColorAsState(bg1, tween(900), label = "a1")
    val a2 by animateColorAsState(bg2, tween(900), label = "a2")
    LaunchedEffect(cur?.id) {
        cur?.let { t -> coverColors(ctx, t.uri)?.let { (x, y) -> bg1 = x; bg2 = y } }
    }

    // Uma música só conta como "ouvida" depois de 30 s tocando
    LaunchedEffect(now?.id) {
        val id = now?.id?.toLongOrNull()
        if (id != null) {
            var secs = 0
            while (secs < 30) { delay(1000); if (now?.playing == true) secs++ }
            Plays.inc(ctx, id)
            plays = Plays.all(ctx)
        }
    }

    // Outros apps tocando (Modo conectado): mostra a barra de reprodução deles aqui também
    var ext by remember { mutableStateOf<Snap?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            ext = withContext(Dispatchers.IO) { pickExternal(ctx) }
            delay(1000)
        }
    }
    val extNow = ext

    BackHandler(showConn) { showConn = false }
    BackHandler(full) { full = false }

    fun playList(list: List<Track>, i: Int) {
        // listas pequenas (um álbum, uma pasta) continuam tocando o restante da biblioteca
        val base = if (list.size >= 30) list else list + tracks.filter { t -> list.none { it.id == t.id } }
        val s0 = maxOf(0, i - 100)
        val sub = base.subList(s0, minOf(base.size, s0 + 400))
        ctl?.run { setMediaItems(sub.map(::item), i - s0, 0L); prepare(); play() }
    }

    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Box(Modifier.fillMaxSize().aurora(a1, a2)) {
            if (!ok) {
                Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                    Text("Media Play", style = TextStyle(brush = Rainbow, fontSize = 34.sp, fontWeight = FontWeight.Bold))
                    Text("Permita o acesso às músicas do seu aparelho para começar.", Modifier.padding(vertical = 16.dp))
                    Button(onClick = { ask.launch(arrayOf(perm, Manifest.permission.POST_NOTIFICATIONS)) }) { Text("Permitir") }
                }
            } else {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    Text(
                        "Media Play", Modifier.padding(20.dp, 14.dp, 20.dp, 8.dp),
                        style = TextStyle(brush = Rainbow, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (tab) {
                            0 -> HomeScreen(tracks, plays, ::playList) { showConn = true }
                            1 -> MusicScreen(tracks, cur?.id, favs, ::playList)
                            2 -> LibraryScreen(tracks, cur?.id, ::playList)
                            else -> SettingsScreen()
                        }
                    }
                }
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(260.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = .92f))))
                )
                // Mini player + barra de navegação flutuantes
                Column(
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (extNow != null && (extNow.playing || cur == null)) {
                        ExternalMini(extNow) { showConn = true }
                    } else if (cur != null && now != null && ctl != null) {
                        Row(
                            Modifier.fillMaxWidth().dock(26).clickable { full = true }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Cover(cur.uri, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)), 128, cur.seed)
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(cur.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(cur.artist, fontSize = 12.sp, color = Color.White.copy(.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Box(Modifier.clickable { ctl?.seekToPreviousMediaItem() }.padding(10.dp)) { Glyph("⏮", 18.dp) }
                            Box(Modifier.clickable { if (now.playing) ctl?.pause() else ctl?.play() }.padding(10.dp)) {
                                Glyph(if (now.playing) "⏸" else "▶", 22.dp)
                            }
                            Box(Modifier.clickable { ctl?.seekToNextMediaItem() }.padding(10.dp)) { Glyph("⏭", 18.dp) }
                        }
                    }
                    Row(Modifier.fillMaxWidth().dock(30).padding(6.dp)) {
                        listOf("Início", "Música", "Biblioteca", "Ajustes").forEachIndexed { i, l ->
                            Box(
                                Modifier.weight(1f).then(if (tab == i) Modifier.glass(24) else Modifier)
                                    .clickable { tab = i }.padding(vertical = 8.dp),
                                Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    NavIcon(i, 22.dp, if (tab == i) Color.White else Color.White.copy(.6f))
                                    Text(l, Modifier.padding(top = 2.dp), color = if (tab == i) Color.White else Color.White.copy(.6f), fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = full && cur != null && now != null && ctl != null,
                    enter = slideInVertically(tween(350)) { it } + fadeIn(tween(350)),
                    exit = slideOutVertically(tween(300)) { it } + fadeOut(tween(300))
                ) {
                    if (cur != null && now != null && ctl != null) NowPlaying(cur, tracks, now, ctl!!, favs, { Favs.toggle(ctx, cur.id); favs = Favs.all(ctx) }, a1, a2) { full = false }
                }
                AnimatedVisibility(
                    visible = showConn,
                    enter = slideInVertically(tween(350)) { it } + fadeIn(tween(350)),
                    exit = slideOutVertically(tween(300)) { it } + fadeOut(tween(300))
                ) {
                    Column(Modifier.fillMaxSize().aurora(a1, a2).statusBarsPadding().navigationBarsPadding()) {
                        Row(Modifier.padding(20.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Round("▾", 44, onClick = { showConn = false })
                            Text("Modo conectado", Modifier.padding(start = 14.dp), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        }
                        ConnectedScreen()
                    }
                }
            }
        }
    }
}
