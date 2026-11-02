package com.stillune.app.health

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.stillune.app.MainActivity
import com.stillune.app.R
import com.stillune.app.playback.PlaybackService

/** Also works when only the microphone service is running and the player has been unbound. */
internal fun notifyHearing(context: Context, notice: HearingNotice?) {
    val manager = context.getSystemService(NotificationManager::class.java)
    if (notice == null) { manager.cancel(18); return }
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    manager.createNotificationChannel(NotificationChannel("stillune_hearing", "护耳提醒", NotificationManager.IMPORTANCE_DEFAULT).apply { setShowBadge(false) })
    val open = PendingIntent.getActivity(context, 18, Intent(context, MainActivity::class.java).setAction(MainActivity.OPEN_HEALTH)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val builder = NotificationCompat.Builder(context, "stillune_hearing").setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(notice.title).setContentText(notice.message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(notice.message)).setContentIntent(open).setAutoCancel(true)
    if (notice.kind != HearingNoticeKind.ENVIRONMENT) {
        val pause = PendingIntent.getService(context, 18, Intent(context, PlaybackService::class.java).setAction("pause"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        builder.addAction(android.R.drawable.ic_media_pause, "暂停休息", pause)
    }
    builder.addAction(0, "查看护耳", open)
    manager.notify(18, builder.build())
}
