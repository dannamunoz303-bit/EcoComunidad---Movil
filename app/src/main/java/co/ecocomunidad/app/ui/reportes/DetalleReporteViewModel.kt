package co.ecocomunidad.app.ui.reportes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.ReportesRepository
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.modelo.Accion
import co.ecocomunidad.app.data.modelo.ESTADOS_REPORTE_CON_NOVEDAD
import co.ecocomunidad.app.data.modelo.EstadoReporte
import co.ecocomunidad.app.data.modelo.Reporte
import co.ecocomunidad.app.data.modelo.Rol
import co.ecocomunidad.app.data.modelo.UsuarioResumen
import co.ecocomunidad.app.data.sesion.UsuarioSesion
import co.ecocomunidad.app.ui.navegacion.mensajeDeError
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

/** Una acción del gestor en la línea de tiempo. El dueño del reporte ve el nombre del gestor (RN-14). */
data class AccionDelGestor(val accion: Accion, val gestor: UsuarioResumen?)

/** Línea de tiempo con las acciones y evidencias del gestor. */
sealed interface LineaDeTiempo {
    data object Cargando : LineaDeTiempo

    /** El backend no la entrega a quien no es dueño del reporte (403, RN-14): no se muestra. */
    data object NoDisponible : LineaDeTiempo

    data class Error(val mensaje: String) : LineaDeTiempo

    /** Más reciente primero. Vacía si todavía no hay acciones. */
    data class Lista(val acciones: List<AccionDelGestor>) : LineaDeTiempo
}

data class DetalleReporteEstado(
    val cargando: Boolean = true,
    /** La carga pasó de 1 s: se muestra Brotico pensando (marca.md). */
    val cargaLarga: Boolean = false,
    val reporte: Reporte? = null,
    /**
     * El reporte es del ciudadano con sesión (para el motivo del rechazo y los botones de dueño).
     * Siempre false para el gestor, que lo abre en solo lectura desde "Todos los reportes".
     */
    val esPropio: Boolean = false,
    val linea: LineaDeTiempo = LineaDeTiempo.Cargando,
    val error: ErrorCarga? = null,
    val eliminando: Boolean = false,
    /** Se eliminó: la pantalla vuelve a la lista. */
    val eliminado: Boolean = false,
    /** Mensaje del backend si no se pudo eliminar (por ejemplo, ya no está pendiente). */
    val avisoEliminar: String? = null,
) {
    /**
     * RN-07 y RN-15: editar, eliminar y cambiar fotos solo el dueño y mientras esté pendiente.
     * Solo decide qué botones se ven; el backend vuelve a revisar al enviar.
     */
    val puedeModificar: Boolean
        get() = esPropio && reporte?.estado == EstadoReporte.PENDIENTE

    /** RN-18: novedades solo sobre el reporte propio y en los estados permitidos. El backend vuelve a revisar. */
    val puedeReportarNovedad: Boolean
        get() = esPropio && reporte?.estado in ESTADOS_REPORTE_CON_NOVEDAD
}

class DetalleReporteViewModel(
    private val repositorio: ReportesRepository,
    private val usuario: Flow<UsuarioSesion?>,
    private val id: Int,
) : ViewModel() {

    private val _estado = MutableStateFlow(DetalleReporteEstado())
    val estado: StateFlow<DetalleReporteEstado> = _estado.asStateFlow()

    private var trabajo: Job? = null

    init {
        cargar()
    }

    fun reintentar() = cargar()

    /** Tras editar: se vuelve a pedir el reporte para mostrar lo que quedó en el backend. */
    fun recargar() = cargar()

    /** Eliminación lógica (RN-07), después de que el ciudadano confirma. */
    fun eliminar() {
        if (_estado.value.eliminando) return
        _estado.update { it.copy(eliminando = true, avisoEliminar = null) }
        viewModelScope.launch {
            when (val r = repositorio.eliminar(id)) {
                is Resultado.Exito -> _estado.update { it.copy(eliminando = false, eliminado = true) }
                is Resultado.Fallo -> _estado.update {
                    it.copy(eliminando = false, avisoEliminar = mensajeDeError(r.error))
                }
            }
        }
    }

    fun eliminadoAtendido() = _estado.update { it.copy(eliminado = false) }

    fun avisoEliminarAtendido() = _estado.update { it.copy(avisoEliminar = null) }

    private fun cargar() {
        trabajo?.cancel()
        _estado.value = DetalleReporteEstado()
        trabajo = viewModelScope.launch {
            val avisoLento = launch {
                delay(UN_SEGUNDO)
                _estado.update { it.copy(cargaLarga = true) }
            }
            val r = repositorio.detalle(id)
            avisoLento.cancel()

            when (r) {
                is Resultado.Exito -> {
                    val sesion = usuario.first()
                    _estado.value = DetalleReporteEstado(
                        cargando = false,
                        reporte = r.datos,
                        esPropio = sesion?.rol == Rol.CIUDADANO && r.datos.ciudadano?.id == sesion.id,
                    )
                    cargarLinea()
                }
                is Resultado.Fallo -> _estado.value = DetalleReporteEstado(
                    cargando = false,
                    error = ErrorCarga(mensajeDeError(r.error), sinConexion = r.error is ErrorAuth.SinConexion),
                )
            }
        }
    }

    /** Se pide siempre; el backend decide quién puede verla (RN-14). */
    private suspend fun cargarLinea() {
        val linea = when (val r = repositorio.tareas(id)) {
            is Resultado.Exito -> LineaDeTiempo.Lista(
                r.datos
                    .flatMap { tarea -> tarea.acciones.map { AccionDelGestor(it, tarea.gestor) } }
                    .sortedByDescending { instante(it.accion.fecha) },
            )
            is Resultado.Fallo -> {
                val error = r.error
                if (error is ErrorAuth.Servidor && error.codigoHttp == 403) LineaDeTiempo.NoDisponible
                else LineaDeTiempo.Error(mensajeDeError(error))
            }
        }
        _estado.update { it.copy(linea = linea) }
    }

    private fun instante(fecha: String): OffsetDateTime? = runCatching { OffsetDateTime.parse(fecha) }.getOrNull()

    private companion object {
        const val UN_SEGUNDO = 1_000L
    }
}
