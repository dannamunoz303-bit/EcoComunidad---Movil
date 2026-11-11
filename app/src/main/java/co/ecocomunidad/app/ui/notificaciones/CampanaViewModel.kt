package co.ecocomunidad.app.ui.notificaciones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.ecocomunidad.app.data.NotificacionesRepository
import co.ecocomunidad.app.data.Resultado
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/**
 * Punto rojo de la campana del inicio: hay al menos una notificación sin leer.
 * [avisosPush]: tipos de los avisos push que llegan con la app abierta (EventosPush); cada uno la actualiza.
 */
class CampanaViewModel(
    private val repositorio: NotificacionesRepository,
    avisosPush: Flow<String> = emptyFlow(),
) : ViewModel() {

    private val _hayNoLeidas = MutableStateFlow(false)
    val hayNoLeidas: StateFlow<Boolean> = _hayNoLeidas.asStateFlow()

    private var trabajo: Job? = null

    init {
        viewModelScope.launch { avisosPush.collect { actualizar() } }
    }

    /** Se llama cada vez que el inicio vuelve a verse. Si falla, se deja lo último que se supo. */
    fun actualizar() {
        trabajo?.cancel()
        trabajo = viewModelScope.launch {
            val r = repositorio.contarNoLeidas()
            if (r is Resultado.Exito) _hayNoLeidas.value = r.datos > 0
        }
    }
}
