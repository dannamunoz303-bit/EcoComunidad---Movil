package co.ecocomunidad.app.ui.tareas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.ecocomunidad.app.R
import co.ecocomunidad.app.data.modelo.TareaGestor
import co.ecocomunidad.app.databinding.ItemEncabezadoGestorBinding
import co.ecocomunidad.app.databinding.ItemReporteBinding
import co.ecocomunidad.app.ui.reportes.cargarFoto
import co.ecocomunidad.app.ui.reportes.fechaCorta
import co.ecocomunidad.app.ui.reportes.mostrarEtiquetaEstado

/** Lo que muestra el encabezado del gestor. [activas] es null mientras no se sabe. */
data class EncabezadoGestor(
    val nombre: String? = null,
    val activas: Int? = null,
    val filtro: FiltroTareas = FiltroTareas.ACTIVAS,
    val hayNoLeidas: Boolean = false,
)

/** Barra (logo, campana y menú), saludo de Brotico con las tareas activas y pestañas. Siempre un elemento. */
class EncabezadoGestorAdapter(
    private val alAbrirNotificaciones: () -> Unit,
    private val alAbrirMenu: (View) -> Unit,
    private val alElegirFiltro: (FiltroTareas) -> Unit,
) : RecyclerView.Adapter<EncabezadoGestorAdapter.Vista>() {

    var encabezado = EncabezadoGestor()
        set(valor) {
            if (field == valor) return
            field = valor
            notifyItemChanged(0)
        }

    class Vista(val binding: ItemEncabezadoGestorBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemCount() = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vista {
        val binding = ItemEncabezadoGestorBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.botonNotificaciones.setOnClickListener { alAbrirNotificaciones() }
        binding.botonMiCuenta.setOnClickListener { alAbrirMenu(it) }
        binding.filtros.setOnCheckedStateChangeListener { _, elegidos ->
            val filtro = if (elegidos.firstOrNull() == R.id.filtro_terminadas) FiltroTareas.TERMINADAS else FiltroTareas.ACTIVAS
            alElegirFiltro(filtro)
        }
        return Vista(binding)
    }

    override fun onBindViewHolder(vista: Vista, position: Int) = with(vista.binding) {
        val contexto = root.context
        saludo.text = encabezado.nombre
            ?.let { contexto.getString(R.string.saludo_gestor, it) }
            ?: contexto.getString(R.string.saludo_gestor_sin_nombre)
        resumen.text = encabezado.activas?.let {
            contexto.resources.getQuantityString(R.plurals.tareas_activas_resumen, it, it)
        }
        resumen.visibility = if (encabezado.activas == null) View.INVISIBLE else View.VISIBLE
        val chip = if (encabezado.filtro == FiltroTareas.TERMINADAS) R.id.filtro_terminadas else R.id.filtro_activas
        if (filtros.checkedChipId != chip) filtros.check(chip)
        puntoCampana.visibility = if (encabezado.hayNoLeidas) View.VISIBLE else View.GONE
        botonNotificaciones.contentDescription = contexto.getString(
            if (encabezado.hayNoLeidas) R.string.notificaciones_sin_leer else R.string.notificaciones,
        )
    }
}

/** Una tarea en la lista: misma tarjeta que un reporte, con el estado de la tarea y la fecha de asignación. */
data class TareaConFoto(val tarea: TareaGestor, val foto: String?)

class TareasAdapter(
    private val alAbrir: (TareaGestor) -> Unit,
) : ListAdapter<TareaConFoto, TareasAdapter.Vista>(Diferencias) {

    class Vista(val binding: ItemReporteBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vista {
        val vista = Vista(ItemReporteBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        vista.binding.tarjeta.setOnClickListener {
            val posicion = vista.bindingAdapterPosition
            if (posicion != RecyclerView.NO_POSITION) alAbrir(getItem(posicion).tarea)
        }
        return vista
    }

    override fun onBindViewHolder(vista: Vista, position: Int) = with(vista.binding) {
        val (tarea, url) = getItem(position)
        val contexto = root.context
        val reporte = tarea.reporte
        foto.cargarFoto(url)
        categoria.text = reporte.categoria
        titulo.text = reporte.titulo
        direccion.text = reporte.direccion
        estado.mostrarEtiquetaEstado(tarea.estado.etiqueta, tarea.estado.color)
        fecha.text = contexto.getString(R.string.tarea_asignada_el, fechaCorta(tarea.fechaAsignacion))
        // Una sola frase para el lector de pantalla
        tarjeta.contentDescription = contexto.getString(
            R.string.tarea_descripcion_item,
            reporte.categoria,
            reporte.titulo,
            reporte.direccion,
            contexto.getString(tarea.estado.etiqueta),
        )
    }

    private object Diferencias : DiffUtil.ItemCallback<TareaConFoto>() {
        override fun areItemsTheSame(antes: TareaConFoto, ahora: TareaConFoto) = antes.tarea.id == ahora.tarea.id
        override fun areContentsTheSame(antes: TareaConFoto, ahora: TareaConFoto) = antes == ahora
    }
}
