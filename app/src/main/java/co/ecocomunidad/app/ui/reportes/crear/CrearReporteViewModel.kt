package co.ecocomunidad.app.ui.reportes.crear

import androidx.lifecycle.viewModelScope
import co.ecocomunidad.app.data.ReportesRepository
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.modelo.NuevoReporte
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Crear reporte (RN-06): todo, fotos incluidas, en una sola petición. */
class CrearReporteViewModel(
    repositorio: ReportesRepository,
    preparador: PreparadorFotos,
) : FormularioReporteViewModel(repositorio, preparador) {

    override fun hayCambios(): Boolean {
        val e = _estado.value
        return e.categoria != null || e.textos.values.any { it.isNotBlank() } ||
            e.fotos.isNotEmpty() || e.preparandoFotos > 0 || e.ubicacion != null
    }

    override fun enviar() {
        val e = _estado.value
        if (e.enviando || !fotosListas()) return

        val errores = erroresLocales(e)
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(errores = errores, aviso = null) }
            return
        }
        val ubicacion = e.ubicacion!!
        val nuevo = NuevoReporte(
            categoria = e.categoria!!.id,
            titulo = e.texto(CampoReporte.TITULO).trim(),
            descripcion = e.texto(CampoReporte.DESCRIPCION).trim(),
            direccion = e.texto(CampoReporte.DIRECCION).trim(),
            descripcionUbicacion = e.texto(CampoReporte.DESCRIPCION_UBICACION).trim().ifEmpty { null },
            latitud = ubicacion.latitud,
            longitud = ubicacion.longitud,
            fotos = e.fotosNuevas,
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
}
