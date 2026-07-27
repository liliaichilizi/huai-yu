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

            // 过滤掉自己和一些不重要的通知
            if (packageName == "com.operit.huaiyu" || title == null || text == null) {
                return
            }

            val intent = Intent(ACTION_NOTIFICATION_POSTED).apply {
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
