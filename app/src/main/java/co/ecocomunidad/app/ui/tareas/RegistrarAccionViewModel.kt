package co.ecocomunidad.app.ui.tareas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.TareasRepository
import co.ecocomunidad.app.data.modelo.Accion
import co.ecocomunidad.app.data.modelo.MAXIMO_EVIDENCIAS
import co.ecocomunidad.app.data.modelo.TareaGestor
import co.ecocomunidad.app.ui.navegacion.mensajeDeError
import co.ecocomunidad.app.ui.reportes.ErrorCarga
import co.ecocomunidad.app.ui.reportes.crear.PreparadorFotos
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class RegistrarAccionEstado(
    // --- La tarea, para la tarjeta de arriba ---
    val cargandoTarea: Boolean = true,
    val tarea: TareaGestor? = null,
    val errorTarea: ErrorCarga? = null,
    // --- El formulario ---
    val descripcion: String = "",
    /** JPEG ya reducidos, en el orden en que se ven. */
    val fotos: List<File> = emptyList(),
    /** Fotos que todavía se están reduciendo. */
    val preparandoFotos: Int = 0,
    val errorDescripcion: String? = null,
    val errorFotos: String? = null,
    val enviando: Boolean = false,
    /** El envío pasó de 1 s: se muestra Brotico pensando (marca.md). */
    val cargaLarga: Boolean = false,
    /** Mensaje del backend que no es de un campo, o un aviso de la app (por ejemplo, el límite de fotos). */
    val aviso: String? = null,
    /** Sin fotos: hay que preguntar "¿Guardar igual?" antes de enviar. */
    val confirmarSinFoto: Boolean = false,
    /**
     * La acción ya existe en el backend pero no se pudieron subir sus fotos: al reintentar solo se suben
     * las fotos, sin crear otra acción. La descripción ya no se puede cambiar.
     */
    val accionCreada: Accion? = null,
    /** Todo quedó guardado: la pantalla vuelve al detalle. */
    val guardada: Boolean = false,
) {
    /** Cuántas fotos más se pueden agregar sin pasar el máximo del backend. */
    val fotosDisponibles: Int get() = (MAXIMO_EVIDENCIAS - fotos.size - preparandoFotos).coerceAtLeast(0)
}

/**
 * Registrar acción (RN-14) con fotos de evidencia (RN-15). El backend las recibe en dos peticiones:
 * primero la acción (tareas/{id}/acciones/) y luego sus fotos (acciones/{id}/fotos/).
 * Aquí solo se revisa que no falte la descripción y que no se pase del máximo de fotos.
 */
class RegistrarAccionViewModel(
    private val repositorio: TareasRepository,
    private val preparador: PreparadorFotos,
    private val idTarea: Int,
) : ViewModel() {

    private val _estado = MutableStateFlow(RegistrarAccionEstado())
    val estado: StateFlow<RegistrarAccionEstado> = _estado.asStateFlow()

    init {
        cargarTarea()
    }

    fun reintentarTarea() = cargarTarea()

    private fun cargarTarea() {
        _estado.update { it.copy(cargandoTarea = true, errorTarea = null) }
        viewModelScope.launch {
            when (val r = repositorio.tarea(idTarea)) {
                is Resultado.Exito -> _estado.update { it.copy(cargandoTarea = false, tarea = r.datos) }
                is Resultado.Fallo -> _estado.update {
                    it.copy(
                        cargandoTarea = false,
                        errorTarea = ErrorCarga(mensajeDeError(r.error), sinConexion = r.error is ErrorAuth.SinConexion),
                    )
                }
            }
        }
    }

    fun cambiarDescripcion(texto: String) = _estado.update { it.copy(descripcion = texto, errorDescripcion = null) }

    /** Agrega las fotos elegidas hasta completar el máximo del backend; si sobran, se avisa. */
    fun agregarFotos(origenes: List<String>) {
        val aceptadas = origenes.take(_estado.value.fotosDisponibles)
        if (origenes.size > aceptadas.size) {
            _estado.update { it.copy(aviso = "Una acción puede tener máximo $MAXIMO_EVIDENCIAS fotos de evidencia.") }
        }
        if (aceptadas.isEmpty()) return
        _estado.update { it.copy(preparandoFotos = it.preparandoFotos + aceptadas.size, errorFotos = null) }
        for (origen in aceptadas) {
            viewModelScope.launch {
                val archivo = runCatching { preparador.preparar(origen) }.getOrNull()
                _estado.update {
                    if (archivo == null) {
                        it.copy(preparandoFotos = it.preparandoFotos - 1, aviso = "No pudimos usar una de las fotos. Prueba con otra.")
                    } else {
                        it.copy(preparandoFotos = it.preparandoFotos - 1, fotos = it.fotos + archivo)
                    }
                }
            }
        }
    }

    fun quitarFoto(foto: File) {
        _estado.update { it.copy(fotos = it.fotos - foto, errorFotos = null) }
        preparador.descartar(foto)
    }

    /** Hay algo escrito o elegido que se perdería al salir sin guardar. */
    fun hayCambios(): Boolean = with(_estado.value) {
        (accionCreada == null && descripcion.isNotBlank()) || fotos.isNotEmpty() || preparandoFotos > 0
    }

    /** [sinFotoConfirmado]: el gestor ya respondió "Guardar igual" al aviso de que no hay fotos. */
    fun guardar(sinFotoConfirmado: Boolean = false) {
        val e = _estado.value
        if (e.enviando) return
        if (e.preparandoFotos > 0) {
            _estado.update { it.copy(aviso = "Espera un momento: todavía estamos preparando las fotos.") }
            return
        }
        if (e.accionCreada == null && e.descripcion.isBlank()) {
            _estado.update { it.copy(errorDescripcion = "Cuenta qué hiciste.", aviso = null) }
            return
        }
        // Sin foto la acción se puede guardar, pero no sirve para terminar la tarea (RN-13)
        if (e.fotos.isEmpty() && e.accionCreada == null && !sinFotoConfirmado) {
            _estado.update { it.copy(confirmarSinFoto = true) }
            return
        }

        _estado.update {
            it.copy(enviando = true, aviso = null, errorDescripcion = null, errorFotos = null, confirmarSinFoto = false)
        }
        viewModelScope.launch {
            val avisoLento = launch {
                delay(UN_SEGUNDO)
                _estado.update { it.copy(cargaLarga = true) }
            }
            enviar(e)
            avisoLento.cancel()
            _estado.update { it.copy(enviando = false, cargaLarga = false) }
        }
    }

    fun sinFotoAtendido() = _estado.update { it.copy(confirmarSinFoto = false) }

    private suspend fun enviar(e: RegistrarAccionEstado) {
        val accion = e.accionCreada ?: when (val r = repositorio.crearAccion(idTarea, e.descripcion.trim())) {
            is Resultado.Exito -> r.datos
            is Resultado.Fallo -> {
                // No se creó nada: se muestra el error sin perder lo escrito ni las fotos
                val error = r.error
                val enDescripcion = (error as? ErrorAuth.Validacion)?.campos?.get("descripcion")?.joinToString("\n")
                _estado.update {
                    it.copy(errorDescripcion = enDescripcion, aviso = if (enDescripcion == null) mensajeDeError(error) else null)
                }
                return
            }
        }
        if (e.fotos.isEmpty()) {
            _estado.update { it.copy(guardada = true) }
            return
        }
        when (val r = repositorio.subirEvidencias(accion.id, e.fotos)) {
            is Resultado.Exito -> {
                e.fotos.forEach(preparador::descartar)
                _estado.update { it.copy(fotos = it.fotos - e.fotos.toSet(), guardada = true) }
            }
            is Resultado.Fallo -> {
                val error = r.error
                val enFotos = (error as? ErrorAuth.Validacion)?.campos?.get("fotos")?.joinToString("\n")
                _estado.update {
                    it.copy(
                        accionCreada = accion,
                        errorFotos = enFotos,
                        aviso = "La acción quedó guardada, pero no pudimos subir las fotos: " +
                            "${enFotos ?: mensajeDeError(error)} Toca \"Guardar acción\" para intentarlo de nuevo.",
                    )
                }
            }
        }
    }

    fun guardadaAtendida() = _estado.update { it.copy(guardada = false) }

    fun avisoAtendido() = _estado.update { it.copy(aviso = null) }

    override fun onCleared() {
        // Las copias reducidas solo sirven para este formulario
        _estado.value.fotos.forEach(preparador::descartar)
    }

    private companion object {
        const val UN_SEGUNDO = 1_000L
    }
}
