package co.ecocomunidad.app.ui.reportes.crear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.ReportesRepository
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.modelo.Categoria
import co.ecocomunidad.app.data.modelo.NuevoReporte
import co.ecocomunidad.app.ui.navegacion.mensajeDeError
import kotlinx.coroutines.delay
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

/** Campos del formulario. [claves] son los nombres en ReporteCrearSerializer del backend. */
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

data class CrearReporteEstado(
    val categorias: List<Categoria> = emptyList(),
    val cargandoCategorias: Boolean = true,
    val errorCategorias: String? = null,
    val categoria: Categoria? = null,
    val textos: Map<CampoReporte, String> = emptyMap(),
    /** JPEG ya reducidos, en el orden en que se agregaron. */
    val fotos: List<File> = emptyList(),
    /** Fotos que todavía se están reduciendo. */
    val preparandoFotos: Int = 0,
    val ubicacion: Ubicacion? = null,
    val errores: Map<CampoReporte, String> = emptyMap(),
    val enviando: Boolean = false,
    /** El envío pasó de 1 s: se muestra Brotico pensando (marca.md). */
    val cargaLarga: Boolean = false,
    val aviso: String? = null,
    /** Se creó el reporte: la pantalla vuelve a la lista. */
    val creado: Boolean = false,
) {
    fun texto(campo: CampoReporte): String = textos[campo].orEmpty()

    /** Cuántas fotos más se pueden agregar sin pasar el máximo del backend. */
    val fotosDisponibles: Int get() = (MAXIMO_FOTOS - fotos.size - preparandoFotos).coerceAtLeast(0)
}

/**
 * Crear reporte (RN-06). Aquí solo se revisa que no falte nada de lo que el backend exige;
 * el resto (categoría activa, rango de coordenadas, formato y peso de las fotos) lo valida el backend.
 */
class CrearReporteViewModel(
    private val repositorio: ReportesRepository,
    private val preparador: PreparadorFotos,
) : ViewModel() {

    private val _estado = MutableStateFlow(CrearReporteEstado())
    val estado: StateFlow<CrearReporteEstado> = _estado.asStateFlow()

    init {
        cargarCategorias()
    }

    fun reintentarCategorias() = cargarCategorias()

    private fun cargarCategorias() {
        _estado.update { it.copy(cargandoCategorias = true, errorCategorias = null) }
        viewModelScope.launch {
            when (val r = repositorio.categorias()) {
                is Resultado.Exito -> _estado.update { e ->
                    // Si la elegida ya no está activa, se vuelve a elegir
                    val sigue = e.categoria?.takeIf { elegida -> r.datos.any { it.id == elegida.id } }
                    e.copy(categorias = r.datos, cargandoCategorias = false, categoria = sigue)
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
                        it.copy(preparandoFotos = it.preparandoFotos - 1, fotos = it.fotos + archivo)
                    }
                }
            }
        }
    }

    fun quitarFoto(archivo: File) {
        _estado.update { it.copy(fotos = it.fotos - archivo) }
        preparador.descartar(archivo)
    }

    fun ponerUbicacion(latitud: Double, longitud: Double) = _estado.update {
        it.copy(ubicacion = Ubicacion(latitud, longitud), errores = it.errores - CampoReporte.UBICACION)
    }

    fun enviar() {
        val e = _estado.value
        if (e.enviando) return
        if (e.preparandoFotos > 0) {
            _estado.update { it.copy(aviso = "Espera un momento: todavía estamos preparando las fotos.") }
            return
        }

        val errores = erroresLocales(e)
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(errores = errores, aviso = null) }
            return
        }
        val categoria = e.categoria!!
        val ubicacion = e.ubicacion!!
        val nuevo = NuevoReporte(
            categoria = categoria.id,
            titulo = e.texto(CampoReporte.TITULO).trim(),
            descripcion = e.texto(CampoReporte.DESCRIPCION).trim(),
            direccion = e.texto(CampoReporte.DIRECCION).trim(),
            descripcionUbicacion = e.texto(CampoReporte.DESCRIPCION_UBICACION).trim().ifEmpty { null },
            latitud = ubicacion.latitud,
            longitud = ubicacion.longitud,
            fotos = e.fotos,
        )

        _estado.update { it.copy(enviando = true, aviso = null, errores = emptyMap()) }
        viewModelScope.launch {
            val avisoLento = launch {
                delay(UN_SEGUNDO)
                _estado.update { it.copy(cargaLarga = true) }
            }
            val r = repositorio.crear(nuevo)
            avisoLento.cancel()

            when (r) {
                is Resultado.Exito -> _estado.update { it.copy(enviando = false, cargaLarga = false, creado = true) }
                // Si el backend rechaza algo, no se creó nada (RN-06): se muestra el mensaje en su campo
                is Resultado.Fallo -> _estado.update {
                    it.conError(r.error).copy(enviando = false, cargaLarga = false)
                }
            }
        }
    }

    fun creadoAtendido() = _estado.update { it.copy(creado = false) }

    fun avisoAtendido() = _estado.update { it.copy(aviso = null) }

    override fun onCleared() {
        // Las copias reducidas solo sirven para este formulario
        _estado.value.fotos.forEach(preparador::descartar)
    }

    private fun erroresLocales(e: CrearReporteEstado): Map<CampoReporte, String> = buildMap {
        if (e.categoria == null) put(CampoReporte.CATEGORIA, "Elige una categoría.")
        if (e.texto(CampoReporte.TITULO).isBlank()) put(CampoReporte.TITULO, "Escribe un título.")
        if (e.texto(CampoReporte.DESCRIPCION).isBlank()) put(CampoReporte.DESCRIPCION, "Describe el problema.")
        if (e.texto(CampoReporte.DIRECCION).isBlank()) put(CampoReporte.DIRECCION, "Escribe la dirección.")
        if (e.fotos.isEmpty()) put(CampoReporte.FOTOS, "Agrega al menos una foto.")
        if (e.ubicacion == null) put(CampoReporte.UBICACION, "Marca el lugar en el mapa o usa tu ubicación.")
    }

    /** Los mensajes del backend se muestran tal cual en su campo; los que no son de un campo, en el aviso. */
    private fun CrearReporteEstado.conError(error: ErrorAuth): CrearReporteEstado {
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

    private companion object {
        const val UN_SEGUNDO = 1_000L
    }
}
