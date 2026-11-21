package com.albertsalendah.cafecashier.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderDraftTest {

    // Shorthand: category 1, no discount unless given.
    private fun OrderDraft.add(productId: Long, name: String, price: Long, percent: Int = 0) =
        addProduct(productId, categoryId = 1, name = name, unitPrice = price, discountPercent = percent)

    @Test
    fun addingAProductCreatesALineWithQuantityOne() {
        val draft = OrderDraft().addProduct(
            productId = 7, categoryId = 3, name = "Bakmi Gr. Roa", unitPrice = 13_000, discountPercent = 0
        )
        assertEquals(1, draft.lines.size)
        val line = draft.lines.single()
        assertEquals(7L, line.productId)
        assertEquals(3L, line.categoryId)
        assertEquals("Bakmi Gr. Roa", line.productName)
        assertEquals(13_000L, line.unitPrice)
        assertEquals(1, line.quantity)
        assertEquals(1, draft.itemCount)
    }

    @Test
    fun addingTheSameProductAgainRaisesTheQuantity() {
        val draft = OrderDraft()
            .add(7, "Bakmi Gr. Roa", 13_000)
            .add(7, "Bakmi Gr. Roa", 13_000)
            .add(7, "Bakmi Gr. Roa", 13_000)
        assertEquals(1, draft.lines.size)
        assertEquals(3, draft.lines.single().quantity)
        assertEquals(3, draft.itemCount)
    }

    @Test
    fun differentProductsGetTheirOwnLinesWithUniqueIds() {
        val draft = OrderDraft()
            .add(1, "A", 5_000)
            .add(2, "B", 6_000)
            .add(1, "A", 5_000)
        assertEquals(2, draft.lines.size)
        assertEquals(listOf(2, 1), draft.lines.map { it.quantity })
        assertEquals(draft.lines.size, draft.lines.map { it.id }.toSet().size)
    }

    @Test
    fun aLineWithAddOnsOrANoteIsNotMergedIntoByNewTaps() {
        val base = OrderDraft().add(1, "A", 5_000)
        val withNote = base.updateLine(base.lines.single().id, addOns = emptyList(), note = "no ice")
        val draft = withNote.add(1, "A", 5_000)
        assertEquals(2, draft.lines.size)
        assertEquals(listOf(1, 1), draft.lines.map { it.quantity })

        val withAddOn = base.updateLine(base.lines.single().id, addOns = listOf("Extra shot"), note = null)
        assertEquals(2, withAddOn.add(1, "A", 5_000).lines.size)
    }

    @Test
    fun aDifferentPriceOrDiscountIsAKeptApartSnapshot() {
        val draft = OrderDraft()
            .add(1, "A", 5_000, 0)
            .add(1, "A", 5_000, 10)
            .add(1, "A", 5_500, 0)
        assertEquals(3, draft.lines.size)
    }

    @Test
    fun changeQuantityMovesTheQuantityAndZeroRemovesTheLine() {
        val draft = OrderDraft().add(1, "A", 5_000).add(1, "A", 5_000).add(1, "A", 5_000)
        val id = draft.lines.single().id
        assertEquals(2, draft.changeQuantity(id, -1).lines.single().quantity)
        assertEquals(4, draft.changeQuantity(id, +1).lines.single().quantity)
        assertTrue(draft.changeQuantity(id, -3).lines.isEmpty())
        assertTrue(draft.changeQuantity(id, -10).lines.isEmpty())
    }

    @Test
    fun changeQuantityOnAnUnknownLineChangesNothing() {
        val draft = OrderDraft().add(1, "A", 5_000)
        assertEquals(draft, draft.changeQuantity(lineId = 99, delta = -1))
        assertEquals(draft, draft.removeLine(lineId = 99))
    }

    @Test
    fun removeLineOnlyRemovesThatLine() {
        val draft = OrderDraft().add(1, "A", 5_000).add(2, "B", 6_000)
        val after = draft.removeLine(draft.lines.first().id)
        assertEquals(listOf("B"), after.lines.map { it.productName })
    }

    @Test
    fun updateLineSetsAddOnsAndTrimsTheNoteAndBlankMeansNoNote() {
        val draft = OrderDraft().add(1, "A", 5_000)
        val id = draft.lines.single().id

        val edited = draft.updateLine(id, addOns = listOf("Less sugar", "Extra shot"), note = "  hot  ")
        assertEquals(listOf("Less sugar", "Extra shot"), edited.lines.single().addOns)
        assertEquals("hot", edited.lines.single().note)

        assertNull(edited.updateLine(id, addOns = emptyList(), note = "   ").lines.single().note)
        assertNull(edited.updateLine(id, addOns = emptyList(), note = null).lines.single().note)
    }

    @Test
    fun decrementProductTakesOneOffTheMostRecentLineOfThatProduct() {
        val base = OrderDraft().add(1, "A", 5_000).add(1, "A", 5_000) // line 1: A x2
        val withNoted = base.updateLine(base.lines.single().id, emptyList(), "no ice")
            .add(1, "A", 5_000)                                       // line 2: A x1 (plain)
        assertEquals(2, withNoted.lines.size)

        val once = withNoted.decrementProduct(1)                      // takes line 2 to 0 -> removed
        assertEquals(1, once.lines.size)
        assertEquals(2, once.lines.single().quantity)

        val twice = once.decrementProduct(1)
        assertEquals(1, twice.lines.single().quantity)
        assertEquals(twice, twice.decrementProduct(productId = 42)) // unknown product: no change
    }

    @Test
    fun emptyDraftHasNoItemsAndNoAmounts() {
        val draft = OrderDraft()
        assertEquals(0, draft.itemCount)
        assertTrue(draft.toCalcLines().isEmpty())
        assertEquals(0L, calculateSale(draft.toCalcLines(), null, 0, 100).total)
    }

    @Test
    fun draftLinesFeedTheCalculator() {
        // 2 x 13.000 with 10% off = 26.000 - 2.600 = 23.400; 1 x 9.000 plain; total 32.400, step 100.
        val draft = OrderDraft()
            .add(1, "A", 13_000, 10)
            .add(1, "A", 13_000, 10)
            .add(2, "B", 9_000)
        val amounts = calculateSale(draft.toCalcLines(), null, taxPercent = 0, roundingStep = 100)
        assertEquals(35_000L, amounts.subtotal)
        assertEquals(2_600L, amounts.productDiscountTotal)
        assertEquals(32_400L, amounts.total)
    }
}
