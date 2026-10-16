package co.ecocomunidad.app.data.modelo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

// Nombres de campo tomados de reportes/serializers.py y tareas/serializers.py del backend.
// Lo que la app no usa (latitud, longitud, eliminado) se ignora al leer.

/** Estado del reporte (sección 6.1). */
@Serializable
enum class EstadoReporte {
    @SerialName("pendiente") PENDIENTE,
    @SerialName("aprobado") APROBADO,
    @SerialName("asignado") ASIGNADO,
    @SerialName("en_proceso") EN_PROCESO,
    @SerialName("resuelto") RESUELTO,
    @SerialName("rechazado") RECHAZADO,
}

/** Estado de la tarea (sección 6.2). */
@Serializable
enum class EstadoTarea {
    @SerialName("asignada") ASIGNADA,
    @SerialName("en_proceso") EN_PROCESO,
    @SerialName("terminada") TERMINADA,
    @SerialName("cancelada") CANCELADA,
}

/** CategoriaSerializer. Al ciudadano el backend solo le devuelve las activas (sección 4.3). */
@Serializable
data class Categoria(
    val id: Int,
    val nombre: String,
    val descripcion: String? = null,
    val activa: Boolean = true,
)

/**
 * Datos para POST reportes/ (ReporteCrearSerializer, RN-06). Se envía en multipart en una sola
 * petición: si el backend rechaza algo, no se crea nada. [fotos] son archivos JPEG ya reducidos.
 */
data class NuevoReporte(
    val categoria: Int,
    val titulo: String,
    val descripcion: String,
    val direccion: String,
    val descripcionUbicacion: String?,
    val latitud: Double,
    val longitud: Double,
    val fotos: List<File>,
)

/** UsuarioResumenSerializer: sin cédula (RN-02). */
@Serializable
data class UsuarioResumen(
    val id: Int,
    val nombre: String,
    val apellido: String,
)

/** FotoSerializer. [url] llega absoluta en producción (Cloudinary); el repositorio resuelve las relativas. */
@Serializable
data class Foto(
    val id: Int,
    val url: String,
    val fecha: String,
)

/**
 * ReporteSerializer. [ciudadano] llega null cuando quien mira no puede ver al creador (RN-10).
 * [fotos] son solo las tipo reporte; las evidencias vienen en las acciones.
 */
@Serializable
data class Reporte(
    val id: Int,
    val ciudadano: UsuarioResumen? = null,
    @SerialName("categoria_id") val categoriaId: Int,
    val categoria: String,
    val titulo: String,
    val descripcion: String,
    val direccion: String,
    @SerialName("descripcion_ubicacion") val descripcionUbicacion: String? = null,
    val estado: EstadoReporte,
    @SerialName("motivo_rechazo") val motivoRechazo: String? = null,
    @SerialName("fecha_creacion") val fechaCreacion: String,
    @SerialName("fecha_actualizacion") val fechaActualizacion: String,
    @SerialName("fecha_revision") val fechaRevision: String? = null,
    val fotos: List<Foto> = emptyList(),
)

/** AccionSerializer: lo que hizo el gestor y sus fotos de evidencia. */
@Serializable
data class Accion(
    val id: Int,
    val descripcion: String,
    val fecha: String,
    val fotos: List<Foto> = emptyList(),
)

/** TareaConAccionesSerializer (GET reportes/{id}/tareas/, RN-14). */
@Serializable
data class Tarea(
    val id: Int,
    val gestor: UsuarioResumen? = null,
    val estado: EstadoTarea,
    @SerialName("fecha_asignacion") val fechaAsignacion: String,
    @SerialName("fecha_fin") val fechaFin: String? = null,
    val acciones: List<Accion> = emptyList(),
)
