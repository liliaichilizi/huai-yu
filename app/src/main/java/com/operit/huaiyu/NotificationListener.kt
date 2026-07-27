package com.operit.huaiyu

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationListener : NotificationListenerService() {

    companion object {
        const val ACTION_NOTIFICATION_POSTED = "com.operit.huaiyu.NOTIFICATION_POSTED"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let {
            val packageName = it.packageName
            val notification = it.notification
            val title = notification.extras.getString("android.title")
            val text = notification.extras.getCharSequence("android.text")?.toString()

            // 通知黑名单：不转发这些App的通知
            val blacklist = setOf(
                "com.operit.huaiyu",      // 自己
                "com.follow.clash",       // 赔钱机场（VPN）
                "com.android.systemui",   // 系统UI
                "android"                 // 系统通知
            )
            if (packageName in blacklist || title == null || text == null) {
                return
            }

            val intent = Intent(ACTION_NOTIFICATION_POSTED).apply {
                setPackage("com.operit.huaiyu")
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TEXT, text)
            }
            sendBroadcast(intent)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // 暂时不处理移除事件
    }
}
