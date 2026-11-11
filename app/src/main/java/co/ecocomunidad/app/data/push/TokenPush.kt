package co.ecocomunidad.app.data.push

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** De dónde sale el código del celular para los avisos push. En las pruebas se cambia por uno falso. */
fun interface TokenPush {
    /** null si no se pudo obtener (sin Google Play Services, sin red, Firebase sin configurar). */
    suspend fun token(): String?
}

/** El token que entrega Firebase Cloud Messaging a esta instalación. */
class TokenFirebase : TokenPush {
    override suspend fun token(): String? =
        try {
            suspendCancellableCoroutine { continuacion ->
                FirebaseMessaging.getInstance().token.addOnCompleteListener { tarea ->
                    continuacion.resume(if (tarea.isSuccessful) tarea.result else null)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // getInstance() falla si Firebase no se pudo iniciar
            null
        }
}
