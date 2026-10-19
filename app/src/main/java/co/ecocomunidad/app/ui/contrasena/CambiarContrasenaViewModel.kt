package co.ecocomunidad.app.ui.contrasena

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.ecocomunidad.app.data.AuthRepository
import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.ui.navegacion.Ruta
import co.ecocomunidad.app.ui.navegacion.destinoPara
import co.ecocomunidad.app.ui.navegacion.mensajeDeError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CambiarContrasenaEstado(
    val actual: String = "",
    val nueva: String = "",
    val confirmar: String = "",
    val errorActual: String? = null,
    val errorNueva: String? = null,
    val errorConfirmar: String? = null,
    val cargando: Boolean = false,
    val aviso: String? = null,
    val destino: Ruta? = null,
    /** Desde "Mi cuenta": se guardó y la pantalla vuelve atrás. */
    val guardada: Boolean = false,
)

/**
 * Cambio obligatorio (RN-05) o, con [desdeMiCuenta], voluntario. Las reglas de la contraseña (RN-03)
 * las valida el backend; aquí solo se revisa que no haya campos vacíos y que la confirmación coincida.
 */
class CambiarContrasenaViewModel(
    private val repositorio: AuthRepository,
    private val desdeMiCuenta: Boolean = false,
) : ViewModel() {

    private val _estado = MutableStateFlow(CambiarContrasenaEstado())
    val estado: StateFlow<CambiarContrasenaEstado> = _estado.asStateFlow()

    fun cambiarActual(valor: String) = _estado.update { it.copy(actual = valor, errorActual = null) }

    fun cambiarNueva(valor: String) = _estado.update { it.copy(nueva = valor, errorNueva = null) }

    fun cambiarConfirmar(valor: String) = _estado.update { it.copy(confirmar = valor, errorConfirmar = null) }

    fun guardar() {
        val e = _estado.value
        if (e.cargando) return

        val errorActual = if (e.actual.isEmpty()) "Escribe tu contraseña actual." else null
        val errorNueva = if (e.nueva.isEmpty()) "Escribe la contraseña nueva." else null
        val errorConfirmar = when {
            e.confirmar.isEmpty() -> "Repite la contraseña nueva."
            e.confirmar != e.nueva -> "Las contraseñas nuevas no coinciden."
            else -> null
        }
        if (errorActual != null || errorNueva != null || errorConfirmar != null) {
            _estado.update {
                it.copy(errorActual = errorActual, errorNueva = errorNueva, errorConfirmar = errorConfirmar)
            }
            return
        }

        _estado.update { it.copy(cargando = true, aviso = null) }
        viewModelScope.launch {
            when (val r = repositorio.cambiarContrasena(e.actual, e.nueva)) {
                is Resultado.Exito -> {
                    if (desdeMiCuenta) {
                        _estado.update { CambiarContrasenaEstado(guardada = true) }
                        return@launch
                    }
                    val usuario = repositorio.usuario.first()
                    val destino = usuario?.let { destinoPara(it.rol, debeCambiarContrasena = false) } ?: Ruta.Login()
                    _estado.update { CambiarContrasenaEstado(destino = destino) }
                }
                is Resultado.Fallo -> _estado.update { it.conError(r.error).copy(cargando = false) }
            }
        }
    }

    fun cerrarSesion() {
        viewModelScope.launch {
            repositorio.cerrarSesion()
            _estado.update { it.copy(destino = Ruta.Login()) }
        }
    }

    fun destinoAtendido() = _estado.update { it.copy(destino = null) }

    fun guardadaAtendida() = _estado.update { it.copy(guardada = false) }

    /** Los mensajes de validación del backend se muestran tal cual, debajo de su campo. */
    private fun CambiarContrasenaEstado.conError(error: ErrorAuth): CambiarContrasenaEstado {
        if (error !is ErrorAuth.Validacion) return copy(aviso = mensajeDeError(error))
        val campos = error.campos
        val otros = campos.filterKeys { it != CAMPO_ACTUAL && it != CAMPO_NUEVA }.values.flatten()
        return copy(
            errorActual = campos[CAMPO_ACTUAL]?.joinToString("\n"),
            errorNueva = campos[CAMPO_NUEVA]?.joinToString("\n"),
            aviso = otros.takeIf { it.isNotEmpty() }?.joinToString("\n"),
        )
    }

    private companion object {
        // Nombres de campo de CambiarContrasenaSerializer en el backend
        const val CAMPO_ACTUAL = "contrasena_actual"
        const val CAMPO_NUEVA = "contrasena_nueva"
    }
}
