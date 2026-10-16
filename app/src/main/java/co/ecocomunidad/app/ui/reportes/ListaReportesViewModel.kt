package co.ecocomunidad.app.ui.reportes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.ReportesRepository
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.modelo.Reporte
import co.ecocomunidad.app.ui.navegacion.mensajeDeError
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FiltroReportes { TODOS, MIOS }

/** Error que deja la pantalla sin datos. [sinConexion]: se muestra con Brotico triste; el resto, sobrio. */
data class ErrorCarga(val mensaje: String, val sinConexion: Boolean)

data class ListaReportesEstado(
    val filtro: FiltroReportes = FiltroReportes.TODOS,
    val reportes: List<Reporte> = emptyList(),
    /** Primera carga del filtro, sin nada que mostrar todavía. */
    val cargando: Boolean = false,
    /** Deslizar hacia abajo: la lista actual sigue visible. */
    val recargando: Boolean = false,
    /** La carga pasó de 1 s: se muestra Brotico pensando (marca.md). */
    val cargaLarga: Boolean = false,
    val error: ErrorCarga? = null,
    /** Falló una recarga con datos en pantalla: se avisa sin quitar la lista. */
    val avisoRecarga: String? = null,
    val cargado: Boolean = false,
) {
    val vacio: Boolean get() = cargado && error == null && reportes.isEmpty()
}

/**
 * Lista del ciudadano. Muestra exactamente lo que devuelve la API (RN-10 la aplica el backend).
 * El backend no pagina, así que cada carga trae la lista completa.
 */
class ListaReportesViewModel(private val repositorio: ReportesRepository) : ViewModel() {

    private val _estado = MutableStateFlow(ListaReportesEstado())
    val estado: StateFlow<ListaReportesEstado> = _estado.asStateFlow()

    /** Lo último que llegó de cada pestaña, para no volver a pedirlo al cambiar de pestaña. */
    private val guardados = mutableMapOf<FiltroReportes, List<Reporte>>()
    private var trabajo: Job? = null

    init {
        cargar(FiltroReportes.TODOS, recarga = false)
    }

    fun elegirFiltro(filtro: FiltroReportes) {
        if (filtro == _estado.value.filtro) return
        val guardado = guardados[filtro]
        if (guardado == null) {
            cargar(filtro, recarga = false)
        } else {
            trabajo?.cancel()
            _estado.value = ListaReportesEstado(filtro = filtro, reportes = guardado, cargado = true)
        }
    }

    fun recargar() = cargar(_estado.value.filtro, recarga = true)

    fun reintentar() = cargar(_estado.value.filtro, recarga = false)

    fun avisoAtendido() = _estado.update { it.copy(avisoRecarga = null) }

    /** Se acaba de crear un reporte: se muestra "Mis reportes" recién pedido; las dos pestañas quedan viejas. */
    fun reporteCreado() {
        guardados.clear()
        cargar(FiltroReportes.MIOS, recarga = false)
    }

    private fun cargar(filtro: FiltroReportes, recarga: Boolean) {
        trabajo?.cancel()
        _estado.update {
            if (recarga) it.copy(recargando = true, avisoRecarga = null)
            else ListaReportesEstado(filtro = filtro, cargando = true)
        }
        trabajo = viewModelScope.launch {
            val avisoLento = if (recarga) null else launch {
                delay(UN_SEGUNDO)
                _estado.update { it.copy(cargaLarga = true) }
            }
            val r = repositorio.listar(soloMios = filtro == FiltroReportes.MIOS)
            avisoLento?.cancel()

            when (r) {
                is Resultado.Exito -> {
                    guardados[filtro] = r.datos
                    _estado.value = ListaReportesEstado(filtro = filtro, reportes = r.datos, cargado = true)
                }
                is Resultado.Fallo -> {
                    val mensaje = mensajeDeError(r.error)
                    _estado.update {
                        if (it.reportes.isNotEmpty()) {
                            it.copy(recargando = false, avisoRecarga = mensaje)
                        } else {
                            ListaReportesEstado(
                                filtro = filtro,
                                error = ErrorCarga(mensaje, sinConexion = r.error is ErrorAuth.SinConexion),
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val UN_SEGUNDO = 1_000L
    }
}
