package co.ecocomunidad.app.data.modelo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Valores de la tabla rol (sección 4.1). */
@Serializable
enum class Rol {
    @SerialName("ciudadano") CIUDADANO,
    @SerialName("gestor") GESTOR,
    @SerialName("administrador") ADMINISTRADOR,
}

/** Estado del usuario (sección 6.4). */
@Serializable
enum class EstadoUsuario {
    @SerialName("activo") ACTIVO,
    @SerialName("inactivo") INACTIVO,
    @SerialName("bloqueado") BLOQUEADO,
}

/** GET auth/yo/ (UsuarioActualSerializer). No incluye cédula (RN-02) ni bloqueado_hasta. */
@Serializable
data class UsuarioActual(
    val id: Int,
    val nombre: String,
    val apellido: String,
    val correo: String,
    val telefono: String? = null,
    val rol: Rol,
    val estado: EstadoUsuario,
    @SerialName("fecha_registro") val fechaRegistro: String,
    @SerialName("debe_cambiar_contrasena") val debeCambiarContrasena: Boolean,
)
