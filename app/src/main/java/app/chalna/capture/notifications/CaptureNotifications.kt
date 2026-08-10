package app.chalna.capture.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.net.toUri
import app.chalna.capture.MainActivity
import app.chalna.capture.R
import app.chalna.capture.capture.CaptureService
import app.chalna.capture.domain.CaptureCommand
import app.chalna.capture.domain.CaptureFailure
import app.chalna.capture.domain.CaptureFailureCode
import app.chalna.capture.domain.CaptureRequest
import app.chalna.capture.domain.CaptureTrigger
import app.chalna.capture.domain.LastCapture
import java.util.UUID

object CaptureNotifications {
    const val ACTIVE_CHANNEL_ID = "active_capture_v2"
    const val RESULT_CHANNEL_ID = "capture_results_v2"
    const val ACTIVE_NOTIFICATION_ID = 1701
    const val RESULT_NOTIFICATION_ID = 1702

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    ACTIVE_CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                },
                NotificationChannel(
                    RESULT_CHANNEL_ID,
                    context.getString(R.string.notification_result_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                },
            ),
        )
    }

    fun starting(context: Context): Notification = activeBase(
        context,
        context.getString(R.string.notification_starting_title),
        context.getString(R.string.notification_starting_text),
    ).build()

    fun active(context: Context, recordingStartedElapsedNanos: Long): Notification {
        val wallStart = System.currentTimeMillis() -
            ((SystemClock.elapsedRealtimeNanos() - recordingStartedElapsedNanos).coerceAtLeast(0) / 1_000_000L)
        return activeBase(
            context,
            context.getString(R.string.notification_recording_title),
            context.getString(R.string.notification_recording_text),
        ).setUsesChronometer(true)
            .setWhen(wallStart)
            .build()
    }

    fun saving(context: Context): Notification = activeBase(
        context,
        context.getString(R.string.notification_saving_title),
        context.getString(R.string.notification_saving_text),
        includeStop = false,
    ).build()

    fun saved(context: Context, capture: LastCapture): Notification {
        ensureChannels(context)
        val view = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_CAPTURE)
            .putExtra(MainActivity.EXTRA_CAPTURE_ID, capture.id)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(
            context,
            capture.id.hashCode(),
            view,
            immutableUpdate(),
        )
        return Notification.Builder(context, RESULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_chalna)
            .setContentTitle(context.getString(R.string.notification_saved_title))
            .setContentText(context.getString(R.string.notification_saved_text))
            .setAutoCancel(true)
            .setSilent(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, context.getString(R.string.action_open), open).build())
            .build()
    }

    fun error(context: Context, failure: CaptureFailure): Notification {
        ensureChannels(context)
        val actionIntent = when (failure.code) {
            CaptureFailureCode.CAMERA_PERMISSION,
            CaptureFailureCode.MICROPHONE_PERMISSION -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData("package:${context.packageName}".toUri())
            CaptureFailureCode.LOW_STORAGE,
            CaptureFailureCode.STORAGE_UNAVAILABLE -> Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            else -> Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val action = PendingIntent.getActivity(
            context,
            50 + failure.code.ordinal,
            actionIntent,
            immutableUpdate(),
        )
        return Notification.Builder(context, RESULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_chalna)
            .setContentTitle(context.getString(R.string.notification_error_title))
            .setContentText(context.getString(failure.messageResource()))
            .setAutoCancel(true)
            .setSilent(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(action)
            .addAction(Notification.Action.Builder(null, context.getString(R.string.action_settings), action).build())
            .build()
    }

    private fun activeBase(
        context: Context,
        title: String,
        text: String,
        includeStop: Boolean = true,
    ): Notification.Builder {
        ensureChannels(context)
        val openIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(context, 20, openIntent, immutableUpdate())
        val builder = Notification.Builder(context, ACTIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_chalna)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        if (includeStop) {
            val request = CaptureRequest(
                invocationId = "notification-${UUID.randomUUID()}",
                command = CaptureCommand.NOTIFICATION_STOP,
                trigger = CaptureTrigger.NOTIFICATION,
                receivedElapsedNanos = SystemClock.elapsedRealtimeNanos(),
            )
            val stop = PendingIntent.getService(
                context,
                21,
                CaptureService.intent(context, request),
                immutableUpdate(),
            )
            builder.addAction(Notification.Action.Builder(null, context.getString(R.string.action_stop), stop).build())
        }
        return builder
    }

    private fun CaptureFailure.messageResource(): Int = when (code) {
        CaptureFailureCode.CAMERA_PERMISSION -> R.string.capture_error_camera_permission
        CaptureFailureCode.MICROPHONE_PERMISSION -> R.string.capture_error_microphone_permission
        CaptureFailureCode.CAMERA_BUSY -> R.string.capture_error_camera_busy
        CaptureFailureCode.LOW_STORAGE -> R.string.capture_error_storage
        CaptureFailureCode.STORAGE_UNAVAILABLE -> R.string.capture_error_storage_unavailable
        else -> R.string.capture_error_camera
    }

    private fun immutableUpdate(): Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}
