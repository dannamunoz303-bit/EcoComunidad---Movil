package co.ecocomunidad.app.data

import co.ecocomunidad.app.data.modelo.CambiarContrasenaPeticion
import co.ecocomunidad.app.data.modelo.LoginPeticion
import co.ecocomunidad.app.data.modelo.RegistroPeticion
import co.ecocomunidad.app.data.modelo.UsuarioActual
import co.ecocomunidad.app.data.modelo.UsuarioRegistrado
import co.ecocomunidad.app.data.remoto.AuthApi
import co.ecocomunidad.app.data.sesion.SesionStore
import co.ecocomunidad.app.data.sesion.UsuarioSesion
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

class AuthRepositoryRemoto(
    private val api: AuthApi,
    private val sesion: SesionStore,
    private val json: Json,
) : AuthRepository {

    override val usuario: Flow<UsuarioSesion?> = sesion.usuario

    /** auth/registro/. No guarda sesión: el ciudadano inicia sesión después. */
    override suspend fun registrar(peticion: RegistroPeticion): Resultado<UsuarioRegistrado> =
        llamar { api.registrar(peticion) }

    /** Inicia sesión y trae los datos del usuario (auth/login/ y luego auth/yo/). */
    override suspend fun login(correo: String, contrasena: String): Resultado<UsuarioActual> {
        val tokens = when (val r = llamar(enLogin = true) { api.login(LoginPeticion(correo, contrasena)) }) {
            is Resultado.Exito -> r.datos
            is Resultado.Fallo -> return r
        }
        sesion.guardarTokens(tokens.access, tokens.refresh)
        val usuario = usuarioActual()
        if (usuario is Resultado.Fallo) sesion.borrar()
        return usuario
    }

    /** El backend no tiene ruta de logout (el refresh no va a una lista negra): solo se borra la sesión local. */
    override suspend fun cerrarSesion() {
        sesion.borrar()
    }

    /** auth/cambiar-contrasena/ devuelve tokens nuevos, que reemplazan a los guardados. */
    override suspend fun cambiarContrasena(contrasenaActual: String, contrasenaNueva: String): Resultado<Unit> {
        val r = llamar { api.cambiarContrasena(CambiarContrasenaPeticion(contrasenaActual, contrasenaNueva)) }
        if (r is Resultado.Fallo) return r
        val tokens = (r as Resultado.Exito).datos
        sesion.guardarTokens(tokens.access, tokens.refresh)
        sesion.marcarContrasenaCambiada()
        return Resultado.Exito(Unit)
    }

    /** auth/yo/. Actualiza los datos guardados del usuario. */
    override suspend fun usuarioActual(): Resultado<UsuarioActual> {
        val r = llamar { api.yo() }
        if (r is Resultado.Exito) sesion.guardarUsuario(r.datos)
        return r
    }

    private suspend fun <T> llamar(enLogin: Boolean = false, bloque: suspend () -> T): Resultado<T> =
        llamarApi(json, enLogin, bloque)
}
