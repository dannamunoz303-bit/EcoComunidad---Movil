package co.ecocomunidad.app.data

import co.ecocomunidad.app.data.modelo.NuevaNovedad
import co.ecocomunidad.app.data.modelo.Novedad
import co.ecocomunidad.app.data.remoto.NovedadesApi
import kotlinx.serialization.json.Json

class NovedadesRepositoryRemoto(
    private val api: NovedadesApi,
    private val json: Json,
) : NovedadesRepository {

    override suspend fun listarMias(): Resultado<List<Novedad>> = llamarApi(json) { api.listar() }

    override suspend fun crear(nueva: NuevaNovedad): Resultado<Novedad> = llamarApi(json) { api.crear(nueva) }
}
