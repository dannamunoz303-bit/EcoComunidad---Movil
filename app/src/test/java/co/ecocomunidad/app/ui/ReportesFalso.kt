package co.ecocomunidad.app.ui

import co.ecocomunidad.app.data.ReportesRepository
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.modelo.Accion
import co.ecocomunidad.app.data.modelo.EstadoReporte
import co.ecocomunidad.app.data.modelo.EstadoTarea
import co.ecocomunidad.app.data.modelo.Reporte
import co.ecocomunidad.app.data.modelo.Tarea
import co.ecocomunidad.app.data.modelo.UsuarioResumen
import kotlinx.coroutines.CompletableDeferred

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

    companion object {
        val ANA = UsuarioResumen(id = 7, nombre = "Ana", apellido = "Pérez")
        val LUIS = UsuarioResumen(id = 4, nombre = "Luis", apellido = "Gómez")

        fun reporte(
            id: Int,
            estado: EstadoReporte = EstadoReporte.EN_PROCESO,
            ciudadano: UsuarioResumen? = null,
            motivoRechazo: String? = null,
        ) = Reporte(
            id = id, ciudadano = ciudadano, categoriaId = 3, categoria = "Basura acumulada",
            titulo = "Basura en la esquina", descripcion = "Hay bolsas desde el lunes.",
            direccion = "Calle 10 # 5-20", estado = estado, motivoRechazo = motivoRechazo,
            fechaCreacion = "2026-10-01T10:00:00-05:00", fechaActualizacion = "2026-10-02T10:00:00-05:00",
        )

        fun accion(id: Int, fecha: String) =
            Accion(id = id, descripcion = "Paso $id", fecha = fecha)

        fun tarea(id: Int, gestor: UsuarioResumen?, vararg acciones: Accion) = Tarea(
            id = id, gestor = gestor, estado = EstadoTarea.EN_PROCESO,
            fechaAsignacion = "2026-10-02T09:00:00-05:00", acciones = acciones.toList(),
        )
    }
}
