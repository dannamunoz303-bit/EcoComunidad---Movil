package co.ecocomunidad.app.data

import co.ecocomunidad.app.data.modelo.CodigoError
import java.time.OffsetDateTime

sealed interface Resultado<out T> {
    data class Exito<T>(val datos: T) : Resultado<T>
    data class Fallo(val error: ErrorAuth) : Resultado<Nothing>
}

/** Qué salió mal, con el [code] del backend cuando lo manda. */
sealed interface ErrorAuth {
    val detail: String?
    val code: String?

    /** 403 usuario_inactivo: el usuario fue dado de baja (RN-04). */
    data class Inactivo(override val detail: String?) : ErrorAuth {
        override val code = CodigoError.USUARIO_INACTIVO
    }

    /** 403 usuario_bloqueado: bloqueo temporal vigente hasta [hasta] (hora de Colombia). */
    data class Bloqueado(override val detail: String?, val hasta: OffsetDateTime?) : ErrorAuth {
        override val code = CodigoError.USUARIO_BLOQUEADO
    }

    /** 403 usuario_bloqueado_permanente. */
    data class BloqueadoPermanente(override val detail: String?) : ErrorAuth {
        override val code = CodigoError.USUARIO_BLOQUEADO_PERMANENTE
    }

    /** 403 debe_cambiar_contrasena: solo puede ver sus datos y cambiar la contraseña (RN-05). */
    data class DebeCambiarContrasena(override val detail: String?) : ErrorAuth {
        override val code = CodigoError.DEBE_CAMBIAR_CONTRASENA
    }

    /** 401 en el login: correo o contraseña incorrectos. */
    data class CredencialesInvalidas(override val detail: String?) : ErrorAuth {
        override val code: String? = null
    }

    /** 401 fuera del login: no se pudo renovar el token y la sesión se cerró. */
    data class SesionExpirada(override val detail: String?, override val code: String?) : ErrorAuth

    /** 400 con errores por campo, por ejemplo {"contrasena_actual": ["..."]}. */
    data class Validacion(val campos: Map<String, List<String>>) : ErrorAuth {
        override val detail = campos["detail"]?.firstOrNull() ?: campos.values.flatten().firstOrNull()
        override val code: String? = null
    }

    /** Cualquier otra respuesta de error del servidor. */
    data class Servidor(val codigoHttp: Int, override val detail: String?, override val code: String?) : ErrorAuth

    /** No se pudo hablar con el servidor. */
    data class SinConexion(val causa: Throwable) : ErrorAuth {
        override val detail: String? = null
        override val code: String? = null
    }
}
