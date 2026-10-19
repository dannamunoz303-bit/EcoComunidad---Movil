package co.ecocomunidad.app.ui.notificaciones

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.ecocomunidad.app.R
import co.ecocomunidad.app.data.modelo.Notificacion
import co.ecocomunidad.app.data.modelo.TipoNotificacion
import co.ecocomunidad.app.databinding.ItemNotificacionBinding
import co.ecocomunidad.app.ui.reportes.fechaLarga

/** Ícono Lucide de cada código de la sección 9; los demás llevan la campana. */
@get:DrawableRes
val Notificacion.icono: Int
    get() = when (tipo) {
        TipoNotificacion.REPORTE_APROBADO, TipoNotificacion.BLOQUEO_LEVANTADO -> R.drawable.ic_listo
        TipoNotificacion.REPORTE_RECHAZADO -> R.drawable.ic_cerrar
        TipoNotificacion.REPORTE_ASIGNADO -> R.drawable.ic_usuario
        TipoNotificacion.REPORTE_ESTADO -> R.drawable.ic_actualizar
        TipoNotificacion.NOVEDAD_RESPONDIDA -> R.drawable.ic_mensaje
        TipoNotificacion.SANCION -> R.drawable.ic_escudo
        TipoNotificacion.REPORTE_ELIMINADO -> R.drawable.ic_basura
        else -> R.drawable.ic_campana
    }

/** Cada notificación. Sin leer: negrita y punto verde; leída: texto gris. */
class NotificacionesAdapter(
    private val alTocar: (Notificacion) -> Unit,
) : ListAdapter<Notificacion, NotificacionesAdapter.Vista>(Diferencias) {

    class Vista(val binding: ItemNotificacionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vista {
        val vista = Vista(ItemNotificacionBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        vista.binding.tarjeta.setOnClickListener {
            val posicion = vista.bindingAdapterPosition
            if (posicion != RecyclerView.NO_POSITION) alTocar(getItem(posicion))
        }
        return vista
    }

    override fun onBindViewHolder(vista: Vista, position: Int) = with(vista.binding) {
        val notificacion = getItem(position)
        val contexto = root.context
        val sinLeer = !notificacion.leida
        val fechaTexto = fechaLarga(notificacion.fecha)

        icono.setImageResource(notificacion.icono)
        mensaje.text = notificacion.mensaje
        mensaje.setTypeface(null, if (sinLeer) Typeface.BOLD else Typeface.NORMAL)
        mensaje.setTextColor(ContextCompat.getColor(contexto, if (sinLeer) R.color.tinta else R.color.tinta_suave))
        fecha.text = fechaTexto
        punto.visibility = if (sinLeer) View.VISIBLE else View.GONE
        // Una sola frase para el lector de pantalla, diciendo si está sin leer
        tarjeta.contentDescription = contexto.getString(
            if (sinLeer) R.string.notificacion_sin_leer else R.string.notificacion_leida,
            notificacion.mensaje,
            fechaTexto,
        )
    }

    private object Diferencias : DiffUtil.ItemCallback<Notificacion>() {
        override fun areItemsTheSame(antes: Notificacion, ahora: Notificacion) = antes.id == ahora.id
        override fun areContentsTheSame(antes: Notificacion, ahora: Notificacion) = antes == ahora
    }
}
