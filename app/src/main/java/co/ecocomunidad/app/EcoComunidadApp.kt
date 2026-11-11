package co.ecocomunidad.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.provider.Settings
import androidx.core.content.getSystemService
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
import co.ecocomunidad.app.data.TareasRepository
import co.ecocomunidad.app.data.TareasRepositoryRemoto
import co.ecocomunidad.app.data.TerritorioRepository
import co.ecocomunidad.app.data.TerritorioRepositoryRemoto
import co.ecocomunidad.app.data.push.CanalesPush
import co.ecocomunidad.app.data.push.EventosPush
import co.ecocomunidad.app.data.push.TokenFirebase
import co.ecocomunidad.app.data.remoto.ApiCliente
import co.ecocomunidad.app.data.sesion.EventosSesion
import co.ecocomunidad.app.data.sesion.SesionStore
import co.ecocomunidad.app.data.sesion.sesionDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.osmdroid.config.Configuration
import java.io.File

class EcoComunidadApp : Application() {

    lateinit var contenedor: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        contenedor = AppContainer(this)
        configurarMapa()
        crearCanalesDeAvisos()
    }

    /**
     * Canales de los avisos push (RN-26), con los ids que manda el backend (notificaciones/push.py).
     * Crearlos otra vez no cambia nada: después el usuario manda en su configuración.
     */
    private fun crearCanalesDeAvisos() {
        val tareas = NotificationChannel(
            CanalesPush.TAREAS_ASIGNADAS,
            getString(R.string.canal_tareas_asignadas),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.canal_tareas_asignadas_descripcion)
            // El sonido de alarma del teléfono, por el canal de alarmas (suena aunque el timbre esté bajo)
            val alarma = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: Settings.System.DEFAULT_NOTIFICATION_URI
            setSound(
                alarma,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
            enableLights(true)
        }
        val avisos = NotificationChannel(
            CanalesPush.AVISOS,
            getString(R.string.canal_avisos),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = getString(R.string.canal_avisos_descripcion) }

        getSystemService<NotificationManager>()?.createNotificationChannels(listOf(tareas, avisos))
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
    /** Trabajo que no debe cancelarse al cerrar una pantalla (registrar el celular para los avisos). */
    val alcanceApp = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val eventosSesion = EventosSesion()
    val eventosPush = EventosPush()
    val sesionStore = SesionStore(context.sesionDataStore)
    val apiCliente = ApiCliente(sesionStore, eventosSesion)
    val authRepository: AuthRepository = AuthRepositoryRemoto(
        apiCliente.authApi, sesionStore, apiCliente.json, apiCliente.dispositivosApi, TokenFirebase(), alcanceApp,
    )
    val reportesRepository: ReportesRepository =
        ReportesRepositoryRemoto(apiCliente.reportesApi, apiCliente.json, apiCliente.urlBase)
    val novedadesRepository: NovedadesRepository =
        NovedadesRepositoryRemoto(apiCliente.novedadesApi, apiCliente.json)
    val notificacionesRepository: NotificacionesRepository =
        NotificacionesRepositoryRemoto(apiCliente.notificacionesApi, apiCliente.json)
    val sancionesRepository: SancionesRepository =
        SancionesRepositoryRemoto(apiCliente.sancionesApi, apiCliente.json)
    val territorioRepository: TerritorioRepository =
        TerritorioRepositoryRemoto(apiCliente.territorioApi, apiCliente.json)
    val tareasRepository: TareasRepository =
        TareasRepositoryRemoto(apiCliente.tareasApi, apiCliente.json, apiCliente.urlBase)
}
