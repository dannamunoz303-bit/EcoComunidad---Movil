package co.ecocomunidad.app.data.push

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import co.ecocomunidad.app.EcoComunidadApp
import co.ecocomunidad.app.MainActivity
import co.ecocomunidad.app.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch

/**
 * Avisos push de Firebase (RN-26).
 *
 * Con la app cerrada o en segundo plano, Android muestra el aviso solo, en el canal que manda el backend
 * y con el ícono del manifest; al tocarlo abre MainActivity con data (tipo y reporte_id) en los extras.
 * Con la app abierta llega aquí: se muestra igual (en tareas_asignadas suena la alarma) y se avisa a
 * las pantallas para que actualicen la campana.
 */
class ServicioAvisos : FirebaseMessagingService() {

    private val contenedor get() = (application as EcoComunidadApp).contenedor

    override fun onMessageReceived(mensaje: RemoteMessage) {
        val tipo = mensaje.data[EXTRA_TIPO].orEmpty()
        val reporteId = mensaje.data[EXTRA_REPORTE_ID].orEmpty()
        val aviso = mensaje.notification
        val canal = aviso?.channelId ?: CanalesPush.paraTipo(tipo)
        val texto = aviso?.body
        if (texto != null) mostrar(aviso.title ?: getString(R.string.app_name), texto, canal, tipo, reporteId)
        contenedor.eventosPush.llego(tipo)
    }

    /** Firebase cambió el código de este celular: si hay sesión, el backend debe saberlo. */
    override fun onNewToken(token: String) {
        contenedor.alcanceApp.launch { contenedor.authRepository.registrarDispositivo(token) }
    }

    private fun mostrar(titulo: String, texto: String, canal: String, tipo: String, reporteId: String) {
        val avisos = NotificationManagerCompat.from(this)
        // Sin el permiso (Android 13+) no se muestra: el aviso sigue en la campana
        if (!avisos.areNotificationsEnabled()) return

        val abrir = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TIPO, tipo)
            putExtra(EXTRA_REPORTE_ID, reporteId)
        }
        val id = System.currentTimeMillis().toInt()
        val alTocar = PendingIntent.getActivity(
            this, id, abrir, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val esTarea = canal == CanalesPush.TAREAS_ASIGNADAS
        val notificacion = NotificationCompat.Builder(this, canal)
            .setSmallIcon(R.drawable.ic_campana)
            .setColor(ContextCompat.getColor(this, R.color.verde_bosque))
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setPriority(if (esTarea) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (esTarea) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(alTocar)
            .setAutoCancel(true)
            .build()
        try {
            avisos.notify(id, notificacion)
        } catch (e: SecurityException) {
            // El permiso se quitó justo ahora
        }
    }
}
