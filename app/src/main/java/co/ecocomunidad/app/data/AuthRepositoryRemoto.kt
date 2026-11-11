package co.ecocomunidad.app.data

import co.ecocomunidad.app.data.modelo.CambiarContrasenaPeticion
import co.ecocomunidad.app.data.modelo.DispositivoPeticion
import co.ecocomunidad.app.data.modelo.LoginPeticion
import co.ecocomunidad.app.data.modelo.RegistroPeticion
import co.ecocomunidad.app.data.modelo.Rol
import co.ecocomunidad.app.data.modelo.UsuarioActual
import co.ecocomunidad.app.data.modelo.UsuarioRegistrado
import co.ecocomunidad.app.data.push.TokenPush
import co.ecocomunidad.app.data.remoto.AuthApi
import co.ecocomunidad.app.data.remoto.DispositivosApi
import co.ecocomunidad.app.data.sesion.SesionStore
import co.ecocomunidad.app.data.sesion.UsuarioSesion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

/**
 * [alcance] vive lo que la app: ahí se registra el celular tras el login, para no hacer esperar al
 * usuario y para que no se cancele cuando se cierra la pantalla del login.
 */
class AuthRepositoryRemoto(
    private val api: AuthApi,
    private val sesion: SesionStore,
    private val json: Json,
    private val dispositivos: DispositivosApi,
    private val tokenPush: TokenPush,
    private val alcance: CoroutineScope,
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
        when {
            usuario is Resultado.Fallo -> sesion.borrar()
            // El administrador no usa la app: su sesión se cierra enseguida
            (usuario as Resultado.Exito).datos.rol != Rol.ADMINISTRADOR -> alcance.launch { registrarDispositivo() }
        }
        return usuario
    }

    /**
     * El backend no tiene ruta de logout (el refresh no va a una lista negra): se quita el celular
     * de los avisos, con el token de la sesión, y se borra la sesión local.
     */
    override suspend fun cerrarSesion() {
        withTimeoutOrNull(LIMITE_QUITAR_DISPOSITIVO) {
            if (sesion.access() == null) return@withTimeoutOrNull
            val token = tokenPush.token() ?: return@withTimeoutOrNull
            llamar { dispositivos.quitar(DispositivoPeticion(token)) }
        }
        sesion.borrar()
    }

    /** notificaciones/dispositivos/. El resultado se ignora: es un aviso extra (RN-26). */
    override suspend fun registrarDispositivo(token: String?) {
        if (sesion.access() == null) return
        val codigo = token ?: tokenPush.token() ?: return
        llamar { dispositivos.registrar(DispositivoPeticion(codigo)) }
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

    private companion object {
        /** Cerrar sesión no espera más que esto a que el backend quite el celular. */
        const val LIMITE_QUITAR_DISPOSITIVO = 5_000L
    }
}
