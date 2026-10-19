package co.ecocomunidad.app.ui.sanciones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.ReportesRepository
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.SancionesRepository
import co.ecocomunidad.app.data.modelo.Sancion
import co.ecocomunidad.app.data.modelo.TipoSancion
import co.ecocomunidad.app.ui.navegacion.mensajeDeError
import co.ecocomunidad.app.ui.reportes.ErrorCarga
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

/** Bloqueo vigente según las sanciones (RN-21: la tabla sancion es la fuente de verdad). */
sealed interface Bloqueo {
    data object Ninguno : Bloqueo
    data class Temporal(val hasta: OffsetDateTime) : Bloqueo
    data object Permanente : Bloqueo
}

/**
 * RN-21: el permanente prevalece. Un permanente vigente no tiene fecha_fin (al levantarlo, el backend
 * se la pone). Un temporal está vigente hasta su fecha_fin; si hay varios, cuenta el que termina más tarde.
 */
fun bloqueoVigente(sanciones: List<Sancion>, ahora: OffsetDateTime): Bloqueo {
    if (sanciones.any { it.tipo == TipoSancion.BLOQUEO_PERMANENTE && it.fechaFin == null }) return Bloqueo.Permanente
    val hasta = sanciones
        .filter { it.tipo == TipoSancion.BLOQUEO_TEMPORAL }
        .mapNotNull { it.fechaFin?.let(::instante) }
        .filter { it.isAfter(ahora) }
        .maxOrNull()
    return hasta?.let { Bloqueo.Temporal(it) } ?: Bloqueo.Ninguno
}

private fun instante(fecha: String): OffsetDateTime? = runCatching { OffsetDateTime.parse(fecha) }.getOrNull()

/** Una sanción con el título de su reporte, si se pudo saber. */
data class SancionConReporte(val sancion: Sancion, val tituloReporte: String?)

data class MisSancionesEstado(
    val cargando: Boolean = true,
    /** La carga pasó de 1 s: se muestra Brotico pensando (marca.md). */
    val cargaLarga: Boolean = false,
    /** Más nueva primero. */
    val sanciones: List<SancionConReporte> = emptyList(),
    val bloqueo: Bloqueo = Bloqueo.Ninguno,
    val error: ErrorCarga? = null,
) {
    val vacio: Boolean get() = !cargando && error == null && sanciones.isEmpty()
}

/**
 * Mis sanciones (RN-19 a RN-21). sanciones/mias/ trae solo el id del reporte: el título se busca en
 * "Mis reportes". Si no está (por ejemplo, el reporte se eliminó) o falla, la tarjeta va sin título.
 */
class MisSancionesViewModel(
    private val sanciones: SancionesRepository,
    private val reportes: ReportesRepository,
    private val ahora: () -> OffsetDateTime = OffsetDateTime::now,
) : ViewModel() {

    private val _estado = MutableStateFlow(MisSancionesEstado())
    val estado: StateFlow<MisSancionesEstado> = _estado.asStateFlow()

    private var trabajo: Job? = null

    init {
        cargar()
    }

    fun reintentar() = cargar()

    private fun cargar() {
        trabajo?.cancel()
        _estado.value = MisSancionesEstado()
        trabajo = viewModelScope.launch {
            val avisoLento = launch {
                delay(UN_SEGUNDO)
                _estado.update { it.copy(cargaLarga = true) }
            }
            val r = sanciones.listarMias()

            when (r) {
                is Resultado.Exito -> {
                    val titulos = titulosDeReportes(r.datos)
                    avisoLento.cancel()
                    val ordenadas = r.datos.sortedWith(
                        compareByDescending<Sancion> { instante(it.fechaInicio) }.thenByDescending { it.id },
                    )
                    _estado.value = MisSancionesEstado(
                        cargando = false,
                        sanciones = ordenadas.map { SancionConReporte(it, it.reporteId?.let(titulos::get)) },
                        bloqueo = bloqueoVigente(r.datos, ahora()),
                    )
                }
                is Resultado.Fallo -> {
                    avisoLento.cancel()
                    _estado.value = MisSancionesEstado(
                        cargando = false,
                        error = ErrorCarga(mensajeDeError(r.error), sinConexion = r.error is ErrorAuth.SinConexion),
                    )
                }
            }
        }
    }

    /** Solo se piden los reportes si alguna sanción tiene uno. */
    private suspend fun titulosDeReportes(lista: List<Sancion>): Map<Int, String> {
        if (lista.none { it.reporteId != null }) return emptyMap()
        val r = reportes.listar(soloMios = true)
        return if (r is Resultado.Exito) r.datos.associate { it.id to it.titulo } else emptyMap()
    }

    private companion object {
        const val UN_SEGUNDO = 1_000L
    }
}
