package co.ecocomunidad.app.data.modelo

import kotlinx.serialization.Serializable

// Nombres tomados de territorio/serializers.py del backend (tablas 4.11 y 4.12).

/** ZonaSerializer. [nombre]: norte, centro o sur. */
@Serializable
data class Zona(val id: Int, val nombre: String)

/**
 * MunicipioSerializer (GET municipios/, con su zona) y MunicipioResumenSerializer (dentro del
 * reporte, sin zona: ahí la zona va en Reporte.zona).
 */
@Serializable
data class Municipio(val id: Int, val nombre: String, val zona: Zona? = null)
