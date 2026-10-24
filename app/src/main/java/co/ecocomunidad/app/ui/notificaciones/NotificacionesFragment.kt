package co.ecocomunidad.app.ui.notificaciones

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
import co.ecocomunidad.app.R
import co.ecocomunidad.app.databinding.FragmentNotificacionesBinding
import co.ecocomunidad.app.ui.componentes.contenedor
import co.ecocomunidad.app.ui.componentes.observar
import co.ecocomunidad.app.ui.navegacion.ARG_ID_REPORTE
import co.ecocomunidad.app.ui.navegacion.ARG_ID_TAREA
import co.ecocomunidad.app.ui.reportes.EstadoVista
import co.ecocomunidad.app.ui.reportes.mostrar
import com.google.android.material.snackbar.Snackbar

/** Notificaciones del ciudadano y del gestor (sección 9). Se abre desde la campana del inicio. */
class NotificacionesFragment : Fragment() {

    private var _binding: FragmentNotificacionesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NotificacionesViewModel by viewModels {
        viewModelFactory {
            initializer {
                NotificacionesViewModel(
                    contenedor.notificacionesRepository,
                    contenedor.tareasRepository,
                    contenedor.authRepository.usuario,
                )
            }
        }
    }

    private val adaptador = NotificacionesAdapter { viewModel.tocar(it) }

    /** La primera vez ya carga el ViewModel; al volver de otra pantalla se pide de nuevo la lista. */
    private var yaSeVio = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotificacionesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.botonVolver.setOnClickListener { findNavController().popBackStack() }
        binding.lista.adapter = adaptador
        observar(viewModel.estado, ::mostrarEstado)
    }

    override fun onStart() {
        super.onStart()
        if (yaSeVio) viewModel.recargar()
        yaSeVio = true
    }

    private fun mostrarEstado(estado: NotificacionesEstado) {
        estado.abrir?.let {
            viewModel.abrirAtendido()
            abrir(it)
        }
        with(binding) {
            val vistaEstado = when {
                estado.cargando -> EstadoVista.Cargando(estado.cargaLarga, R.string.notificaciones_cargando)
                estado.error != null -> EstadoVista.Error(R.string.notificaciones_error_titulo, estado.error)
                estado.vacio -> EstadoVista.Vacio(R.string.notificaciones_vacio)
                else -> null
            }
            estadoCarga.root.visibility = if (vistaEstado == null) View.GONE else View.VISIBLE
            vistaEstado?.let { estadoCarga.mostrar(it, viewModel::reintentar) }
            lista.visibility = if (vistaEstado == null) View.VISIBLE else View.GONE
            adaptador.submitList(estado.notificaciones)
        }
        estado.aviso?.let {
            Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
            viewModel.avisoAtendido()
        }
    }

    private fun abrir(destino: DestinoNotificacion) {
        val nav = findNavController()
        when (destino) {
            DestinoNotificacion.MisNovedades -> nav.navigate(R.id.misNovedadesFragment)
            DestinoNotificacion.MisSanciones -> nav.navigate(R.id.misSancionesFragment)
            is DestinoNotificacion.Reporte ->
                nav.navigate(R.id.detalleReporteFragment, bundleOf(ARG_ID_REPORTE to destino.id))
            // Mis tareas es el inicio del gestor: está debajo en la pila
            DestinoNotificacion.MisTareas -> nav.popBackStack(R.id.inicioGestorFragment, false)
            is DestinoNotificacion.Tarea ->
                nav.navigate(R.id.detalleTareaFragment, bundleOf(ARG_ID_TAREA to destino.id))
            // El ViewModel ya la cambió por Tarea o MisTareas
            is DestinoNotificacion.TareaDelReporte -> Unit
        }
    }

    override fun onDestroyView() {
        binding.lista.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
