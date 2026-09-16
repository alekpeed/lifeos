package com.alekpeed.lifeos.data

import com.alekpeed.lifeos.TestHome
import com.alekpeed.lifeos.calendar.reminderAlarmId
import com.alekpeed.lifeos.tasks.taskReminderId
import com.alekpeed.lifeos.timecapsules.capsuleReminderId
import kotlin.test.*

class AlarmIdsTest {
    @BeforeTest fun setup() = TestHome.clear()
    @AfterTest fun cleanup() = TestHome.clear()
    @Test fun equalModuloIdsDoNotReplaceEachOthersAlarms() {
        val id = MIN_RECORD_ID + 10
        val first = taskReminderId(id)
        assertNotEquals(first, taskReminderId(id + 90_000))
        assertEquals(first, taskReminderId(id)) // mapping read back from disk
        assertNotEquals(first, capsuleReminderId(id))
        assertNotEquals(first, reminderAlarmId(id))
    }
    @Test fun concurrentRecordCreationIsSafe() {
        val pool = java.util.concurrent.Executors.newFixedThreadPool(8)
        try {
            val ids = pool.invokeAll(List(2_000) { java.util.concurrent.Callable { newRecordId() } }).map { it.get() }
            assertEquals(ids.size, ids.toSet().size)
        } finally { pool.shutdownNow() }
    }
    @Test fun existingAlarmCodesRemainCompatible() {
        assertEquals(800_123, taskReminderId(123))
        assertEquals(900_123, capsuleReminderId(123))
        assertEquals(700_123, reminderAlarmId(123))
    }
}
