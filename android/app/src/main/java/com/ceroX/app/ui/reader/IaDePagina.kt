package com.ceroX.app.ui.reader

import android.content.Context
import android.graphics.Bitmap
import com.ceroX.app.modules.reader.ReaderPipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * El puente entre la página que se decodificó y el pipeline de modelos.
 *
 * QUÉ PROBLEMA RESUELVE
 * ---------------------
 * `ReaderPipeline` existía, estaba probado y **no lo llamaba nadie**. El lector
 * decodificaba el bitmap y lo pintaba directamente, así que la colorización y el
 * reescalado ×4 —dos de las cuatro funciones que el README anuncia— estaban
 * escritas, empaquetadas, con 373 MiB de modelos dentro del APK, y nunca se
 * ejecutaban.
 *
 * Aquí se conecta, con tres reglas:
 *
 *  1. **Si está apagado, no se toca el bitmap.** Ni una copia. Una página que ya
 *     está en memoria no se duplica por comprobar que no hay que hacerle nada.
 *  2. **Nunca en el hilo de dibujo.** 1 490 ms en el hilo principal son un ANR.
 *     Todo va en `Dispatchers.Default`, y quien llama recibe el resultado.
 *  3. **Un fallo devuelve la página original y el motivo.** Un modelo que explota
 *     no puede dejar la pantalla en negro: se pinta lo que había y se dice por qué.
 */
class IaDePagina(
    context: Context,
    private val preferencias: PreferenciasDeIa = PreferenciasDeIa(context),
) {

    private val pipeline by lazy { ReaderPipeline(context.applicationContext) }

    /** El último resultado, para poder enseñarlo sin volver a procesar. */
    @Volatile
    var ultimo: ReaderPipeline.Resultado? = null
        private set

    /** ¿Hay algo encendido? Si no, el llamador no debe ni pedir el trabajo. */
    val activa: Boolean get() = preferencias.colorizar || preferencias.reescalar

    /**
     * Procesa una página. Devuelve el bitmap que hay que pintar, que puede ser el
     * mismo que entró.
     */
    suspend fun procesar(pagina: Bitmap): Bitmap {
        if (!activa) return pagina
        val resultado = withContext(Dispatchers.Default) {
            pipeline.procesar(pagina, preferencias.ajustes())
        }
        ultimo = resultado
        preferencias.anotar(resultado)
        return resultado.pagina
    }
}
