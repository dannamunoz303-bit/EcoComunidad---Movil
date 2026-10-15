package co.ecocomunidad.app.data.remoto

import co.ecocomunidad.app.data.sesion.SesionStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/** Marca las peticiones que no llevan token (login y refresh). El interceptor la quita antes de enviar. */
internal const val CABECERA_SIN_AUTORIZACION = "Sin-Autorizacion"
internal const val SIN_AUTORIZACION = "$CABECERA_SIN_AUTORIZACION: true"

/** Agrega "Authorization: Bearer <access>" si hay sesión. */
class AutorizacionInterceptor(private val sesion: SesionStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val peticion = chain.request()
        if (peticion.header(CABECERA_SIN_AUTORIZACION) != null) {
            return chain.proceed(peticion.newBuilder().removeHeader(CABECERA_SIN_AUTORIZACION).build())
        }
        val access = runBlocking { sesion.access() } ?: return chain.proceed(peticion)
        return chain.proceed(peticion.newBuilder().header("Authorization", "Bearer $access").build())
    }
}
