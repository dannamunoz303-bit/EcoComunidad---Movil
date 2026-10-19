package co.ecocomunidad.app.ui

import co.ecocomunidad.app.data.ErrorAuth
import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.TareasRepository
import co.ecocomunidad.app.data.modelo.Accion
import co.ecocomunidad.app.data.modelo.EstadoReporte
import co.ecocomunidad.app.data.modelo.EstadoTarea
import co.ecocomunidad.app.data.modelo.ReporteBasico
import co.ecocomunidad.app.data.modelo.TareaGestor

class TareasFalso : TareasRepository {

    var mias: Resultado<List<TareaGestor>> = Resultado.Exito(emptyList())
    var acciones: Resultado<List<Accion>> = Resultado.Exito(emptyList())
    /** null = la tarea pasa al estado siguiente. */
    var resultadoIniciar: Resultado<TareaGestor>? = null
    var resultadoTerminar: Resultado<TareaGestor>? = null

    var listados = 0
    val operaciones = mutableListOf<String>()

    override suspend fun mias(): Resultado<List<TareaGestor>> {
        listados++
        return mias
    }

    override suspend fun tarea(id: Int): Resultado<TareaGestor> =
        when (val r = mias()) {
            is Resultado.Exito -> r.datos.firstOrNull { it.id == id }?.let { Resultado.Exito(it) }
                ?: Resultado.Fallo(ErrorAuth.Servidor(404, "No encontramos esta tarea entre las tuyas.", null))
            is Resultado.Fallo -> r
        }

    override suspend fun acciones(id: Int): Resultado<List<Accion>> = acciones

    override suspend fun iniciar(id: Int): Resultado<TareaGestor> {
        operaciones += "iniciar $id"
        return resultadoIniciar ?: cambiar(id, EstadoTarea.EN_PROCESO)
    }

    override suspend fun terminar(id: Int): Resultado<TareaGestor> {
        operaciones += "terminar $id"
        return resultadoTerminar ?: cambiar(id, EstadoTarea.TERMINADA)
    }

    /** Como el backend: la tarea cambia y la próxima consulta ya la trae así. */
    private fun cambiar(id: Int, estado: EstadoTarea): Resultado<TareaGestor> {
        val lista = (mias as Resultado.Exito).datos
        val nueva = lista.first { it.id == id }.copy(estado = estado)
        mias = Resultado.Exito(lista.map { if (it.id == id) nueva else it })
        return Resultado.Exito(nueva)
    }

    companion object {
        fun tarea(id: Int, estado: EstadoTarea, reporte: Int = 1) = TareaGestor(
            id = id,
            reporte = ReporteBasico(
                id = reporte, titulo = "Basura en la esquina", categoria = "Basura acumulada",
                direccion = "Calle 10 # 5-20", latitud = "4.60971000", longitud = "-74.08175000",
                estado = EstadoReporte.EN_PROCESO,
            ),
            estado = estado,
            fechaAsignacion = "2026-10-02T09:00:00-05:00",
        )
    }
}
