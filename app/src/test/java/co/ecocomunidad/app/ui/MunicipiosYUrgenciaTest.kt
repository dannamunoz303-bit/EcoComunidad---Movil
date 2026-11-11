package co.ecocomunidad.app.ui

import co.ecocomunidad.app.R
import co.ecocomunidad.app.data.modelo.Municipio
import co.ecocomunidad.app.data.modelo.Urgencia
import co.ecocomunidad.app.ui.TerritorioFalso.Companion.ALMAGUER
import co.ecocomunidad.app.ui.TerritorioFalso.Companion.POPAYAN
import co.ecocomunidad.app.ui.TerritorioFalso.Companion.SANTANDER
import co.ecocomunidad.app.ui.TerritorioFalso.Companion.TIMBIO
import co.ecocomunidad.app.ui.reportes.color
import co.ecocomunidad.app.ui.reportes.crear.buscarMunicipios
import co.ecocomunidad.app.ui.reportes.crear.ordenarMunicipios
import co.ecocomunidad.app.ui.reportes.etiqueta
import co.ecocomunidad.app.ui.reportes.lugarDelReporte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Selector de municipio (RN-23), lugar del reporte en el detalle y etiqueta de urgencia (RN-25). */
class MunicipiosYUrgenciaTest {

    private val todos = ordenarMunicipios(listOf(TIMBIO, POPAYAN, ALMAGUER, SANTANDER))

    @Test
    fun ordenPorNombre_comoEnElDiccionario() {
        assertEquals(listOf("Almaguer", "Popayán", "Santander de Quilichao", "Timbío"), todos.map { it.nombre })
        // La tilde no manda la palabra al final
        val conTilde = ordenarMunicipios(listOf(Municipio(9, "Zarzal"), Municipio(8, "Ínzá"), Municipio(7, "Bolívar")))
        assertEquals(listOf("Bolívar", "Ínzá", "Zarzal"), conTilde.map { it.nombre })
    }

    @Test
    fun busqueda_sinImportarTildesNiMayusculas() {
        assertEquals(listOf(POPAYAN), buscarMunicipios(todos, "popayan"))
        assertEquals(listOf(POPAYAN), buscarMunicipios(todos, " POPAYÁN "))
        assertEquals(listOf(TIMBIO), buscarMunicipios(todos, "timbio"))
        // Busca en cualquier parte del nombre
        assertEquals(listOf(SANTANDER), buscarMunicipios(todos, "quili"))
        // "Popayán" también tiene "an" sin la tilde; se conserva el orden
        assertEquals(listOf(POPAYAN, SANTANDER), buscarMunicipios(todos, "an"))
    }

    @Test
    fun busquedaVacia_dejaTodos_ySinCoincidencias_ninguno() {
        assertEquals(todos, buscarMunicipios(todos, ""))
        assertEquals(todos, buscarMunicipios(todos, "   "))
        assertTrue(buscarMunicipios(todos, "Bogotá").isEmpty())
    }

    @Test
    fun lugar_conMunicipioZonaYVereda() {
        assertEquals(
            "Popayán · Zona centro\nVereda o corregimiento: La Playa",
            lugarDelReporte("Popayán", " La Playa ", "centro"),
        )
        assertEquals("Popayán · Zona centro", lugarDelReporte("Popayán", "  ", "centro"))
    }

    @Test
    fun reporteViejoSinMunicipio_diceSinMunicipio() {
        assertEquals("Sin municipio", lugarDelReporte(null, null, null))
        assertEquals("Sin municipio\nVereda o corregimiento: El Cofre", lugarDelReporte(null, "El Cofre", null))
    }

    @Test
    fun urgencia_textoYColorDeMarca() {
        assertEquals(R.string.urgencia_alta, Urgencia.ALTA.etiqueta)
        assertEquals(R.string.urgencia_media, Urgencia.MEDIA.etiqueta)
        assertEquals(R.string.urgencia_baja, Urgencia.BAJA.etiqueta)
        assertEquals(R.color.arcilla, Urgencia.ALTA.color)
        assertEquals(R.color.ambar, Urgencia.MEDIA.color)
        assertEquals(R.color.verde_musgo, Urgencia.BAJA.color)
    }
}
