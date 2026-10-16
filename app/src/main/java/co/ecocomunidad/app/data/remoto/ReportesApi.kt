package co.ecocomunidad.app.data.remoto

import co.ecocomunidad.app.data.modelo.Categoria
import co.ecocomunidad.app.data.modelo.Reporte
import co.ecocomunidad.app.data.modelo.Tarea
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Rutas de ReporteViewSet y CategoriaViewSet (reportes/views.py), registradas en api/ (config/urls.py).
 * El backend no pagina: las listas llegan completas como un arreglo.
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

    /** Para quien no es administrador, solo las activas. */
    @GET("categorias/")
    suspend fun categorias(): List<Categoria>

    /** Crea el reporte con sus fotos (multipart/form-data, RN-06). El cuerpo lo arma el repositorio. */
    @POST("reportes/")
    suspend fun crear(@Body cuerpo: MultipartBody): Reporte
}
