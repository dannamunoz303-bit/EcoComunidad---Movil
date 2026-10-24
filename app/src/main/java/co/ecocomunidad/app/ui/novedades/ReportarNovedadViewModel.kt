package co.ecocomunidad.app.ui.novedades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.NovedadesRepository
import co.ecocomunidad.app.data.ReportesRepository
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.TareasRepository
import co.ecocomunidad.app.data.modelo.NuevaNovedad
import co.ecocomunidad.app.data.modelo.Reporte
import co.ecocomunidad.app.data.modelo.Tarea
import co.ecocomunidad.app.data.modelo.TareaGestor
import co.ecocomunidad.app.data.modelo.TipoNovedad
import co.ecocomunidad.app.ui.navegacion.mensajeDeError
import co.ecocomunidad.app.ui.reportes.ErrorCarga
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Campos del formulario. [claves] son los nombres en CrearNovedadSerializer. */
enum class CampoNovedad(val claves: Set<String>) {
    TIPO(setOf("tipo")),
    GESTOR(setOf("tarea")),
    DESCRIPCION(setOf("descripcion")),
}

/** Un gestor del reporte para el desplegable. Se envía el id de su tarea (tarea_id). */
data class OpcionGestor(val tareaId: Int, val nombre: String)

/** Quién escribe la novedad (RN-18): cambia los tipos, lo que se muestra arriba y lo que se envía. */
sealed interface ModoNovedad {
    /** Los tipos que puede elegir, en el orden de la pantalla. */
    val tipos: List<TipoNovedad>

    /** Los campos que se ven en el formulario. Un error del backend en otro campo va al aviso. */
    val campos: Set<CampoNovedad>

    /** Queja o incidente sobre un reporte propio. El gestor (tarea_id) es opcional. */
    data class Ciudadano(val idReporte: Int) : ModoNovedad {
        override val tipos = listOf(TipoNovedad.QUEJA, TipoNovedad.INCIDENTE)
        override val campos = CampoNovedad.entries.toSet()
    }

    /** Pedir ayuda o avisar un incidente sobre su tarea. La tarea es obligatoria y no se elige. */
    data class Gestor(val idTarea: Int) : ModoNovedad {
        override val tipos = listOf(TipoNovedad.AYUDA, TipoNovedad.INCIDENTE)
        override val campos = setOf(CampoNovedad.TIPO, CampoNovedad.DESCRIPCION)
    }
}

data class ReportarNovedadEstado(
    /** Lo de la tarjeta de arriba: el reporte (ciudadano) o la tarea (gestor). */
    val cargandoReporte: Boolean = true,
    val reporte: Reporte? = null,
    /** Solo el gestor: su tarea, con lo básico del reporte. */
    val tarea: TareaGestor? = null,
    val errorReporte: ErrorCarga? = null,
    /** Gestores con tarea en el reporte, sin repetir. Vacía si no hay o no se pudieron cargar. */
    val gestores: List<OpcionGestor> = emptyList(),
    val cargandoGestores: Boolean = true,
    val errorGestores: String? = null,
    val tipo: TipoNovedad? = null,
    /** null = "Ninguno en particular" (tarea_id es opcional para el ciudadano, RN-18). */
    val gestor: OpcionGestor? = null,
    val descripcion: String = "",
    val errores: Map<CampoNovedad, String> = emptyMap(),
    val enviando: Boolean = false,
    /** El envío pasó de 1 s: se muestra Brotico pensando (marca.md). */
    val cargaLarga: Boolean = false,
    /** Mensaje del backend que no es de un campo (por ejemplo, el estado del reporte ya no lo permite). */
    val aviso: String? = null,
    /** Se creó: la pantalla abre "Mis novedades". */
    val enviada: Boolean = false,
)

/**
 * Novedad sobre un reporte (RN-18). El ciudadano se queja o avisa de un incidente sobre su reporte;
 * el gestor pide ayuda o avisa de un incidente sobre su tarea activa (ver [ModoNovedad]).
 * Aquí solo se revisa que no falte nada; si ya no se permite, lo dice el backend.
 */
class ReportarNovedadViewModel(
    private val reportes: ReportesRepository,
    private val tareas: TareasRepository,
    private val novedades: NovedadesRepository,
    val modo: ModoNovedad,
) : ViewModel() {

    private val _estado = MutableStateFlow(ReportarNovedadEstado())
    val estado: StateFlow<ReportarNovedadEstado> = _estado.asStateFlow()

    private var trabajo: Job? = null

    init {
        cargar()
    }

    fun reintentar() = cargar()

    private fun cargar() {
        trabajo?.cancel()
        trabajo = when (modo) {
            is ModoNovedad.Ciudadano -> cargarReporte(modo.idReporte)
            is ModoNovedad.Gestor -> cargarTarea(modo.idTarea)
        }
    }

    /** El reporte (para la tarjeta de arriba) y sus gestores. Si fallan los gestores, se puede enviar sin elegir. */
    private fun cargarReporte(idReporte: Int): Job {
        _estado.update {
            it.copy(cargandoReporte = true, errorReporte = null, cargandoGestores = true, errorGestores = null)
        }
        return viewModelScope.launch {
            launch { cargarGestores(idReporte) }
            when (val r = reportes.detalle(idReporte)) {
                is Resultado.Exito -> _estado.update { it.copy(cargandoReporte = false, reporte = r.datos) }
                is Resultado.Fallo -> _estado.update { it.copy(cargandoReporte = false, errorReporte = errorCarga(r.error)) }
            }
        }
    }

    /** La tarea del gestor (tareas/mias/): de ahí salen el reporte y la tarjeta de arriba. No hay desplegable. */
    private fun cargarTarea(idTarea: Int): Job {
        _estado.update { it.copy(cargandoReporte = true, errorReporte = null, cargandoGestores = false) }
        return viewModelScope.launch {
            when (val r = tareas.tarea(idTarea)) {
                is Resultado.Exito -> _estado.update { it.copy(cargandoReporte = false, tarea = r.datos) }
                is Resultado.Fallo -> _estado.update { it.copy(cargandoReporte = false, errorReporte = errorCarga(r.error)) }
            }
        }
    }

    private fun errorCarga(error: ErrorAuth) =
        ErrorCarga(mensajeDeError(error), sinConexion = error is ErrorAuth.SinConexion)

    private suspend fun cargarGestores(idReporte: Int) {
        when (val r = reportes.tareas(idReporte)) {
            is Resultado.Exito -> _estado.update { it.copy(cargandoGestores = false, gestores = opciones(r.datos)) }
            is Resultado.Fallo -> _estado.update {
                it.copy(cargandoGestores = false, errorGestores = mensajeDeError(r.error))
            }
        }
    }

    /** Un gestor con varias tareas en el reporte aparece una vez, con la más reciente (el backend las manda así). */
    private fun opciones(tareas: List<Tarea>): List<OpcionGestor> =
        tareas.mapNotNull { tarea -> tarea.gestor?.let { it.id to OpcionGestor(tarea.id, "${it.nombre} ${it.apellido}") } }
            .distinctBy { it.first }
            .map { it.second }

    fun elegirTipo(tipo: TipoNovedad) {
        if (tipo !in modo.tipos) return
        _estado.update { it.copy(tipo = tipo, errores = it.errores - CampoNovedad.TIPO) }
    }

    /** null = "Ninguno en particular". */
    fun elegirGestor(gestor: OpcionGestor?) = _estado.update {
        it.copy(gestor = gestor, errores = it.errores - CampoNovedad.GESTOR)
    }

    fun cambiarDescripcion(texto: String) = _estado.update {
        it.copy(descripcion = texto, errores = it.errores - CampoNovedad.DESCRIPCION)
    }

    /** Hay algo escrito o elegido que se perdería al salir sin enviar. */
    fun hayCambios(): Boolean = with(_estado.value) { tipo != null || gestor != null || descripcion.isNotBlank() }

    fun enviar() {
        val e = _estado.value
        if (e.enviando) return

        val errores = buildMap {
            if (e.tipo == null) put(CampoNovedad.TIPO, errorTipo())
            if (e.descripcion.isBlank()) put(CampoNovedad.DESCRIPCION, "Cuéntanos qué pasó.")
        }
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(errores = errores, aviso = null) }
            return
        }
        val nueva = when (modo) {
            is ModoNovedad.Ciudadano -> NuevaNovedad(
                reporteId = modo.idReporte,
                tipo = e.tipo!!,
                descripcion = e.descripcion.trim(),
                tareaId = e.gestor?.tareaId,
            )
            // El formulario solo se ve con la tarea cargada
            is ModoNovedad.Gestor -> NuevaNovedad(
                reporteId = e.tarea?.reporte?.id ?: return,
                tipo = e.tipo!!,
                descripcion = e.descripcion.trim(),
                tareaId = modo.idTarea,
            )
        }

        _estado.update { it.copy(enviando = true, aviso = null, errores = emptyMap()) }
        viewModelScope.launch {
            val avisoLento = launch {
                delay(UN_SEGUNDO)
                _estado.update { it.copy(cargaLarga = true) }
            }
            val r = novedades.crear(nueva)
            avisoLento.cancel()

            when (r) {
                is Resultado.Exito -> _estado.update { it.copy(enviando = false, cargaLarga = false, enviada = true) }
                is Resultado.Fallo -> _estado.update {
                    it.conError(r.error).copy(enviando = false, cargaLarga = false)
                }
            }
        }
    }

    fun enviadaAtendida() = _estado.update { it.copy(enviada = false) }

    private fun errorTipo() = when (modo) {
        is ModoNovedad.Ciudadano -> "Elige si es una queja o un incidente."
        is ModoNovedad.Gestor -> "Elige si necesitas ayuda o es un incidente."
    }

    /**
     * Los mensajes del backend van en su campo; los demás ("reporte", "detail"…) y los de campos que
     * no se ven (la tarea del gestor), en el aviso.
     */
    private fun ReportarNovedadEstado.conError(error: ErrorAuth): ReportarNovedadEstado {
        if (error !is ErrorAuth.Validacion) return copy(aviso = mensajeDeError(error))
        val porCampo = mutableMapOf<CampoNovedad, MutableList<String>>()
        val otros = mutableListOf<String>()
        for ((clave, mensajes) in error.campos) {
            val campo = modo.campos.firstOrNull { clave in it.claves }
            if (campo != null) porCampo.getOrPut(campo) { mutableListOf() } += mensajes else otros += mensajes
        }
        val aviso = when {
            otros.isNotEmpty() -> otros.joinToString("\n")
            porCampo.isEmpty() -> mensajeDeError(error)
            else -> null
        }
        return copy(errores = porCampo.mapValues { it.value.joinToString("\n") }, aviso = aviso)
    }

    private companion object {
        const val UN_SEGUNDO = 1_000L
    }
}
