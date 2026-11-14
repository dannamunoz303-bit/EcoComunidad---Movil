package co.ecocomunidad.app.data.modelo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

// Nombres de campo tomados de reportes/serializers.py y tareas/serializers.py del backend.
// Lo que la app no usa (eliminado) se ignora al leer.

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

/** Urgencia del reporte (RN-25). La pone el backend con la categoría; el ciudadano no la elige. */
@Serializable
enum class Urgencia {
    @SerialName("alta") ALTA,
    @SerialName("media") MEDIA,
    @SerialName("baja") BAJA,
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
    /** Id de uno de los 42 municipios (RN-23). */
    val municipio: Int,
    val vereda: String?,
    /** RN-27: opcional. Solo la ve el administrador; el backend nunca la devuelve al ciudadano. */
    val causanteCedula: String? = null,
    val latitud: Double,
    val longitud: Double,
    val fotos: List<File>,
)

/**
 * Campos de ReporteEditarSerializer que cambiaron (PATCH reportes/{id}/, RN-07). null = sin cambio.
 * Para borrar las referencias de ubicación o la vereda se manda texto vacío (los campos permiten blank).
 * El municipio se puede cambiar pero no quitar (RN-23).
 * La latitud y la longitud van siempre juntas.
 */
data class CambiosReporte(
    val categoria: Int? = null,
    val titulo: String? = null,
    val descripcion: String? = null,
    val direccion: String? = null,
    val descripcionUbicacion: String? = null,
    val municipio: Int? = null,
    val vereda: String? = null,
    /** RN-27: "" la quita. */
    val causanteCedula: String? = null,
    val latitud: Double? = null,
    val longitud: Double? = null,
) {
    val vacio: Boolean
        get() = this == CambiosReporte()
}

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
 * [municipio] y [zona] llegan null en los reportes creados antes de RN-23; la zona sale del municipio.
 * causante_cedula y causante no están a propósito: solo los recibe el administrador (RN-02, RN-27).
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
    /** MunicipioResumenSerializer: solo id y nombre (la zona va aparte). */
    val municipio: Municipio? = null,
    val vereda: String? = null,
    val zona: Zona? = null,
    /** DecimalField: DRF las manda como texto ("4.60971000"). Sirven para poner el pin al editar. */
    val latitud: String? = null,
    val longitud: String? = null,
    val estado: EstadoReporte,
    val urgencia: Urgencia? = null,
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
