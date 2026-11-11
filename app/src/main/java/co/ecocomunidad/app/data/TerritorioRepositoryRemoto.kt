package co.ecocomunidad.app.data

import co.ecocomunidad.app.data.modelo.Municipio
import co.ecocomunidad.app.data.remoto.TerritorioApi
import kotlinx.serialization.json.Json

/** Los municipios no cambian desde la app: se cargan con una migración del backend (tabla 4.12). */
class TerritorioRepositoryRemoto(
    private val api: TerritorioApi,
    private val json: Json,
) : TerritorioRepository {

    override suspend fun municipios(): Resultado<List<Municipio>> = llamarApi(json) { api.municipios() }
}
