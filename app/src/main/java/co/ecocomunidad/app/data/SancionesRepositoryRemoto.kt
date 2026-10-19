package co.ecocomunidad.app.data

import co.ecocomunidad.app.data.modelo.Sancion
import co.ecocomunidad.app.data.remoto.SancionesApi
import kotlinx.serialization.json.Json

class SancionesRepositoryRemoto(
    private val api: SancionesApi,
    private val json: Json,
) : SancionesRepository {

    override suspend fun listarMias(): Resultado<List<Sancion>> = llamarApi(json) { api.mias() }
}
