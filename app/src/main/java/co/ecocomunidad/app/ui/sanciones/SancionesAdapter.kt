package co.ecocomunidad.app.ui.sanciones

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.ecocomunidad.app.R
import co.ecocomunidad.app.data.modelo.TipoSancion
import co.ecocomunidad.app.databinding.ItemAvisoBloqueoBinding
import co.ecocomunidad.app.databinding.ItemSancionBinding
import co.ecocomunidad.app.ui.componentes.mostrar
import co.ecocomunidad.app.ui.navegacion.formatearFechaColombia
import co.ecocomunidad.app.ui.reportes.fechaLarga
import co.ecocomunidad.app.ui.reportes.mostrarEtiquetaEstado

@get:StringRes
val TipoSancion.etiqueta: Int
    get() = when (this) {
        TipoSancion.ADVERTENCIA -> R.string.sancion_advertencia
        TipoSancion.BLOQUEO_TEMPORAL -> R.string.sancion_bloqueo_temporal
        TipoSancion.BLOQUEO_PERMANENTE -> R.string.sancion_bloqueo_permanente
    }

/** Advertencia en ámbar; los bloqueos en arcilla (marca.md). */
@get:ColorRes
val TipoSancion.color: Int
    get() = if (this == TipoSancion.ADVERTENCIA) R.color.ambar else R.color.arcilla

/** Aviso del bloqueo vigente, arriba de la lista. Un elemento cuando ya cargó; ninguno mientras carga. */
class AvisoBloqueoAdapter : RecyclerView.Adapter<AvisoBloqueoAdapter.Vista>() {

    var bloqueo: Bloqueo? = null
        set(valor) {
            if (field == valor) return
            val antes = field
            field = valor
            when {
                antes == null && valor != null -> notifyItemInserted(0)
                antes != null && valor == null -> notifyItemRemoved(0)
                else -> notifyItemChanged(0)
            }
        }

    class Vista(val binding: ItemAvisoBloqueoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemCount() = if (bloqueo == null) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Vista(ItemAvisoBloqueoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(vista: Vista, position: Int) {
        val bloqueo = bloqueo ?: return
        val contexto = vista.binding.root.context
        val binding = vista.binding
        binding.sinBloqueo.mostrar(if (bloqueo == Bloqueo.Ninguno) contexto.getString(R.string.sin_bloqueos) else null)
        binding.conBloqueo.mostrar(
            when (bloqueo) {
                Bloqueo.Ninguno -> null
                is Bloqueo.Temporal -> contexto.getString(R.string.bloqueado_hasta, formatearFechaColombia(bloqueo.hasta))
                Bloqueo.Permanente -> contexto.getString(R.string.bloqueo_permanente_activo)
            },
        )
    }
}

/** Cada sanción: tipo, fecha de inicio, motivo y el reporte si se conoce. Sin el administrador (RN-21). */
class SancionesAdapter : ListAdapter<SancionConReporte, SancionesAdapter.Vista>(Diferencias) {

    class Vista(val binding: ItemSancionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Vista(ItemSancionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(vista: Vista, position: Int) = with(vista.binding) {
        val (sancion, tituloReporte) = getItem(position)
        val contexto = root.context
        tipo.mostrarEtiquetaEstado(sancion.tipo.etiqueta, sancion.tipo.color)
        fecha.text = contexto.getString(R.string.sancion_fecha, fechaLarga(sancion.fechaInicio))
        motivo.text = sancion.motivo
        reporte.text = tituloReporte?.let { contexto.getString(R.string.reporte_con_titulo, it) }
        reporte.visibility = if (tituloReporte == null) View.GONE else View.VISIBLE

        // Temporal: hasta cuándo. Permanente con fecha_fin: se levantó ese día (RN-21)
        val textoFin = sancion.fechaFin?.let {
            when (sancion.tipo) {
                TipoSancion.BLOQUEO_TEMPORAL -> contexto.getString(R.string.sancion_hasta, fechaLarga(it))
                TipoSancion.BLOQUEO_PERMANENTE -> contexto.getString(R.string.sancion_levantada, fechaLarga(it))
                TipoSancion.ADVERTENCIA -> null
            }
        }
        fin.text = textoFin
        fin.visibility = if (textoFin == null) View.GONE else View.VISIBLE
    }

    private object Diferencias : DiffUtil.ItemCallback<SancionConReporte>() {
        override fun areItemsTheSame(antes: SancionConReporte, ahora: SancionConReporte) = antes.sancion.id == ahora.sancion.id
        override fun areContentsTheSame(antes: SancionConReporte, ahora: SancionConReporte) = antes == ahora
    }
}
