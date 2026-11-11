package co.ecocomunidad.app.ui.notificaciones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.TareasRepository
import co.ecocomunidad.app.data.sesion.UsuarioSesion
import co.ecocomunidad.app.ui.tareas.tareaDelReporte
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Se tocó un aviso push (RN-26): MainActivity se abre con tipo y reporte_id en los extras y aquí se
 * decide la pantalla, con las mismas reglas que al tocar la notificación en la lista ([destinoDe]).
 * Sin sesión o con la contraseña pendiente (RN-05) no se abre nada: se queda en la pantalla de inicio.
 */
class AvisoPushViewModel(
    private val tareas: TareasRepository,
    private val usuario: Flow<UsuarioSesion?>,
) : ViewModel() {

    // Channel: espera a que MainActivity tenga el grafo de navegación y esté escuchando
    private val destinos = Channel<DestinoNotificacion>(Channel.BUFFERED)

    /** Pantalla que hay que abrir sobre el inicio. */
    val abrir: Flow<DestinoNotificacion> = destinos.receiveAsFlow()

    fun avisoTocado(tipo: String?, reporteId: String?) {
        if (tipo.isNullOrBlank()) return
        viewModelScope.launch {
            val sesion = usuario.first() ?: return@launch
            if (sesion.debeCambiarContrasena) return@launch
            val destino = destinoDe(tipo, reporteId?.toIntOrNull(), sesion.rol) ?: return@launch
            destinos.send(resolver(destino))
        }
    }

    /** tarea_asignada trae el reporte: se busca su tarea. Si no se puede, Mis tareas (el inicio del gestor). */
    private suspend fun resolver(destino: DestinoNotificacion): DestinoNotificacion {
        if (destino !is DestinoNotificacion.TareaDelReporte) return destino
        val r = tareas.mias()
        val tarea = (r as? Resultado.Exito)?.datos?.let { tareaDelReporte(it, destino.idReporte) }
        return tarea?.let { DestinoNotificacion.Tarea(it.id) } ?: DestinoNotificacion.MisTareas
    }
}
