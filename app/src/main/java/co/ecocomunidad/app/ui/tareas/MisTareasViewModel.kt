package co.ecocomunidad.app.ui.tareas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.ReportesRepository
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.TareasRepository
import co.ecocomunidad.app.data.modelo.ESTADOS_TAREA_ACTIVOS
import co.ecocomunidad.app.data.modelo.EstadoTarea
import co.ecocomunidad.app.data.modelo.TareaGestor
import co.ecocomunidad.app.ui.navegacion.mensajeDeError
import co.ecocomunidad.app.ui.reportes.ErrorCarga
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FiltroTareas { ACTIVAS, TERMINADAS }

data class MisTareasEstado(
    val filtro: FiltroTareas = FiltroTareas.ACTIVAS,
    /** Todas las tareas del gestor tal como llegan; las pestañas filtran sobre esta lista. */
    val todas: List<TareaGestor> = emptyList(),
    /** Primera foto de cada reporte (por id del reporte). Sin entrada: todavía no llega o no tiene. */
    val fotos: Map<Int, String> = emptyMap(),
    /** Primera carga, sin nada que mostrar todavía. */
    val cargando: Boolean = true,
    /** Deslizar hacia abajo o volver a la pantalla: la lista actual sigue visible. */
    val recargando: Boolean = false,
    /** La carga pasó de 1 s: se muestra Brotico pensando (marca.md). */
    val cargaLarga: Boolean = false,
    val error: ErrorCarga? = null,
    /** Falló una recarga con datos en pantalla: se avisa sin quitar la lista. */
    val avisoRecarga: String? = null,
) {
    /** Activas: asignada y en_proceso. Terminadas: terminada. Las canceladas no se muestran. */
    val visibles: List<TareaGestor>
        get() = todas.filter {
            when (filtro) {
                FiltroTareas.ACTIVAS -> it.estado in ESTADOS_TAREA_ACTIVOS
                FiltroTareas.TERMINADAS -> it.estado == EstadoTarea.TERMINADA
            }
        }

    /** Para el saludo: "Tienes N tareas activas". */
    val cantidadActivas: Int get() = todas.count { it.estado in ESTADOS_TAREA_ACTIVOS }

    val vacio: Boolean get() = !cargando && error == null && visibles.isEmpty()
}

/**
 * Mis tareas del gestor. tareas/mias/ trae todas en una sola petición; las pestañas filtran aquí.
 * Como no trae fotos, después se pide reportes/{id}/ de cada reporte para la foto de la tarjeta.
 */
class MisTareasViewModel(
    private val tareas: TareasRepository,
    private val reportes: ReportesRepository,
) : ViewModel() {

    private val _estado = MutableStateFlow(MisTareasEstado())
    val estado: StateFlow<MisTareasEstado> = _estado.asStateFlow()

    private var trabajo: Job? = null

    /** Reportes cuya foto ya se pidió (aunque no tenga), para no volver a pedirla al recargar. */
    private val fotosPedidas = mutableSetOf<Int>()

    init {
        cargar(recarga = false)
    }

    fun elegirFiltro(filtro: FiltroTareas) = _estado.update { it.copy(filtro = filtro) }

    /** Deslizar hacia abajo, o volver a la pantalla después de iniciar o terminar una tarea. */
    fun recargar() = cargar(recarga = _estado.value.todas.isNotEmpty())

    fun reintentar() = cargar(recarga = false)

    fun avisoAtendido() = _estado.update { it.copy(avisoRecarga = null) }

    private fun cargar(recarga: Boolean) {
        trabajo?.cancel()
        _estado.update {
            if (recarga) it.copy(recargando = true, avisoRecarga = null)
            else MisTareasEstado(filtro = it.filtro, fotos = it.fotos)
        }
        trabajo = viewModelScope.launch {
            val avisoLento = if (recarga) null else launch {
                delay(UN_SEGUNDO)
                _estado.update { it.copy(cargaLarga = true) }
            }
            val r = tareas.mias()
            avisoLento?.cancel()

            when (r) {
                is Resultado.Exito -> {
                    _estado.update {
                        MisTareasEstado(filtro = it.filtro, todas = r.datos, fotos = it.fotos, cargando = false)
                    }
                    pedirFotos(r.datos)
                }
                is Resultado.Fallo -> {
                    val mensaje = mensajeDeError(r.error)
                    _estado.update {
                        if (it.todas.isNotEmpty()) {
                            it.copy(recargando = false, avisoRecarga = mensaje)
                        } else {
                            MisTareasEstado(
                                filtro = it.filtro,
                                cargando = false,
                                error = ErrorCarga(mensaje, sinConexion = r.error is ErrorAuth.SinConexion),
                            )
                        }
                    }
                }
            }
        }
    }

    /** Una petición por reporte, en paralelo. Si falla, la tarjeta se queda con el ícono de imagen. */
    private fun pedirFotos(lista: List<TareaGestor>) {
        val nuevos = lista.map { it.reporte.id }.distinct().filter { fotosPedidas.add(it) }
        for (idReporte in nuevos) {
            viewModelScope.launch {
                val r = reportes.detalle(idReporte)
                val url = (r as? Resultado.Exito)?.datos?.fotos?.firstOrNull()?.url
                if (url != null) _estado.update { it.copy(fotos = it.fotos + (idReporte to url)) }
                // Si falló, se puede volver a intentar en la próxima recarga
                if (r is Resultado.Fallo) fotosPedidas.remove(idReporte)
            }
        }
    }

    private companion object {
        const val UN_SEGUNDO = 1_000L
    }
}
