package co.ecocomunidad.app.data.modelo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Campos de la sección 4.8 con los nombres que usa la API (novedades/serializers.py del backend):
// reporte_id llega como "reporte" y tarea_id como "tarea".

/** Tipo de novedad (sección 4.8). El ciudadano solo crea queja o incidente (RN-18). */
@Serializable
enum class TipoNovedad {
    @SerialName("ayuda") AYUDA,
    @SerialName("queja") QUEJA,
    @SerialName("incidente") INCIDENTE,
}

/** Estado de la novedad (sección 6.3): abierta → atendida cuando el administrador responde. */
@Serializable
enum class EstadoNovedad {
    @SerialName("abierta") ABIERTA,
    @SerialName("atendida") ATENDIDA,
}

/** RN-18: el ciudadano crea novedades sobre su reporte solo en estos estados. */
val ESTADOS_REPORTE_CON_NOVEDAD = setOf(EstadoReporte.ASIGNADO, EstadoReporte.EN_PROCESO, EstadoReporte.RESUELTO)

/** NovedadSerializer. [reporteTitulo] lo agrega el backend para no pedir el reporte aparte. */
@Serializable
data class Novedad(
    val id: Int,
    @SerialName("reporte") val reporteId: Int,
    @SerialName("reporte_titulo") val reporteTitulo: String? = null,
    @SerialName("tarea") val tareaId: Int? = null,
    @SerialName("creado_por") val creadoPor: UsuarioResumen? = null,
    val tipo: TipoNovedad,
    val descripcion: String,
    val estado: EstadoNovedad,
    /** Respuesta del administrador; null mientras está abierta. */
    val respuesta: String? = null,
    val fecha: String,
)

/** Cuerpo de POST novedades/ (CrearNovedadSerializer). [tareaId] null no se envía. */
@Serializable
data class NuevaNovedad(
    @SerialName("reporte") val reporteId: Int,
    val tipo: TipoNovedad,
    val descripcion: String,
    @SerialName("tarea") val tareaId: Int? = null,
)
