package com.alekpeed.lifeos.collections

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.TestHome
import com.alekpeed.lifeos.history.History
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// §5.3. The module has to be category-agnostic, so the tests are too: nothing here knows
// what a baseball card is. What is worth pinning down is that the numbers are honest —
// completeness only when a target set says what completes it, quantity carried through
// cost and value, and a rollup that admits how much of itself is missing.
class CollectionsTest {

    @BeforeTest
    fun setUp() {
        TestHome.clear()
        History.forget()
    }

    @AfterTest
    fun tearDown() {
        TestHome.clear()
    }

    private fun cards(vararg items: CollItem) = Collection(
        id = 1,
        name = "1952 Topps",
        category = "cards",
        catalogSystem = "Beckett",
        conditionScale = listOf("Poor", "Good", "VG", "NM", "Gem Mint"),
        targetSet = listOf("1", "2", "3", "4"),
        defaultCurrency = "USD",
        items = items.toList(),
    )

    @Test
    fun `a collection with no target set has no completeness rather than a fake hundred percent`() {
        val c = cards().copy(targetSet = emptyList())
        assertNull(completeness(c), "claiming a set is complete when nobody said what completes it is worse than saying nothing")
    }

    @Test
    fun `completeness counts what is held against the target and names what is missing`() {
        val c = cards(
            CollItem(1, "Mantle", catalogNumber = "1"),
            CollItem(2, "Mays", catalogNumber = "3"),
        )
        val done = completeness(c)!!
        assertEquals(4, done.target)
        assertEquals(2, done.held)
        assertEquals(listOf("2", "4"), done.missing)
        assertEquals(0.5f, done.fraction)
        assertFalse(done.complete)
    }

    @Test
    fun `catalog references match regardless of case and padding`() {
        val c = cards(CollItem(1, "a", catalogNumber = " 1 ")).copy(targetSet = listOf("1"))
        assertTrue(completeness(c)!!.complete)
    }

    @Test
    fun `a wanted item does not count towards the set you actually hold`() {
        val c = cards(
            CollItem(1, "Have it", catalogNumber = "1"),
            CollItem(2, "Want it", catalogNumber = "2", status = ItemStatus.WANTED),
        )
        val done = completeness(c)!!
        assertEquals(1, done.held, "wanting a card is not owning it")
        assertTrue(done.missing.contains("2"))
    }

    @Test
    fun `a sold item drops out of the holding and out of the value`() {
        val c = cards(
            CollItem(1, "Kept", catalogNumber = "1", estimatedValue = 100.0),
            CollItem(2, "Sold", catalogNumber = "2", estimatedValue = 500.0, status = ItemStatus.SOLD),
        )
        assertEquals(1, completeness(c)!!.held)
        assertEquals(100.0, valueRollup(c).value)
    }

    @Test
    fun `quantity carries through cost and value because duplicates are normal`() {
        val i = CollItem(1, "Common", quantity = 3, acquiredPrice = 2.0, estimatedValue = 5.0)
        assertEquals(6.0, i.costBasis)
        assertEquals(15.0, i.valuation)
        assertTrue(i.isDuplicate)
        assertFalse(CollItem(2, "Single", quantity = 1, acquiredPrice = 2.0).isDuplicate)
    }

    @Test
    fun `the rollup says how much of itself is missing`() {
        val c = cards(
            CollItem(1, "Priced and valued", acquiredPrice = 10.0, estimatedValue = 40.0),
            CollItem(2, "Neither"),
        )
        val roll = valueRollup(c)
        assertEquals(10.0, roll.cost)
        assertEquals(40.0, roll.value)
        assertEquals(30.0, roll.gain)
        assertEquals(1, roll.unpriced)
        assertEquals(1, roll.unvalued)
    }

    @Test
    fun `a loss reads as a loss rather than a negative gain`() {
        val c = cards(CollItem(1, "Bought high", acquiredPrice = 100.0, estimatedValue = 40.0))
        assertEquals(-60.0, valueRollup(c).gain)
    }

    @Test
    fun `the want list spans every collection, because a shop does not care which one`() {
        val data = CollectionsData(
            listOf(
                cards(CollItem(1, "Card I want", status = ItemStatus.WANTED)),
                Collection(2, "Coins", items = listOf(CollItem(1, "Coin on order", status = ItemStatus.ON_ORDER))),
            ),
        )
        val want = wantList(data)
        assertEquals(2, want.size)
        assertEquals(setOf("1952 Topps", "Coins"), want.map { it.collection.name }.toSet())
    }

    @Test
    fun `duplicates are the trade stock, worst-kept first`() {
        val c = cards(
            CollItem(1, "Three of these", quantity = 3),
            CollItem(2, "Two of these", quantity = 2),
            CollItem(3, "Just one", quantity = 1),
            CollItem(4, "Spare but sold", quantity = 5, status = ItemStatus.SOLD),
        )
        assertEquals(listOf("Three of these", "Two of these"), duplicates(c).map { it.name })
    }

    @Test
    fun `grouping by condition follows the collection's own scale, not the alphabet`() {
        val c = cards(
            CollItem(1, "a", condition = "Gem Mint"),
            CollItem(2, "b", condition = "Poor"),
            CollItem(3, "c", condition = "VG"),
        )
        assertEquals(
            listOf("Poor", "VG", "Gem Mint"),
            grouped(c, GroupBy.CONDITION).map { it.first },
            "alphabetical order on condition grades is meaningless",
        )
    }

    @Test
    fun `an ungraded item groups under Unfiled, at the end`() {
        val c = cards(CollItem(1, "a", condition = "Poor"), CollItem(2, "b"))
        assertEquals(listOf("Poor", "Unfiled"), grouped(c, GroupBy.CONDITION).map { it.first })
    }

    @Test
    fun `the insurance export names values, photos and where things are kept`() {
        val c = cards(
            CollItem(
                1, "Mantle", catalogNumber = "1", year = "1952", condition = "NM",
                quantity = 2, estimatedValue = 1000.0, storageLocation = "binder 2",
                graded = true, grader = "PSA", gradeValue = "7",
            ),
            CollItem(2, "Not mine any more", status = ItemStatus.SOLD, estimatedValue = 99.0),
        )
        val text = insuranceExport(c)

        assertTrue(text.contains("1952 Topps"))
        assertTrue(text.contains("Catalog system: Beckett"))
        assertTrue(text.contains("Total estimated value: USD 2000.0"), "quantity has to be in the total")
        assertTrue(text.contains("graded PSA 7"))
        assertTrue(text.contains("binder 2"))
        assertFalse(text.contains("Not mine any more"), "a sold item is not insured")
    }

    @Test
    fun `a blob written before the overhaul still reads, and gains the new fields`() {
        Storage.write(
            "Collections",
            """{"collections":[{"id":1,"name":"Old","description":"","items":[
                {"id":2,"name":"A thing","acquiredDate":"2026-01-01","tags":["x"],"notes":"n"}
            ],"photoBlob":""}]}""".trimIndent(),
        )
        val c = loadCollections().collections.single()
        assertEquals("Old", c.name)

        val i = c.items.single()
        assertEquals("A thing", i.name)
        assertEquals("2026-01-01", i.acquiredDate)
        assertEquals(listOf("x"), i.tags)
        // The new fields arrive at their defaults rather than breaking the decode.
        assertEquals(1, i.quantity)
        assertEquals(ItemStatus.OWNED, i.status)
        assertEquals("", i.catalogNumber)
    }

    @Test
    fun `a flat text store still migrates into one collection`() {
        Storage.write("Collections", "First\nSecond\n")
        val c = loadCollections().collections.single()
        assertEquals(listOf("First", "Second"), c.items.map { it.name })
    }
}
