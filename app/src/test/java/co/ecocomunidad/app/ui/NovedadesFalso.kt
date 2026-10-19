package co.ecocomunidad.app.ui

import co.ecocomunidad.app.data.NovedadesRepository
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.modelo.EstadoNovedad
import co.ecocomunidad.app.data.modelo.NuevaNovedad
import co.ecocomunidad.app.data.modelo.Novedad
import co.ecocomunidad.app.data.modelo.TipoNovedad
import kotlinx.coroutines.CompletableDeferred

class NovedadesFalso : NovedadesRepository {

    var mias: Resultado<List<Novedad>> = Resultado.Exito(emptyList())
    var creada: Resultado<Novedad> = Resultado.Exito(novedad(1))

    /** Si no es null, crear espera a que se complete (para ver el estado de envío). */
    var esperaCrear: CompletableDeferred<Unit>? = null

    val creadas = mutableListOf<NuevaNovedad>()
    var listados = 0

    override suspend fun listarMias(): Resultado<List<Novedad>> {
        listados++
        return mias
    }

    override suspend fun crear(nueva: NuevaNovedad): Resultado<Novedad> {
        creadas += nueva
        esperaCrear?.await()
        return creada
    }

    companion object {
        fun novedad(
            id: Int,
            fecha: String = "2026-10-05T09:00:00-05:00",
            estado: EstadoNovedad = EstadoNovedad.ABIERTA,
            respuesta: String? = null,
        ) = Novedad(
            id = id, reporteId = 1, reporteTitulo = "Basura en la esquina", tipo = TipoNovedad.QUEJA,
            descripcion = "No recogieron todo.", estado = estado, respuesta = respuesta, fecha = fecha,
        )
    }
}
