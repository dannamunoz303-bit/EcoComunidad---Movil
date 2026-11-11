package co.ecocomunidad.app.ui.reportes.crear

import android.view.View
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import co.ecocomunidad.app.R
import co.ecocomunidad.app.data.modelo.Municipio
import co.ecocomunidad.app.databinding.DialogoMunicipiosBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Diálogo para elegir el municipio del reporte (RN-23): los 42 en orden y un buscador que no
 * distingue tildes ni mayúsculas ([buscarMunicipios]). Tocar uno lo elige y cierra.
 */
object SelectorMunicipio {

    fun mostrar(fragmento: Fragment, municipios: List<Municipio>, alElegir: (Municipio) -> Unit) {
        val vista = DialogoMunicipiosBinding.inflate(fragmento.layoutInflater)
        var visibles = municipios
        val adaptador = ArrayAdapter(fragmento.requireContext(), R.layout.item_categoria, visibles.map { it.nombre })
        vista.lista.adapter = adaptador

        val dialogo = MaterialAlertDialogBuilder(fragmento.requireContext())
            .setTitle(R.string.municipio_titulo)
            .setView(vista.root)
            .setNegativeButton(R.string.cancelar, null)
            .show()

        vista.lista.setOnItemClickListener { _, _, posicion, _ ->
            visibles.getOrNull(posicion)?.let(alElegir)
            dialogo.dismiss()
        }
        vista.textoBuscar.doAfterTextChanged { texto ->
            visibles = buscarMunicipios(municipios, texto?.toString().orEmpty())
            adaptador.clear()
            adaptador.addAll(visibles.map { it.nombre })
            vista.sinResultados.visibility = if (visibles.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
