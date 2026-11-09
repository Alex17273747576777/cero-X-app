package com.ceroX.app.ui.idiomas

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ceroX.app.core.ConfigManager
import com.ceroX.app.core.Prefs
import com.ceroX.app.modules.translation.ProveedorTraduccionAndroid
import com.ceroX.app.ui.theme.CeroXTheme
import com.cerox.comun.Idiomas
import com.cerox.comun.Traduccion
import java.util.Locale

/**
 * La pantalla donde se elige el idioma de la app y cómo se escribe.
 *
 * QUÉ SE PUEDE ELEGIR AQUÍ, Y POR QUÉ CADA COSA
 * ---------------------------------------------
 *   1. **Idioma de la app.** Los 40 de [Idiomas.INTERFAZ]. Se marcan con un punto
 *      los que ya tienen textos traducidos y con la palabra «inglés» los que
 *      todavía no: elegir uno sin traducción no rompe nada, se lee en inglés y la
 *      pantalla lo avisa antes, no después.
 *   2. **Registro**: neutro o con modismos. Son dos formas de escribir el mismo
 *      idioma, no dos idiomas. Neutro no aplica ninguna tabla regional; con
 *      modismos se aplica la variante elegida.
 *   3. **Variante regional**, solo cuando el registro es «con modismos». Enseñar
 *      el selector de variante en modo neutro sería ofrecer algo que no hace nada.
 *   4. **Idiomas del contenido**: qué idiomas de fuente se ofrecen al filtrar.
 *      Solo aparecen los que tienen extensiones de verdad ([Idiomas.hayContenidoEn]).
 *   5. **Proveedor de traducción** y su aviso de privacidad, con la dirección del
 *      servidor cuando el proveedor la admite (LibreTranslate).
 *
 * Lo que esta pantalla NO hace: cambiar el idioma del sistema. Cambia el idioma
 * de cero-X, que es lo que el usuario pidió y lo único que una app puede hacer sin
 * root.
 */
class IdiomasActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        // ConfigManager se instancia para que dispare la migración de las
        // preferencias viejas del almacenamiento interno en cuanto se abre esta
        // pantalla; si no, la migración dependería de qué pantalla se abriera antes.
        ConfigManager(this)

        setContent {
            CeroXTheme {
                var idioma by remember { mutableStateOf(prefs.getString(CLAVE_IDIOMA, "") ?: "") }
                var registro by remember {
                    mutableStateOf(prefs.getString(CLAVE_REGISTRO, Idiomas.Registro.NEUTRO.id) ?: Idiomas.Registro.NEUTRO.id)
                }
                var variante by remember {
                    mutableStateOf(prefs.getString(CLAVE_VARIANTE, "es-DO") ?: "es-DO")
                }
                var deDerechaAIzquierda by remember {
                    mutableStateOf(Idiomas.esDeDerechaAIzquierda(idioma))
                }
                var proveedor by remember {
                    mutableStateOf(
                        Traduccion.Proveedor.porId(prefs.getString(CLAVE_PROVEEDOR, null)),
                    )
                }
                var urlLibre by remember { mutableStateOf(prefs.getString(CLAVE_URL_LIBRE, "") ?: "") }
                var filtros by remember {
                    mutableStateOf(
                        (prefs.getString(CLAVE_FILTROS, "es,en,all") ?: "es,en,all").split(',')
                            .filter { it.isNotBlank() }.toSet(),
                    )
                }
                var estado by remember { mutableStateOf("") }

                // La dirección de lectura se aplica a toda la pantalla en cuanto
                // se elige el idioma: si el árabe se pintara de izquierda a
                // derecha, la vista previa mentiría sobre lo que va a pasar.
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalLayoutDirection provides
                        if (deDerechaAIzquierda) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        Text("Idiomas", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "El español dominicano es obligatorio para lo que publica La Bandita, " +
                                "no para la app. Aquí eliges en qué idioma la lees tú.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )

                        // ── 1 · Idioma de la app ─────────────────────────────
                        Seccion("Idioma de la app")
                        IdiomaActual(idioma)
                        Idiomas.INTERFAZ.forEach { i ->
                            val traducido = Idiomas.resolverConCaida(i.tag).let { !it.second }
                            Fila(
                                titulo = i.nativo,
                                subtitulo = buildString {
                                    append(i.enEspanol)
                                    if (!traducido) append(" · aún en inglés")
                                    if (i.escrituraDeDerechaAIzquierda) append(" · de derecha a izquierda")
                                },
                                seleccionado = idioma == i.tag,
                                atenuado = !traducido,
                            ) {
                                idioma = i.tag
                                deDerechaAIzquierda = i.escrituraDeDerechaAIzquierda
                                prefs.putString(CLAVE_IDIOMA, i.tag)
                                // Se aplica de verdad al idioma de la app. Sin
                                // esto el ajuste sería un adorno que no cambia nada.
                                aplicarIdioma(i.tag)
                                estado = if (traducido) {
                                    "Idioma aplicado: ${i.nativo}"
                                } else {
                                    "Idioma aplicado: ${i.nativo}. Los textos de la app " +
                                        "todavía están en inglés y se irán traduciendo."
                                }
                            }
                        }

                        // ── 2 · Registro ─────────────────────────────────────
                        Seccion("Cómo se escribe")
                        Idiomas.Registro.entries.forEach { r ->
                            Fila(
                                titulo = setResourceText(r.id),
                                subtitulo = r.explicacion,
                                seleccionado = registro == r.id,
                            ) {
                                registro = r.id
                                prefs.putString(CLAVE_REGISTRO, r.id)
                                estado = "Registro: ${r.nombre}"
                            }
                        }

                        // La variante solo tiene sentido con modismos.
                        if (registro == Idiomas.Registro.MODISMOS.id) {
                            Seccion("Variante regional")
                            com.cerox.comun.Variante.todas.forEach { v ->
                                Fila(
                                    titulo = v.nombre,
                                    subtitulo = if (v.vocabulario.isEmpty()) {
                                        "Sin tabla regional"
                                    } else {
                                        v.vocabulario.entries.take(4)
                                            .joinToString(" · ") { "${it.key} → ${it.value}" }
                                    },
                                    seleccionado = variante == v.codigo,
                                ) {
                                    variante = v.codigo
                                    prefs.putString(CLAVE_VARIANTE, v.codigo)
                                    estado = "Variante: ${v.nombre}"
                                }
                            }
                        }

                        // ── 3 · Idiomas del contenido ────────────────────────
                        Seccion("Idiomas del contenido")
                        Text(
                            "Solo aparecen los idiomas en los que hay extensiones de verdad. " +
                                "«Multiidioma» incluye las que sirven cualquier idioma.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Idiomas.CONTENIDO.chunked(3).forEach { fila ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    fila.forEach { c ->
                                        FilterChip(
                                            selected = c.codigo in filtros,
                                            onClick = {
                                                filtros = if (c.codigo in filtros) {
                                                    filtros - c.codigo
                                                } else {
                                                    filtros + c.codigo
                                                }
                                                prefs.putString(CLAVE_FILTROS, filtros.joinToString(","))
                                                estado = "Filtros: ${filtros.joinToString(", ")}"
                                            },
                                            label = {
                                                Text(
                                                    "${c.codigo} · ${c.extensiones}",
                                                    fontSize = 12.sp,
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        // ── 4 · Traducción de los textos de la app ───────────
                        Seccion("Traducir los textos de la app")
                        Text(
                            Traduccion.avisoDePrivacidad(proveedor),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (proveedor.enviaTextoFuera) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                        Traduccion.Proveedor.entries.forEach { p ->
                            val posible = Traduccion.puedeTraducir(p, Traduccion.Objetivo.INTERFAZ)
                            Fila(
                                titulo = p.nombre,
                                subtitulo = p.nota,
                                seleccionado = proveedor == p,
                                atenuado = !posible.posible,
                            ) {
                                proveedor = p
                                prefs.putString(CLAVE_PROVEEDOR, p.id)
                                estado = "Proveedor: ${p.nombre}. ${Traduccion.avisoDePrivacidad(p)}"
                            }
                        }
                        if (proveedor == Traduccion.Proveedor.LIBRE) {
                            OutlinedTextField(
                                value = urlLibre,
                                onValueChange = {
                                    urlLibre = it
                                    prefs.putString(CLAVE_URL_LIBRE, it)
                                },
                                label = { Text("Servidor (vacío = translate.disroot.org)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        // El botón de traducir solo se dibuja si hay proveedor y
                        // destino: un botón que no puede hacer nada no se pone.
                        val destino = Idiomas.resolverConCaida(idioma).first
                        val puedeAhora = Traduccion.puedeTraducir(
                            proveedor, Traduccion.Objetivo.INTERFAZ,
                        ).posible && Traduccion.soporta(proveedor, destino) && destino != "es"
                        TextButton(
                            onClick = {
                                estado = "Traduciendo los textos a $destino con " +
                                    "${proveedor.nombre}…"
                                Thread {
                                    val r = ProveedorTraduccionAndroid(this@IdiomasActivity, prefs)
                                        .traducirInterfaz(destino)
                                    runOnUiThread { estado = r }
                                }.start()
                            },
                            enabled = puedeAhora,
                        ) {
                            Text("Traducir la app a este idioma ($destino)")
                        }
                        if (!puedeAhora) {
                            Text(
                                when {
                                    destino == "es" -> "Los textos ya están en español."
                                    !Traduccion.soporta(proveedor, destino) ->
                                        "${proveedor.nombre} no soporta $destino."
                                    else -> Traduccion.puedeTraducir(
                                        proveedor, Traduccion.Objetivo.INTERFAZ,
                                    ).motivo.ifBlank { "No disponible." }
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            )
                        }

                        Spacer(Modifier.height(24.dp))
                        if (estado.isNotBlank()) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            ) {
                                Text(estado, Modifier.padding(12.dp), fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Dónde se guarda: ${com.ceroX.app.core.AlmacenExterno.estado(this@IdiomasActivity)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    /**
     * Cambia el idioma **de la app**, no el del sistema.
     *
     * `AppCompatDelegate.setApplicationLocales` es la vía oficial desde AppCompat
     * 1.6: persiste el idioma elegido, recrea las actividades y el sistema
     * devuelve los recursos de ese idioma. Si no hubiera traducción, Android cae
     * a los recursos por defecto, que son los de inglés.
     */
    private fun aplicarIdioma(tag: String) {
        runCatching {
            val locales = androidx.core.os.LocaleListCompat.forLanguageTags(tag)
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(locales)
        }.onFailure {
            // Sin AppCompat suficiente, se aplica al menos a esta pantalla y se
            // dice: es mejor un cambio parcial y explicado que un ajuste mudo.
            val conf = Configuration(resources.configuration)
            conf.setLocale(Locale.forLanguageTag(tag))
            @Suppress("DEPRECATION")
            resources.updateConfiguration(conf, resources.displayMetrics)
        }
    }

    /**
     * El nombre del registro, sacado del recurso traducido.
     *
     * `R.string.registro_neutro` y `registro_modismos` son los mismos dos en los
     * siete idiomas que ya trae la app: la pantalla de ajustes se lee en el
     * idioma que el usuario eligió, que es todo el punto de esta pantalla.
     */
    private fun setResourceText(id: String): String = when (id) {
        "neutro" -> getString(com.ceroX.app.R.string.registro_neutro)
        "modismos" -> getString(com.ceroX.app.R.string.registro_modismos)
        else -> id
    }

    @Composable
    private fun Seccion(titulo: String) {
        Spacer(Modifier.height(24.dp))
        Text(titulo, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Divider()
        Spacer(Modifier.height(4.dp))
    }

    @Composable
    private fun IdiomaActual(tag: String) {
        if (tag.isBlank()) {
            Text(
                "Ahora mismo: el idioma del teléfono.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Fila(
        titulo: String,
        subtitulo: String,
        seleccionado: Boolean,
        atenuado: Boolean = false,
        alPulsar: () -> Unit,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = alPulsar)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    titulo,
                    fontSize = 15.sp,
                    fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Normal,
                    color = if (atenuado) {
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                )
                if (subtitulo.isNotBlank()) {
                    Text(
                        subtitulo,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            if (seleccionado) {
                Text("✓", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    companion object {
        const val CLAVE_IDIOMA = "idioma_interfaz"
        const val CLAVE_REGISTRO = "idioma_registro"
        const val CLAVE_VARIANTE = "idioma_variante"
        const val CLAVE_FILTROS = "idioma_filtros_contenido"
        const val CLAVE_PROVEEDOR = "traduccion_proveedor"
        const val CLAVE_URL_LIBRE = "traduccion_url_libre"
    }
}

