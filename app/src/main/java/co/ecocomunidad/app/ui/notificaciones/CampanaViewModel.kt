package co.ecocomunidad.app.ui.notificaciones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.ecocomunidad.app.data.NotificacionesRepository
import co.ecocomunidad.app.data.Resultado
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Punto rojo de la campana del inicio: hay al menos una notificación sin leer. */
class CampanaViewModel(private val repositorio: NotificacionesRepository) : ViewModel() {

    private val _hayNoLeidas = MutableStateFlow(false)
    val hayNoLeidas: StateFlow<Boolean> = _hayNoLeidas.asStateFlow()

    private var trabajo: Job? = null

    /** Se llama cada vez que el inicio vuelve a verse. Si falla, se deja lo último que se supo. */
    fun actualizar() {
        trabajo?.cancel()
        trabajo = viewModelScope.launch {
            val r = repositorio.contarNoLeidas()
            if (r is Resultado.Exito) _hayNoLeidas.value = r.datos > 0
        }
    }
}
