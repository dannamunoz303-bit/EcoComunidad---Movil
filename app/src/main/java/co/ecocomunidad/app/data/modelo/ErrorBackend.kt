package co.ecocomunidad.app.data.modelo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Cuerpo de los errores con detail (sección 14):
 * {"detail": mensaje, "code": código, "bloqueado_hasta": fecha ISO en hora de Colombia o null}.
 * Con debe_cambiar_contrasena el backend no manda bloqueado_hasta.
 */
@Serializable
data class ErrorBackend(
    val detail: String? = null,
    val code: String? = null,
    @SerialName("bloqueado_hasta") val bloqueadoHasta: String? = null,
)

/** Valores de "code" en el 403 por estado del usuario o contraseña pendiente. */
object CodigoError {
    const val USUARIO_INACTIVO = "usuario_inactivo"
    const val USUARIO_BLOQUEADO = "usuario_bloqueado"
    const val USUARIO_BLOQUEADO_PERMANENTE = "usuario_bloqueado_permanente"
    const val DEBE_CAMBIAR_CONTRASENA = "debe_cambiar_contrasena"
}
