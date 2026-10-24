package co.ecocomunidad.app.ui.notificaciones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.NotificacionesRepository
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.TareasRepository
import co.ecocomunidad.app.data.modelo.ESTADOS_TAREA_ACTIVOS
import co.ecocomunidad.app.data.modelo.Notificacion
import co.ecocomunidad.app.data.modelo.Rol
import co.ecocomunidad.app.data.modelo.TareaGestor
import co.ecocomunidad.app.data.modelo.TipoNotificacion
import co.ecocomunidad.app.data.sesion.UsuarioSesion
import co.ecocomunidad.app.ui.navegacion.mensajeDeError
import co.ecocomunidad.app.ui.reportes.ErrorCarga
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

/** A dónde lleva tocar una notificación. */
sealed interface DestinoNotificacion {
    data object MisNovedades : DestinoNotificacion
    data object MisSanciones : DestinoNotificacion
    data class Reporte(val id: Int) : DestinoNotificacion
    /** Inicio del gestor. */
    data object MisTareas : DestinoNotificacion
    data class Tarea(val id: Int) : DestinoNotificacion

    /**
     * La tarea del gestor en ese reporte: la notificación solo trae el reporte. El ViewModel la busca
     * en tareas/mias/ y la cambia por [Tarea] (o [MisTareas] si ya no está) antes de abrirla.
     */
    data class TareaDelReporte(val idReporte: Int) : DestinoNotificacion
}

/**
 * Ciudadano: novedad_respondida → Mis novedades; sancion y bloqueo_levantado → Mis sanciones; las
 * demás con reporte → su detalle, salvo reporte_eliminado (ya no se puede abrir).
 * Gestor (sección 9): tarea_asignada → el detalle de esa tarea; tarea_cancelada → Mis tareas;
 * novedad_respondida → Mis novedades. Si no hay a dónde ir, null: solo se marca como leída.
 */
fun destinoDe(notificacion: Notificacion, rol: Rol): DestinoNotificacion? = when (rol) {
    Rol.CIUDADANO -> when (notificacion.tipo) {
        TipoNotificacion.NOVEDAD_RESPONDIDA -> DestinoNotificacion.MisNovedades
        TipoNotificacion.SANCION, TipoNotificacion.BLOQUEO_LEVANTADO -> DestinoNotificacion.MisSanciones
        TipoNotificacion.REPORTE_ELIMINADO -> null
        else -> notificacion.reporteId?.let { DestinoNotificacion.Reporte(it) }
    }
    Rol.GESTOR -> when (notificacion.tipo) {
        TipoNotificacion.TAREA_ASIGNADA ->
            notificacion.reporteId?.let { DestinoNotificacion.TareaDelReporte(it) } ?: DestinoNotificacion.MisTareas
        TipoNotificacion.TAREA_CANCELADA -> DestinoNotificacion.MisTareas
        TipoNotificacion.NOVEDAD_RESPONDIDA -> DestinoNotificacion.MisNovedades
        else -> null
    }
    // El administrador no usa la app
    Rol.ADMINISTRADOR -> null
}

/**
 * La tarea del gestor en el reporte: la activa si la hay (RN-11: solo una por gestor y reporte) y,
 * si no, la más reciente. [tareas] viene de tareas/mias/, más nuevas primero.
 */
fun tareaDelReporte(tareas: List<TareaGestor>, idReporte: Int): TareaGestor? {
    val delReporte = tareas.filter { it.reporte.id == idReporte }
    return delReporte.firstOrNull { it.estado in ESTADOS_TAREA_ACTIVOS } ?: delReporte.firstOrNull()
}

data class NotificacionesEstado(
    val cargando: Boolean = true,
    /** La carga pasó de 1 s: se muestra Brotico pensando (marca.md). */
    val cargaLarga: Boolean = false,
    /** Más nueva primero. */
    val notificaciones: List<Notificacion> = emptyList(),
    val error: ErrorCarga? = null,
    /** No se pudo marcar como leída: se avisa sin quitar la lista. */
    val aviso: String? = null,
    /** Pantalla que hay que abrir tras tocar una notificación. */
    val abrir: DestinoNotificacion? = null,
) {
    val vacio: Boolean get() = !cargando && error == null && notificaciones.isEmpty()
}

/** Notificaciones de quien tiene la sesión (sección 9). [usuario] decide a dónde lleva cada una. */
class NotificacionesViewModel(
    private val repositorio: NotificacionesRepository,
    private val tareas: TareasRepository,
    private val usuario: Flow<UsuarioSesion?>,
) : ViewModel() {

    private val _estado = MutableStateFlow(NotificacionesEstado())
    val estado: StateFlow<NotificacionesEstado> = _estado.asStateFlow()

    private var trabajo: Job? = null

    init {
        cargar()
    }

    fun reintentar() = cargar()

    /** Al volver de otra pantalla, para que se vea lo que llegó mientras tanto. Sin quitar la lista. */
    fun recargar() {
        if (_estado.value.notificaciones.isEmpty()) return cargar()
        trabajo?.cancel()
        trabajo = viewModelScope.launch {
            val r = repositorio.listar()
            if (r is Resultado.Exito) _estado.update { it.copy(notificaciones = ordenadas(r.datos)) }
        }
    }

    private fun cargar() {
        trabajo?.cancel()
        _estado.value = NotificacionesEstado()
        trabajo = viewModelScope.launch {
            val avisoLento = launch {
                delay(UN_SEGUNDO)
                _estado.update { it.copy(cargaLarga = true) }
            }
            val r = repositorio.listar()
            avisoLento.cancel()

            _estado.value = when (r) {
                is Resultado.Exito -> NotificacionesEstado(cargando = false, notificaciones = ordenadas(r.datos))
                is Resultado.Fallo -> NotificacionesEstado(
                    cargando = false,
                    error = ErrorCarga(mensajeDeError(r.error), sinConexion = r.error is ErrorAuth.SinConexion),
                )
            }
        }
    }

    /** Se marca como leída al instante (si falla, vuelve a no leída) y se abre su pantalla. */
    fun tocar(notificacion: Notificacion) {
        viewModelScope.launch {
            val rol = usuario.first()?.rol ?: return@launch
            val destino = destinoDe(notificacion, rol)?.let { resolver(it) }
            _estado.update { it.copy(abrir = destino) }
        }
        if (notificacion.leida) return
        ponerLeida(notificacion.id, leida = true)
        viewModelScope.launch {
            val r = repositorio.marcarLeida(notificacion.id)
            if (r is Resultado.Fallo) {
                ponerLeida(notificacion.id, leida = false)
                _estado.update { it.copy(aviso = mensajeDeError(r.error)) }
            }
        }
    }

    /** Busca la tarea de [DestinoNotificacion.TareaDelReporte]. Si no se puede pedir, avisa y no abre nada. */
    private suspend fun resolver(destino: DestinoNotificacion): DestinoNotificacion? {
        if (destino !is DestinoNotificacion.TareaDelReporte) return destino
        return when (val r = tareas.mias()) {
            is Resultado.Exito -> tareaDelReporte(r.datos, destino.idReporte)
                ?.let { DestinoNotificacion.Tarea(it.id) }
                ?: DestinoNotificacion.MisTareas
            is Resultado.Fallo -> {
                _estado.update { it.copy(aviso = mensajeDeError(r.error)) }
                null
            }
        }
    }

    fun abrirAtendido() = _estado.update { it.copy(abrir = null) }

    fun avisoAtendido() = _estado.update { it.copy(aviso = null) }

    private fun ponerLeida(id: Int, leida: Boolean) = _estado.update { e ->
        e.copy(notificaciones = e.notificaciones.map { if (it.id == id) it.copy(leida = leida) else it })
    }

    // El backend ya las ordena; se ordena aquí también para no depender de eso
    private fun ordenadas(lista: List<Notificacion>) =
        lista.sortedWith(compareByDescending<Notificacion> { instante(it.fecha) }.thenByDescending { it.id })

    private fun instante(fecha: String): OffsetDateTime? = runCatching { OffsetDateTime.parse(fecha) }.getOrNull()

    private companion object {
        const val UN_SEGUNDO = 1_000L
    }
}
