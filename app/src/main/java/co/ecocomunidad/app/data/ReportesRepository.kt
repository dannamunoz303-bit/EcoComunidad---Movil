package co.ecocomunidad.app.data

import co.ecocomunidad.app.data.modelo.CambiosReporte
import co.ecocomunidad.app.data.modelo.Categoria
import co.ecocomunidad.app.data.modelo.NuevoReporte
import co.ecocomunidad.app.data.modelo.Reporte
import co.ecocomunidad.app.data.modelo.Tarea
import java.io.File

/**
 * Consulta de reportes. La visibilidad (RN-10) la decide el backend: aquí no se filtra nada.
 * La implementación real es [ReportesRepositoryRemoto].
 */
interface ReportesRepository {

    /** Todos los reportes que el usuario puede ver, o solo los propios si [soloMios]. Sin paginación. */
    suspend fun listar(soloMios: Boolean): Resultado<List<Reporte>>

    suspend fun detalle(id: Int): Resultado<Reporte>

    /** Tareas, acciones y evidencias del reporte. Para quien no es el dueño, el backend responde 403 (RN-14). */
    suspend fun tareas(id: Int): Resultado<List<Tarea>>

    /** Categorías activas para elegir al crear un reporte. */
    suspend fun categorias(): Resultado<List<Categoria>>

    /** Crea el reporte con sus fotos en una sola petición. Los 400 llegan por campo en [ErrorAuth.Validacion]. */
    suspend fun crear(nuevo: NuevoReporte): Resultado<Reporte>

    /**
     * Cambia solo los campos de [cambios] (RN-07). El backend decide: si el reporte ya no está
     * pendiente responde 400 con detail, y si no es del usuario, 403.
     */
    suspend fun editar(id: Int, cambios: CambiosReporte): Resultado<Reporte>

    /** Eliminación lógica (RN-07). */
    suspend fun eliminar(id: Int): Resultado<Unit>

    /** Sube fotos tipo reporte (RN-15). Devuelve el reporte con todas sus fotos. */
    suspend fun agregarFotos(id: Int, fotos: List<File>): Resultado<Reporte>

    /** Borra una foto tipo reporte (RN-15). El backend no deja borrar la última. */
    suspend fun borrarFoto(id: Int, fotoId: Int): Resultado<Unit>
}
