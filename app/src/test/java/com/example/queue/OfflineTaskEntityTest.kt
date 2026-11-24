package com.example.queue

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineTaskEntityTest {

    @Test
    fun taskPriorityLabels_mapCorrectly() {
        assertEquals("HIGH", TaskPriority.label(TaskPriority.HIGH))
        assertEquals("NORMAL", TaskPriority.label(TaskPriority.NORMAL))
        assertEquals("LOW", TaskPriority.label(TaskPriority.LOW))
    }

    @Test
    fun defaultOfflineTaskEntity_hasPendingNetworkAndNormalPriority() {
        val task = OfflineTaskEntity(
            prompt = "Connect to github.com",
            taskType = TaskType.WEB_CONNECT,
            targetValue = "github.com",
            intentName = "WEB_CONNECT",
            confidence = 0.92f
        )
        assertEquals(TaskStatus.PENDING_NETWORK, task.status)
        assertEquals(TaskPriority.NORMAL, task.priority)
        assertEquals(0, task.retryCount)
        assertEquals(3, task.maxRetries)
    }
}
