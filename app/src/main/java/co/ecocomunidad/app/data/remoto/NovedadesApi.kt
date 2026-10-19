package co.ecocomunidad.app.data.remoto

import co.ecocomunidad.app.data.modelo.NuevaNovedad
import co.ecocomunidad.app.data.modelo.Novedad
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** Rutas de NovedadViewSet (novedades/views.py), registradas en api/ (config/urls.py). Sin paginación. */
interface NovedadesApi {

    /** A quien no es administrador, el backend le devuelve solo las que creó, más nuevas primero. */
    @GET("novedades/")
    suspend fun listar(): List<Novedad>

    /** RN-18 la valida el backend: reporte propio, estado permitido y tarea de ese reporte. Responde 201. */
    @POST("novedades/")
    suspend fun crear(@Body nueva: NuevaNovedad): Novedad
}
