package co.ecocomunidad.app.ui.reportes.crear

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.addCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import co.ecocomunidad.app.R
import co.ecocomunidad.app.data.modelo.Categoria
import co.ecocomunidad.app.databinding.FragmentCrearReporteBinding
import co.ecocomunidad.app.databinding.ItemFotoNuevaBinding
import co.ecocomunidad.app.ui.componentes.alEscribir
import co.ecocomunidad.app.ui.componentes.cerrarTeclado
import co.ecocomunidad.app.ui.componentes.contenedor
import co.ecocomunidad.app.ui.componentes.mostrar
import co.ecocomunidad.app.ui.componentes.observar
import co.ecocomunidad.app.ui.navegacion.ARG_ID_REPORTE
import co.ecocomunidad.app.ui.navegacion.RESULTADO_REPORTE_CREADO
import co.ecocomunidad.app.ui.navegacion.RESULTADO_REPORTE_EDITADO
import co.ecocomunidad.app.ui.reportes.EstadoVista
import co.ecocomunidad.app.ui.reportes.cargarFoto
import co.ecocomunidad.app.ui.reportes.mostrar as mostrarEstadoCarga
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * Formulario del reporte. Sin argumentos crea uno nuevo (RN-06); con [ARG_ID_REPORTE] edita uno
 * propio que está pendiente (RN-07, RN-15). La latitud y la longitud no las escribe el ciudadano:
 * salen del GPS ("Usar mi ubicación") o del pin que mueve en el mapa.
 */
class CrearReporteFragment : Fragment() {

    private var _binding: FragmentCrearReporteBinding? = null
    private val binding get() = _binding!!

    /** null al crear; el id del reporte al editar. */
    private val idReporte: Int?
        get() = arguments?.takeIf { it.containsKey(ARG_ID_REPORTE) }?.getInt(ARG_ID_REPORTE)

    private val editando: Boolean get() = idReporte != null

    private val viewModel: FormularioReporteViewModel by viewModels {
        viewModelFactory {
            initializer {
                val repositorio = contenedor.reportesRepository
                val reductor = ReductorFotos(requireContext().applicationContext)
                val id = idReporte
                if (id == null) CrearReporteViewModel(repositorio, reductor)
                else EditarReporteViewModel(repositorio, reductor, id)
            }
        }
    }

    /** Dónde guarda la cámara la foto que está tomando. Se conserva si el sistema cierra la app mientras tanto. */
    private var uriCamara: Uri? = null

    private val tomarFoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { tomada ->
        val uri = uriCamara
        uriCamara = null
        if (tomada && uri != null) viewModel.agregarFotos(listOf(uri.toString()))
    }

    // El selector de fotos del sistema (Photo Picker): no necesita permisos de almacenamiento
    private val elegirVarias = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAXIMO_FOTOS)) { uris ->
        viewModel.agregarFotos(uris.map { it.toString() })
    }
    private val elegirUna = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.agregarFotos(listOf(uri.toString()))
    }

    private val pedirUbicacion = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permisos ->
        if (permisos.values.any { it }) obtenerUbicacion() else mostrarMensajeUbicacion(R.string.ubicacion_sin_permiso)
    }

    private var busquedaUbicacion: CancellationTokenSource? = null
    private var pin: Marker? = null

    /** Lo último que se pintó, para no rehacer vistas en cada emisión del estado. */
    private var categoriasPintadas: List<Categoria>? = null
    private var fotosPintadas: List<FotoFormulario>? = null
    private var ubicacionPintada: Ubicacion? = null
    private var estabaEnviando = false

    /** El mapa se centra cuando la ubicación llega del GPS; al tocar o arrastrar, no. */
    private var centrarEnLaUbicacion = false
    private var mensajeUbicacion: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uriCamara = savedInstanceState?.getString(ESTADO_URI_CAMARA)?.toUri()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCrearReporteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        categoriasPintadas = null
        fotosPintadas = null
        ubicacionPintada = null
        pin = null

        binding.botonVolver.setOnClickListener { volver() }
        // Atrás del sistema: si hay algo sin enviar, se pregunta antes de perderlo
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { volver() }
        binding.tituloPantalla.setText(if (editando) R.string.editar_reporte else R.string.nuevo_reporte)
        binding.textoCargaLarga.setText(if (editando) R.string.editar_guardando else R.string.crear_enviando)
        binding.botonReintentarCategorias.setOnClickListener { viewModel.reintentarCategorias() }
        binding.textoCategoria.setOnItemClickListener { _, _, posicion, _ ->
            categoriasPintadas?.getOrNull(posicion)?.let { viewModel.elegirCategoria(it.id) }
        }
        campos.forEach { (campo, vista) -> vista.alEscribir { viewModel.cambiar(campo, it) } }

        binding.botonTomarFoto.setOnClickListener { abrirCamara() }
        binding.botonElegirFotos.setOnClickListener { abrirGaleria() }
        binding.botonMiUbicacion.setOnClickListener { usarMiUbicacion() }
        binding.botonEnviar.setOnClickListener {
            cerrarTeclado()
            viewModel.enviar()
            // Los faltantes se revisan al instante: se lleva al ciudadano al primero
            desplazarAlPrimerError(viewModel.estado.value.errores)
        }

        prepararMapa()
        observar(viewModel.estado, ::mostrarEstado)
    }

    private val campos: Map<CampoReporte, TextInputLayout>
        get() = mapOf(
            CampoReporte.TITULO to binding.campoTitulo,
            CampoReporte.DESCRIPCION to binding.campoDescripcion,
            CampoReporte.DIRECCION to binding.campoDireccion,
            CampoReporte.DESCRIPCION_UBICACION to binding.campoDescripcionUbicacion,
        )

    private fun mostrarEstado(estado: FormularioReporteEstado) {
        if (estado.creado || estado.guardado) {
            when (val vm = viewModel) {
                is CrearReporteViewModel -> vm.creadoAtendido()
                is EditarReporteViewModel -> vm.guardadoAtendido()
            }
            val resultado = if (estado.creado) RESULTADO_REPORTE_CREADO else RESULTADO_REPORTE_EDITADO
            findNavController().previousBackStackEntry?.savedStateHandle?.set(resultado, true)
            findNavController().popBackStack()
            return
        }
        mostrarCargaDelReporte(estado)
        val habilitado = !estado.enviando
        mostrarCategorias(estado, habilitado)
        campos.forEach { (campo, vista) ->
            vista.mostrar(estado.texto(campo), estado.errores[campo])
            vista.isEnabled = habilitado
        }
        mostrarFotos(estado, habilitado)
        mostrarUbicacion(estado, habilitado)

        with(binding) {
            aviso.mostrar(estado.aviso)
            botonEnviar.isEnabled = habilitado
            botonEnviar.text = when {
                estado.enviando -> ""
                editando -> getString(R.string.boton_guardar_cambios)
                else -> getString(R.string.boton_enviar_reporte)
            }
            progreso.visibility = if (estado.enviando) View.VISIBLE else View.GONE
            cargaLarga.visibility = if (estado.cargaLarga) View.VISIBLE else View.GONE
        }

        // Si el backend rechazó algo, se lleva al ciudadano al primer campo con error (o al aviso)
        if (estabaEnviando && !estado.enviando) {
            if (estado.errores.isNotEmpty()) desplazarAlPrimerError(estado.errores)
            else if (estado.aviso != null) mostrarEnPantalla(binding.aviso.root)
        }
        estabaEnviando = estado.enviando
    }

    /** Al editar, mientras se abre el reporte (o si no se pudo) no se muestra el formulario. */
    private fun mostrarCargaDelReporte(estado: FormularioReporteEstado) = with(binding) {
        val vistaEstado = when {
            estado.cargandoReporte -> EstadoVista.Cargando(conBrotico = false, texto = R.string.editar_cargando)
            estado.errorReporte != null -> EstadoVista.Error(R.string.editar_error_titulo, estado.errorReporte)
            else -> null
        }
        estadoCarga.root.visibility = if (vistaEstado == null) View.GONE else View.VISIBLE
        vistaEstado?.let { estadoCarga.mostrarEstadoCarga(it) { (viewModel as? EditarReporteViewModel)?.reintentarReporte() } }
        desplazamiento.visibility = if (vistaEstado == null) View.VISIBLE else View.GONE
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

    // --- Categoría ---

    private fun mostrarCategorias(estado: FormularioReporteEstado, habilitado: Boolean) = with(binding) {
        if (estado.categorias != categoriasPintadas) {
            categoriasPintadas = estado.categorias
            textoCategoria.setAdapter(ArrayAdapter(requireContext(), R.layout.item_categoria, estado.categorias.map { it.nombre }))
        }
        val nombre = estado.categoria?.nombre.orEmpty()
        if (textoCategoria.text?.toString() != nombre) textoCategoria.setText(nombre, false)

        campoCategoria.isEnabled = habilitado && estado.categorias.isNotEmpty()
        val error = estado.errorCategorias ?: estado.errores[CampoReporte.CATEGORIA]
        if (campoCategoria.error?.toString() != error) campoCategoria.error = error
        campoCategoria.helperText = when {
            error != null -> null
            estado.cargandoCategorias -> getString(R.string.categorias_cargando)
            else -> estado.categoria?.descripcion
        }
        botonReintentarCategorias.visibility = if (estado.errorCategorias != null) View.VISIBLE else View.GONE
    }

    // --- Fotos ---

    private fun abrirCamara() {
        val contexto = requireContext()
        val archivo = File(ReductorFotos.carpetaCamara(contexto), "camara_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(contexto, ReductorFotos.autoridadArchivos(contexto), archivo)
        uriCamara = uri
        try {
            tomarFoto.launch(uri)
        } catch (e: ActivityNotFoundException) {
            uriCamara = null
            Snackbar.make(binding.root, R.string.sin_camara, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun abrirGaleria() {
        val solicitud = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        // El selector de varias pide al menos 2; si solo cabe una más, se usa el de una
        if (viewModel.estado.value.fotosDisponibles == 1) elegirUna.launch(solicitud) else elegirVarias.launch(solicitud)
    }

    private fun mostrarFotos(estado: FormularioReporteEstado, habilitado: Boolean) = with(binding) {
        fotosAyuda.text = getString(R.string.fotos_ayuda, MAXIMO_FOTOS, estado.fotos.size)
        fotosPreparando.visibility = if (estado.preparandoFotos > 0) View.VISIBLE else View.GONE
        val error = estado.errores[CampoReporte.FOTOS]
        fotosError.text = error
        fotosError.visibility = if (error == null) View.GONE else View.VISIBLE
        val puedeAgregar = habilitado && estado.fotosDisponibles > 0
        botonTomarFoto.isEnabled = puedeAgregar
        botonElegirFotos.isEnabled = puedeAgregar

        carruselFotos.visibility = if (estado.fotos.isEmpty()) View.GONE else View.VISIBLE
        // Al editar, la última foto no se puede quitar (RN-15): su botón no aparece
        val quitarVisible = if (estado.puedeQuitarFotos) View.VISIBLE else View.GONE
        if (estado.fotos == fotosPintadas) {
            fotos.forEachChild {
                it.findViewById<View>(R.id.boton_quitar).apply {
                    isEnabled = habilitado
                    visibility = quitarVisible
                }
            }
            return@with
        }
        fotosPintadas = estado.fotos
        fotos.removeAllViews()
        estado.fotos.forEachIndexed { i, foto ->
            val item = ItemFotoNuevaBinding.inflate(layoutInflater, fotos, false)
            item.foto.contentDescription = getString(R.string.foto_nueva, i + 1)
            when (foto) {
                is FotoFormulario.Guardada -> item.foto.cargarFoto(foto.foto.url)
                is FotoFormulario.Nueva -> item.foto.load(foto.archivo) {
                    placeholder(R.drawable.ic_imagen)
                    error(R.drawable.ic_imagen_rota)
                    crossfade(true)
                }
            }
            item.botonQuitar.contentDescription = getString(R.string.quitar_foto, i + 1)
            item.botonQuitar.isEnabled = habilitado
            item.botonQuitar.visibility = quitarVisible
            item.botonQuitar.setOnClickListener { viewModel.quitarFoto(foto) }
            fotos.addView(item.root)
        }
    }

    private inline fun ViewGroup.forEachChild(accion: (View) -> Unit) {
        for (i in 0 until childCount) accion(getChildAt(i))
    }

    // --- Ubicación ---

    @SuppressLint("ClickableViewAccessibility")
    private fun prepararMapa() = with(binding.mapa) {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        controller.setZoom(ZOOM_INICIAL)
        controller.setCenter(CENTRO_INICIAL)
        // Tocar el mapa mueve el pin. Va primero para que el pin reciba antes el arrastre
        overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(punto: GeoPoint): Boolean {
                if (viewModel.estado.value.enviando) return false
                viewModel.ponerUbicacion(punto.latitude, punto.longitude)
                return true
            }

            override fun longPressHelper(punto: GeoPoint) = false
        }))
        // La política de OpenStreetMap pide mostrar la atribución
        overlays.add(CopyrightOverlay(context))
        // Dentro del formulario que se desplaza, el gesto sobre el mapa es para el mapa
        setOnTouchListener { vista, evento ->
            if (evento.action == MotionEvent.ACTION_DOWN) vista.parent.requestDisallowInterceptTouchEvent(true)
            false
        }
    }

    private fun mostrarUbicacion(estado: FormularioReporteEstado, habilitado: Boolean) = with(binding) {
        botonMiUbicacion.isEnabled = habilitado && busquedaUbicacion == null
        val ubicacion = estado.ubicacion
        if (ubicacion != ubicacionPintada) {
            // Al abrir para editar, el mapa se centra en el lugar guardado
            if (ubicacionPintada == null && ubicacion != null && ubicacion == estado.ubicacionGuardada) {
                mapa.controller.setZoom(ZOOM_UBICACION)
                mapa.controller.setCenter(GeoPoint(ubicacion.latitud, ubicacion.longitud))
            }
            ubicacionPintada = ubicacion
            if (ubicacion != null) moverPin(ubicacion)
        }
        pin?.isDraggable = habilitado
        coordenadas.text = ubicacion
            ?.let { getString(R.string.ubicacion_elegida, coordenada(it.latitud), coordenada(it.longitud)) }
            ?: getString(R.string.ubicacion_sin_marcar)

        // El error del formulario manda sobre los mensajes del GPS
        val error = estado.errores[CampoReporte.UBICACION]
        val texto = error ?: mensajeUbicacion?.let(::getString)
        ubicacionMensaje.text = texto
        ubicacionMensaje.visibility = if (texto == null) View.GONE else View.VISIBLE
        ubicacionMensaje.setTextColor(
            ContextCompat.getColor(requireContext(), if (error != null) R.color.arcilla else R.color.tinta_suave),
        )
    }

    private fun moverPin(ubicacion: Ubicacion) {
        val mapa = binding.mapa
        val punto = GeoPoint(ubicacion.latitud, ubicacion.longitud)
        val marcador = pin ?: Marker(mapa).apply {
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.pin_mapa)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            setInfoWindow(null)
            setOnMarkerClickListener { _, _ -> true }
            setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                override fun onMarkerDrag(marker: Marker) = Unit
                override fun onMarkerDragStart(marker: Marker) = Unit
                override fun onMarkerDragEnd(marker: Marker) {
                    viewModel.ponerUbicacion(marker.position.latitude, marker.position.longitude)
                }
            })
            mapa.overlays.add(this)
            pin = this
        }
        marcador.position = punto
        if (centrarEnLaUbicacion) {
            centrarEnLaUbicacion = false
            mapa.controller.animateTo(punto, ZOOM_UBICACION, DURACION_CENTRAR_MS)
        }
        mapa.invalidate()
    }

    private fun usarMiUbicacion() {
        val contexto = requireContext()
        val tienePermiso = PERMISOS_UBICACION.any {
            ContextCompat.checkSelfPermission(contexto, it) == PackageManager.PERMISSION_GRANTED
        }
        if (tienePermiso) {
            obtenerUbicacion()
            return
        }
        // Primero se explica para qué se usa; luego el sistema pide el permiso
        MaterialAlertDialogBuilder(contexto)
            .setTitle(R.string.permiso_ubicacion_titulo)
            .setMessage(R.string.permiso_ubicacion_explicacion)
            .setPositiveButton(R.string.permiso_permitir) { _, _ -> pedirUbicacion.launch(PERMISOS_UBICACION) }
            .setNegativeButton(R.string.permiso_ahora_no) { _, _ -> mostrarMensajeUbicacion(R.string.ubicacion_sin_permiso) }
            .show()
    }

    @SuppressLint("MissingPermission") // Solo se llama con al menos uno de los dos permisos concedido
    private fun obtenerUbicacion() {
        val busqueda = CancellationTokenSource()
        busquedaUbicacion = busqueda
        mostrarBuscando(true)
        LocationServices.getFusedLocationProviderClient(requireActivity())
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, busqueda.token)
            .addOnSuccessListener { lugar ->
                if (_binding == null) return@addOnSuccessListener
                terminarBusqueda()
                if (lugar == null) {
                    mostrarMensajeUbicacion(R.string.ubicacion_no_disponible)
                } else {
                    mostrarMensajeUbicacion(null)
                    centrarEnLaUbicacion = true
                    viewModel.ponerUbicacion(lugar.latitude, lugar.longitude)
                }
            }
            .addOnFailureListener {
                if (_binding == null) return@addOnFailureListener
                terminarBusqueda()
                mostrarMensajeUbicacion(R.string.ubicacion_no_disponible)
            }
    }

    private fun terminarBusqueda() {
        busquedaUbicacion = null
        mostrarBuscando(false)
    }

    private fun mostrarBuscando(buscando: Boolean) = with(binding.botonMiUbicacion) {
        isEnabled = !buscando && !viewModel.estado.value.enviando
        setText(if (buscando) R.string.buscando_ubicacion else R.string.usar_mi_ubicacion)
    }

    private fun mostrarMensajeUbicacion(texto: Int?) {
        mensajeUbicacion = texto
        if (_binding != null) mostrarUbicacion(viewModel.estado.value, !viewModel.estado.value.enviando)
    }

    private fun coordenada(valor: Double) = String.format(Locale.ROOT, "%.5f", valor)

    // --- Errores ---

    private fun desplazarAlPrimerError(errores: Map<CampoReporte, String>) {
        val primero = CampoReporte.entries.firstOrNull { it in errores } ?: return
        mostrarEnPantalla(
            when (primero) {
                CampoReporte.CATEGORIA -> binding.campoCategoria
                CampoReporte.TITULO -> binding.campoTitulo
                CampoReporte.DESCRIPCION -> binding.campoDescripcion
                CampoReporte.FOTOS -> binding.fotosAyuda
                CampoReporte.DIRECCION -> binding.campoDireccion
                CampoReporte.DESCRIPCION_UBICACION -> binding.campoDescripcionUbicacion
                CampoReporte.UBICACION -> binding.ubicacionMensaje
            },
        )
    }

    private fun mostrarEnPantalla(vista: View) {
        vista.post { vista.requestRectangleOnScreen(Rect(0, 0, vista.width, vista.height), false) }
    }

    // --- Ciclo de vida del mapa y de la cámara ---

    override fun onResume() {
        super.onResume()
        binding.mapa.onResume()
    }

    override fun onPause() {
        binding.mapa.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        uriCamara?.let { outState.putString(ESTADO_URI_CAMARA, it.toString()) }
    }

    override fun onDestroyView() {
        busquedaUbicacion?.cancel()
        busquedaUbicacion = null
        binding.mapa.onDetach()
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val ESTADO_URI_CAMARA = "uriCamara"
        val PERMISOS_UBICACION = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        // Solo la vista inicial del mapa: el lugar no queda marcado hasta que el ciudadano lo elige
        val CENTRO_INICIAL = GeoPoint(4.7110, -74.0721) // Bogotá
        const val ZOOM_INICIAL = 12.0
        const val ZOOM_UBICACION = 17.0
        const val DURACION_CENTRAR_MS = 600L
    }
}
