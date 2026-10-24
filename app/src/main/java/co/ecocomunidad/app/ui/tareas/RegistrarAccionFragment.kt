package co.ecocomunidad.app.ui.tareas

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import co.ecocomunidad.app.R
import co.ecocomunidad.app.data.modelo.MAXIMO_EVIDENCIAS
import co.ecocomunidad.app.databinding.FragmentRegistrarAccionBinding
import co.ecocomunidad.app.databinding.ItemFotoNuevaBinding
import co.ecocomunidad.app.ui.componentes.alEscribir
import co.ecocomunidad.app.ui.componentes.cerrarTeclado
import co.ecocomunidad.app.ui.componentes.contenedor
import co.ecocomunidad.app.ui.componentes.mostrar
import co.ecocomunidad.app.ui.componentes.observar
import co.ecocomunidad.app.ui.componentes.SelectorFotos
import co.ecocomunidad.app.ui.navegacion.ARG_ID_TAREA
import co.ecocomunidad.app.ui.navegacion.RESULTADO_ACCION_REGISTRADA
import co.ecocomunidad.app.ui.reportes.EstadoVista
import co.ecocomunidad.app.ui.reportes.crear.ReductorFotos
import co.ecocomunidad.app.ui.reportes.mostrarEtiquetaEstado
import co.ecocomunidad.app.ui.reportes.mostrar as mostrarEstadoCarga
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

/**
 * Registrar acción (RN-14) con fotos de evidencia (RN-15). La cámara, la galería y la reducción de
 * fotos son las mismas de Crear reporte ([SelectorFotos] y ReductorFotos).
 */
class RegistrarAccionFragment : Fragment() {

    private var _binding: FragmentRegistrarAccionBinding? = null
    private val binding get() = _binding!!

    private val idTarea: Int get() = requireArguments().getInt(ARG_ID_TAREA)

    private val viewModel: RegistrarAccionViewModel by viewModels {
        viewModelFactory {
            initializer {
                RegistrarAccionViewModel(
                    contenedor.tareasRepository,
                    ReductorFotos(requireContext().applicationContext),
                    idTarea,
                )
            }
        }
    }

    private val selectorFotos = SelectorFotos(
        fragmento = this,
        maximo = MAXIMO_EVIDENCIAS,
        disponibles = { viewModel.estado.value.fotosDisponibles },
        alElegir = { viewModel.agregarFotos(it) },
    )

    /** Lo último que se pintó, para no rehacer las miniaturas en cada emisión. */
    private var fotosPintadas: List<File>? = null
    private var estabaEnviando = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRegistrarAccionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fotosPintadas = null
        binding.botonVolver.setOnClickListener { volver() }
        // Atrás del sistema: si hay algo sin guardar, se pregunta antes de perderlo
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { volver() }
        binding.campoDescripcion.alEscribir(viewModel::cambiarDescripcion)
        binding.recuadroCamara.setOnClickListener { selectorFotos.abrirCamara() }
        binding.recuadroGaleria.setOnClickListener { selectorFotos.abrirGaleria() }
        binding.botonGuardar.setOnClickListener {
            cerrarTeclado()
            viewModel.guardar()
        }
        observar(viewModel.estado, ::mostrarEstado)
    }

    private fun mostrarEstado(estado: RegistrarAccionEstado) {
        if (estado.guardada) {
            viewModel.guardadaAtendida()
            avisarAlDetalle()
            findNavController().popBackStack()
            return
        }
        if (estado.confirmarSinFoto) {
            viewModel.sinFotoAtendido()
            preguntarSinFoto()
        }
        mostrarCarga(estado)
        val habilitado = !estado.enviando

        with(binding) {
            estado.tarea?.let {
                tituloTarea.text = getString(R.string.accion_tarea_titulo, it.id, it.reporte.titulo)
                estadoTarea.mostrarEtiquetaEstado(it.estado.etiqueta, it.estado.color)
            }
            // Si la acción ya quedó guardada, la descripción no se puede cambiar: solo faltan las fotos
            campoDescripcion.mostrar(estado.descripcion, estado.errorDescripcion)
            campoDescripcion.isEnabled = habilitado && estado.accionCreada == null

            aviso.mostrar(estado.aviso)
            botonGuardar.isEnabled = habilitado
            botonGuardar.text = if (estado.enviando) "" else getString(R.string.guardar_accion)
            progreso.visibility = if (estado.enviando) View.VISIBLE else View.GONE
            cargaLarga.visibility = if (estado.cargaLarga) View.VISIBLE else View.GONE
        }
        mostrarFotos(estado, habilitado)

        // Si el backend rechazó algo, se lleva al gestor al campo con error o al aviso
        if (estabaEnviando && !estado.enviando) {
            when {
                estado.errorDescripcion != null -> mostrarEnPantalla(binding.campoDescripcion)
                estado.errorFotos != null -> mostrarEnPantalla(binding.seccionFotos)
                estado.aviso != null -> mostrarEnPantalla(binding.aviso.root)
            }
        }
        estabaEnviando = estado.enviando
    }

    /** Mientras se abre la tarea (o si no se pudo) no se muestra el formulario. */
    private fun mostrarCarga(estado: RegistrarAccionEstado) = with(binding) {
        val vistaEstado = when {
            estado.cargandoTarea -> EstadoVista.Cargando(conBrotico = false, texto = R.string.tarea_cargando)
            estado.errorTarea != null -> EstadoVista.Error(R.string.tarea_error_titulo, estado.errorTarea)
            else -> null
        }
        estadoCarga.root.visibility = if (vistaEstado == null) View.GONE else View.VISIBLE
        vistaEstado?.let { estadoCarga.mostrarEstadoCarga(it, viewModel::reintentarTarea) }
        desplazamiento.visibility = if (vistaEstado == null) View.VISIBLE else View.GONE
    }

    private fun mostrarFotos(estado: RegistrarAccionEstado, habilitado: Boolean) = with(binding) {
        contadorFotos.text = getString(R.string.contador_fotos, estado.fotos.size, MAXIMO_EVIDENCIAS)
        fotosPreparando.visibility = if (estado.preparandoFotos > 0) View.VISIBLE else View.GONE
        fotosError.text = estado.errorFotos
        fotosError.visibility = if (estado.errorFotos == null) View.GONE else View.VISIBLE
        val puedeAgregar = habilitado && estado.fotosDisponibles > 0
        for (recuadro in listOf(recuadroCamara, recuadroGaleria)) {
            recuadro.isEnabled = puedeAgregar
            recuadro.alpha = if (puedeAgregar) 1f else ALFA_DESHABILITADO
        }

        if (estado.fotos == fotosPintadas) {
            for (i in 0 until fotos.childCount) fotos.getChildAt(i).findViewById<View>(R.id.boton_quitar).isEnabled = habilitado
            return@with
        }
        fotosPintadas = estado.fotos
        fotos.removeAllViews()
        estado.fotos.forEachIndexed { i, archivo ->
            val item = ItemFotoNuevaBinding.inflate(layoutInflater, fotos, false)
            item.foto.contentDescription = getString(R.string.evidencia_nueva, i + 1)
            item.foto.load(archivo) {
                placeholder(R.drawable.ic_imagen)
                error(R.drawable.ic_imagen_rota)
                crossfade(true)
            }
            item.botonQuitar.contentDescription = getString(R.string.quitar_foto, i + 1)
            item.botonQuitar.isEnabled = habilitado
            item.botonQuitar.setOnClickListener { viewModel.quitarFoto(archivo) }
            fotos.addView(item.root)
        }
    }

    private fun preguntarSinFoto() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sin_foto_titulo)
            .setMessage(R.string.sin_foto_mensaje)
            .setPositiveButton(R.string.guardar_igual) { _, _ -> viewModel.guardar(sinFotoConfirmado = true) }
            .setNegativeButton(R.string.agregar_foto, null)
            .show()
    }

    // --- Salir ---

    /** Volver: si hay algo sin guardar, se pregunta si se descarta. */
    private fun volver() {
        if (viewModel.estado.value.enviando || !viewModel.hayCambios()) {
            salir()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.descartar_titulo)
            .setMessage(R.string.descartar_mensaje)
            .setPositiveButton(R.string.descartar) { _, _ -> salir() }
            .setNegativeButton(R.string.seguir_editando, null)
            .show()
    }

    /** Si la acción alcanzó a guardarse (aunque sin todas sus fotos), el detalle la tiene que mostrar. */
    private fun salir() {
        if (viewModel.estado.value.accionCreada != null) avisarAlDetalle()
        findNavController().popBackStack()
    }

    private fun avisarAlDetalle() {
        findNavController().previousBackStackEntry?.savedStateHandle?.set(RESULTADO_ACCION_REGISTRADA, true)
    }

    private fun mostrarEnPantalla(vista: View) {
        vista.post { vista.requestRectangleOnScreen(Rect(0, 0, vista.width, vista.height), false) }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val ALFA_DESHABILITADO = 0.4f
    }
}
