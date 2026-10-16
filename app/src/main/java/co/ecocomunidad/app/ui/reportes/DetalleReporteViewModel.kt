package co.ecocomunidad.app.ui.reportes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.ReportesRepository
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.modelo.Accion
import co.ecocomunidad.app.data.modelo.Reporte
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
    /** El reporte es del usuario con sesión (para el motivo del rechazo). */
    val esPropio: Boolean = false,
    val linea: LineaDeTiempo = LineaDeTiempo.Cargando,
    val error: ErrorCarga? = null,
)

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
                    val idUsuario = usuario.first()?.id
                    _estado.value = DetalleReporteEstado(
                        cargando = false,
                        reporte = r.datos,
                        esPropio = idUsuario != null && r.datos.ciudadano?.id == idUsuario,
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
