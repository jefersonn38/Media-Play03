package com.mediaplay.app

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaCodecList
import android.media.audiofx.Equalizer
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Abre uma tela do sistema; se o aparelho não tiver, abre a alternativa. */
fun openSafely(c: Context, main: Intent, fallback: Intent) {
    try { c.startActivity(main) } catch (_: Exception) { runCatching { c.startActivity(fallback) } }
}

/** Mostra onde o som está saindo agora: Bluetooth, fone com fio ou alto-falante. */
fun currentOutput(c: Context): String {
    val am = c.getSystemService(AudioManager::class.java)
    val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    outs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        ?.let { return it.productName?.toString()?.takeIf { n -> n.isNotBlank() } ?: "Dispositivo Bluetooth" }
    if (outs.any { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_USB_HEADSET })
        return "Fone com fio"
    return "Alto-falante do celular"
}

/** Taxas de amostragem que o aparelho Bluetooth conectado informa ao Android. */
fun bluetoothDetails(c: Context): String {
    val am = c.getSystemService(AudioManager::class.java)
    val d = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        ?: return "Nenhum aparelho Bluetooth conectado."
    val rates = d.sampleRates.toList().sorted()
    return if (rates.isEmpty()) "Taxas de amostragem: informadas pelo codec em uso."
    else "Taxas de amostragem: " + rates.joinToString(" / ") { "%.1f kHz".format(it / 1000f) }
}

private val CODEC_NAMES = mapOf(
    "mpeg" to "MP3", "mp4a-latm" to "AAC", "flac" to "FLAC", "opus" to "Opus", "vorbis" to "Vorbis", "alac" to "ALAC",
    "raw" to "PCM / WAV", "ac3" to "AC-3", "eac3" to "E-AC-3", "amr-wb" to "AMR-WB", "3gpp" to "AMR-NB", "mpeg-L2" to "MP2",
    "ac4" to "AC-4", "amr-wb-plus" to "AMR-WB+", "dsd" to "DSD", "eac3-joc" to "Dolby Atmos (E-AC-3 JOC)", "evrc" to "EVRC",
    "g711-alaw" to "G.711 A-law", "g711-mlaw" to "G.711 µ-law", "qcelp" to "QCELP", "x-ape" to "APE", "x-ms-wma" to "WMA", "gsm" to "GSM"
)

private val CODEC_INFO = mapOf(
    "MP3" to "Formato mais comum. Compacta bastante, com pequena perda de qualidade.",
    "AAC" to "Sucessor do MP3: mais qualidade com o mesmo tamanho. Usado em arquivos M4A, YouTube e iPhone.",
    "AAC / M4A" to "AAC dentro de um arquivo M4A (o áudio de arquivos MP4). Boa qualidade com tamanho pequeno.",
    "FLAC" to "Sem perdas: o som é idêntico ao original, com arquivo cerca de metade do tamanho do WAV.",
    "ALAC" to "Sem perdas, criado pela Apple. Arquivos .m4a de iTunes.",
    "WAV" to "Áudio bruto, sem compressão e sem perdas. Arquivos grandes.",
    "PCM / WAV" to "Áudio bruto, sem compressão e sem perdas. Arquivos grandes.",
    "OGG Vorbis" to "Formato aberto e compacto, comum em jogos e rádios.",
    "Vorbis" to "Formato aberto e compacto, comum em jogos e rádios.",
    "Opus" to "Formato aberto moderno: ótima qualidade mesmo em arquivos bem pequenos.",
    "AC-3" to "Dolby Digital, usado em filmes e TV.",
    "E-AC-3" to "Dolby Digital Plus, usado em streaming de filmes.",
    "WMA" to "Formato da Microsoft, comum em músicas antigas de PC.",
    "APE" to "Monkey's Audio: sem perdas, comum em coleções de áudio de alta qualidade.",
    "DSD" to "Áudio de altíssima resolução usado em SACD."
)

fun codecInfo(name: String): String =
    CODEC_INFO[name] ?: "Codec de áudio que este telefone consegue decodificar."

private class Info(val title: String, val text: String, val dev: Boolean = false)

/** Codecs de áudio que este telefone sabe decodificar (lidos do próprio Android). */
fun phoneAudioDecoders(): List<String> = runCatching {
    MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
        .filter { !it.isEncoder }
        .flatMap { it.supportedTypes.toList() }
        .filter { it.startsWith("audio/") }
        .map { it.removePrefix("audio/") }
        .distinct()
        .map { CODEC_NAMES[it] ?: it }
        .distinct()
        .sorted()
}.getOrDefault(emptyList())

/** Equalizador do próprio Android ligado à sessão de áudio do player. */
object EqHolder {
    var eq: Equalizer? = null

    fun attach(c: Context, session: Int) {
        if (session == 0) return
        runCatching { eq?.release() }
        eq = runCatching { Equalizer(0, session) }.getOrNull()
        val e = eq ?: return
        val sp = c.getSharedPreferences("eq", Context.MODE_PRIVATE)
        runCatching {
            for (b in 0 until e.numberOfBands) e.setBandLevel(b.toShort(), sp.getInt("b$b", 0).toShort())
            e.setEnabled(sp.getBoolean("on", false))
        }
    }

    fun release() {
        runCatching { eq?.release() }
        eq = null
    }
}

@Composable
private fun Section(title: String) {
    Text(title, Modifier.padding(top = 24.dp, bottom = 10.dp), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun InfoRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).glass(16).clickable(onClick = onClick).padding(14.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically
    ) {
        Text(title, Modifier.weight(1f).padding(end = 8.dp))
        Text("$value  ›", color = Color.White.copy(.6f), fontSize = 13.sp)
    }
}

@Composable
private fun Chips(items: List<String>, onTap: (String) -> Unit) {
    items.chunked(3).forEach { row ->
        Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { Text(it, Modifier.glass(14).clickable { onTap(it) }.padding(12.dp, 6.dp), fontSize = 13.sp) }
        }
    }
}

@Composable
private fun EqualizerCard() {
    val ctx = LocalContext.current
    var eqState by remember { mutableStateOf(EqHolder.eq) }
    LaunchedEffect(Unit) { while (eqState == null) { delay(500); eqState = EqHolder.eq } }
    val e = eqState
    Column(Modifier.fillMaxWidth().glass(24).padding(16.dp)) {
        if (e == null) {
            Text(
                "O equalizador fica disponível quando o player estiver pronto. Toque uma música e volte aqui.",
                color = Color.White.copy(.7f), fontSize = 13.sp
            )
        } else {
            val bands = e.numberOfBands.toInt()
            val lo = e.bandLevelRange[0].toFloat()
            val hi = e.bandLevelRange[1].toFloat()
            var on by remember { mutableStateOf(e.enabled) }
            val levels = remember {
                mutableStateListOf<Float>().apply { for (b in 0 until bands) add(e.getBandLevel(b.toShort()).toFloat()) }
            }
            fun save() {
                val ed = ctx.getSharedPreferences("eq", Context.MODE_PRIVATE).edit()
                ed.putBoolean("on", on)
                for (b in 0 until bands) ed.putInt("b$b", levels[b].toInt())
                ed.apply()
            }
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Ativado")
                Switch(checked = on, onCheckedChange = { on = it; e.setEnabled(it); save() })
            }
            for (b in 0 until bands) {
                val hz = e.getCenterFreq(b.toShort()) / 1000
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (hz >= 1000) "%.1f kHz".format(hz / 1000f) else "$hz Hz",
                        Modifier.width(70.dp), fontSize = 12.sp, color = Color.White.copy(.7f)
                    )
                    Slider(
                        value = levels[b],
                        onValueChange = { levels[b] = it; e.setBandLevel(b.toShort(), it.toInt().toShort()) },
                        onValueChangeFinished = { save() },
                        modifier = Modifier.weight(1f),
                        valueRange = lo..hi,
                        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Color.White.copy(.2f))
                    )
                }
            }
            Text(
                "Redefinir",
                Modifier.padding(top = 6.dp).glass(14).clickable {
                    for (b in 0 until bands) { levels[b] = 0f; e.setBandLevel(b.toShort(), 0) }
                    save()
                }.padding(14.dp, 6.dp),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var out by remember { mutableStateOf(currentOutput(ctx)) }
    var btInfo by remember { mutableStateOf(bluetoothDetails(ctx)) }
    LaunchedEffect(Unit) { while (true) { out = currentOutput(ctx); btInfo = bluetoothDetails(ctx); delay(1500) } }
    var hires by remember { mutableStateOf(sp.getBoolean("float", true)) }
    var skip by remember { mutableStateOf(sp.getBoolean("skip", false)) }
    var speed by remember { mutableStateOf(PlayerRef.exo?.playbackParameters?.speed ?: 1f) }
    var info by remember { mutableStateOf<Info?>(null) }
    var decoders by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(Unit) { decoders = withContext(Dispatchers.Default) { phoneAudioDecoders() } }

    info?.let { i ->
        AlertDialog(
            onDismissRequest = { info = null },
            confirmButton = { TextButton(onClick = { info = null }) { Text("Ok") } },
            dismissButton = if (i.dev) {
                {
                    TextButton(onClick = {
                        info = null
                        openSafely(ctx, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS), Intent(Settings.ACTION_SETTINGS))
                    }) { Text("Opções do desenvolvedor") }
                }
            } else null,
            title = { Text(i.title) },
            text = { Text(i.text) }
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp, 0.dp, 20.dp, 200.dp)) {
        Text("Configurações", style = TextStyle(brush = Rainbow, fontSize = 26.sp, fontWeight = FontWeight.Bold))

        Section("Qualidade")
        InfoRow("Reprodução sem perdas (FLAC, WAV, ALAC)", "Ativa") {
            info = Info("Reprodução sem perdas", "Arquivos FLAC, WAV e ALAC guardam o som sem perder qualidade, e o Media Play toca todos eles como estão. Com a saída de 24 bits ligada (abaixo), arquivos de alta resolução também são preservados.")
        }
        InfoRow("Sem pausa entre faixas", "Ativa") {
            info = Info("Sem pausa entre faixas", "Entre músicas do mesmo álbum, o player já prepara a próxima antes da atual terminar, para não haver silêncio no meio (quando o formato do arquivo permite).")
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp).glass(16).padding(14.dp, 8.dp),
            Arrangement.SpaceBetween, Alignment.CenterVertically
        ) {
            Text("Saída de alta resolução (24 bits)", Modifier.weight(1f).padding(end = 8.dp))
            Switch(checked = hires, onCheckedChange = { hires = it; sp.edit().putBoolean("float", it).apply() })
        }
        Text("A saída de 24 bits vale na próxima vez que você abrir o app.", fontSize = 12.sp, color = Color.White.copy(.55f))
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp).glass(16).padding(14.dp, 8.dp),
            Arrangement.SpaceBetween, Alignment.CenterVertically
        ) {
            Text("Pular silêncios", Modifier.weight(1f).padding(end = 8.dp))
            Switch(checked = skip, onCheckedChange = {
                skip = it
                sp.edit().putBoolean("skip", it).apply()
                PlayerRef.exo?.skipSilenceEnabled = it
            })
        }
        Text("Velocidade de reprodução", Modifier.padding(top = 8.dp, bottom = 8.dp), fontSize = 14.sp, color = Color.White.copy(.8f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.75f, 1f, 1.25f, 1.5f).forEach { v ->
                Text(
                    if (v == 1f) "Normal" else "${v}x",
                    Modifier.then(if (speed == v) Modifier.glass(14) else Modifier.glass(14).alpha(.6f))
                        .clickable { speed = v; PlayerRef.exo?.setPlaybackSpeed(v) }.padding(14.dp, 7.dp),
                    fontSize = 13.sp
                )
            }
        }

        Section("Equalizador")
        EqualizerCard()

        Section("Caixa de som e fone")
        Column(Modifier.fillMaxWidth().glass(24).padding(18.dp)) {
            Text("Saída atual: $out", color = Color.White.copy(.85f))
            Text(btInfo, Modifier.padding(top = 6.dp, bottom = 14.dp), fontSize = 12.sp, color = Color.White.copy(.6f))
            Button(onClick = { openSafely(ctx, Intent(Settings.Panel.ACTION_VOLUME), Intent(Settings.ACTION_SOUND_SETTINGS)) }) { Text("Escolher saída de áudio") }
            Spacer(Modifier.height(10.dp))
            Button(onClick = { openSafely(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), Intent(Settings.ACTION_SETTINGS)) }) { Text("Parear caixa ou fone Bluetooth") }
        }

        Section("Formatos de áudio")
        Chips(listOf("MP3", "AAC / M4A", "FLAC", "WAV", "OGG Vorbis", "Opus", "ALAC")) { info = Info(it, codecInfo(it)) }

        Section("Codecs de áudio deste telefone")
        if (decoders.isEmpty()) Text("Lendo os codecs do telefone…", color = Color.White.copy(.6f), fontSize = 13.sp)
        else Chips(decoders) { info = Info(it, codecInfo(it)) }

        Section("Codecs Bluetooth")
        InfoRow("SBC", "Padrão") { info = Info("SBC", "Codec que todo aparelho Bluetooth entende. Qualidade boa e funciona com tudo.", true) }
        InfoRow("AAC", "iPhone e Android") { info = Info("AAC", "Bom equilíbrio entre qualidade e consumo. Padrão em iPhones e em muitos Androids.", true) }
        InfoRow("aptX / aptX HD", "Qualcomm") { info = Info("aptX / aptX HD", "Qualidade melhor e menos atraso. Precisa de celular e fone compatíveis.", true) }
        InfoRow("LDAC", "Sony, alta resolução") { info = Info("LDAC", "Da Sony: a melhor qualidade em Bluetooth, de até 990 kbps. Precisa de celular e fone compatíveis.", true) }
        Text(
            "O codec é escolhido pelo Android e pelo fone. Toque em um deles para saber mais e abrir as Opções do desenvolvedor, onde dá para trocar à mão " +
                "(se não aparecerem, toque 7 vezes em Número da versão, em Sobre o telefone).",
            fontSize = 12.sp, color = Color.White.copy(.6f), modifier = Modifier.padding(bottom = 10.dp)
        )
        Button(onClick = { openSafely(ctx, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS), Intent(Settings.ACTION_SETTINGS)) }) {
            Text("Abrir Opções do desenvolvedor")
        }

        Section("Permissões do app")
        Text(
            "O Media Play pede só o acesso à Música e áudio, para listar as suas músicas, e às Notificações, para os controles " +
                "do player. Ele não precisa de acesso a todos os arquivos do aparelho.",
            fontSize = 13.sp, color = Color.White.copy(.7f), modifier = Modifier.padding(bottom = 10.dp)
        )
        Button(onClick = {
            openSafely(
                ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)),
                Intent(Settings.ACTION_SETTINGS)
            )
        }) { Text("Abrir permissões do app") }
    }
}
