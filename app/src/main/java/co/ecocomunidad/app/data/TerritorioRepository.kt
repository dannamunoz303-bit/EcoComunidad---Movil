package co.ecocomunidad.app.data

import co.ecocomunidad.app.data.modelo.Municipio

/** Municipios y zonas del Cauca (RN-23). La implementación real es [TerritorioRepositoryRemoto]. */
interface TerritorioRepository {

    /** Los municipios para elegir al crear o editar un reporte, cada uno con su zona. */
    suspend fun municipios(): Resultado<List<Municipio>>
}
