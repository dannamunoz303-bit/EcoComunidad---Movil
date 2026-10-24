package co.ecocomunidad.app.ui.componentes

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import co.ecocomunidad.app.R
import co.ecocomunidad.app.ui.reportes.crear.ReductorFotos
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.util.UUID

/**
 * Cámara y galería para agregar fotos (Crear reporte y Registrar acción), sin pedir permisos:
 * la cámara se abre con un intent que guarda la foto en la carpeta de [ReductorFotos] (vía
 * FileProvider) y la galería es el selector de fotos del sistema (Photo Picker).
 *
 * [alElegir] recibe las uri como texto; la pantalla se las pasa a su ViewModel, que las reduce con
 * [ReductorFotos]. [disponibles] dice cuántas fotos caben todavía.
 *
 * Se crea como propiedad del fragmento, porque registra sus resultados antes de que se cree.
 * La uri de la foto que se está tomando se conserva si el sistema cierra la app mientras tanto.
 */
class SelectorFotos(
    private val fragmento: Fragment,
    maximo: Int,
    private val disponibles: () -> Int,
    private val alElegir: (List<String>) -> Unit,
) {
    /** Dónde guarda la cámara la foto que está tomando. */
    private var uriCamara: Uri? = null

    private val tomarFoto = fragmento.registerForActivityResult(ActivityResultContracts.TakePicture()) { tomada ->
        val uri = uriCamara
        uriCamara = null
        if (tomada && uri != null) alElegir(listOf(uri.toString()))
    }

    private val elegirVarias =
        fragmento.registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maximo)) { uris ->
            alElegir(uris.map { it.toString() })
        }
    private val elegirUna = fragmento.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) alElegir(listOf(uri.toString()))
    }

    init {
        val registro = fragmento.savedStateRegistry
        registro.registerSavedStateProvider(CLAVE_ESTADO) { bundleOf(URI_CAMARA to uriCamara?.toString()) }
        // Lo guardado se puede leer cuando el fragmento ya se creó
        fragmento.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event != Lifecycle.Event.ON_CREATE) return
                source.lifecycle.removeObserver(this)
                registro.consumeRestoredStateForKey(CLAVE_ESTADO)?.getString(URI_CAMARA)?.let { uriCamara = it.toUri() }
            }
        })
    }

    fun abrirCamara() {
        val contexto = fragmento.requireContext()
        val archivo = File(ReductorFotos.carpetaCamara(contexto), "camara_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(contexto, ReductorFotos.autoridadArchivos(contexto), archivo)
        uriCamara = uri
        try {
            tomarFoto.launch(uri)
        } catch (e: ActivityNotFoundException) {
            uriCamara = null
            Snackbar.make(fragmento.requireView(), R.string.sin_camara, Snackbar.LENGTH_LONG).show()
        }
    }

    fun abrirGaleria() {
        val solicitud = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        // El selector de varias pide al menos 2; si solo cabe una más, se usa el de una
        if (disponibles() == 1) elegirUna.launch(solicitud) else elegirVarias.launch(solicitud)
    }

    private companion object {
        const val CLAVE_ESTADO = "selectorFotos"
        const val URI_CAMARA = "uriCamara"
    }
}
