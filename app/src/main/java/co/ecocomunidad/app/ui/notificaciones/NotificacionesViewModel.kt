package co.ecocomunidad.app.ui.notificaciones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.NotificacionesRepository
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.modelo.Notificacion
import co.ecocomunidad.app.data.modelo.TipoNotificacion
import co.ecocomunidad.app.ui.navegacion.mensajeDeError
import co.ecocomunidad.app.ui.reportes.ErrorCarga
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

/** A dónde lleva tocar una notificación. */
sealed interface DestinoNotificacion {
    data object MisNovedades : DestinoNotificacion
    data object MisSanciones : DestinoNotificacion
    data class Reporte(val id: Int) : DestinoNotificacion
}

/**
 * novedad_respondida → Mis novedades; sancion y bloqueo_levantado → Mis sanciones; las demás con
 * reporte → su detalle, salvo reporte_eliminado (ya no se puede abrir). Si no hay a dónde ir, null.
 */
fun destinoDe(notificacion: Notificacion): DestinoNotificacion? = when (notificacion.tipo) {
    TipoNotificacion.NOVEDAD_RESPONDIDA -> DestinoNotificacion.MisNovedades
    TipoNotificacion.SANCION, TipoNotificacion.BLOQUEO_LEVANTADO -> DestinoNotificacion.MisSanciones
    TipoNotificacion.REPORTE_ELIMINADO -> null
    else -> notificacion.reporteId?.let { DestinoNotificacion.Reporte(it) }
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

class NotificacionesViewModel(private val repositorio: NotificacionesRepository) : ViewModel() {

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
        _estado.update { it.copy(abrir = destinoDe(notificacion)) }
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
