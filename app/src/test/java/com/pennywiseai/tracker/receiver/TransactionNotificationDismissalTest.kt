package com.pennywiseai.tracker.receiver

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.service.notification.StatusBarNotification
import io.mockk.*
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionNotificationDismissalTest {

    @Test
    fun `channel id is upgraded to v2 for high importance`() {
        assertEquals("transaction_notifications_v2", SmsBroadcastReceiver.CHANNEL_ID)
    }

    @Test
    fun `dismissTransactionAlert ignores non-positive transaction ids`() {
        val context = mockk<Context>(relaxed = true)
        SmsBroadcastReceiver.dismissTransactionAlert(context, 0L)
        SmsBroadcastReceiver.dismissTransactionAlert(context, -1L)
        verify { context wasNot Called }
    }

    @Test
    fun `dismissTransactionAlert cancels notification with transaction id`() {
        val context = mockk<Context>()
        val notificationManager = mockk<NotificationManager>(relaxed = true)
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager
        every { context.applicationContext } returns context
        every { notificationManager.activeNotifications } returns emptyArray()

        SmsBroadcastReceiver.dismissTransactionAlert(context, 42L)

        verify { notificationManager.cancel(42) }
    }

    @Test
    fun `dismissAllTransactionAlerts cancels active transaction alerts and group summary`() {
        val context = mockk<Context>()
        val notificationManager = mockk<NotificationManager>(relaxed = true)
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager

        val notif1 = mockk<StatusBarNotification>()
        val notifObj1 = mockk<Notification>()
        every { notif1.notification } returns notifObj1
        every { notif1.tag } returns null
        every { notif1.id } returns 101
        every { notifObj1.group } returns "com.pennywiseai.tracker.TRANSACTION_ALERTS"
        every { notifObj1.channelId } returns SmsBroadcastReceiver.CHANNEL_ID

        val notif2 = mockk<StatusBarNotification>()
        val notifObj2 = mockk<Notification>()
        every { notif2.notification } returns notifObj2
        every { notif2.tag } returns "other_tag"
        every { notif2.id } returns 202
        every { notifObj2.group } returns null
        every { notifObj2.channelId } returns SmsBroadcastReceiver.CHANNEL_ID

        val notif3 = mockk<StatusBarNotification>()
        val notifObj3 = mockk<Notification>()
        every { notif3.notification } returns notifObj3
        every { notif3.tag } returns "unrelated"
        every { notif3.id } returns 303
        every { notifObj3.group } returns "unrelated_group"
        every { notifObj3.channelId } returns "unrelated_channel"

        every { notificationManager.activeNotifications } returns arrayOf(notif1, notif2, notif3)

        SmsBroadcastReceiver.dismissAllTransactionAlerts(context)

        verify { notificationManager.cancel(null, 101) }
        verify { notificationManager.cancel("other_tag", 202) }
        verify(exactly = 0) { notificationManager.cancel("unrelated", 303) }
        verify { notificationManager.cancel(-3) }
    }
}
