package co.ecocomunidad.app.data.remoto

import co.ecocomunidad.app.data.modelo.Sancion
import retrofit2.http.GET

/** Ruta del ciudadano en SancionViewSet (sanciones/views.py). Las demás son del administrador. */
interface SancionesApi {

    /** Las sanciones del ciudadano con sesión, más nuevas primero. A otros roles el backend responde 403. */
    @GET("sanciones/mias/")
    suspend fun mias(): List<Sancion>
}
