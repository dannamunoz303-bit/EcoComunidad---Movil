package co.ecocomunidad.app.ui.reportes.crear

import co.ecocomunidad.app.data.modelo.Municipio
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

private val espanol = Locale.forLanguageTag("es-CO")

/** Por nombre, como en el diccionario: "Íquira" va con la I y no después de la Z. */
fun ordenarMunicipios(municipios: List<Municipio>): List<Municipio> {
    val collator = Collator.getInstance(espanol).apply { strength = Collator.SECONDARY }
    return municipios.sortedWith(compareBy(collator) { it.nombre })
}

/**
 * Los municipios cuyo nombre contiene [consulta], sin importar mayúsculas ni tildes
 * ("popayan" encuentra Popayán). Una consulta vacía los deja todos, en el mismo orden.
 */
fun buscarMunicipios(municipios: List<Municipio>, consulta: String): List<Municipio> {
    val buscada = sinTildes(consulta.trim())
    if (buscada.isEmpty()) return municipios
    return municipios.filter { sinTildes(it.nombre).contains(buscada) }
}

private val marcas = Regex("\\p{Mn}+")

private fun sinTildes(texto: String): String =
    marcas.replace(Normalizer.normalize(texto, Normalizer.Form.NFD), "").lowercase(espanol)
