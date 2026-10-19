package co.ecocomunidad.app.ui

import co.ecocomunidad.app.data.NotificacionesRepository
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.SancionesRepository
import co.ecocomunidad.app.data.modelo.Notificacion
import co.ecocomunidad.app.data.modelo.Sancion
import co.ecocomunidad.app.data.modelo.TipoSancion

class NotificacionesFalso : NotificacionesRepository {

    var lista: Resultado<List<Notificacion>> = Resultado.Exito(emptyList())
    var noLeidas: Resultado<Int> = Resultado.Exito(0)
    /** null = responde la notificación marcada como leída. */
    var resultadoLeer: Resultado<Notificacion>? = null

    var listados = 0
    val leidas = mutableListOf<Int>()

    override suspend fun listar(): Resultado<List<Notificacion>> {
        listados++
        return lista
    }

    override suspend fun contarNoLeidas(): Resultado<Int> = noLeidas

    override suspend fun marcarLeida(id: Int): Resultado<Notificacion> {
        leidas += id
        return resultadoLeer ?: Resultado.Exito(notificacion(id, "reporte_estado", leida = true))
    }

    companion object {
        fun notificacion(
            id: Int,
            tipo: String,
            reporte: Int? = null,
            leida: Boolean = false,
            fecha: String = "2026-10-05T09:00:00-05:00",
        ) = Notificacion(id = id, tipo = tipo, mensaje = "Mensaje $id", leida = leida, fecha = fecha, reporteId = reporte)
    }
}

class SancionesFalso : SancionesRepository {

    var mias: Resultado<List<Sancion>> = Resultado.Exito(emptyList())

    override suspend fun listarMias(): Resultado<List<Sancion>> = mias

    companion object {
        fun sancion(
            id: Int,
            tipo: TipoSancion,
            inicio: String = "2026-10-05T09:00:00-05:00",
            fin: String? = null,
            reporte: Int? = null,
        ) = Sancion(id = id, tipo = tipo, motivo = "Motivo $id", fechaInicio = inicio, fechaFin = fin, reporteId = reporte)
    }
}
