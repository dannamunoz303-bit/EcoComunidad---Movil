package co.ecocomunidad.app.data.remoto

import co.ecocomunidad.app.data.modelo.Municipio
import retrofit2.http.GET

/** Rutas de la app territorio del backend (territorio/views.py). Solo lectura y sin paginación. */
interface TerritorioApi {

    /** Los 42 municipios del Cauca con su zona, ordenados por nombre (RN-23). */
    @GET("municipios/")
    suspend fun municipios(): List<Municipio>
}
