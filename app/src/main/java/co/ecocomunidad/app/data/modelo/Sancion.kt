package co.ecocomunidad.app.data.modelo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Tipo de sanción (sección 4.9, RN-20). */
@Serializable
enum class TipoSancion {
    @SerialName("advertencia") ADVERTENCIA,
    @SerialName("bloqueo_temporal") BLOQUEO_TEMPORAL,
    @SerialName("bloqueo_permanente") BLOQUEO_PERMANENTE,
}

/**
 * SancionCiudadanoSerializer (GET sanciones/mias/): sin el administrador (RN-21).
 * reporte_id llega como "reporte", sin el título. [fechaFin] es null en advertencia y en el
 * permanente vigente; al levantar un bloqueo el backend la pone en la fecha en que se levantó.
 */
@Serializable
data class Sancion(
    val id: Int,
    val tipo: TipoSancion,
    val motivo: String,
    @SerialName("fecha_inicio") val fechaInicio: String,
    @SerialName("fecha_fin") val fechaFin: String? = null,
    @SerialName("reporte") val reporteId: Int? = null,
)
