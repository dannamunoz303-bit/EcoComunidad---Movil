package co.ecocomunidad.app.ui.reportes.crear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.ReportesRepository
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.modelo.Categoria
import co.ecocomunidad.app.data.modelo.Foto
import co.ecocomunidad.app.ui.navegacion.mensajeDeError
import co.ecocomunidad.app.ui.reportes.ErrorCarga
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** MAXIMO_FOTOS_REPORTE del backend (reportes/servicios.py, RN-15). */
const val MAXIMO_FOTOS = 5

/** Reduce las fotos elegidas antes de subirlas. La implementación real es [ReductorFotos]. */
interface PreparadorFotos {
    /** Toma la foto de [origen] (uri de la cámara o de la galería) y devuelve un JPEG reducido listo para subir. */
    suspend fun preparar(origen: String): File

    fun descartar(archivo: File)
}

/** Campos del formulario. [claves] son los nombres en ReporteCrearSerializer y ReporteEditarSerializer. */
enum class CampoReporte(val claves: Set<String>) {
    CATEGORIA(setOf("categoria")),
    TITULO(setOf("titulo")),
    DESCRIPCION(setOf("descripcion")),
    DIRECCION(setOf("direccion")),
    DESCRIPCION_UBICACION(setOf("descripcion_ubicacion")),
    FOTOS(setOf("fotos")),

    /** Latitud y longitud: no las escribe el ciudadano, salen del GPS o del pin (RN-06). */
    UBICACION(setOf("latitud", "longitud")),
}

data class Ubicacion(val latitud: Double, val longitud: Double)

/** Una foto del formulario: ya guardada en el backend o nueva (JPEG reducido en el teléfono). */
sealed interface FotoFormulario {
    data class Guardada(val foto: Foto) : FotoFormulario
    data class Nueva(val archivo: File) : FotoFormulario
}

data class FormularioReporteEstado(
    val categorias: List<Categoria> = emptyList(),
    val cargandoCategorias: Boolean = true,
    val errorCategorias: String? = null,
    val categoria: Categoria? = null,
    val textos: Map<CampoReporte, String> = emptyMap(),
    /** En el orden en que se ven. */
    val fotos: List<FotoFormulario> = emptyList(),
    /** Fotos que todavía se están reduciendo. */
    val preparandoFotos: Int = 0,
    val ubicacion: Ubicacion? = null,
    val errores: Map<CampoReporte, String> = emptyMap(),
    val enviando: Boolean = false,
    /** El envío pasó de 1 s: se muestra Brotico pensando (marca.md). */
    val cargaLarga: Boolean = false,
    val aviso: String? = null,
    /** Crear: se creó el reporte y la pantalla vuelve a la lista. */
    val creado: Boolean = false,
    /** Editar: se guardaron los cambios y la pantalla vuelve al detalle. */
    val guardado: Boolean = false,
    // --- Solo al editar ---
    val cargandoReporte: Boolean = false,
    val errorReporte: ErrorCarga? = null,
    /** El lugar que tenía el reporte, para centrar el mapa al abrir. */
    val ubicacionGuardada: Ubicacion? = null,
    /** Al editar, el reporte nunca puede quedar sin al menos una foto (RN-15). */
    val conservaUnaFoto: Boolean = false,
) {
    fun texto(campo: CampoReporte): String = textos[campo].orEmpty()

    /** Cuántas fotos más se pueden agregar sin pasar el máximo del backend. */
    val fotosDisponibles: Int get() = (MAXIMO_FOTOS - fotos.size - preparandoFotos).coerceAtLeast(0)

    val puedeQuitarFotos: Boolean get() = !conservaUnaFoto || fotos.size > 1

    val fotosNuevas: List<File> get() = fotos.filterIsInstance<FotoFormulario.Nueva>().map { it.archivo }
}

typealias CrearReporteEstado = FormularioReporteEstado

/**
 * Lo común a crear y editar un reporte. Aquí solo se revisa que no falte nada de lo que el backend
 * exige; el resto (categoría activa, rango de coordenadas, formato y peso de las fotos) lo valida el backend.
 */
abstract class FormularioReporteViewModel(
    protected val repositorio: ReportesRepository,
    private val preparador: PreparadorFotos,
) : ViewModel() {

    protected val _estado = MutableStateFlow(FormularioReporteEstado())
    val estado: StateFlow<FormularioReporteEstado> = _estado.asStateFlow()

    init {
        cargarCategorias()
    }

    abstract fun enviar()

    /** Hay algo escrito o elegido que se perdería al salir sin enviar. */
    abstract fun hayCambios(): Boolean

    fun reintentarCategorias() = cargarCategorias()

    private fun cargarCategorias() {
        _estado.update { it.copy(cargandoCategorias = true, errorCategorias = null) }
        viewModelScope.launch {
            when (val r = repositorio.categorias()) {
                is Resultado.Exito -> _estado.update { e ->
                    // La elegida se conserva aunque ya no esté activa: al editar no se envía si no cambia
                    val elegida = e.categoria?.let { actual -> r.datos.firstOrNull { it.id == actual.id } ?: actual }
                    e.copy(categorias = r.datos, cargandoCategorias = false, categoria = elegida)
                }
                is Resultado.Fallo -> _estado.update {
                    it.copy(cargandoCategorias = false, errorCategorias = mensajeDeError(r.error))
                }
            }
        }
    }

    fun elegirCategoria(id: Int) = _estado.update { e ->
        val categoria = e.categorias.firstOrNull { it.id == id } ?: return@update e
        e.copy(categoria = categoria, errores = e.errores - CampoReporte.CATEGORIA)
    }

    fun cambiar(campo: CampoReporte, valor: String) = _estado.update {
        it.copy(textos = it.textos + (campo to valor), errores = it.errores - campo)
    }

    /** Agrega las fotos elegidas hasta completar el máximo del backend; si sobran, se avisa. */
    fun agregarFotos(origenes: List<String>) {
        val aceptadas = origenes.take(_estado.value.fotosDisponibles)
        val sobran = origenes.size > aceptadas.size
        if (sobran) _estado.update { it.copy(aviso = "Un reporte puede tener máximo $MAXIMO_FOTOS fotos.") }
        if (aceptadas.isEmpty()) return
        _estado.update {
            it.copy(preparandoFotos = it.preparandoFotos + aceptadas.size, errores = it.errores - CampoReporte.FOTOS)
        }
        for (origen in aceptadas) {
            viewModelScope.launch {
                val archivo = runCatching { preparador.preparar(origen) }.getOrNull()
                _estado.update {
                    if (archivo == null) {
                        it.copy(preparandoFotos = it.preparandoFotos - 1, aviso = "No pudimos usar una de las fotos. Prueba con otra.")
                    } else {
                        it.copy(preparandoFotos = it.preparandoFotos - 1, fotos = it.fotos + FotoFormulario.Nueva(archivo))
                    }
                }
            }
        }
    }

    /** Al editar, la última foto no se puede quitar (RN-15; es el mismo mensaje del backend). */
    fun quitarFoto(foto: FotoFormulario) {
        if (!_estado.value.puedeQuitarFotos) {
            _estado.update { it.copy(aviso = "No se puede borrar la última foto del reporte.") }
            return
        }
        _estado.update { it.copy(fotos = it.fotos - foto) }
        if (foto is FotoFormulario.Nueva) preparador.descartar(foto.archivo)
    }

    fun ponerUbicacion(latitud: Double, longitud: Double) = _estado.update {
        it.copy(ubicacion = Ubicacion(latitud, longitud), errores = it.errores - CampoReporte.UBICACION)
    }

    fun avisoAtendido() = _estado.update { it.copy(aviso = null) }

    override fun onCleared() {
        // Las copias reducidas solo sirven para este formulario
        _estado.value.fotosNuevas.forEach(preparador::descartar)
    }

    protected fun descartar(archivo: File) = preparador.descartar(archivo)

    /** Antes de enviar: si faltan fotos por reducir, se avisa y no se envía. */
    protected fun fotosListas(): Boolean {
        if (_estado.value.preparandoFotos == 0) return true
        _estado.update { it.copy(aviso = "Espera un momento: todavía estamos preparando las fotos.") }
        return false
    }

    /** Lo que el backend exige y falta. */
    protected fun erroresLocales(e: FormularioReporteEstado): Map<CampoReporte, String> = buildMap {
        if (e.categoria == null) put(CampoReporte.CATEGORIA, "Elige una categoría.")
        if (e.texto(CampoReporte.TITULO).isBlank()) put(CampoReporte.TITULO, "Escribe un título.")
        if (e.texto(CampoReporte.DESCRIPCION).isBlank()) put(CampoReporte.DESCRIPCION, "Describe el problema.")
        if (e.texto(CampoReporte.DIRECCION).isBlank()) put(CampoReporte.DIRECCION, "Escribe la dirección.")
        if (e.fotos.isEmpty()) put(CampoReporte.FOTOS, "Agrega al menos una foto.")
        if (e.ubicacion == null) put(CampoReporte.UBICACION, "Marca el lugar en el mapa o usa tu ubicación.")
    }

    /** Los mensajes del backend se muestran tal cual en su campo; los que no son de un campo, en el aviso. */
    protected fun FormularioReporteEstado.conError(error: ErrorAuth): FormularioReporteEstado {
        if (error !is ErrorAuth.Validacion) return copy(aviso = mensajeDeError(error))
        val porCampo = mutableMapOf<CampoReporte, MutableList<String>>()
        val otros = mutableListOf<String>()
        for ((clave, mensajes) in error.campos) {
            val campo = CampoReporte.entries.firstOrNull { clave in it.claves }
            if (campo != null) porCampo.getOrPut(campo) { mutableListOf() } += mensajes else otros += mensajes
        }
        val aviso = when {
            otros.isNotEmpty() -> otros.joinToString("\n")
            porCampo.isEmpty() -> mensajeDeError(error) // 400 sin campos reconocibles
            else -> null
        }
        return copy(errores = porCampo.mapValues { it.value.joinToString("\n") }, aviso = aviso)
    }

    protected companion object {
        const val UN_SEGUNDO = 1_000L
    }
}
