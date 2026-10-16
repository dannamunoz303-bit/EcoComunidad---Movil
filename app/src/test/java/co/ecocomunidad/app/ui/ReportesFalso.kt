package co.ecocomunidad.app.ui

import co.ecocomunidad.app.data.ReportesRepository
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.modelo.Accion
import co.ecocomunidad.app.data.modelo.CambiosReporte
import co.ecocomunidad.app.data.modelo.Categoria
import co.ecocomunidad.app.data.modelo.Foto
import co.ecocomunidad.app.data.modelo.NuevoReporte
import co.ecocomunidad.app.data.modelo.EstadoReporte
import co.ecocomunidad.app.data.modelo.EstadoTarea
import co.ecocomunidad.app.data.modelo.Reporte
import co.ecocomunidad.app.data.modelo.Tarea
import co.ecocomunidad.app.data.modelo.UsuarioResumen
import kotlinx.coroutines.CompletableDeferred
import java.io.File

class ReportesFalso : ReportesRepository {

    var todos: Resultado<List<Reporte>> = Resultado.Exito(listOf(reporte(1), reporte(2)))
    var mios: Resultado<List<Reporte>> = Resultado.Exito(listOf(reporte(1)))
    var detalle: Resultado<Reporte> = Resultado.Exito(reporte(1))
    var tareas: Resultado<List<Tarea>> = Resultado.Exito(emptyList())

    /** Si no es null, listar espera a que se complete (para ver el estado de carga). */
    var esperaListar: CompletableDeferred<Unit>? = null

    val listados = mutableListOf<Boolean>()
    val tareasPedidas = mutableListOf<Int>()

    override suspend fun listar(soloMios: Boolean): Resultado<List<Reporte>> {
        listados += soloMios
        esperaListar?.await()
        return if (soloMios) mios else todos
    }

    /** Si no es null, detalle espera a que se complete (para ver el estado de carga). */
    var esperaDetalle: CompletableDeferred<Unit>? = null

    override suspend fun detalle(id: Int): Resultado<Reporte> {
        esperaDetalle?.await()
        return detalle
    }

    override suspend fun tareas(id: Int): Resultado<List<Tarea>> {
        tareasPedidas += id
        return tareas
    }

    var categorias: Resultado<List<Categoria>> = Resultado.Exito(
        listOf(Categoria(3, "Basura acumulada"), Categoria(4, "Árbol caído")),
    )
    var creado: Resultado<Reporte> = Resultado.Exito(reporte(9, EstadoReporte.PENDIENTE))

    /** Si no es null, crear espera a que se complete (para ver el estado de carga). */
    var esperaCrear: CompletableDeferred<Unit>? = null
    val creados = mutableListOf<NuevoReporte>()

    // --- Editar, eliminar y fotos ---

    var editado: Resultado<Reporte>? = null
    var eliminado: Resultado<Unit> = Resultado.Exito(Unit)
    var fotosAgregadas: Resultado<Reporte>? = null
    var fotoBorrada: Resultado<Unit> = Resultado.Exito(Unit)

    /** Cada llamada, en orden: "editar", "borrar 31", "subir a.jpg,b.jpg", "eliminar". */
    val operaciones = mutableListOf<String>()
    val cambiosEnviados = mutableListOf<CambiosReporte>()

    /** Lo que el backend tiene, para que las respuestas de fotos sean realistas. */
    private var enBackend: Reporte? = null
    private var siguienteFoto = 100

    override suspend fun editar(id: Int, cambios: CambiosReporte): Resultado<Reporte> {
        operaciones += "editar"
        cambiosEnviados += cambios
        editado?.let { return it }
        val actual = enBackend ?: (detalle as Resultado.Exito).datos
        val nuevo = actual.copy(
            titulo = cambios.titulo ?: actual.titulo,
            categoriaId = cambios.categoria ?: actual.categoriaId,
        )
        enBackend = nuevo
        return Resultado.Exito(nuevo)
    }

    override suspend fun eliminar(id: Int): Resultado<Unit> {
        operaciones += "eliminar"
        return eliminado
    }

    override suspend fun agregarFotos(id: Int, fotos: List<File>): Resultado<Reporte> {
        operaciones += "subir ${fotos.joinToString(",") { it.name }}"
        fotosAgregadas?.let { return it }
        val actual = enBackend ?: (detalle as Resultado.Exito).datos
        val nuevas = fotos.map { Foto(siguienteFoto++, "https://fotos/${it.name}", "2026-10-05T10:00:00-05:00") }
        check(actual.fotos.size + nuevas.size <= 5) { "El backend respondería 400: más de 5 fotos" }
        val nuevo = actual.copy(fotos = actual.fotos + nuevas)
        enBackend = nuevo
        return Resultado.Exito(nuevo)
    }

    override suspend fun borrarFoto(id: Int, fotoId: Int): Resultado<Unit> {
        operaciones += "borrar $fotoId"
        if (fotoBorrada is Resultado.Fallo) return fotoBorrada
        val actual = enBackend ?: (detalle as Resultado.Exito).datos
        check(actual.fotos.size > 1) { "El backend respondería 400: es la última foto" }
        enBackend = actual.copy(fotos = actual.fotos.filter { it.id != fotoId })
        return fotoBorrada
    }

    override suspend fun categorias(): Resultado<List<Categoria>> = categorias

    override suspend fun crear(nuevo: NuevoReporte): Resultado<Reporte> {
        creados += nuevo
        esperaCrear?.await()
        return creado
    }

    companion object {
        val ANA = UsuarioResumen(id = 7, nombre = "Ana", apellido = "Pérez")
        val LUIS = UsuarioResumen(id = 4, nombre = "Luis", apellido = "Gómez")

        fun reporte(
            id: Int,
            estado: EstadoReporte = EstadoReporte.EN_PROCESO,
            ciudadano: UsuarioResumen? = null,
            motivoRechazo: String? = null,
            fotos: List<Foto> = emptyList(),
        ) = Reporte(
            id = id, ciudadano = ciudadano, categoriaId = 3, categoria = "Basura acumulada",
            titulo = "Basura en la esquina", descripcion = "Hay bolsas desde el lunes.",
            direccion = "Calle 10 # 5-20", descripcionUbicacion = "Frente al parque",
            latitud = "4.60971000", longitud = "-74.08175000",
            estado = estado, motivoRechazo = motivoRechazo,
            fechaCreacion = "2026-10-01T10:00:00-05:00", fechaActualizacion = "2026-10-02T10:00:00-05:00",
            fotos = fotos,
        )

        fun foto(id: Int) = Foto(id, "https://fotos/$id.jpg", "2026-10-01T10:00:00-05:00")

        fun accion(id: Int, fecha: String) =
            Accion(id = id, descripcion = "Paso $id", fecha = fecha)

        fun tarea(id: Int, gestor: UsuarioResumen?, vararg acciones: Accion) = Tarea(
            id = id, gestor = gestor, estado = EstadoTarea.EN_PROCESO,
            fechaAsignacion = "2026-10-02T09:00:00-05:00", acciones = acciones.toList(),
        )
    }
}
