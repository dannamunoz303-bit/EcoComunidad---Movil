package co.ecocomunidad.app

import android.app.Application
import android.content.Context
import co.ecocomunidad.app.data.AuthRepository
import co.ecocomunidad.app.data.AuthRepositoryRemoto
import co.ecocomunidad.app.data.remoto.ApiCliente
import co.ecocomunidad.app.data.sesion.EventosSesion
import co.ecocomunidad.app.data.sesion.SesionStore
import co.ecocomunidad.app.data.sesion.sesionDataStore

class EcoComunidadApp : Application() {

    lateinit var contenedor: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        contenedor = AppContainer(this)
    }
}

/** Crea una sola vez las piezas compartidas de la app (sin Hilt). */
class AppContainer(context: Context) {
    val eventosSesion = EventosSesion()
    val sesionStore = SesionStore(context.sesionDataStore)
    val apiCliente = ApiCliente(sesionStore, eventosSesion)
    val authRepository: AuthRepository =
        AuthRepositoryRemoto(apiCliente.authApi, sesionStore, apiCliente.json)
}
