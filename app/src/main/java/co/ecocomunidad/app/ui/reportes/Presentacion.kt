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
fun TextView.mostrarEstado(estado: EstadoReporte) {
    setText(estado.etiqueta)
    val color = ContextCompat.getColor(context, estado.color)
    TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(color))
    backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, ALFA_FONDO_ESTADO))
}

private const val ALFA_FONDO_ESTADO = 0x24 // ~14 %

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
    /** [conBrotico]: la carga pasó de 1 s y Brotico piensa. */
    data class Cargando(val conBrotico: Boolean, @param:StringRes val texto: Int) : EstadoVista

    /** Lista vacía: Brotico triste (marca.md). */
    data class Vacio(@param:StringRes val texto: Int) : EstadoVista

    /** Sin conexión con Brotico triste; los demás errores, sobrios con el ícono de alerta. */
    data class Error(@param:StringRes val titulo: Int, val error: ErrorCarga) : EstadoVista
}

fun VistaEstadoCargaBinding.mostrar(estado: EstadoVista, alReintentar: () -> Unit) {
    fun TextView.poner(texto: CharSequence?) {
        text = texto
        visibility = if (texto == null) View.GONE else View.VISIBLE
    }
    fun brotico(@DrawableRes imagen: Int?) {
        brotico.visibility = if (imagen == null) View.GONE else View.VISIBLE
        if (imagen != null) brotico.setImageResource(imagen)
    }
    val contexto = root.context
    when (estado) {
        is EstadoVista.Cargando -> {
            progreso.visibility = if (estado.conBrotico) View.GONE else View.VISIBLE
            brotico(if (estado.conBrotico) R.drawable.brotico_piensa else null)
            iconoError.visibility = View.GONE
            titulo.poner(null)
            mensaje.poner(if (estado.conBrotico) contexto.getString(estado.texto) else null)
            botonReintentar.visibility = View.GONE
        }
        is EstadoVista.Vacio -> {
            progreso.visibility = View.GONE
            brotico(R.drawable.brotico_triste)
            iconoError.visibility = View.GONE
            titulo.poner(null)
            mensaje.poner(contexto.getString(estado.texto))
            botonReintentar.visibility = View.GONE
        }
        is EstadoVista.Error -> {
            progreso.visibility = View.GONE
            brotico(if (estado.error.sinConexion) R.drawable.brotico_triste else null)
            iconoError.visibility = if (estado.error.sinConexion) View.GONE else View.VISIBLE
            titulo.poner(contexto.getString(estado.titulo))
            mensaje.poner(estado.error.mensaje)
            botonReintentar.visibility = View.VISIBLE
            botonReintentar.setOnClickListener { alReintentar() }
        }
    }
}
