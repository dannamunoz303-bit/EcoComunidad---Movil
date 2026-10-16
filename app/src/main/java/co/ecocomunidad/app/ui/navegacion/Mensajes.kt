package co.ecocomunidad.app.ui.navegacion

import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.modelo.CodigoError
import co.ecocomunidad.app.data.modelo.ErrorBackend
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object Mensajes {
    const val PANEL_WEB = "El administrador usa el panel web."
    const val SESION_TERMINADA = "Tu sesión terminó. Inicia sesión de nuevo."
}

private val zonaColombia = ZoneId.of("America/Bogota")
private val formatoFecha =
    DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy 'a las' h:mm a", Locale.forLanguageTag("es-CO"))

/** Ej.: "20 de octubre de 2026 a las 8:30 a. m.", siempre en hora de Colombia. */
fun formatearFechaColombia(fecha: OffsetDateTime): String =
    fecha.atZoneSameInstant(zonaColombia).format(formatoFecha)

/** Texto para el usuario según el error del backend. Sobrio: sin Brotico (marca.md). */
fun mensajeDeError(error: ErrorAuth): String = when (error) {
    is ErrorAuth.Inactivo -> "Tu cuenta está inactiva y no puede iniciar sesión."
    is ErrorAuth.Bloqueado -> error.hasta
        ?.let { "Tu cuenta está bloqueada hasta el ${formatearFechaColombia(it)}." }
        ?: "Tu cuenta está bloqueada por ahora."
    is ErrorAuth.BloqueadoPermanente -> "Tu cuenta está bloqueada de forma permanente."
    is ErrorAuth.DebeCambiarContrasena -> "Debes cambiar tu contraseña antes de continuar."
    is ErrorAuth.CredencialesInvalidas -> "El correo o la contraseña no son correctos."
    is ErrorAuth.SesionExpirada -> Mensajes.SESION_TERMINADA
    is ErrorAuth.Validacion -> error.detail ?: "Revisa los datos e inténtalo de nuevo."
    // En los 4xx (por ejemplo 403 "Solo el ciudadano que creó el reporte…") el backend explica qué pasó
    is ErrorAuth.Servidor -> error.detail?.takeIf { error.codigoHttp in 400..499 && it.isNotBlank() }
        ?: "El servidor no respondió bien. Inténtalo en unos minutos."
    is ErrorAuth.SinConexion -> "No hay conexión con el servidor. Revisa tu internet e inténtalo de nuevo."
}

/** Motivo que se muestra en el login cuando la sesión se cerró sola (EventosSesion). */
fun mensajeDeCierre(error: ErrorBackend?): String = when (error?.code) {
    CodigoError.USUARIO_INACTIVO -> mensajeDeError(ErrorAuth.Inactivo(error.detail))
    CodigoError.USUARIO_BLOQUEADO ->
        mensajeDeError(ErrorAuth.Bloqueado(error.detail, error.bloqueadoHasta?.aFecha()))
    CodigoError.USUARIO_BLOQUEADO_PERMANENTE -> mensajeDeError(ErrorAuth.BloqueadoPermanente(error.detail))
    else -> Mensajes.SESION_TERMINADA
}

private fun String.aFecha(): OffsetDateTime? =
    try {
        OffsetDateTime.parse(this)
    } catch (e: DateTimeParseException) {
        null
    }
