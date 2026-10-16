package co.ecocomunidad.app.ui.reportes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.ecocomunidad.app.R
import co.ecocomunidad.app.data.modelo.Reporte
import co.ecocomunidad.app.databinding.ItemEncabezadoCiudadanoBinding
import co.ecocomunidad.app.databinding.ItemReporteBinding
import co.ecocomunidad.app.databinding.VistaEstadoCargaBinding
import co.ecocomunidad.app.ui.componentes.compactarSiHaceFalta

/** Lo que muestra el encabezado: el nombre para el saludo y la pestaña elegida. */
data class Encabezado(val nombre: String?, val filtro: FiltroReportes)

/** Barra con Cerrar sesión, saludo de Brotico y las pestañas "Todos" y "Mis reportes". Siempre un elemento. */
class EncabezadoAdapter(
    private val alCerrarSesion: () -> Unit,
    private val alElegirFiltro: (FiltroReportes) -> Unit,
) : RecyclerView.Adapter<EncabezadoAdapter.Vista>() {

    var encabezado = Encabezado(nombre = null, filtro = FiltroReportes.TODOS)
        set(valor) {
            if (field == valor) return
            field = valor
            notifyItemChanged(0)
        }

    class Vista(val binding: ItemEncabezadoCiudadanoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemCount() = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vista {
        val binding = ItemEncabezadoCiudadanoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.botonCerrarSesion.compactarSiHaceFalta()
        binding.botonCerrarSesion.setOnClickListener {
            it.isEnabled = false
            alCerrarSesion()
        }
        binding.filtros.setOnCheckedStateChangeListener { _, elegidos ->
            val filtro = if (elegidos.firstOrNull() == R.id.filtro_mios) FiltroReportes.MIOS else FiltroReportes.TODOS
            alElegirFiltro(filtro)
        }
        return Vista(binding)
    }

    override fun onBindViewHolder(vista: Vista, position: Int) = with(vista.binding) {
        val contexto = root.context
        saludo.text = encabezado.nombre
            ?.let { contexto.getString(R.string.saludo_con_nombre, it) }
            ?: contexto.getString(R.string.saludo_sin_nombre)
        val chip = if (encabezado.filtro == FiltroReportes.MIOS) R.id.filtro_mios else R.id.filtro_todos
        if (filtros.checkedChipId != chip) filtros.check(chip)
    }
}

/** Cargando, vacío o error debajo de las pestañas. Cero o un elemento. */
class EstadoListaAdapter(private val alReintentar: () -> Unit) : RecyclerView.Adapter<EstadoListaAdapter.Vista>() {

    var estado: EstadoVista? = null
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

    class Vista(val binding: VistaEstadoCargaBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemCount() = if (estado == null) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Vista(VistaEstadoCargaBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(vista: Vista, position: Int) {
        estado?.let { vista.binding.mostrar(it, alReintentar) }
    }
}

/** Cada reporte: foto pequeña, categoría, título, dirección, estado y fecha. */
class ReportesAdapter(
    private val alAbrir: (Reporte) -> Unit,
) : ListAdapter<Reporte, ReportesAdapter.Vista>(Diferencias) {

    class Vista(val binding: ItemReporteBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vista {
        val vista = Vista(ItemReporteBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        vista.binding.tarjeta.setOnClickListener {
            val posicion = vista.bindingAdapterPosition
            if (posicion != RecyclerView.NO_POSITION) alAbrir(getItem(posicion))
        }
        return vista
    }

    override fun onBindViewHolder(vista: Vista, position: Int) = with(vista.binding) {
        val reporte = getItem(position)
        val contexto = root.context
        foto.cargarFoto(reporte.fotos.firstOrNull()?.url)
        categoria.text = reporte.categoria
        titulo.text = reporte.titulo
        direccion.text = reporte.direccion
        estado.mostrarEstado(reporte.estado)
        fecha.text = fechaCorta(reporte.fechaCreacion)
        // Una sola frase para el lector de pantalla
        tarjeta.contentDescription = contexto.getString(
            R.string.reporte_descripcion_item,
            reporte.categoria,
            reporte.titulo,
            reporte.direccion,
            contexto.getString(reporte.estado.etiqueta),
        )
    }

    private object Diferencias : DiffUtil.ItemCallback<Reporte>() {
        override fun areItemsTheSame(antes: Reporte, ahora: Reporte) = antes.id == ahora.id
        override fun areContentsTheSame(antes: Reporte, ahora: Reporte) = antes == ahora
    }
}
