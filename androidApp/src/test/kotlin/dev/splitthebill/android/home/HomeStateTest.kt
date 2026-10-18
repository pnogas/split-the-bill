package dev.splitthebill.android.home

import dev.splitthebill.android.data.ExampleBillRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStateTest {
    @Test
    fun exampleBillsMatchTheHomeMockupRowsAndNetToTheBalance() {
        val state = homeUiState(ExampleBillRepository())

        assertEquals("PN", state.initials)
        assertTrue(state.exampleData)
        assertEquals(
            listOf("Pho Lien", "Banh Mi Boys", "Sushi Masa", "The Ossington"),
            state.bills.map { it.merchant },
        )
        assertEquals("Oct 9", state.bills[0].dateLabel)
        assertEquals("4 people", state.bills[0].peopleLabel)
        assertEquals("You're owed $31.20", state.bills[0].status.label)
        assertEquals(
            "Pho Lien, October 9, 4 people, you're owed $31.20",
            state.bills[0].contentDescription,
        )
        assertEquals("Oct 3", state.bills[1].dateLabel)
        assertEquals("3 people", state.bills[1].peopleLabel)
        assertEquals("Settled", state.bills[1].status.label)
        assertEquals("Sep 27", state.bills[2].dateLabel)
        assertEquals("5 people", state.bills[2].peopleLabel)
        assertEquals("You're owed $55.20", state.bills[2].status.label)
        assertEquals("Sep 20", state.bills[3].dateLabel)
        assertEquals("2 people", state.bills[3].peopleLabel)
        assertEquals("You owe $18.75", state.bills[3].status.label)
        assertEquals(
            "The Ossington, September 20, 2 people, you owe $18.75",
            state.bills[3].contentDescription,
        )

        val balance = state.balance as HomeBalance.Owed
        assertEquals("$67.65", balance.amountLabel)
        assertEquals("across 3 bills · example data", balance.detail)
    }

    @Test
    fun emptyHistoryHidesTheBalanceCard() {
        val repository = ExampleBillRepository()
        val state = homeUiState(repository.viewer, emptyList(), exampleData = true)

        assertNull(state.balance)
        assertTrue(state.bills.isEmpty())
        assertEquals("PN", state.initials)
    }
}
