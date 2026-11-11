package co.ecocomunidad.app.ui

import co.ecocomunidad.app.data.Resultado
import co.ecocomunidad.app.data.TerritorioRepository
import co.ecocomunidad.app.data.modelo.Municipio
import co.ecocomunidad.app.data.modelo.Zona

class TerritorioFalso : TerritorioRepository {

    /** A propósito sin orden: el formulario los ordena por nombre. */
    var municipios: Resultado<List<Municipio>> = Resultado.Exito(listOf(TIMBIO, POPAYAN, ALMAGUER, SANTANDER))
    var consultas = 0

    override suspend fun municipios(): Resultado<List<Municipio>> {
        consultas++
        return municipios
    }

    companion object {
        val NORTE = Zona(1, "norte")
        val CENTRO = Zona(2, "centro")
        val SUR = Zona(3, "sur")
        val POPAYAN = Municipio(1, "Popayán", CENTRO)
        val TIMBIO = Municipio(2, "Timbío", CENTRO)
        val ALMAGUER = Municipio(3, "Almaguer", SUR)
        val SANTANDER = Municipio(4, "Santander de Quilichao", NORTE)
    }
}
