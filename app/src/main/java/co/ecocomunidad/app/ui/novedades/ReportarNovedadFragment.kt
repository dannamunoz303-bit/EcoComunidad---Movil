package co.ecocomunidad.app.ui.novedades

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.addCallback
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import co.ecocomunidad.app.R
import co.ecocomunidad.app.data.modelo.TipoNovedad
import co.ecocomunidad.app.databinding.FragmentReportarNovedadBinding
import co.ecocomunidad.app.ui.componentes.alEscribir
import co.ecocomunidad.app.ui.componentes.cerrarTeclado
import co.ecocomunidad.app.ui.componentes.contenedor
import co.ecocomunidad.app.ui.componentes.mostrar
import co.ecocomunidad.app.ui.componentes.observar
import co.ecocomunidad.app.ui.navegacion.ARG_ID_REPORTE
import co.ecocomunidad.app.ui.navegacion.ARG_ID_TAREA
import co.ecocomunidad.app.ui.navegacion.ARG_NOVEDAD_ENVIADA
import co.ecocomunidad.app.ui.reportes.EstadoVista
import co.ecocomunidad.app.ui.reportes.mostrarEstado
import co.ecocomunidad.app.ui.reportes.mostrarEtiquetaEstado
import co.ecocomunidad.app.ui.tareas.color
import co.ecocomunidad.app.ui.tareas.etiqueta
import co.ecocomunidad.app.ui.reportes.mostrar as mostrarEstadoCarga
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Novedad sobre un reporte (RN-18). Con idReporte (reportarNovedadFragment) es la queja o incidente del
 * ciudadano; con idTarea (pedirAyudaFragment), el pedido de ayuda o incidente del gestor.
 * Al enviar bien, abre "Mis novedades" en su lugar: atrás vuelve al detalle del reporte o de la tarea.
 */
class ReportarNovedadFragment : Fragment() {

    private var _binding: FragmentReportarNovedadBinding? = null
    private val binding get() = _binding!!

    private val modo: ModoNovedad
        get() = with(requireArguments()) {
            if (containsKey(ARG_ID_TAREA)) ModoNovedad.Gestor(getInt(ARG_ID_TAREA))
            else ModoNovedad.Ciudadano(getInt(ARG_ID_REPORTE))
        }

    private val viewModel: ReportarNovedadViewModel by viewModels {
        viewModelFactory {
            initializer {
                ReportarNovedadViewModel(
                    contenedor.reportesRepository,
                    contenedor.tareasRepository,
                    contenedor.novedadesRepository,
                    modo,
                )
            }
        }
    }

    /** La tarjeta de cada tipo. Solo se ven las de [ModoNovedad.tipos]. */
    private val opciones
        get() = mapOf(
            TipoNovedad.AYUDA to binding.opcionAyuda,
            TipoNovedad.QUEJA to binding.opcionQueja,
            TipoNovedad.INCIDENTE to binding.opcionIncidente,
        )

    /** Lo último que se pintó en el desplegable, para no rehacerlo en cada emisión. */
    private var gestoresPintados: List<OpcionGestor>? = null
    private var estabaEnviando = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReportarNovedadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        gestoresPintados = null
        binding.botonVolver.setOnClickListener { volver() }
        // Atrás del sistema: si hay algo sin enviar, se pregunta antes de perderlo
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { volver() }

        prepararModo()
        for ((tipo, opcion) in opciones) opcion.setOnClickListener { viewModel.elegirTipo(tipo) }
        // La posición 0 es "Ninguno en particular"
        binding.textoGestor.setOnItemClickListener { _, _, posicion, _ ->
            viewModel.elegirGestor(gestoresPintados?.getOrNull(posicion - 1))
        }
        binding.campoDescripcion.alEscribir(viewModel::cambiarDescripcion)
        binding.botonEnviar.setOnClickListener {
            cerrarTeclado()
            viewModel.enviar()
            desplazarAlPrimerError()
        }

        observar(viewModel.estado, ::mostrarEstado)
    }

    /** Lo que no cambia mientras se ve la pantalla: textos y campos de cada modo. */
    private fun prepararModo() = with(binding) {
        val gestor = viewModel.modo is ModoNovedad.Gestor
        for ((tipo, opcion) in opciones) {
            opcion.visibility = if (tipo in viewModel.modo.tipos) View.VISIBLE else View.GONE
        }
        tituloPantalla.setText(if (gestor) R.string.pedir_ayuda_titulo else R.string.reportar_novedad)
        etiquetaTarjeta.setText(if (gestor) R.string.novedad_sobre_tarea else R.string.novedad_sobre_reporte)
        explicacionIncidente.setText(if (gestor) R.string.incidente_gestor_explicacion else R.string.tipo_incidente_ayuda)
        avisoRespuesta.setText(if (gestor) R.string.pedir_ayuda_aviso else R.string.novedad_aviso_respuesta)
        campoGestor.visibility = if (gestor) View.GONE else View.VISIBLE
    }

    private fun mostrarEstado(estado: ReportarNovedadEstado) {
        if (estado.enviada) {
            viewModel.enviadaAtendida()
            // Se reemplaza el formulario: atrás desde "Mis novedades" vuelve al detalle
            val nav = findNavController()
            nav.navigate(
                R.id.misNovedadesFragment,
                bundleOf(ARG_NOVEDAD_ENVIADA to true),
                navOptions { popUpTo(nav.currentDestination!!.id) { inclusive = true } },
            )
            return
        }
        mostrarCarga(estado)
        val habilitado = !estado.enviando

        with(binding) {
            estado.reporte?.let {
                tituloReporte.text = it.titulo
                estadoReporte.mostrarEstado(it.estado)
            }
            estado.tarea?.let {
                tituloReporte.text = getString(R.string.accion_tarea_titulo, it.id, it.reporte.titulo)
                estadoReporte.mostrarEtiquetaEstado(it.estado.etiqueta, it.estado.color)
            }

            for ((tipo, opcion) in opciones) {
                opcion.isChecked = estado.tipo == tipo
                opcion.isEnabled = habilitado
            }
            val mensajeTipo = estado.errores[CampoNovedad.TIPO]
            errorTipo.text = mensajeTipo
            errorTipo.visibility = if (mensajeTipo == null) View.GONE else View.VISIBLE

            if (viewModel.modo is ModoNovedad.Ciudadano) mostrarGestores(estado, habilitado)
            campoDescripcion.mostrar(estado.descripcion, estado.errores[CampoNovedad.DESCRIPCION])
            campoDescripcion.isEnabled = habilitado

            aviso.mostrar(estado.aviso)
            botonEnviar.isEnabled = habilitado
            botonEnviar.text = when {
                estado.enviando -> ""
                viewModel.modo is ModoNovedad.Gestor -> getString(R.string.boton_enviar)
                else -> getString(R.string.boton_enviar_novedad)
            }
            progreso.visibility = if (estado.enviando) View.VISIBLE else View.GONE
            cargaLarga.visibility = if (estado.cargaLarga) View.VISIBLE else View.GONE
        }

        // Si el backend rechazó algo, se lleva al ciudadano al campo con error o al aviso
        if (estabaEnviando && !estado.enviando) {
            if (estado.errores.isNotEmpty()) desplazarAlPrimerError()
            else if (estado.aviso != null) mostrarEnPantalla(binding.aviso.root)
        }
        estabaEnviando = estado.enviando
    }

    /** Mientras se abre el reporte o la tarea (o si no se pudo) no se muestra el formulario. */
    private fun mostrarCarga(estado: ReportarNovedadEstado) = with(binding) {
        val gestor = viewModel.modo is ModoNovedad.Gestor
        val vistaEstado = when {
            estado.cargandoReporte -> EstadoVista.Cargando(
                conBrotico = false,
                texto = if (gestor) R.string.tarea_cargando else R.string.novedad_cargando,
            )
            estado.errorReporte != null -> EstadoVista.Error(
                if (gestor) R.string.tarea_error_titulo else R.string.novedad_error_titulo,
                estado.errorReporte,
            )
            else -> null
        }
        estadoCarga.root.visibility = if (vistaEstado == null) View.GONE else View.VISIBLE
        vistaEstado?.let { estadoCarga.mostrarEstadoCarga(it, viewModel::reintentar) }
        desplazamiento.visibility = if (vistaEstado == null) View.VISIBLE else View.GONE
    }

    private fun mostrarGestores(estado: ReportarNovedadEstado, habilitado: Boolean) = with(binding) {
        val ninguno = getString(R.string.gestor_ninguno)
        if (estado.gestores != gestoresPintados) {
            gestoresPintados = estado.gestores
            val nombres = listOf(ninguno) + estado.gestores.map { it.nombre }
            textoGestor.setAdapter(ArrayAdapter(requireContext(), R.layout.item_categoria, nombres))
        }
        val elegido = estado.gestor?.nombre ?: ninguno
        if (textoGestor.text?.toString() != elegido) textoGestor.setText(elegido, false)

        campoGestor.isEnabled = habilitado && !estado.cargandoGestores
        val error = estado.errores[CampoNovedad.GESTOR]
        if (campoGestor.error?.toString() != error) campoGestor.error = error
        campoGestor.helperText = when {
            error != null -> null
            estado.cargandoGestores -> getString(R.string.gestores_cargando)
            estado.errorGestores != null -> getString(R.string.gestores_error)
            else -> null
        }
    }

    /** Volver: si hay algo sin enviar, se pregunta si se descarta. */
    private fun volver() {
        if (viewModel.estado.value.enviando || !viewModel.hayCambios()) {
            findNavController().popBackStack()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.descartar_titulo)
            .setMessage(R.string.descartar_mensaje)
            .setPositiveButton(R.string.descartar) { _, _ -> findNavController().popBackStack() }
            .setNegativeButton(R.string.seguir_editando, null)
            .show()
    }

    private fun desplazarAlPrimerError() {
        val errores = viewModel.estado.value.errores
        val vista = when {
            CampoNovedad.TIPO in errores -> opciones.getValue(viewModel.modo.tipos.first())
            CampoNovedad.GESTOR in errores -> binding.campoGestor
            CampoNovedad.DESCRIPCION in errores -> binding.campoDescripcion
            else -> return
        }
        mostrarEnPantalla(vista)
    }

    private fun mostrarEnPantalla(vista: View) {
        vista.post { vista.requestRectangleOnScreen(Rect(0, 0, vista.width, vista.height), false) }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
