package co.ecocomunidad.app.data.remoto

import co.ecocomunidad.app.data.modelo.Reporte
import co.ecocomunidad.app.data.modelo.Tarea
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Rutas de ReporteViewSet (reportes/views.py), registradas en api/reportes/ (config/urls.py).
 * El backend no pagina: la lista llega completa como un arreglo.
 */
interface ReportesApi {

    /** RN-10 la aplica el backend. [mios] = "true" deja solo los propios; null los trae todos. */
    @GET("reportes/")
    suspend fun listar(@Query("mios") mios: String? = null): List<Reporte>

    @GET("reportes/{id}/")
    suspend fun detalle(@Path("id") id: Int): Reporte

    /** Tareas con acciones y evidencias. Solo para el dueño, sus gestores y el administrador (RN-14). */
    @GET("reportes/{id}/tareas/")
    suspend fun tareas(@Path("id") id: Int): List<Tarea>
}
