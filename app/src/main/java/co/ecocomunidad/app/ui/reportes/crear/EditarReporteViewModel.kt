package co.ecocomunidad.app.ui.reportes.crear

import androidx.lifecycle.viewModelScope
import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.ReportesRepository
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.modelo.CambiosReporte
import co.ecocomunidad.app.data.modelo.Categoria
import co.ecocomunidad.app.data.modelo.Foto
import co.ecocomunidad.app.data.modelo.Reporte
import co.ecocomunidad.app.ui.navegacion.mensajeDeError
import co.ecocomunidad.app.ui.reportes.ErrorCarga
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Editar un reporte propio mientras está pendiente (RN-07) y sus fotos (RN-15).
 * La app solo abre esta pantalla cuando aplica, pero quien decide es el backend: si responde
 * 400 o 403 se muestra su mensaje.
 */
class EditarReporteViewModel(
    repositorio: ReportesRepository,
    preparador: PreparadorFotos,
    private val id: Int,
) : FormularioReporteViewModel(repositorio, preparador) {

    /** El reporte como está en el backend; se actualiza con cada respuesta para no repetir cambios. */
    private var guardado: Reporte? = null

    init {
        _estado.update { it.copy(conservaUnaFoto = true) }
        cargarReporte()
    }

    fun reintentarReporte() = cargarReporte()

    private fun cargarReporte() {
        _estado.update { it.copy(cargandoReporte = true, errorReporte = null) }
        viewModelScope.launch {
            when (val r = repositorio.detalle(id)) {
                is Resultado.Exito -> {
                    guardado = r.datos
                    _estado.update { it.conReporte(r.datos) }
                }
                is Resultado.Fallo -> _estado.update {
                    it.copy(
                        cargandoReporte = false,
                        errorReporte = ErrorCarga(mensajeDeError(r.error), r.error is ErrorAuth.SinConexion),
                    )
                }
            }
        }
    }

    private fun FormularioReporteEstado.conReporte(reporte: Reporte): FormularioReporteEstado {
        val ubicacion = ubicacionDe(reporte)
        return copy(
            cargandoReporte = false,
            // Si la categoría ya no está entre las activas, se muestra igual: solo se envía si cambia
            categoria = categorias.firstOrNull { it.id == reporte.categoriaId }
                ?: Categoria(reporte.categoriaId, reporte.categoria),
            textos = mapOf(
                CampoReporte.TITULO to reporte.titulo,
                CampoReporte.DESCRIPCION to reporte.descripcion,
                CampoReporte.DIRECCION to reporte.direccion,
                CampoReporte.DESCRIPCION_UBICACION to reporte.descripcionUbicacion.orEmpty(),
            ),
            fotos = reporte.fotos.map { FotoFormulario.Guardada(it) },
            ubicacion = ubicacion,
            ubicacionGuardada = ubicacion,
        )
    }

    private fun ubicacionDe(reporte: Reporte): Ubicacion? {
        val latitud = reporte.latitud?.toDoubleOrNull() ?: return null
        val longitud = reporte.longitud?.toDoubleOrNull() ?: return null
        return Ubicacion(latitud, longitud)
    }

    override fun hayCambios(): Boolean {
        val original = guardado ?: return false
        val e = _estado.value
        return !cambios(original, e).vacio || fotosPorBorrar(original, e).isNotEmpty() ||
            e.fotosNuevas.isNotEmpty() || e.preparandoFotos > 0
    }

    /** Solo los campos que el backend deja editar y que de verdad cambiaron. */
    private fun cambios(original: Reporte, e: FormularioReporteEstado): CambiosReporte {
        fun nuevo(campo: CampoReporte, antes: String) = e.texto(campo).trim().takeIf { it != antes }
        val ubicacion = e.ubicacion?.takeIf { it != ubicacionDe(original) }
        return CambiosReporte(
            categoria = e.categoria?.id?.takeIf { it != original.categoriaId },
            titulo = nuevo(CampoReporte.TITULO, original.titulo),
            descripcion = nuevo(CampoReporte.DESCRIPCION, original.descripcion),
            direccion = nuevo(CampoReporte.DIRECCION, original.direccion),
            descripcionUbicacion = nuevo(CampoReporte.DESCRIPCION_UBICACION, original.descripcionUbicacion.orEmpty()),
            latitud = ubicacion?.latitud,
            longitud = ubicacion?.longitud,
        )
    }

    private fun fotosPorBorrar(original: Reporte, e: FormularioReporteEstado): List<Foto> {
        val quedan = e.fotos.filterIsInstance<FotoFormulario.Guardada>().map { it.foto.id }.toSet()
        return original.fotos.filter { it.id !in quedan }
    }

    override fun enviar() {
        val e = _estado.value
        val original = guardado ?: return
        if (e.enviando || e.cargandoReporte || !fotosListas()) return

        val errores = erroresLocales(e)
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(errores = errores, aviso = null) }
            return
        }
        if (!hayCambios()) {
            _estado.update { it.copy(guardado = true) }
            return
        }

        val cambios = cambios(original, e)
        val porBorrar = fotosPorBorrar(original, e).toMutableList()
        val porSubir = e.fotosNuevas.toMutableList()

        _estado.update { it.copy(enviando = true, aviso = null, errores = emptyMap()) }
        viewModelScope.launch {
            val avisoLento = launch {
                delay(UN_SEGUNDO)
                _estado.update { it.copy(cargaLarga = true) }
            }
            val error = guardar(cambios, porBorrar, porSubir)
            avisoLento.cancel()

            if (error == null) {
                _estado.update { it.copy(enviando = false, cargaLarga = false, guardado = true) }
            } else {
                // El formulario queda con lo que ya está en el backend más lo que faltó por enviar
                val actual = guardado!!
                _estado.update {
                    it.conError(error).copy(
                        enviando = false,
                        cargaLarga = false,
                        fotos = actual.fotos.filter { foto -> foto !in porBorrar }.map { foto -> FotoFormulario.Guardada(foto) } +
                            porSubir.map { archivo -> FotoFormulario.Nueva(archivo) },
                    )
                }
            }
        }
    }

    /**
     * Envía los cambios con las rutas del backend: PATCH de los campos y luego las fotos.
     * El backend no deja pasar de [MAXIMO_FOTOS] ni quedar en cero, así que se alterna: se borra
     * mientras quede al menos una y se sube mientras haya cupo. Devuelve el primer error, o null.
     */
    private suspend fun guardar(cambios: CambiosReporte, porBorrar: MutableList<Foto>, porSubir: MutableList<File>): ErrorAuth? {
        if (!cambios.vacio) {
            when (val r = repositorio.editar(id, cambios)) {
                is Resultado.Exito -> guardado = r.datos
                is Resultado.Fallo -> return r.error
            }
        }
        while (porBorrar.isNotEmpty() || porSubir.isNotEmpty()) {
            var avanzo = false
            while (porBorrar.isNotEmpty() && guardado!!.fotos.size > 1) {
                val foto = porBorrar.first()
                when (val r = repositorio.borrarFoto(id, foto.id)) {
                    is Resultado.Exito -> {
                        porBorrar.removeAt(0)
                        guardado = guardado!!.copy(fotos = guardado!!.fotos - foto)
                        avanzo = true
                    }
                    is Resultado.Fallo -> return r.error
                }
            }
            val cupo = MAXIMO_FOTOS - guardado!!.fotos.size
            if (porSubir.isNotEmpty() && cupo > 0) {
                val lote = porSubir.take(cupo)
                when (val r = repositorio.agregarFotos(id, lote)) {
                    is Resultado.Exito -> {
                        porSubir.removeAll(lote)
                        lote.forEach(::descartar)
                        guardado = r.datos
                        avanzo = true
                    }
                    is Resultado.Fallo -> return r.error
                }
            }
            // No debería pasar: el formulario no deja quedar en cero ni pasar del máximo
            if (!avanzo) return ErrorAuth.Validacion(mapOf("fotos" to listOf("No pudimos ordenar los cambios de las fotos.")))
        }
        return null
    }

    fun guardadoAtendido() = _estado.update { it.copy(guardado = false) }
}
