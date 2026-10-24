package co.ecocomunidad.app.data.modelo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Códigos de tipo de la sección 9 que la app trata aparte. El backend puede mandar
 * otros (por ejemplo bloqueo_levantado o contrasena_restablecida): por eso [Notificacion.tipo] es texto.
 */
object TipoNotificacion {
    const val REPORTE_RECHAZADO = "reporte_rechazado"
    const val REPORTE_APROBADO = "reporte_aprobado"
    const val REPORTE_ASIGNADO = "reporte_asignado"
    const val REPORTE_ESTADO = "reporte_estado"
    const val NOVEDAD_RESPONDIDA = "novedad_respondida"
    const val SANCION = "sancion"
    const val BLOQUEO_LEVANTADO = "bloqueo_levantado"
    const val REPORTE_ELIMINADO = "reporte_eliminado"
    const val TAREA_ASIGNADA = "tarea_asignada"
    const val TAREA_CANCELADA = "tarea_cancelada"
}

/** NotificacionSerializer (sección 4.10). reporte_id llega como "reporte". */
@Serializable
data class Notificacion(
    val id: Int,
    val tipo: String,
    val mensaje: String,
    val leida: Boolean,
    val fecha: String,
    @SerialName("reporte") val reporteId: Int? = null,
)

/** GET notificaciones/no-leidas/ (TotalSerializer). */
@Serializable
data class TotalNoLeidas(val total: Int)
