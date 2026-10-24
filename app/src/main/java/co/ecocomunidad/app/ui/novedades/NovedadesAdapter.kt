package co.ecocomunidad.app.ui.novedades

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.ecocomunidad.app.R
import co.ecocomunidad.app.data.modelo.EstadoNovedad
import co.ecocomunidad.app.data.modelo.Novedad
import co.ecocomunidad.app.data.modelo.TipoNovedad
import co.ecocomunidad.app.databinding.ItemNovedadBinding
import co.ecocomunidad.app.ui.reportes.fechaLarga
import co.ecocomunidad.app.ui.reportes.mostrarEtiquetaEstado

@get:StringRes
val TipoNovedad.etiqueta: Int
    get() = when (this) {
        TipoNovedad.AYUDA -> R.string.tipo_ayuda
        TipoNovedad.QUEJA -> R.string.tipo_queja
        TipoNovedad.INCIDENTE -> R.string.tipo_incidente
    }

/** Sección 6.3. */
@get:StringRes
val EstadoNovedad.etiqueta: Int
    get() = when (this) {
        EstadoNovedad.ABIERTA -> R.string.novedad_estado_abierta
        EstadoNovedad.ATENDIDA -> R.string.novedad_estado_atendida
    }

/** Abierta en ámbar (espera respuesta), atendida en verde. */
@get:ColorRes
val EstadoNovedad.color: Int
    get() = when (this) {
        EstadoNovedad.ABIERTA -> R.color.ambar
        EstadoNovedad.ATENDIDA -> R.color.verde_bosque
    }

/** Cada novedad en "Mis novedades". No se abren: toda la información ya está en la tarjeta. */
class NovedadesAdapter : ListAdapter<Novedad, NovedadesAdapter.Vista>(Diferencias) {

    /** Mostrar la tarea junto al reporte (gestor). */
    var conTarea = false
        set(valor) {
            if (field == valor) return
            field = valor
            notifyItemRangeChanged(0, itemCount)
        }

    class Vista(val binding: ItemNovedadBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Vista(ItemNovedadBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(vista: Vista, position: Int) = with(vista.binding) {
        val novedad = getItem(position)
        val contexto = root.context
        tipo.setText(novedad.tipo.etiqueta)
        estado.mostrarEtiquetaEstado(novedad.estado.etiqueta, novedad.estado.color)
        val textoReporte = textoReporte(contexto, novedad)
        reporte.text = textoReporte
        reporte.visibility = if (textoReporte == null) View.GONE else View.VISIBLE
        descripcion.text = novedad.descripcion
        fecha.text = contexto.getString(R.string.novedad_fecha, fechaLarga(novedad.fecha))
        respuesta.text = novedad.respuesta
        cajaRespuesta.visibility = if (novedad.respuesta.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    /** "Reporte: título" o, para el gestor, "título · Tarea #id". */
    private fun textoReporte(contexto: Context, novedad: Novedad): String? {
        val titulo = novedad.reporteTitulo?.takeIf { it.isNotBlank() }
        val tarea = novedad.tareaId?.takeIf { conTarea }
        return when {
            titulo != null && tarea != null -> contexto.getString(R.string.novedad_reporte_tarea, titulo, tarea)
            titulo != null -> contexto.getString(R.string.reporte_con_titulo, titulo)
            tarea != null -> contexto.getString(R.string.tarea_titulo, tarea)
            else -> null
        }
    }

    private object Diferencias : DiffUtil.ItemCallback<Novedad>() {
        override fun areItemsTheSame(antes: Novedad, ahora: Novedad) = antes.id == ahora.id
        override fun areContentsTheSame(antes: Novedad, ahora: Novedad) = antes == ahora
    }
}
