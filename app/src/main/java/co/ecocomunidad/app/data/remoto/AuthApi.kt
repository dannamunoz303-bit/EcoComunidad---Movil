package co.ecocomunidad.app.data.remoto

import co.ecocomunidad.app.data.modelo.CambiarContrasenaPeticion
import co.ecocomunidad.app.data.modelo.LoginPeticion
import co.ecocomunidad.app.data.modelo.LoginRespuesta
import co.ecocomunidad.app.data.modelo.RefreshPeticion
import co.ecocomunidad.app.data.modelo.RefreshRespuesta
import co.ecocomunidad.app.data.modelo.Tokens
import co.ecocomunidad.app.data.modelo.UsuarioActual
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

/** Rutas de usuarios/urls.py, montadas en api/auth/ (config/urls.py). */
interface AuthApi {

    @Headers(SIN_AUTORIZACION)
    @POST("auth/login/")
    suspend fun login(@Body peticion: LoginPeticion): LoginRespuesta

    /** Síncrona porque la llama el Authenticator de OkHttp desde su propio hilo. */
    @Headers(SIN_AUTORIZACION)
    @POST("auth/refresh/")
    fun refrescar(@Body peticion: RefreshPeticion): Call<RefreshRespuesta>

    @GET("auth/yo/")
    suspend fun yo(): UsuarioActual

    @POST("auth/cambiar-contrasena/")
    suspend fun cambiarContrasena(@Body peticion: CambiarContrasenaPeticion): Tokens
}
