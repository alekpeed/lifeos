package com.alekpeed.lifeos.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

class RecordMergeTest {
    private fun check(base: String?, local: String?, remote: String?, expected: String?) {
        val actual = RecordMerge.text(base, local, remote)
        assertEquals(expected?.let { Json.parseToJsonElement(it) }, actual?.let { Json.parseToJsonElement(it) })
    }
    @Test fun separateFieldsSurvive() = check("{\"a\":1,\"b\":1}", "{\"a\":2,\"b\":1}", "{\"a\":1,\"b\":2}", "{\"a\":2,\"b\":2}")
    @Test fun staleDeviceDoesNotResurrectDeletion() = check("[{\"id\":1}]", "[{\"id\":1}]", "[]", "[]")
    @Test fun additionsAndDeletionMerge() = check("[{\"id\":1},{\"id\":2}]", "[{\"id\":2},{\"id\":3}]", "[{\"id\":1},{\"id\":2},{\"id\":4}]", "[{\"id\":2},{\"id\":4},{\"id\":3}]")
    @Test fun initialPull() = check(null, null, "[{\"id\":1}]", "[{\"id\":1}]")
    @Test fun editMadeDuringRequestSurvivesResponse() = check("{\"title\":\"sent\",\"done\":false}", "{\"title\":\"new edit\",\"done\":false}", "{\"title\":\"sent\",\"done\":true}", "{\"title\":\"new edit\",\"done\":true}")
    @Test fun moduleDeletion() = check("{\"a\":1}", null, "{\"a\":1}", null)
}
