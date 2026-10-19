package co.ecocomunidad.app.ui.tareas

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.ecocomunidad.app.R
import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.ReportesRepository
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.TareasRepository
import co.ecocomunidad.app.data.modelo.Accion
import co.ecocomunidad.app.data.modelo.EstadoTarea
import co.ecocomunidad.app.data.modelo.Reporte
import co.ecocomunidad.app.data.modelo.TareaGestor
import co.ecocomunidad.app.ui.navegacion.mensajeDeError
import co.ecocomunidad.app.ui.reportes.ErrorCarga
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Qué se puede hacer con la tarea según su estado (RN-13). */
enum class AccionDisponible { INICIAR, TERMINAR, NINGUNA }

fun accionDisponible(estado: EstadoTarea): AccionDisponible = when (estado) {
    EstadoTarea.ASIGNADA -> AccionDisponible.INICIAR
    EstadoTarea.EN_PROCESO -> AccionDisponible.TERMINAR
    EstadoTarea.TERMINADA, EstadoTarea.CANCELADA -> AccionDisponible.NINGUNA
}

/** Las acciones que registró el gestor en esta tarea. */
sealed interface MisAcciones {
    data object Cargando : MisAcciones
    data class Error(val mensaje: String) : MisAcciones

    /** Más reciente primero. Vacía si todavía no hay acciones. */
    data class Lista(val acciones: List<Accion>) : MisAcciones
}

data class DetalleTareaEstado(
    val cargando: Boolean = true,
    /** La carga pasó de 1 s: se muestra Brotico pensando (marca.md). */
    val cargaLarga: Boolean = false,
    val tarea: TareaGestor? = null,
    /** reportes/{id}/: fotos y descripción del ciudadano. Si no se pudo pedir, la pantalla va sin ellas. */
    val reporte: Reporte? = null,
    val acciones: MisAcciones = MisAcciones.Cargando,
    val error: ErrorCarga? = null,
    /** Se está iniciando o terminando la tarea. */
    val procesando: Boolean = false,
    /** Mensaje del backend al no poder iniciar o terminar (por ejemplo, falta la foto de evidencia). */
    val aviso: String? = null,
    /** Salió bien: se muestra una vez. */
    @param:StringRes val mensaje: Int? = null,
) {
    val accion: AccionDisponible
        get() = tarea?.let { accionDisponible(it.estado) } ?: AccionDisponible.NINGUNA
}

/**
 * Detalle de una tarea del gestor. El backend no tiene GET tareas/{id}/: se arma con la tarea de
 * tareas/mias/, el reporte de reportes/{id}/ y las acciones de tareas/{id}/acciones/.
 */
class DetalleTareaViewModel(
    private val tareas: TareasRepository,
    private val reportes: ReportesRepository,
    private val id: Int,
) : ViewModel() {

    private val _estado = MutableStateFlow(DetalleTareaEstado())
    val estado: StateFlow<DetalleTareaEstado> = _estado.asStateFlow()

    private var trabajo: Job? = null

    init {
        cargar(silenciosa = false)
    }

    fun reintentar() = cargar(silenciosa = false)

    fun iniciar() = cambiarEstado(R.string.tarea_iniciada) { tareas.iniciar(id) }

    fun terminar() = cambiarEstado(R.string.tarea_terminada) { tareas.terminar(id) }

    fun mensajeAtendido() = _estado.update { it.copy(mensaje = null) }

    fun avisoAtendido() = _estado.update { it.copy(aviso = null) }

    private fun cambiarEstado(@StringRes exito: Int, llamada: suspend () -> Resultado<TareaGestor>) {
        if (_estado.value.procesando) return
        _estado.update { it.copy(procesando = true, aviso = null) }
        viewModelScope.launch {
            when (val r = llamada()) {
                is Resultado.Exito -> {
                    _estado.update { it.copy(procesando = false, tarea = r.datos, mensaje = exito) }
                    // El reporte también cambió de estado (RN-16): se vuelve a pedir todo sin ocultar la pantalla
                    cargar(silenciosa = true)
                }
                is Resultado.Fallo -> _estado.update {
                    it.copy(procesando = false, aviso = mensajeDeError(r.error))
                }
            }
        }
    }

    /** [silenciosa]: tras iniciar o terminar, lo que hay en pantalla se queda mientras llega lo nuevo. */
    private fun cargar(silenciosa: Boolean) {
        trabajo?.cancel()
        if (!silenciosa) _estado.value = DetalleTareaEstado()
        trabajo = viewModelScope.launch {
            val avisoLento = if (silenciosa) null else launch {
                delay(UN_SEGUNDO)
                _estado.update { it.copy(cargaLarga = true) }
            }
            val acciones = async { tareas.acciones(id) }
            val tarea = tareas.tarea(id)
            avisoLento?.cancel()

            when (tarea) {
                is Resultado.Exito -> {
                    _estado.update { it.copy(cargando = false, cargaLarga = false, tarea = tarea.datos, error = null) }
                    val reporte = reportes.detalle(tarea.datos.reporte.id)
                    if (reporte is Resultado.Exito) _estado.update { it.copy(reporte = reporte.datos) }
                }
                is Resultado.Fallo -> if (!silenciosa) {
                    acciones.cancel()
                    _estado.value = DetalleTareaEstado(
                        cargando = false,
                        error = ErrorCarga(mensajeDeError(tarea.error), sinConexion = tarea.error is ErrorAuth.SinConexion),
                    )
                    return@launch
                }
            }

            val lista = when (val r = acciones.await()) {
                is Resultado.Exito -> MisAcciones.Lista(r.datos)
                is Resultado.Fallo -> MisAcciones.Error(mensajeDeError(r.error))
            }
            _estado.update { it.copy(acciones = lista) }
        }
    }

    private companion object {
        const val UN_SEGUNDO = 1_000L
    }
}
