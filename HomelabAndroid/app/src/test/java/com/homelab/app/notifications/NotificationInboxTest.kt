package com.homelab.app.notifications

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NotificationInboxTest {
    @get:Rule
    val files = TemporaryFolder()

    @Test
    fun `records different sources newest first and ignores a replayed Docker message`() = runTest {
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        every { context.filesDir } returns files.root
        val inbox = NotificationInbox(context, Json)
        val docker = InboxNotification("docker:123", NotificationSource.DOCKER, "Docker", "Container stopped", 1L)
        val service = InboxNotification("service:456", NotificationSource.SERVICE, "Service", "Offline", 2L)

        inbox.record(docker)
        inbox.record(service)
        inbox.record(docker.copy(receivedAt = 3L))

        assertEquals(listOf(service, docker), inbox.notifications.first())
    }
}
