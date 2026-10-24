package co.ecocomunidad.app.ui.novedades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.NovedadesRepository
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.modelo.Novedad
import co.ecocomunidad.app.data.modelo.Rol
import co.ecocomunidad.app.data.sesion.UsuarioSesion
import co.ecocomunidad.app.ui.navegacion.mensajeDeError
import co.ecocomunidad.app.ui.reportes.ErrorCarga
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

data class MisNovedadesEstado(
    val cargando: Boolean = true,
    /** La carga pasó de 1 s: se muestra Brotico pensando (marca.md). */
    val cargaLarga: Boolean = false,
    /** Más nueva primero. */
    val novedades: List<Novedad> = emptyList(),
    val error: ErrorCarga? = null,
) {
    val vacio: Boolean get() = !cargando && error == null && novedades.isEmpty()
}

/** Las novedades que creó el ciudadano o el gestor, con la respuesta del administrador si ya la hay (RN-18). */
class MisNovedadesViewModel(
    private val repositorio: NovedadesRepository,
    usuario: Flow<UsuarioSesion?>,
) : ViewModel() {

    private val _estado = MutableStateFlow(MisNovedadesEstado())
    val estado: StateFlow<MisNovedadesEstado> = _estado.asStateFlow()

    /** El gestor ve en cada tarjeta su tarea ("título · Tarea #id"); el ciudadano, solo el reporte. */
    val conTarea: StateFlow<Boolean> =
        usuario.map { it?.rol == Rol.GESTOR }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var trabajo: Job? = null

    init {
        cargar()
    }

    fun reintentar() = cargar()

    private fun cargar() {
        trabajo?.cancel()
        _estado.value = MisNovedadesEstado()
        trabajo = viewModelScope.launch {
            val avisoLento = launch {
                delay(UN_SEGUNDO)
                _estado.update { it.copy(cargaLarga = true) }
            }
            val r = repositorio.listarMias()
            avisoLento.cancel()

            _estado.value = when (r) {
                // El backend ya las ordena; se ordena aquí también para no depender de eso
                is Resultado.Exito -> MisNovedadesEstado(
                    cargando = false,
                    novedades = r.datos.sortedWith(compareByDescending<Novedad> { instante(it.fecha) }.thenByDescending { it.id }),
                )
                is Resultado.Fallo -> MisNovedadesEstado(
                    cargando = false,
                    error = ErrorCarga(mensajeDeError(r.error), sinConexion = r.error is ErrorAuth.SinConexion),
                )
            }
        }
    }

    private fun instante(fecha: String): OffsetDateTime? = runCatching { OffsetDateTime.parse(fecha) }.getOrNull()

    private companion object {
        const val UN_SEGUNDO = 1_000L
    }
}
