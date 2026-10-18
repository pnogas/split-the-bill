package dev.splitthebill.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class BillDraftTest {
    @Test
    fun ownerStartsOnTheBillAndCannotBeRemoved() {
        val draft = addPerson(sampleDraft(), "Maya")

        assertEquals(listOf("Me", "Maya"), draft.people.map { it.name })
        assertFalse(sampleDraft().hasEnoughPeople())
        assertTrue(draft.hasEnoughPeople())
        assertEquals(draft.people, removePerson(draft, BillOwner.id).people)
    }

    @Test
    fun blankNamesAreIgnoredAndPeopleCanBeRenamed() {
        val added = addPerson(sampleDraft(), "   ")
        assertEquals(1, added.people.size)

        val maya = addPerson(sampleDraft(), " Maya ")
        val renamed = renamePerson(maya, maya.people.last().id, "Maya Chen")
        assertEquals("Maya Chen", renamed.people.last().name)
        assertEquals(maya.people, renamePerson(maya, maya.people.last().id, "  ").people)
    }

    @Test
    fun removingAPersonDropsTheirAssignments() {
        val maya = addPerson(sampleDraft(), "Maya")
        val mayaId = maya.people.last().id
        val assigned = assignEveryone(maya, pho)
        val removed = removePerson(assigned, mayaId)

        assertEquals(setOf(BillOwner.id), removed.items.single().sharedBy)
        assertEquals(listOf(BillOwner.person), removed.people)
    }

    @Test
    fun toggleEveryoneAndSplitRemaining() {
        val draft = addPerson(sampleDraft(items = listOf(phoItem(), rollsItem())), "Maya")
        val maya = draft.people.last().id

        val claimed = toggleAssignment(draft, pho, BillOwner.id)
        assertEquals(setOf(BillOwner.id), claimed.items.first().sharedBy)
        val cleared = toggleAssignment(claimed, pho, BillOwner.id)
        assertTrue(cleared.items.first().sharedBy.isEmpty())

        val everyone = assignEveryone(draft, pho)
        assertEquals(setOf(BillOwner.id, maya), everyone.items.first { it.id == pho }.sharedBy)
        assertTrue(everyone.items.first { it.id == rolls }.sharedBy.isEmpty())

        val filled = splitRemainingEvenly(everyone)
        assertEquals(setOf(BillOwner.id, maya), filled.items.first { it.id == pho }.sharedBy)
        assertEquals(setOf(BillOwner.id, maya), filled.items.first { it.id == rolls }.sharedBy)
    }

    @Test
    fun unassignedItemsBlockContinuingThroughSplit() {
        val draft = addPerson(sampleDraft(items = listOf(phoItem(), rollsItem())), "Maya")
        val partial = toggleAssignment(draft, pho, BillOwner.id)

        val error = assignmentError(partial)
        assertIs<SplitError.UnassignedItems>(error)
        assertEquals(listOf(rolls), error.itemIds)
        assertEquals(listOf("Spring Rolls"), unassignedItemNames(partial))
        assertEquals("Assign every item. Still open: Spring Rolls.", assignmentMessage(partial))

        val ready = assignEveryone(partial, rolls)
        assertNull(assignmentError(ready))
        assertNull(assignmentMessage(ready))
        val shares = assertIs<SplitResult.Success>(split(ready.toBill())).shares
        assertTrue(sharesAddUp(ready.toBill(), shares))
    }

    @Test
    fun tipPercentRoundsHalfUpAndParsedTipIsKept() {
        assertEquals(Cents(615), tipAmount(Cents(4_100), TipChoice.Percent(15)))
        assertEquals(Cents(180), tipAmount(Cents(1_000), TipChoice.Percent(18)))
        assertEquals(Cents(67), tipAmount(Cents(333), TipChoice.Percent(20)))
        assertEquals(0L, percentOf(0, 15))
        assertEquals(Cents(250), tipAmount(Cents(1_000), TipChoice.Fixed(Cents(250))))
        assertEquals(Cents.Zero, tipAmount(Cents(1_000), TipChoice.None))
        assertEquals(Cents.Zero, tipAmount(Cents(1_000), TipChoice.Unset))

        assertEquals(TipChoice.Fixed(Cents(600)), initialTipChoice(Cents(600), editedTip = null))
        assertEquals(TipChoice.Fixed(Cents(700)), initialTipChoice(Cents(600), editedTip = Cents(700)))
        assertEquals(TipChoice.Unset, initialTipChoice(parsedTip = null, editedTip = null))
        assertEquals(TipChoice.Unset, initialTipChoice(Cents.Zero, Cents.Zero))
    }

    @Test
    fun shareSummaryListsEachPersonAndConfirmsTheTotal() {
        val draft = assignEveryone(addPerson(sampleDraft(), "Maya"), pho).copy(
            tipChoice = TipChoice.Fixed(Cents(200)),
            tax = Cents(100),
        )
        val bill = draft.toBill()
        val shares = assertIs<SplitResult.Success>(split(bill)).shares

        val text = shareSummary(bill, shares)
        assertTrue(text.startsWith("Pho Lien"))
        assertTrue(text.contains("Rare Beef Pho · Me, Maya · $16.50"))
        assertTrue(text.contains("Tip $2.00"))
        assertTrue(text.contains("Total $19.50"))
        assertTrue(text.contains("Me"))
        assertTrue(text.contains("Maya"))
        assertTrue(text.endsWith("These add up to $19.50."))
        assertTrue(sharesAddUp(bill, shares))
        assertEquals(bill.total, shares.fold(Cents.Zero) { acc, share -> acc + share.total })
    }
}

private val pho = LineItemId("pho")
private val rolls = LineItemId("rolls")

private fun sampleDraft(items: List<LineItem> = listOf(phoItem())) = BillDraft(
    id = BillId("bill-1"),
    createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000),
    merchant = "Pho Lien",
    people = listOf(BillOwner.person),
    items = items,
    tax = Cents.Zero,
    tipChoice = TipChoice.Unset,
    parsedTip = null,
    nextPersonNumber = 1,
)

private fun phoItem() = LineItem(pho, "Rare Beef Pho", Cents(1_650), 1, emptySet())

private fun rollsItem() = LineItem(rolls, "Spring Rolls", Cents(1_100), 1, emptySet())
