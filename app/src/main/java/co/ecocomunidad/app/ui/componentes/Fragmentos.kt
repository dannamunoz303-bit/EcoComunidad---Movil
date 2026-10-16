package co.ecocomunidad.app.ui.componentes

import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import co.ecocomunidad.app.AppContainer
import co.ecocomunidad.app.EcoComunidadApp
import co.ecocomunidad.app.databinding.VistaAvisoBinding
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

val Fragment.contenedor: AppContainer
    get() = (requireActivity().application as EcoComunidadApp).contenedor

/** Recoge [flujo] mientras la vista del fragment está al menos iniciada. */
fun <T> Fragment.observar(flujo: Flow<T>, alRecibir: (T) -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            flujo.collect(alRecibir)
        }
    }
}

/** Pasa al ViewModel lo que se escribe en el campo. */
fun TextInputLayout.alEscribir(alCambiar: (String) -> Unit) {
    editText?.doAfterTextChanged { alCambiar(it?.toString().orEmpty()) }
}

/** Pone el texto del estado solo si cambió, para no mover el cursor ni repetir eventos. */
fun TextInputLayout.mostrar(texto: String, error: String?) {
    val campo = editText ?: return
    if (campo.text?.toString() != texto) campo.setText(texto)
    if (this.error?.toString() != error) this.error = error
}

fun VistaAvisoBinding.mostrar(texto: String?) {
    root.visibility = if (texto == null) View.GONE else View.VISIBLE
    if (avisoTexto.text?.toString() != texto) avisoTexto.text = texto
}

/** Quita el foco y esconde el teclado antes de enviar un formulario. */
fun Fragment.cerrarTeclado() {
    val vista = view?.findFocus() ?: return
    vista.clearFocus()
    requireContext().getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(vista.windowToken, 0)
}
