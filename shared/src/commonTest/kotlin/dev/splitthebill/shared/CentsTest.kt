package dev.splitthebill.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class CentsTest {
    @Test
    fun plusAndMinusKeepWholeCents() {
        val ten = Cents(1_000)
        val three = Cents(300)

        assertEquals(Cents(1_300), ten + three)
        assertEquals(Cents(700), ten - three)
    }

    @Test
    fun timesScalesAUnitPriceByQuantity() {
        assertEquals(Cents(1_500), Cents(500) * 3)
        assertEquals(Cents.Zero, Cents(500) * 0)
    }
}
