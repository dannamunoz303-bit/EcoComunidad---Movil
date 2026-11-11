package co.ecocomunidad.app.data.modelo

import kotlinx.serialization.Serializable

/** DispositivoSerializer: el código que Firebase Cloud Messaging le da a esta instalación de la app. */
@Serializable
data class DispositivoPeticion(val token: String)
