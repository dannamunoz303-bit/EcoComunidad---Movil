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
import co.ecocomunidad.app.databinding.FragmentMisTareasBinding
import co.ecocomunidad.app.ui.componentes.contenedor
import co.ecocomunidad.app.ui.componentes.observar
import co.ecocomunidad.app.ui.inicio.InicioViewModel
import co.ecocomunidad.app.ui.navegacion.ARG_ID_TAREA
import co.ecocomunidad.app.ui.notificaciones.CampanaViewModel
import co.ecocomunidad.app.ui.reportes.EstadoListaAdapter
import co.ecocomunidad.app.ui.reportes.EstadoVista
import com.google.android.material.snackbar.Snackbar

/**
 * Inicio del gestor: Mis tareas. Pestañas Activas (asignada y en proceso) y Terminadas.
 * La campana abre Notificaciones y el botón ☰, Mi cuenta.
 */
class MisTareasFragment : Fragment() {

    private var _binding: FragmentMisTareasBinding? = null
    private val binding get() = _binding!!

    private val inicio: InicioViewModel by viewModels {
        viewModelFactory { initializer { InicioViewModel(contenedor.authRepository) } }
    }
    private val tareas: MisTareasViewModel by viewModels {
        viewModelFactory { initializer { MisTareasViewModel(contenedor.tareasRepository, contenedor.reportesRepository) } }
    }
    private val campana: CampanaViewModel by viewModels {
        viewModelFactory { initializer { CampanaViewModel(contenedor.notificacionesRepository) } }
    }

    private lateinit var encabezado: EncabezadoGestorAdapter
    private lateinit var estadoLista: EstadoListaAdapter
    private lateinit var lista: TareasAdapter

    /** La primera vez ya carga el ViewModel; al volver (por ejemplo, del detalle) se pide de nuevo. */
    private var yaSeVio = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMisTareasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        encabezado = EncabezadoGestorAdapter(
            alAbrirNotificaciones = { findNavController().navigate(R.id.notificacionesFragment) },
            alAbrirMiCuenta = { findNavController().navigate(R.id.miCuentaFragment) },
            alElegirFiltro = tareas::elegirFiltro,
        )
        estadoLista = EstadoListaAdapter(alReintentar = tareas::reintentar)
        lista = TareasAdapter { tarea ->
            findNavController().navigate(R.id.detalleTareaFragment, bundleOf(ARG_ID_TAREA to tarea.id))
        }

        binding.lista.layoutManager = LinearLayoutManager(requireContext())
        binding.lista.adapter = ConcatAdapter(encabezado, estadoLista, lista)
        binding.recargar.setColorSchemeResources(R.color.verde_bosque)
        binding.recargar.setOnRefreshListener { tareas.recargar() }

        observar(inicio.usuario) { usuario ->
            encabezado.encabezado = encabezado.encabezado.copy(nombre = usuario?.nombre)
        }
        observar(campana.hayNoLeidas) { hay ->
            encabezado.encabezado = encabezado.encabezado.copy(hayNoLeidas = hay)
        }
        observar(tareas.estado, ::mostrarEstado)
    }

    /** Cada vez que la pantalla vuelve a verse se revisa la campana y, si no es la primera vez, las tareas. */
    override fun onStart() {
        super.onStart()
        campana.actualizar()
        if (yaSeVio) tareas.recargar()
        yaSeVio = true
    }

    private fun mostrarEstado(estado: MisTareasEstado) {
        encabezado.encabezado = encabezado.encabezado.copy(
            filtro = estado.filtro,
            activas = if (estado.cargando || estado.error != null) null else estado.cantidadActivas,
        )
        estadoLista.estado = when {
            estado.cargando -> EstadoVista.Cargando(estado.cargaLarga, R.string.tareas_cargando)
            estado.error != null -> EstadoVista.Error(R.string.tareas_error_titulo, estado.error)
            estado.vacio -> EstadoVista.Vacio(
                if (estado.filtro == FiltroTareas.ACTIVAS) R.string.tareas_vacio_activas
                else R.string.tareas_vacio_terminadas,
            )
            else -> null
        }
        lista.submitList(estado.visibles.map { TareaConFoto(it, estado.fotos[it.reporte.id]) })
        binding.recargar.isRefreshing = estado.recargando
        // Mientras carga por primera vez el indicador ya está en la lista
        binding.recargar.isEnabled = !estado.cargando

        estado.avisoRecarga?.let {
            Snackbar.make(binding.raiz, it, Snackbar.LENGTH_LONG).show()
            tareas.avisoAtendido()
        }
    }

    override fun onDestroyView() {
        binding.lista.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
