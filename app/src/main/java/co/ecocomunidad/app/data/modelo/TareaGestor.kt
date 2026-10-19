package co.ecocomunidad.app.data.modelo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Nombres tomados de tareas/serializers.py del backend (TareaGestorSerializer y ReporteBasicoSerializer).
// Las acciones de la tarea (accion_tarea, sección 4.6) usan [Accion], el mismo AccionSerializer,
// con sus fotos de evidencia (sección 4.7, tipo evidencia).

/** Estados de la tarea en los que el gestor todavía trabaja (asignada o en_proceso). RN-14: y registra acciones. */
val ESTADOS_TAREA_ACTIVOS = setOf(EstadoTarea.ASIGNADA, EstadoTarea.EN_PROCESO)

/** MAXIMO_EVIDENCIAS_ACCION del backend (tareas/servicios.py, RN-15): cuenta las que la acción ya tiene. */
const val MAXIMO_EVIDENCIAS = 5

/** Cuerpo de POST tareas/{id}/acciones/ (AccionSerializer). Las fotos van aparte, en acciones/{id}/fotos/. */
@Serializable
data class NuevaAccion(val descripcion: String)

/** ReporteBasicoSerializer: lo del reporte que llega con la tarea. Sin fotos ni descripción del ciudadano. */
@Serializable
data class ReporteBasico(
    val id: Int,
    val titulo: String,
    val categoria: String,
    val direccion: String,
    @SerialName("descripcion_ubicacion") val descripcionUbicacion: String? = null,
    /** DecimalField: DRF las manda como texto ("4.60971000"). */
    val latitud: String? = null,
    val longitud: String? = null,
    val estado: EstadoReporte,
)

/** TareaGestorSerializer (GET tareas/mias/ y las respuestas de iniciar y terminar). Sección 4.5. */
@Serializable
data class TareaGestor(
    val id: Int,
    val reporte: ReporteBasico,
    val estado: EstadoTarea,
    @SerialName("fecha_asignacion") val fechaAsignacion: String,
    /** Se llena al pasar a terminada (RN-13). */
    @SerialName("fecha_fin") val fechaFin: String? = null,
)
