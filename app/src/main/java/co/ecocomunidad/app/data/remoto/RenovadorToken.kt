package co.ecocomunidad.app.data.remoto

import co.ecocomunidad.app.data.modelo.ErrorBackend
import co.ecocomunidad.app.data.modelo.RefreshPeticion
import co.ecocomunidad.app.data.sesion.EventosSesion
import co.ecocomunidad.app.data.sesion.SesionStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException

/**
 * Ante un 401 intenta una sola vez renovar el access con el refresh (auth/refresh/).
 * Si el backend rechaza el refresh (401 vencido o 403 por estado del usuario, sección 14),
 * borra la sesión y emite [EventosSesion.sesionCerrada].
 */
class RenovadorToken(
    private val sesion: SesionStore,
    private val authApi: () -> AuthApi,
    private val eventos: EventosSesion,
    private val json: Json,
) : Authenticator {

    private val candado = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Sin token no hay nada que renovar (login con credenciales malas, refresh rechazado)
        val tokenUsado = response.request.header("Authorization")?.removePrefix("Bearer ") ?: return null

        // Ya se reintentó con un token nuevo y volvió a dar 401
        if (response.priorResponse != null) {
            cerrarSesion(null)
            return null
        }

        synchronized(candado) {
            val access = runBlocking { sesion.access() } ?: return null
            // Otra petición ya lo renovó mientras esta esperaba
            if (access != tokenUsado) return response.request.conToken(access)

            val refresh = runBlocking { sesion.refresh() }
            if (refresh == null) {
                cerrarSesion(null)
                return null
            }

            val respuesta = try {
                authApi().refrescar(RefreshPeticion(refresh)).execute()
            } catch (e: IOException) {
                // Sin conexión: no se cierra la sesión, la petición original falla con su 401
                return null
            }

            val nuevo = respuesta.body()?.access
            if (!respuesta.isSuccessful || nuevo == null) {
                cerrarSesion(leerError(respuesta.errorBody()?.string()))
                return null
            }
            runBlocking { sesion.guardarAccess(nuevo) }
            return response.request.conToken(nuevo)
        }
    }

    private fun cerrarSesion(error: ErrorBackend?) {
        runBlocking { sesion.borrar() }
        eventos.emitirSesionCerrada(error)
    }

    private fun leerError(cuerpo: String?): ErrorBackend? =
        cuerpo?.let { runCatching { json.decodeFromString<ErrorBackend>(it) }.getOrNull() }

    private fun Request.conToken(access: String): Request =
        newBuilder().header("Authorization", "Bearer $access").build()
}
