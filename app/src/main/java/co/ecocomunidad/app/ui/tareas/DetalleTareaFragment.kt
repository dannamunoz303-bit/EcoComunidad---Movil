package co.ecocomunidad.app.ui.tareas

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import co.ecocomunidad.app.R
import co.ecocomunidad.app.data.modelo.Accion
import co.ecocomunidad.app.data.modelo.EstadoTarea
import co.ecocomunidad.app.data.modelo.Foto
import co.ecocomunidad.app.data.modelo.Reporte
import co.ecocomunidad.app.data.modelo.TareaGestor
import co.ecocomunidad.app.databinding.FragmentDetalleTareaBinding
import co.ecocomunidad.app.databinding.ItemAccionBinding
import co.ecocomunidad.app.databinding.ItemFotoCarruselBinding
import co.ecocomunidad.app.databinding.ItemFotoEvidenciaBinding
import co.ecocomunidad.app.ui.componentes.contenedor
import co.ecocomunidad.app.ui.componentes.mostrar
import co.ecocomunidad.app.ui.componentes.observar
import co.ecocomunidad.app.ui.navegacion.ARG_ID_TAREA
import co.ecocomunidad.app.ui.navegacion.ARG_URL_FOTO
import co.ecocomunidad.app.ui.navegacion.RESULTADO_ACCION_REGISTRADA
import co.ecocomunidad.app.ui.reportes.EstadoVista
import co.ecocomunidad.app.ui.reportes.cargarFoto
import co.ecocomunidad.app.ui.reportes.fechaLarga
import co.ecocomunidad.app.ui.reportes.mostrarEtiquetaEstado
import co.ecocomunidad.app.ui.reportes.mostrar as mostrarEstadoCarga
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker

/** Detalle de una tarea del gestor: el reporte, el mapa, mis acciones e iniciar o terminar (RN-13). */
class DetalleTareaFragment : Fragment() {

    private var _binding: FragmentDetalleTareaBinding? = null
    private val binding get() = _binding!!

    private val idTarea: Int get() = requireArguments().getInt(ARG_ID_TAREA)

    private val viewModel: DetalleTareaViewModel by viewModels {
        viewModelFactory {
            initializer { DetalleTareaViewModel(contenedor.tareasRepository, contenedor.reportesRepository, idTarea) }
        }
    }

    /** Lo último que se pintó, para no rehacer fotos, mapa y acciones en cada emisión. */
    private var tareaPintada: TareaGestor? = null
    private var reportePintado: Reporte? = null
    private var accionesPintadas: MisAcciones? = null
    private var pin: Marker? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDetalleTareaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        tareaPintada = null
        reportePintado = null
        accionesPintadas = null
        pin = null
        binding.tituloPantalla.text = getString(R.string.tarea_titulo, idTarea)
        binding.botonVolver.setOnClickListener { findNavController().popBackStack() }
        binding.botonEstado.setOnClickListener { confirmar(viewModel.estado.value.accion) }
        binding.botonRegistrarAccion.setOnClickListener {
            findNavController().navigate(R.id.registrarAccionFragment, bundleOf(ARG_ID_TAREA to idTarea))
        }
        prepararMapa()
        escucharAccionRegistrada()
        observar(viewModel.estado, ::mostrarEstado)
    }

    /** Al volver de Registrar acción con la acción guardada: se recargan Mis acciones y se avisa. */
    private fun escucharAccionRegistrada() {
        val resultados = findNavController().getBackStackEntry(R.id.detalleTareaFragment).savedStateHandle
        observar(resultados.getStateFlow(RESULTADO_ACCION_REGISTRADA, false)) { registrada ->
            if (!registrada) return@observar
            resultados[RESULTADO_ACCION_REGISTRADA] = false
            viewModel.accionRegistrada()
        }
    }

    private fun confirmar(accion: AccionDisponible) {
        val (titulo, mensaje, boton, alAceptar) = when (accion) {
            AccionDisponible.INICIAR ->
                Confirmacion(R.string.iniciar_titulo, R.string.iniciar_mensaje, R.string.iniciar_tarea, viewModel::iniciar)
            AccionDisponible.TERMINAR ->
                Confirmacion(R.string.terminar_titulo, R.string.terminar_mensaje, R.string.terminar_tarea, viewModel::terminar)
            AccionDisponible.NINGUNA -> return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(titulo)
            .setMessage(mensaje)
            .setPositiveButton(boton) { _, _ -> alAceptar() }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private data class Confirmacion(val titulo: Int, val mensaje: Int, val boton: Int, val alAceptar: () -> Unit)

    private fun mostrarEstado(estado: DetalleTareaEstado) {
        val vistaEstado = when {
            estado.cargando -> EstadoVista.Cargando(estado.cargaLarga, R.string.tarea_cargando)
            estado.error != null -> EstadoVista.Error(R.string.tarea_error_titulo, estado.error)
            else -> null
        }
        binding.estadoCarga.root.visibility = if (vistaEstado == null) View.GONE else View.VISIBLE
        vistaEstado?.let { binding.estadoCarga.mostrarEstadoCarga(it, viewModel::reintentar) }
        binding.contenido.visibility = if (estado.tarea == null) View.GONE else View.VISIBLE

        val tarea = estado.tarea
        if (tarea != null && tarea != tareaPintada) {
            tareaPintada = tarea
            mostrarTarea(tarea)
        }
        if (estado.reporte != reportePintado) {
            reportePintado = estado.reporte
            mostrarReporte(estado.reporte)
        }
        if (estado.acciones != accionesPintadas) {
            accionesPintadas = estado.acciones
            mostrarAcciones(estado.acciones)
        }
        if (tarea != null) mostrarBotones(estado, tarea)

        estado.mensaje?.let {
            Snackbar.make(binding.raiz, it, Snackbar.LENGTH_LONG).show()
            viewModel.mensajeAtendido()
        }
    }

    /** Lo que llega con la tarea (tareas/mias/): siempre está, aunque no se pueda pedir el reporte. */
    private fun mostrarTarea(tarea: TareaGestor) = with(binding) {
        val reporte = tarea.reporte
        categoria.text = reporte.categoria
        estado.mostrarEtiquetaEstado(tarea.estado.etiqueta, tarea.estado.color)
        titulo.text = reporte.titulo
        fechas.text = listOfNotNull(
            getString(R.string.tarea_asignada_el, fechaLarga(tarea.fechaAsignacion)),
            tarea.fechaFin?.let { getString(R.string.tarea_terminada_el, fechaLarga(it)) },
        ).joinToString("\n")
        direccion.text = reporte.direccion
        descripcionUbicacion.text = reporte.descripcionUbicacion
        descripcionUbicacion.visibility = if (reporte.descripcionUbicacion.isNullOrBlank()) View.GONE else View.VISIBLE
        ponerPin(reporte.latitud?.toDoubleOrNull(), reporte.longitud?.toDoubleOrNull())
    }

    /** reportes/{id}/: fotos y descripción del ciudadano. Si no llegó, esas partes no se muestran. */
    private fun mostrarReporte(reporte: Reporte?) = with(binding) {
        mostrarFotos(reporte?.fotos.orEmpty())
        descripcion.text = reporte?.descripcion
        val conDescripcion = !reporte?.descripcion.isNullOrBlank()
        descripcionTitulo.visibility = if (conDescripcion) View.VISIBLE else View.GONE
        descripcion.visibility = if (conDescripcion) View.VISIBLE else View.GONE
    }

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

    private fun mostrarAcciones(acciones: MisAcciones) = with(binding) {
        progresoAcciones.visibility = if (acciones == MisAcciones.Cargando) View.VISIBLE else View.GONE
        this.acciones.removeAllViews()
        val mensaje = when (acciones) {
            is MisAcciones.Error -> getString(R.string.tarea_acciones_error, acciones.mensaje)
            is MisAcciones.Lista -> if (acciones.acciones.isEmpty()) getString(R.string.tarea_sin_acciones) else null
            MisAcciones.Cargando -> null
        }
        accionesMensaje.text = mensaje
        accionesMensaje.visibility = if (mensaje == null) View.GONE else View.VISIBLE
        if (acciones is MisAcciones.Lista) {
            acciones.acciones.forEachIndexed { i, accion ->
                agregarAccion(accion, esUltima = i == acciones.acciones.lastIndex)
            }
        }
    }

    /** Misma línea de tiempo que el detalle del reporte, sin el nombre del gestor (son las mías). */
    private fun agregarAccion(accion: Accion, esUltima: Boolean) {
        val item = ItemAccionBinding.inflate(layoutInflater, binding.acciones, false)
        item.fecha.text = fechaLarga(accion.fecha)
        item.gestor.visibility = View.GONE
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

    /** asignada: Iniciar. en_proceso: Terminar con su ayuda. terminada o cancelada: solo un aviso. */
    private fun mostrarBotones(estado: DetalleTareaEstado, tarea: TareaGestor) = with(binding) {
        val accion = estado.accion
        botonRegistrarAccion.visibility = if (estado.puedeRegistrarAccion) View.VISIBLE else View.GONE
        botonRegistrarAccion.isEnabled = !estado.procesando
        marcoBoton.visibility = if (accion == AccionDisponible.NINGUNA) View.GONE else View.VISIBLE
        ayudaTerminar.visibility = if (accion == AccionDisponible.TERMINAR) View.VISIBLE else View.GONE
        botonEstado.isEnabled = !estado.procesando
        botonEstado.text = when {
            estado.procesando -> ""
            accion == AccionDisponible.INICIAR -> getString(R.string.iniciar_tarea)
            else -> getString(R.string.terminar_tarea)
        }
        progreso.visibility = if (estado.procesando) View.VISIBLE else View.GONE

        // El mensaje del backend (por ejemplo, falta la foto de evidencia) o, si está cancelada, el aviso
        aviso.mostrar(
            estado.aviso ?: if (tarea.estado == EstadoTarea.CANCELADA) getString(R.string.tarea_aviso_cancelada) else null,
        )
        avisoTerminada.mostrar(
            if (tarea.estado == EstadoTarea.TERMINADA) {
                getString(R.string.tarea_aviso_terminada, tarea.fechaFin?.let(::fechaLarga) ?: "")
            } else {
                null
            },
        )
    }

    private fun abrirFoto(url: String) {
        findNavController().navigate(R.id.fotoFragment, bundleOf(ARG_URL_FOTO to url))
    }

    // --- Mapa (osmdroid, igual que en el formulario del reporte) ---

    @SuppressLint("ClickableViewAccessibility")
    private fun prepararMapa() = with(binding.mapa) {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        // La política de OpenStreetMap pide mostrar la atribución
        overlays.add(CopyrightOverlay(context))
        // Dentro de la pantalla que se desplaza, el gesto sobre el mapa es para el mapa
        setOnTouchListener { vista, evento ->
            if (evento.action == MotionEvent.ACTION_DOWN) vista.parent.requestDisallowInterceptTouchEvent(true)
            false
        }
    }

    /** Pin fijo en el lugar del reporte. Sin coordenadas, el mapa no se muestra. */
    private fun ponerPin(latitud: Double?, longitud: Double?) {
        val mapa = binding.mapa
        binding.marcoMapa.visibility = if (latitud == null || longitud == null) View.GONE else View.VISIBLE
        if (latitud == null || longitud == null) return
        val punto = GeoPoint(latitud, longitud)
        val marcador = pin ?: Marker(mapa).apply {
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.pin_mapa)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            setOnMarkerClickListener { _, _ -> true }
            mapa.overlays.add(this)
            pin = this
        }
        marcador.position = punto
        mapa.controller.setZoom(ZOOM)
        mapa.controller.setCenter(punto)
        mapa.invalidate()
    }

    override fun onResume() {
        super.onResume()
        binding.mapa.onResume()
    }

    override fun onPause() {
        binding.mapa.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        binding.mapa.onDetach()
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val ZOOM = 17.0
    }
}
