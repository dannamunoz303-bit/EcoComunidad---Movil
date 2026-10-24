package co.ecocomunidad.app.ui.tareas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.TareasRepository
import co.ecocomunidad.app.data.modelo.TareaGestor
import co.ecocomunidad.app.ui.navegacion.mensajeDeError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A dónde lleva tocar un reporte en "Todos los reportes" del gestor. */
sealed interface DestinoReporte {
    /** El detalle de su tarea en ese reporte. */
    data class Tarea(val id: Int) : DestinoReporte

    /** El detalle del reporte en solo lectura (no es suyo: sin editar, eliminar ni novedad). */
    data class Reporte(val id: Int) : DestinoReporte
}

/** Con una tarea suya en el reporte (cualquier estado), su tarea; si no, el reporte. */
fun destinoReporte(tareas: List<TareaGestor>, idReporte: Int): DestinoReporte =
    tareaDelReporte(tareas, idReporte)?.let { DestinoReporte.Tarea(it.id) } ?: DestinoReporte.Reporte(idReporte)

data class AbrirReporteEstado(
    /** Se están pidiendo sus tareas: no se atienden más toques. */
    val buscando: Boolean = false,
    /** Pantalla que hay que abrir. */
    val abrir: DestinoReporte? = null,
    /** No se pudieron pedir sus tareas: se avisa y no se abre nada. */
    val aviso: String? = null,
)

/**
 * Tocar un reporte en "Todos los reportes" del gestor. Sus tareas se piden en cada toque
 * (tareas/mias/), para no abrir el reporte en solo lectura si le acaban de asignar una.
 */
class AbrirReporteViewModel(private val tareas: TareasRepository) : ViewModel() {

    private val _estado = MutableStateFlow(AbrirReporteEstado())
    val estado: StateFlow<AbrirReporteEstado> = _estado.asStateFlow()

    fun tocar(idReporte: Int) {
        if (_estado.value.buscando) return
        _estado.update { it.copy(buscando = true, aviso = null) }
        viewModelScope.launch {
            when (val r = tareas.mias()) {
                is Resultado.Exito -> _estado.update {
                    it.copy(buscando = false, abrir = destinoReporte(r.datos, idReporte))
                }
                is Resultado.Fallo -> _estado.update { it.copy(buscando = false, aviso = mensajeDeError(r.error)) }
            }
        }
    }

    fun abrirAtendido() = _estado.update { it.copy(abrir = null) }

    fun avisoAtendido() = _estado.update { it.copy(aviso = null) }
}
