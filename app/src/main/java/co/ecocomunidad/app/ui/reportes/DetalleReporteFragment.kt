package co.ecocomunidad.app.ui.reportes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import co.ecocomunidad.app.R
import co.ecocomunidad.app.data.modelo.EstadoReporte
import co.ecocomunidad.app.data.modelo.Foto
import co.ecocomunidad.app.data.modelo.Reporte
import co.ecocomunidad.app.databinding.FragmentDetalleReporteBinding
import co.ecocomunidad.app.databinding.ItemAccionBinding
import co.ecocomunidad.app.databinding.ItemFotoCarruselBinding
import co.ecocomunidad.app.databinding.ItemFotoEvidenciaBinding
import co.ecocomunidad.app.ui.componentes.contenedor
import co.ecocomunidad.app.ui.componentes.mostrar
import co.ecocomunidad.app.ui.componentes.observar
import co.ecocomunidad.app.ui.navegacion.ARG_ID_REPORTE
import co.ecocomunidad.app.ui.navegacion.ARG_URL_FOTO

/**
 * Detalle de un reporte. Muestra solo lo que entrega el backend: sin cédula (RN-02) y sin el nombre
 * del creador; el motivo del rechazo, solo si el reporte es del ciudadano con sesión.
 */
class DetalleReporteFragment : Fragment() {

    private var _binding: FragmentDetalleReporteBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DetalleReporteViewModel by viewModels {
        viewModelFactory {
            initializer {
                DetalleReporteViewModel(
                    contenedor.reportesRepository,
                    contenedor.authRepository.usuario,
                    requireArguments().getInt(ARG_ID_REPORTE),
                )
            }
        }
    }

    /** Lo último que se pintó, para no rehacer las vistas de fotos y acciones en cada emisión. */
    private var reportePintado: Reporte? = null
    private var lineaPintada: LineaDeTiempo? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDetalleReporteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        reportePintado = null
        lineaPintada = null
        binding.botonVolver.setOnClickListener { findNavController().popBackStack() }
        observar(viewModel.estado, ::mostrarEstado)
    }

    private fun mostrarEstado(estado: DetalleReporteEstado) {
        val reporte = estado.reporte
        val vistaEstado = when {
            estado.cargando -> EstadoVista.Cargando(estado.cargaLarga, R.string.detalle_cargando)
            estado.error != null -> EstadoVista.Error(R.string.detalle_error_titulo, estado.error)
            else -> null
        }
        binding.estadoCarga.root.visibility = if (vistaEstado == null) View.GONE else View.VISIBLE
        vistaEstado?.let { binding.estadoCarga.mostrar(it, viewModel::reintentar) }
        binding.contenido.visibility = if (reporte == null) View.GONE else View.VISIBLE

        if (reporte != null && reporte != reportePintado) {
            reportePintado = reporte
            mostrarReporte(reporte, estado.esPropio)
        }
        if (estado.linea != lineaPintada) {
            lineaPintada = estado.linea
            mostrarLinea(estado.linea)
        }
    }

    private fun mostrarReporte(reporte: Reporte, esPropio: Boolean) = with(binding) {
        mostrarFotos(reporte.fotos)
        categoria.text = reporte.categoria
        estado.mostrarEstado(reporte.estado)
        titulo.text = reporte.titulo
        descripcion.text = reporte.descripcion
        direccion.text = reporte.direccion
        descripcionUbicacion.text = reporte.descripcionUbicacion
        descripcionUbicacion.visibility = if (reporte.descripcionUbicacion.isNullOrBlank()) View.GONE else View.VISIBLE

        val motivo = reporte.motivoRechazo
            ?.takeIf { esPropio && reporte.estado == EstadoReporte.RECHAZADO && it.isNotBlank() }
        motivoRechazo.mostrar(motivo?.let { getString(R.string.detalle_motivo_rechazo, it) })
        resuelto.visibility = if (reporte.estado == EstadoReporte.RESUELTO) View.VISIBLE else View.GONE

        fechas.text = listOfNotNull(
            getString(R.string.detalle_fecha_creacion, fechaLarga(reporte.fechaCreacion)),
            reporte.fechaRevision?.let { getString(R.string.detalle_fecha_revision, fechaLarga(it)) },
            getString(R.string.detalle_fecha_actualizacion, fechaLarga(reporte.fechaActualizacion)),
        ).joinToString("\n")
    }

    /** Carrusel horizontal de las fotos del ciudadano; tocar una la abre en grande. */
    private fun mostrarFotos(lista: List<Foto>) {
        binding.fotos.removeAllViews()
        binding.carrusel.visibility = if (lista.isEmpty()) View.GONE else View.VISIBLE
        lista.forEachIndexed { i, foto ->
            val item = ItemFotoCarruselBinding.inflate(layoutInflater, binding.fotos, false)
            item.foto.cargarFoto(foto.url)
            item.root.contentDescription = getString(R.string.foto_numero, i + 1, lista.size)
            item.root.setOnClickListener { abrirFoto(foto.url) }
            binding.fotos.addView(item.root)
        }
    }

    private fun mostrarLinea(linea: LineaDeTiempo) = with(binding) {
        seccionAcciones.visibility = if (linea == LineaDeTiempo.NoDisponible) View.GONE else View.VISIBLE
        progresoAcciones.visibility = if (linea == LineaDeTiempo.Cargando) View.VISIBLE else View.GONE
        acciones.removeAllViews()

        val mensaje = when (linea) {
            is LineaDeTiempo.Error -> getString(R.string.detalle_acciones_error, linea.mensaje)
            is LineaDeTiempo.Lista -> if (linea.acciones.isEmpty()) getString(R.string.detalle_sin_acciones) else null
            else -> null
        }
        accionesMensaje.text = mensaje
        accionesMensaje.visibility = if (mensaje == null) View.GONE else View.VISIBLE

        if (linea is LineaDeTiempo.Lista) {
            linea.acciones.forEachIndexed { i, accion ->
                agregarAccion(accion, esUltima = i == linea.acciones.lastIndex)
            }
        }
    }

    private fun agregarAccion(accionDelGestor: AccionDelGestor, esUltima: Boolean) {
        val (accion, gestor) = accionDelGestor
        val item = ItemAccionBinding.inflate(layoutInflater, binding.acciones, false)
        item.fecha.text = fechaLarga(accion.fecha)
        // RN-14: el ciudadano dueño ve el nombre del gestor
        item.gestor.text = gestor?.let { getString(R.string.detalle_gestor, it.nombre, it.apellido) }
        item.gestor.visibility = if (gestor == null) View.GONE else View.VISIBLE
        item.descripcion.text = accion.descripcion
        item.linea.visibility = if (esUltima) View.INVISIBLE else View.VISIBLE

        item.carruselEvidencias.visibility = if (accion.fotos.isEmpty()) View.GONE else View.VISIBLE
        accion.fotos.forEachIndexed { i, foto ->
            val miniatura = ItemFotoEvidenciaBinding.inflate(layoutInflater, item.evidencias, false)
            miniatura.foto.cargarFoto(foto.url)
            miniatura.root.contentDescription = getString(R.string.evidencia_numero, i + 1, accion.fotos.size)
            miniatura.root.setOnClickListener { abrirFoto(foto.url) }
            item.evidencias.addView(miniatura.root)
        }
        binding.acciones.addView(
            item.root,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )
    }

    private fun abrirFoto(url: String) {
        findNavController().navigate(R.id.fotoFragment, bundleOf(ARG_URL_FOTO to url))
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
