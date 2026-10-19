package co.ecocomunidad.app

import android.app.Application
import android.content.Context
import co.ecocomunidad.app.data.AuthRepository
import co.ecocomunidad.app.data.AuthRepositoryRemoto
import co.ecocomunidad.app.data.NotificacionesRepository
import co.ecocomunidad.app.data.NotificacionesRepositoryRemoto
import co.ecocomunidad.app.data.NovedadesRepository
import co.ecocomunidad.app.data.NovedadesRepositoryRemoto
import co.ecocomunidad.app.data.ReportesRepository
import co.ecocomunidad.app.data.ReportesRepositoryRemoto
import co.ecocomunidad.app.data.SancionesRepository
import co.ecocomunidad.app.data.SancionesRepositoryRemoto
import co.ecocomunidad.app.data.remoto.ApiCliente
import co.ecocomunidad.app.data.sesion.EventosSesion
import co.ecocomunidad.app.data.sesion.SesionStore
import co.ecocomunidad.app.data.sesion.sesionDataStore
import org.osmdroid.config.Configuration
import java.io.File

class EcoComunidadApp : Application() {

    lateinit var contenedor: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        contenedor = AppContainer(this)
        configurarMapa()
    }

    /**
     * osmdroid (OpenStreetMap, sin clave). La política de uso de OSM exige un user agent propio.
     * Los mapas se guardan en la caché de la app, así no hace falta permiso de almacenamiento.
     */
    private fun configurarMapa() {
        val base = File(cacheDir, "osmdroid")
        Configuration.getInstance().apply {
            userAgentValue = "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}"
            osmdroidBasePath = base
            osmdroidTileCache = File(base, "teselas")
        }
    }
}

/** Crea una sola vez las piezas compartidas de la app (sin Hilt). */
class AppContainer(context: Context) {
    val eventosSesion = EventosSesion()
    val sesionStore = SesionStore(context.sesionDataStore)
    val apiCliente = ApiCliente(sesionStore, eventosSesion)
    val authRepository: AuthRepository =
        AuthRepositoryRemoto(apiCliente.authApi, sesionStore, apiCliente.json)
    val reportesRepository: ReportesRepository =
        ReportesRepositoryRemoto(apiCliente.reportesApi, apiCliente.json, apiCliente.urlBase)
    val novedadesRepository: NovedadesRepository =
        NovedadesRepositoryRemoto(apiCliente.novedadesApi, apiCliente.json)
    val notificacionesRepository: NotificacionesRepository =
        NotificacionesRepositoryRemoto(apiCliente.notificacionesApi, apiCliente.json)
    val sancionesRepository: SancionesRepository =
        SancionesRepositoryRemoto(apiCliente.sancionesApi, apiCliente.json)
}
