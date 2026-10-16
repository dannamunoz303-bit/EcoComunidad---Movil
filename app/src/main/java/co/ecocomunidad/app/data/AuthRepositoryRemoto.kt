package co.ecocomunidad.app.data

import co.ecocomunidad.app.data.modelo.CambiarContrasenaPeticion
import co.ecocomunidad.app.data.modelo.CodigoError
import co.ecocomunidad.app.data.modelo.ErrorBackend
import co.ecocomunidad.app.data.modelo.LoginPeticion
import co.ecocomunidad.app.data.modelo.UsuarioActual
import co.ecocomunidad.app.data.remoto.AuthApi
import co.ecocomunidad.app.data.sesion.SesionStore
import co.ecocomunidad.app.data.sesion.UsuarioSesion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.HttpException
import java.io.IOException
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

class AuthRepositoryRemoto(
    private val api: AuthApi,
    private val sesion: SesionStore,
    private val json: Json,
) : AuthRepository {

    override val usuario: Flow<UsuarioSesion?> = sesion.usuario

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
        try {
            Resultado.Exito(bloque())
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            Resultado.Fallo(aError(e.code(), e.response()?.errorBody()?.string(), enLogin))
        } catch (e: IOException) {
            Resultado.Fallo(ErrorAuth.SinConexion(e))
        }

    private fun aError(codigoHttp: Int, cuerpo: String?, enLogin: Boolean): ErrorAuth {
        val error = cuerpo?.let { runCatching { json.decodeFromString<ErrorBackend>(it) }.getOrNull() }
        val detail = error?.detail
        return when {
            codigoHttp == 403 && error?.code == CodigoError.USUARIO_INACTIVO -> ErrorAuth.Inactivo(detail)
            codigoHttp == 403 && error?.code == CodigoError.USUARIO_BLOQUEADO ->
                ErrorAuth.Bloqueado(detail, error.bloqueadoHasta?.aFecha())
            codigoHttp == 403 && error?.code == CodigoError.USUARIO_BLOQUEADO_PERMANENTE ->
                ErrorAuth.BloqueadoPermanente(detail)
            codigoHttp == 403 && error?.code == CodigoError.DEBE_CAMBIAR_CONTRASENA ->
                ErrorAuth.DebeCambiarContrasena(detail)
            codigoHttp == 401 && enLogin -> ErrorAuth.CredencialesInvalidas(detail)
            codigoHttp == 401 -> ErrorAuth.SesionExpirada(detail, error?.code)
            codigoHttp == 400 -> ErrorAuth.Validacion(camposConError(cuerpo))
            else -> ErrorAuth.Servidor(codigoHttp, detail, error?.code)
        }
    }

    /** DRF manda {"campo": ["mensaje", ...]} o {"campo": "mensaje"}. */
    private fun camposConError(cuerpo: String?): Map<String, List<String>> {
        val objeto = cuerpo?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
            ?: return emptyMap()
        return objeto.mapValues { (_, valor) ->
            when (valor) {
                is JsonArray -> valor.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                is JsonPrimitive -> listOfNotNull(valor.contentOrNull)
                else -> emptyList()
            }
        }
    }

    private fun String.aFecha(): OffsetDateTime? =
        try {
            OffsetDateTime.parse(this)
        } catch (e: DateTimeParseException) {
            null
        }
}
