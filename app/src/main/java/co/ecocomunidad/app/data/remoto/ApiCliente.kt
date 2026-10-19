package co.ecocomunidad.app.data.remoto

import co.ecocomunidad.app.BuildConfig
import co.ecocomunidad.app.data.sesion.EventosSesion
import co.ecocomunidad.app.data.sesion.SesionStore
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

/** Arma OkHttp y Retrofit con el token, la renovación y, en debug, el log de peticiones. */
class ApiCliente(
    sesion: SesionStore,
    eventos: EventosSesion,
    val urlBase: String = BuildConfig.API_URL,
    registrarPeticiones: Boolean = BuildConfig.DEBUG,
) {
    val json = Json { ignoreUnknownKeys = true }

    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(AutorizacionInterceptor(sesion))
        .authenticator(RenovadorToken(sesion, { authApi }, eventos, json))
        .apply {
            if (registrarPeticiones) {
                addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
            }
        }
        .build()

    val retrofit: Retrofit = Retrofit.Builder()
        // Retrofit exige la barra final para resolver rutas relativas
        .baseUrl(urlBase.trimEnd('/') + "/")
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val authApi: AuthApi by lazy { retrofit.create() }

    val reportesApi: ReportesApi by lazy { retrofit.create() }

    val novedadesApi: NovedadesApi by lazy { retrofit.create() }

    val notificacionesApi: NotificacionesApi by lazy { retrofit.create() }

    val sancionesApi: SancionesApi by lazy { retrofit.create() }

    val tareasApi: TareasApi by lazy { retrofit.create() }
}
