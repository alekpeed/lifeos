package com.alekpeed.lifeos.data

import com.alekpeed.lifeos.sync.RecordMerge
import kotlinx.serialization.json.*
import kotlin.test.*

class RecordIdsTest {
    @Test fun rejectsSmallAndRepeatedDraws() {
        val draws = listOf(0L, 1L, MIN_RECORD_ID shl 11, MIN_RECORD_ID shl 11, -1L).iterator()
        val generator = RecordIdGenerator { draws.next() }
        assertEquals(MIN_RECORD_ID, generator.next())
        assertEquals(MAX_RECORD_ID, generator.next())
    }
    @Test fun offlineAdditionsFromSameSnapshotRemainSeparate() {
        val old = """[{"id":1,"title":"Existing"}]"""
        val snapshot = com.alekpeed.lifeos.collections.CollectionsData()
        val a = com.alekpeed.lifeos.collections.nextCollectionId(snapshot)
        val b = com.alekpeed.lifeos.collections.nextCollectionId(snapshot)
        assertNotEquals(a, b)
        val merged = RecordMerge.text(old, """[{"id":1,"title":"Existing"},{"id":$a,"title":"Phone"}]""", """[{"id":1,"title":"Existing"},{"id":$b,"title":"Desktop"}]""")!!
        val rows = Json.parseToJsonElement(merged).jsonArray
        assertEquals(setOf(1L,a,b), rows.map { it.jsonObject.getValue("id").jsonPrimitive.long }.toSet())
        assertEquals(setOf("Existing","Phone","Desktop"), rows.map { it.jsonObject.getValue("title").jsonPrimitive.content }.toSet())
    }
    @Test fun batchIdsAreDistinctAndExactlyRepresentableInJavascript() {
        val ids = List(20_000) { newRecordId() }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { id ->
            assertTrue(id in MIN_RECORD_ID..MAX_RECORD_ID)
            assertEquals(id, id.toDouble().toLong())
            assertEquals(id, Json.parseToJsonElement(id.toString()).jsonPrimitive.long)
        }
    }
}
