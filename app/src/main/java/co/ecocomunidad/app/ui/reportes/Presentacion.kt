package co.ecocomunidad.app.ui.reportes

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.TextViewCompat
import co.ecocomunidad.app.R
import co.ecocomunidad.app.data.modelo.EstadoReporte
import co.ecocomunidad.app.data.modelo.Urgencia
import co.ecocomunidad.app.databinding.VistaEstadoCargaBinding
import co.ecocomunidad.app.ui.navegacion.formatearFechaColombia
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Texto legible de cada estado (sección 6.1). */
@get:StringRes
val EstadoReporte.etiqueta: Int
    get() = when (this) {
        EstadoReporte.PENDIENTE -> R.string.estado_pendiente
        EstadoReporte.APROBADO -> R.string.estado_aprobado
        EstadoReporte.ASIGNADO -> R.string.estado_asignado
        EstadoReporte.EN_PROCESO -> R.string.estado_en_proceso
        EstadoReporte.RESUELTO -> R.string.estado_resuelto
        EstadoReporte.RECHAZADO -> R.string.estado_rechazado
    }

/**
 * Color de marca de cada estado. marca.md fija ámbar para alerta y arcilla para rechazado;
 * los demás van de lo neutro (aprobado) al verde más fuerte (resuelto).
 */
@get:ColorRes
val EstadoReporte.color: Int
    get() = when (this) {
        EstadoReporte.PENDIENTE -> R.color.ambar
        EstadoReporte.APROBADO -> R.color.tinta_suave
        EstadoReporte.ASIGNADO -> R.color.verde_musgo
        EstadoReporte.EN_PROCESO -> R.color.verde_hoja
        EstadoReporte.RESUELTO -> R.color.verde_bosque
        EstadoReporte.RECHAZADO -> R.color.arcilla
    }

/** Etiqueta de estado: punto del color del estado, fondo del mismo color muy suave y texto en tinta. */
fun TextView.mostrarEstado(estado: EstadoReporte) = mostrarEtiquetaEstado(estado.etiqueta, estado.color)

/** La misma etiqueta para cualquier estado (por ejemplo, el de una novedad). */
fun TextView.mostrarEtiquetaEstado(@StringRes texto: Int, @ColorRes colorEstado: Int) {
    setText(texto)
    val color = ContextCompat.getColor(context, colorEstado)
    TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(color))
    backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, ALFA_FONDO_ESTADO))
}

private const val ALFA_FONDO_ESTADO = 0x24 // ~14 %

/** Texto de cada urgencia (RN-25). */
@get:StringRes
val Urgencia.etiqueta: Int
    get() = when (this) {
        Urgencia.ALTA -> R.string.urgencia_alta
        Urgencia.MEDIA -> R.string.urgencia_media
        Urgencia.BAJA -> R.string.urgencia_baja
    }

/** Los colores de alerta de marca.md: arcilla lo más urgente, ámbar lo medio y verde musgo lo que puede esperar. */
@get:ColorRes
val Urgencia.color: Int
    get() = when (this) {
        Urgencia.ALTA -> R.color.arcilla
        Urgencia.MEDIA -> R.color.ambar
        Urgencia.BAJA -> R.color.verde_musgo
    }

/** La misma etiqueta del estado, con el color de la urgencia. Sin urgencia, no se muestra. */
fun TextView.mostrarUrgencia(urgencia: Urgencia?) {
    visibility = if (urgencia == null) View.GONE else View.VISIBLE
    if (urgencia != null) mostrarEtiquetaEstado(urgencia.etiqueta, urgencia.color)
}

/**
 * Dónde queda el reporte (RN-23): "Popayán · Zona centro" y, si la escribió, la vereda en otra línea.
 * Los reportes anteriores a RN-23 no tienen municipio ni zona: "Sin municipio".
 */
fun lugarDelReporte(municipio: String?, vereda: String?, zona: String?): String {
    val principal = municipio?.takeIf { it.isNotBlank() } ?: "Sin municipio"
    return listOfNotNull(
        principal + (zona?.takeIf { it.isNotBlank() }?.let { " · Zona $it" } ?: ""),
        vereda?.trim()?.takeIf { it.isNotEmpty() }?.let { "Vereda o corregimiento: $it" },
    ).joinToString("\n")
}

private val zonaColombia = ZoneId.of("America/Bogota")
private val formatoCorto = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-CO"))

private fun fecha(iso: String): OffsetDateTime? =
    try {
        OffsetDateTime.parse(iso)
    } catch (e: DateTimeParseException) {
        null
    }

/** Ej.: "1 oct. 2026", en hora de Colombia. Si la fecha no se entiende, se muestra tal cual. */
fun fechaCorta(iso: String): String =
    fecha(iso)?.atZoneSameInstant(zonaColombia)?.format(formatoCorto) ?: iso

/** Ej.: "1 de octubre de 2026 a las 10:00 a. m.", en hora de Colombia. */
fun fechaLarga(iso: String): String = fecha(iso)?.let(::formatearFechaColombia) ?: iso

/**
 * Carga una foto desde su url con Coil. Mientras carga y si falla se ve un ícono centrado
 * sobre el fondo del marco; la foto, recortada para llenar el espacio.
 */
fun ImageView.cargarFoto(url: String?, escala: ImageView.ScaleType = ImageView.ScaleType.CENTER_CROP) {
    scaleType = ImageView.ScaleType.CENTER
    load(url) {
        placeholder(R.drawable.ic_imagen)
        error(R.drawable.ic_imagen_rota)
        crossfade(true)
        listener(onSuccess = { _, _ -> scaleType = escala })
    }
}

/** Qué muestra la vista de carga, vacío o error. */
sealed interface EstadoVista {
    /** [cargaLarga]: la carga pasó de 1 s y el indicador va con su texto. */
    data class Cargando(val cargaLarga: Boolean, @param:StringRes val texto: Int) : EstadoVista

    /** Lista vacía: ícono de bandeja con el mensaje. */
    data class Vacio(@param:StringRes val texto: Int) : EstadoVista

    /** Sin conexión con el wifi tachado; los demás errores, sobrios con el ícono de alerta. */
    data class Error(@param:StringRes val titulo: Int, val error: ErrorCarga) : EstadoVista
}

fun VistaEstadoCargaBinding.mostrar(estado: EstadoVista, alReintentar: () -> Unit) {
    fun TextView.poner(texto: CharSequence?) {
        text = texto
        visibility = if (texto == null) View.GONE else View.VISIBLE
    }
    fun icono(@DrawableRes imagen: Int?) {
        iconoEstado.visibility = if (imagen == null) View.GONE else View.VISIBLE
        if (imagen != null) iconoEstado.setImageResource(imagen)
    }
    val contexto = root.context
    when (estado) {
        is EstadoVista.Cargando -> {
            progreso.visibility = View.VISIBLE
            icono(null)
            iconoError.visibility = View.GONE
            titulo.poner(null)
            mensaje.poner(if (estado.cargaLarga) contexto.getString(estado.texto) else null)
            botonReintentar.visibility = View.GONE
        }
        is EstadoVista.Vacio -> {
            progreso.visibility = View.GONE
            icono(R.drawable.ic_bandeja)
            iconoError.visibility = View.GONE
            titulo.poner(null)
            mensaje.poner(contexto.getString(estado.texto))
            botonReintentar.visibility = View.GONE
        }
        is EstadoVista.Error -> {
            progreso.visibility = View.GONE
            icono(if (estado.error.sinConexion) R.drawable.ic_sin_wifi else null)
            iconoError.visibility = if (estado.error.sinConexion) View.GONE else View.VISIBLE
            titulo.poner(contexto.getString(estado.titulo))
            mensaje.poner(estado.error.mensaje)
            botonReintentar.visibility = View.VISIBLE
            botonReintentar.setOnClickListener { alReintentar() }
        }
    }
}
