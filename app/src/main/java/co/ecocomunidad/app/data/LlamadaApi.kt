package co.ecocomunidad.app.data

import co.ecocomunidad.app.data.modelo.CodigoError
import co.ecocomunidad.app.data.modelo.ErrorBackend
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.HttpException
import java.io.IOException
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Ejecuta una llamada a la API y la convierte en [Resultado], con el error del backend
 * traducido a [ErrorAuth]. La comparten todos los repositorios remotos.
 *
 * [enLogin]: un 401 en el login son credenciales malas; fuera de él, la sesión se cerró.
 */
internal suspend fun <T> llamarApi(json: Json, enLogin: Boolean = false, bloque: suspend () -> T): Resultado<T> =
    try {
        Resultado.Exito(bloque())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        Resultado.Fallo(aError(json, e.code(), e.response()?.errorBody()?.string(), enLogin))
    } catch (e: IOException) {
        Resultado.Fallo(ErrorAuth.SinConexion(e))
    }

private fun aError(json: Json, codigoHttp: Int, cuerpo: String?, enLogin: Boolean): ErrorAuth {
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
        codigoHttp == 400 -> ErrorAuth.Validacion(camposConError(json, cuerpo))
        else -> ErrorAuth.Servidor(codigoHttp, detail, error?.code)
    }
}

/** DRF manda {"campo": ["mensaje", ...]} o {"campo": "mensaje"}. */
private fun camposConError(json: Json, cuerpo: String?): Map<String, List<String>> {
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
