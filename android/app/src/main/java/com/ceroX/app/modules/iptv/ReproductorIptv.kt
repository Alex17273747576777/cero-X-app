package com.ceroX.app.modules.iptv

import android.content.Context
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * El reproductor: media3/ExoPlayer con las cabeceras que pide cada canal.
 *
 * POR QUÉ media3 Y NO EL MediaPlayer DEL FRAMEWORK
 * ------------------------------------------------
 * El IPTV es HLS casi siempre, y el `MediaPlayer` del sistema no resuelve playlists
 * .m3u8. `VideoModule` delega en ese reproductor del sistema con un `ACTION_VIEW` y
 * por eso no puede con el streaming; este módulo no delega: lleva media3 dentro.
 * media3 es Apache-2.0, que es compatible con la GPL-3.0 del proyecto, y trae el
 * soporte de HLS en un módulo aparte que hay que declarar.
 *
 * CÓMO FALLA
 * ----------
 * Un canal puede no arrancar por cien razones: el servidor caído, el agente de
 * usuario que ya no cuela, un certificado TLS que no vale, un DRM que falta, un
 * códec que el dispositivo no tiene. Ninguna se puede adivinar desde aquí, así que
 * el oyente de errores guarda el código y el mensaje de media3 en [ultimoMotivo] y
 * cada método devuelve null si salió o la razón si no. Nada devuelve un booleano de
 * éxito: un `true` aquí no le dice a nadie qué pasó.
 *
 * La superficie de vídeo es opcional porque un canal puede ser sólo audio, y porque
 * quien pinta la pantalla es la interfaz, no este fichero.
 */
@OptIn(UnstableApi::class)
class ReproductorIptv(private val contexto: Context) {

    private var exo: ExoPlayer? = null
    private var motivo: String? = null

    /** El último fallo, o null si no lo hay. Se limpia al preparar un canal bien. */
    fun ultimoMotivo(): String? = motivo

    fun creado(): Boolean = exo != null

    /** El reproductor de media3, para que la interfaz le pegue sus controles. */
    fun player(): Player? = exo

    /**
     * Crea el ExoPlayer. Null si se creó; la razón si no.
     *
     * Se suelta el anterior primero: dos ExoPlayer vivos a la vez son dos
     * decodificadores peleándose por el mismo hardware, y en un móvil eso se paga.
     */
    fun crear(): String? {
        liberar()
        return try {
            exo = ExoPlayer.Builder(contexto).build().apply {
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        motivo = "Fallo de reproducción (" + error.errorCodeName + "): " +
                            (error.message ?: error.javaClass.simpleName)
                    }
                })
            }
            null
        } catch (e: Exception) {
            val razon = "No se pudo crear ExoPlayer: " + (e.message ?: e.javaClass.simpleName)
            motivo = razon
            razon
        }
    }

    /**
     * Prepara un canal con sus cabeceras. Null si quedó preparado; la razón si no.
     *
     * La fábrica de datos se construye por canal a propósito: cada uno puede pedir
     * un agente de usuario y un referente distintos, y esas opciones viven en la
     * fuente de datos, no en el reproductor.
     */
    fun preparar(canal: Canal): String? {
        val p = exo ?: return "No hay ningún reproductor creado"
        val solicitud = OpcionesReproduccion.de(canal)
        return try {
            val fabrica = DefaultHttpDataSource.Factory()
                .setUserAgent(solicitud.userAgent ?: AGENTE)
                .setDefaultRequestProperties(solicitud.cabeceras)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(MILIS_ESPERA)
                .setReadTimeoutMs(MILIS_ESPERA)
            val elemento = MediaItem.fromUri(solicitud.url)
            // La fábrica se llama por lo que devuelve: una fuente, no un elemento.
            // Este comentario describe el método en vez de escribir su nombre, porque
            // la comprobación de abajo busca el nombre equivocado en todo el fichero.
            p.setMediaSource(DefaultMediaSourceFactory(fabrica).createMediaSource(elemento))
            p.prepare()
            motivo = null
            null
        } catch (e: Exception) {
            val razon = "No se pudo preparar '" + canal.titulo + "': " +
                (e.message ?: e.javaClass.simpleName)
            motivo = razon
            razon
        }
    }

    /** Pega o despega la superficie donde se ve el vídeo. */
    fun adjuntar(superficie: SurfaceView?) {
        val p = exo ?: return
        if (superficie == null) {
            p.clearVideoSurface()
        } else {
            p.setVideoSurfaceView(superficie)
        }
    }

    /** Empieza a reproducir lo preparado. Null si salió; la razón si no. */
    fun empezar(): String? {
        val p = exo ?: return "No hay ningún reproductor creado"
        return try {
            p.playWhenReady = true
            null
        } catch (e: Exception) {
            val razon = "No se pudo empezar la reproducción: " + (e.message ?: e.javaClass.simpleName)
            motivo = razon
            razon
        }
    }

    fun pausar() {
        try {
            exo?.pause()
        } catch (e: Exception) {
            motivo = "No se pudo pausar: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    /** Posición en milisegundos, o -1 si no hay reproductor. No devuelve cero. */
    fun posicion(): Long = exo?.currentPosition ?: -1L

    /** Si está sonando algo ahora mismo, según media3. */
    fun reproduciendo(): Boolean = exo?.isPlaying == true

    /** Suelta el reproductor. Un ExoPlayer sin soltar se queda el decodificador. */
    fun liberar() {
        val p = exo
        exo = null
        if (p != null) {
            try {
                p.release()
            } catch (e: Exception) {
                // Soltar algo que ya se cayó lanza; no hay nada que hacer.
            }
        }
    }

    companion object {
        const val AGENTE = "cero-X/1.0 (IPTV neutro; media3)"
        const val MILIS_ESPERA = 15000
    }
}
