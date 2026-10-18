package dev.splitthebill.android.review

import dev.splitthebill.shared.BillOwner
import dev.splitthebill.shared.Cents
import dev.splitthebill.shared.LineItemId
import dev.splitthebill.shared.Person
import dev.splitthebill.shared.PersonId
import dev.splitthebill.shared.TipChoice
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillFlowTest {
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    @Test
    fun blankNamesAreDroppedAndAParsedTipIsKept() {
        val review = ReviewUiState(
            merchant = "Pho Lien",
            items = listOf(
                ReviewItemUi("blank", "  ", "1.00", "1"),
                ReviewItemUi("pho", "Rare Beef Pho", "16.50", "1"),
            ),
            tax = "",
            tip = "",
            receiptSubtotal = null,
            receiptTotal = null,
            parsedTip = Cents(600),
        )

        val opened = openDraft(existing = null, review, now, keepTip = false) as DraftOpen.Ready

        assertEquals(listOf(BillOwner.person), opened.draft.people)
        assertEquals(listOf("Rare Beef Pho"), opened.draft.items.map { it.name })
        assertEquals(Cents.Zero, opened.draft.tax)
        assertEquals(TipChoice.Fixed(Cents(600)), opened.draft.tipChoice)
    }

    @Test
    fun returningFromReviewKeepsPeopleAndAssignments() {
        val first = openDraft(null, sampleReview().copy(parsedTip = Cents(200)), now, keepTip = false) as DraftOpen.Ready
        val withMaya = first.draft.copy(
            people = first.draft.people + Person(PersonId("person-1"), "Maya"),
            nextPersonNumber = 2,
            items = first.draft.items.map { it.copy(sharedBy = setOf(BillOwner.id)) },
            tipChoice = TipChoice.Percent(18),
        )
        val edited = sampleReview().copy(
            parsedTip = Cents(200),
            items = sampleReview().items.dropLast(1),
        )

        val again = openDraft(withMaya, edited, now, keepTip = true) as DraftOpen.Ready

        assertEquals(listOf("Me", "Maya"), again.draft.people.map { it.name })
        assertEquals(TipChoice.Percent(18), again.draft.tipChoice)
        assertEquals(setOf(BillOwner.id), again.draft.items.first { it.id == LineItemId("pho") }.sharedBy)
        assertEquals(first.draft.id, again.draft.id)
    }

    @Test
    fun anItemWithoutAPriceBlocksPeople() {
        val review = sampleReview().copy(
            items = sampleReview().items.mapIndexed { index, item ->
                if (index == 0) item.copy(price = "") else item
            },
        )

        val opened = openDraft(null, review, now, keepTip = false)

        assertTrue(opened is DraftOpen.Blocked)
        assertEquals("Enter a price and quantity for each item.", (opened as DraftOpen.Blocked).message)
    }
}
