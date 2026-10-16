package co.ecocomunidad.app.ui.inicio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.ecocomunidad.app.data.AuthRepository
import co.ecocomunidad.app.data.sesion.UsuarioSesion
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InicioViewModel(private val repositorio: AuthRepository) : ViewModel() {

    val usuario: StateFlow<UsuarioSesion?> =
        repositorio.usuario.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun cerrarSesion(alTerminar: () -> Unit) {
        viewModelScope.launch {
            repositorio.cerrarSesion()
            alTerminar()
        }
    }
}
