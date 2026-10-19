package co.ecocomunidad.app.data

import co.ecocomunidad.app.data.modelo.Sancion

/** Sanciones del ciudadano (RN-19 a RN-21). Solo se consultan. La implementación real es [SancionesRepositoryRemoto]. */
interface SancionesRepository {

    suspend fun listarMias(): Resultado<List<Sancion>>
}
