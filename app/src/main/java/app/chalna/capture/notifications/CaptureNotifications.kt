package app.chalna.capture.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import app.chalna.capture.MainActivity
import app.chalna.capture.R
import app.chalna.capture.capture.CaptureService
import app.chalna.capture.domain.LastCapture
import androidx.core.net.toUri

object CaptureNotifications {
    const val CHANNEL_ID = "active_capture"
    const val NOTIFICATION_ID = 1701

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW))
    }

    fun active(context: Context): Notification {
        ensureChannel(context)
        val stop = PendingIntent.getService(context, 1, CaptureService.intent(context, CaptureService.ACTION_STOP), immutableUpdate())
        val openIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(context, 2, openIntent, immutableUpdate())
        val settings = PendingIntent.getActivity(context, 3, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData("package:${context.packageName}".toUri()), immutableUpdate())
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_chalna)
            .setContentTitle(context.getString(R.string.notification_recording_title))
            .setContentText(context.getString(R.string.notification_recording_text))
            .setOngoing(true).setCategory(Notification.CATEGORY_SERVICE).setContentIntent(open)
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis())
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, context.getString(R.string.action_stop), stop).build())
            .build()
    }

    fun starting(context: Context): Notification {
        ensureChannel(context)
        val stop = PendingIntent.getService(context, 1, CaptureService.intent(context, CaptureService.ACTION_STOP), immutableUpdate())
        val openIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(context, 2, openIntent, immutableUpdate())
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_chalna)
            .setContentTitle(context.getString(R.string.notification_starting_title))
            .setContentText(context.getString(R.string.notification_starting_text))
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, context.getString(R.string.action_stop), stop).build())
            .build()
    }

    fun saved(context: Context, capture: LastCapture): Notification {
        ensureChannel(context)
        val view = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_CAPTURE)
            .putExtra(MainActivity.EXTRA_CAPTURE_ID, capture.id)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(context, capture.id.hashCode(), view, immutableUpdate())
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_chalna)
            .setContentTitle(context.getString(R.string.notification_saved_title))
            .setContentText(context.getString(R.string.notification_saved_text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, context.getString(R.string.action_open), open).build())
            .build()
    }

    fun error(context: Context, message: String): Notification {
        ensureChannel(context)
        val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData("package:${context.packageName}".toUri())
        val settings = PendingIntent.getActivity(context, 5, settingsIntent, immutableUpdate())
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_chalna)
            .setContentTitle(context.getString(R.string.notification_error_title))
            .setContentText(message.take(120))
            .setAutoCancel(true)
            .setContentIntent(settings)
            .addAction(Notification.Action.Builder(null, context.getString(R.string.action_settings), settings).build())
            .build()
    }

    private fun immutableUpdate() = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}
