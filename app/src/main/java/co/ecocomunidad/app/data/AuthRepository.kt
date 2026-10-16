package co.ecocomunidad.app.data

import co.ecocomunidad.app.data.modelo.UsuarioActual
import co.ecocomunidad.app.data.sesion.UsuarioSesion
import kotlinx.coroutines.flow.Flow

/** Sesión del usuario. La implementación real es [AuthRepositoryRemoto]. */
interface AuthRepository {

    /** Usuario guardado con la sesión, o null si no hay sesión. */
    val usuario: Flow<UsuarioSesion?>

    suspend fun login(correo: String, contrasena: String): Resultado<UsuarioActual>

    suspend fun cerrarSesion()

    suspend fun cambiarContrasena(contrasenaActual: String, contrasenaNueva: String): Resultado<Unit>

    suspend fun usuarioActual(): Resultado<UsuarioActual>
}
