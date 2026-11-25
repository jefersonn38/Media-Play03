package com.mediaplay.app

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** Referência ao player (mesmo processo) para os ajustes de velocidade e silêncio. */
object PlayerRef { var exo: ExoPlayer? = null }

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // Saída em ponto flutuante (opcional em Ajustes): preserva melhor arquivos de alta resolução
        val floatOut = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("float", true)
        val factory = DefaultRenderersFactory(this).setEnableAudioFloatOutput(floatOut)
        val player = ExoPlayer.Builder(this, factory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        PlayerRef.exo = player
        player.skipSilenceEnabled = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("skip", false)
        // Equalizador do Android ligado à sessão de áudio do player
        EqHolder.attach(this, player.audioSessionId)
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                EqHolder.attach(this@PlaybackService, audioSessionId)
            }
        })
        session = MediaSession.Builder(this, player).setCallback(Cb()).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        EqHolder.release()
        PlayerRef.exo = null
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }

    private class Cb : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
            mediaItems.map {
                it.buildUpon().setUri(it.requestMetadata.mediaUri ?: it.localConfiguration?.uri).build()
            }.toMutableList()
        )
    }
}
