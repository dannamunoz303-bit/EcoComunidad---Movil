package co.ecocomunidad.app.data.modelo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Nombres de campo tomados de usuarios/serializers.py y usuarios/views.py del backend

/** POST auth/login/. El backend usa "correo" como USERNAME_FIELD y "password" de simplejwt. */
@Serializable
data class LoginPeticion(
    val correo: String,
    val password: String,
)

@Serializable
data class LoginRespuesta(
    val access: String,
    val refresh: String,
    @SerialName("debe_cambiar_contrasena") val debeCambiarContrasena: Boolean,
)

/** POST auth/refresh/. Sin rotación de refresh: solo devuelve un access nuevo. */
@Serializable
data class RefreshPeticion(
    val refresh: String,
)

@Serializable
data class RefreshRespuesta(
    val access: String,
)

/** Respuesta de POST auth/cambiar-contrasena/. */
@Serializable
data class Tokens(
    val access: String,
    val refresh: String,
)

@Serializable
data class CambiarContrasenaPeticion(
    @SerialName("contrasena_actual") val contrasenaActual: String,
    @SerialName("contrasena_nueva") val contrasenaNueva: String,
)
