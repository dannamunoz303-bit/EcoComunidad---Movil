package co.ecocomunidad.app.ui.inicio

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
import androidx.recyclerview.widget.RecyclerView
import co.ecocomunidad.app.R
import co.ecocomunidad.app.databinding.FragmentInicioCiudadanoBinding
import co.ecocomunidad.app.ui.componentes.contenedor
import co.ecocomunidad.app.ui.componentes.observar
import co.ecocomunidad.app.ui.navegacion.ARG_ID_REPORTE
import co.ecocomunidad.app.ui.navegacion.RESULTADO_REPORTES_CAMBIADOS
import co.ecocomunidad.app.ui.navegacion.RESULTADO_REPORTE_CREADO
import co.ecocomunidad.app.ui.navegacion.RESULTADO_REPORTE_ELIMINADO
import co.ecocomunidad.app.ui.navegacion.Ruta
import co.ecocomunidad.app.ui.navegacion.irA
import co.ecocomunidad.app.ui.reportes.EncabezadoAdapter
import co.ecocomunidad.app.ui.reportes.EstadoListaAdapter
import co.ecocomunidad.app.ui.reportes.EstadoVista
import co.ecocomunidad.app.ui.reportes.FiltroReportes
import co.ecocomunidad.app.ui.reportes.ListaReportesEstado
import co.ecocomunidad.app.ui.reportes.ListaReportesViewModel
import co.ecocomunidad.app.ui.reportes.ReportesAdapter
import com.google.android.material.snackbar.Snackbar

/** Pantalla principal del ciudadano: saludo, Cerrar sesión y los reportes ("Todos" y "Mis reportes"). */
class InicioCiudadanoFragment : Fragment() {

    private var _binding: FragmentInicioCiudadanoBinding? = null
    private val binding get() = _binding!!

    private val inicio: InicioViewModel by viewModels {
        viewModelFactory { initializer { InicioViewModel(contenedor.authRepository) } }
    }
    private val lista: ListaReportesViewModel by viewModels {
        viewModelFactory { initializer { ListaReportesViewModel(contenedor.reportesRepository) } }
    }

    private lateinit var encabezado: EncabezadoAdapter
    private lateinit var estadoLista: EstadoListaAdapter
    private lateinit var reportes: ReportesAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInicioCiudadanoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        encabezado = EncabezadoAdapter(
            alCerrarSesion = {
                inicio.cerrarSesion {
                    // La vista puede haberse ido mientras se borraba la sesión
                    if (view.isAttachedToWindow) findNavController().irA(Ruta.Login())
                }
            },
            alElegirFiltro = lista::elegirFiltro,
        )
        estadoLista = EstadoListaAdapter(alReintentar = lista::reintentar)
        reportes = ReportesAdapter { reporte ->
            findNavController().navigate(R.id.detalleReporteFragment, bundleOf(ARG_ID_REPORTE to reporte.id))
        }

        binding.lista.layoutManager = LinearLayoutManager(requireContext())
        binding.lista.adapter = ConcatAdapter(encabezado, estadoLista, reportes)
        binding.recargar.setColorSchemeResources(R.color.verde_bosque)
        binding.recargar.setOnRefreshListener { lista.recargar() }

        binding.botonNuevoReporte.setOnClickListener { findNavController().navigate(R.id.crearReporteFragment) }
        // Al bajar por la lista el botón queda solo con el ícono, para no tapar los reportes
        binding.lista.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) binding.botonNuevoReporte.shrink() else if (dy < 0) binding.botonNuevoReporte.extend()
            }
        })
        escucharResultados()

        observar(inicio.usuario) { usuario ->
            encabezado.encabezado = encabezado.encabezado.copy(nombre = usuario?.nombre)
        }
        observar(lista.estado, ::mostrarEstado)
    }

    /**
     * Lo que dejan las otras pantallas al volver: reporte creado ("Mis reportes" recién pedido),
     * eliminado o editado (se recarga la pestaña actual).
     */
    private fun escucharResultados() {
        val resultados = findNavController().getBackStackEntry(R.id.inicioCiudadanoFragment).savedStateHandle
        fun alRecibir(clave: String, accion: () -> Unit) {
            observar(resultados.getStateFlow(clave, false)) { llego ->
                if (!llego) return@observar
                resultados[clave] = false
                accion()
            }
        }
        alRecibir(RESULTADO_REPORTE_CREADO) {
            lista.reporteCreado()
            avisar(R.string.reporte_creado)
        }
        alRecibir(RESULTADO_REPORTE_ELIMINADO) {
            lista.reportesCambiados()
            avisar(R.string.reporte_eliminado)
        }
        alRecibir(RESULTADO_REPORTES_CAMBIADOS) { lista.reportesCambiados() }
    }

    private fun avisar(texto: Int) {
        Snackbar.make(binding.raiz, texto, Snackbar.LENGTH_LONG).setAnchorView(binding.botonNuevoReporte).show()
    }

    private fun mostrarEstado(estado: ListaReportesEstado) {
        encabezado.encabezado = encabezado.encabezado.copy(filtro = estado.filtro)
        estadoLista.estado = when {
            estado.cargando -> EstadoVista.Cargando(estado.cargaLarga, R.string.reportes_cargando)
            estado.error != null -> EstadoVista.Error(R.string.reportes_error_titulo, estado.error)
            estado.vacio -> EstadoVista.Vacio(
                if (estado.filtro == FiltroReportes.MIOS) R.string.reportes_vacio_mios
                else R.string.reportes_vacio_todos,
            )
            else -> null
        }
        reportes.submitList(estado.reportes)
        binding.recargar.isRefreshing = estado.recargando
        // Mientras carga por primera vez el indicador ya está en la lista
        binding.recargar.isEnabled = !estado.cargando

        estado.avisoRecarga?.let {
            Snackbar.make(binding.raiz, it, Snackbar.LENGTH_LONG).setAnchorView(binding.botonNuevoReporte).show()
            lista.avisoAtendido()
        }
    }

    override fun onDestroyView() {
        binding.lista.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
