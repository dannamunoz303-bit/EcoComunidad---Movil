package co.ecocomunidad.app.ui.tareas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import co.ecocomunidad.app.R
import co.ecocomunidad.app.databinding.FragmentTodosLosReportesBinding
import co.ecocomunidad.app.ui.componentes.contenedor
import co.ecocomunidad.app.ui.componentes.observar
import co.ecocomunidad.app.ui.navegacion.ARG_ID_REPORTE
import co.ecocomunidad.app.ui.navegacion.ARG_ID_TAREA
import co.ecocomunidad.app.ui.reportes.EstadoListaAdapter
import co.ecocomunidad.app.ui.reportes.EstadoVista
import co.ecocomunidad.app.ui.reportes.ListaReportesEstado
import co.ecocomunidad.app.ui.reportes.ListaReportesViewModel
import co.ecocomunidad.app.ui.reportes.ReportesAdapter
import com.google.android.material.snackbar.Snackbar

/**
 * "Todos los reportes" del gestor (RN-10): la pestaña Todos del ciudadano, sin pestañas ni botón de
 * crear. El backend solo le manda los aprobados, asignados, en proceso y resueltos. Tocar uno abre
 * su tarea si tiene una en ese reporte; si no, el reporte en solo lectura.
 */
class TodosLosReportesFragment : Fragment() {

    private var _binding: FragmentTodosLosReportesBinding? = null
    private val binding get() = _binding!!

    private val lista: ListaReportesViewModel by viewModels {
        viewModelFactory { initializer { ListaReportesViewModel(contenedor.reportesRepository) } }
    }
    private val abrir: AbrirReporteViewModel by viewModels {
        viewModelFactory { initializer { AbrirReporteViewModel(contenedor.tareasRepository) } }
    }

    private lateinit var estadoLista: EstadoListaAdapter
    private lateinit var reportes: ReportesAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTodosLosReportesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.botonVolver.setOnClickListener { findNavController().popBackStack() }
        estadoLista = EstadoListaAdapter(alReintentar = lista::reintentar)
        reportes = ReportesAdapter { reporte -> abrir.tocar(reporte.id) }

        binding.lista.layoutManager = LinearLayoutManager(requireContext())
        binding.lista.adapter = ConcatAdapter(estadoLista, reportes)
        binding.recargar.setColorSchemeResources(R.color.verde_bosque)
        binding.recargar.setOnRefreshListener { lista.recargar() }

        observar(lista.estado, ::mostrarEstado)
        observar(abrir.estado, ::mostrarAbrir)
    }

    private fun mostrarEstado(estado: ListaReportesEstado) {
        estadoLista.estado = when {
            estado.cargando -> EstadoVista.Cargando(estado.cargaLarga, R.string.reportes_cargando)
            estado.error != null -> EstadoVista.Error(R.string.reportes_error_titulo, estado.error)
            estado.vacio -> EstadoVista.Vacio(R.string.todos_reportes_vacio)
            else -> null
        }
        reportes.submitList(estado.reportes)
        binding.recargar.isRefreshing = estado.recargando
        // Mientras carga por primera vez el indicador ya está en la lista
        binding.recargar.isEnabled = !estado.cargando

        estado.avisoRecarga?.let {
            Snackbar.make(binding.raiz, it, Snackbar.LENGTH_LONG).show()
            lista.avisoAtendido()
        }
    }

    private fun mostrarAbrir(estado: AbrirReporteEstado) {
        binding.progresoAbrir.visibility = if (estado.buscando) View.VISIBLE else View.GONE
        estado.abrir?.let {
            abrir.abrirAtendido()
            when (it) {
                is DestinoReporte.Tarea ->
                    findNavController().navigate(R.id.detalleTareaFragment, bundleOf(ARG_ID_TAREA to it.id))
                is DestinoReporte.Reporte ->
                    findNavController().navigate(R.id.detalleReporteFragment, bundleOf(ARG_ID_REPORTE to it.id))
            }
        }
        estado.aviso?.let {
            Snackbar.make(binding.raiz, it, Snackbar.LENGTH_LONG).show()
            abrir.avisoAtendido()
        }
    }

    override fun onDestroyView() {
        binding.lista.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
