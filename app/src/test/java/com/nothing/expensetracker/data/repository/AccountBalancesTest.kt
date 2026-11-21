package com.nothing.expensetracker.data.repository

import com.nothing.expensetracker.data.local.MethodTotals
import com.nothing.expensetracker.data.local.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic that decides what a user's money adds up to.
 *
 * It replaces three hardcoded payment-method names in SQL, so the first thing these tests pin is
 * that an existing install gets the same totals out of the new code as it did out of the old —
 * and the second is the case the old code could not express at all: a second bank account.
 */
class AccountBalancesTest {

    private val bank = PaymentMethod(id = 1, name = "Bank", isSystem = true, openingBalance = 50_000.0)
    private val cash = PaymentMethod(id = 2, name = "Cash", isSystem = true, openingBalance = 2_000.0)
    private val upi = PaymentMethod(id = 3, name = "UPI", isSystem = true, fundedById = 1)

    private fun totals(vararg rows: Triple<String, Double, Double>) =
        rows.map { (method, credits, debits) -> MethodTotals(method, credits, debits) }

    private fun AccountRollUp.balanceOf(name: String) =
        accounts.first { it.name == name }.balance

    @Test
    fun `UPI spending comes off the bank account it draws on`() {
        val rollUp = rollUpAccountBalances(
            listOf(bank, cash, upi),
            totals(
                Triple("Bank", 10_000.0, 4_000.0),
                Triple("UPI", 0.0, 3_000.0),
                Triple("Cash", 0.0, 500.0)
            )
        )

        // 50,000 + 10,000 − 4,000 − 3,000. Exactly what 'Bank' OR 'UPI' used to produce.
        assertEquals(53_000.0, rollUp.balanceOf("Bank"), 0.001)
        assertEquals(1_500.0, rollUp.balanceOf("Cash"), 0.001)
        assertEquals(54_500.0, rollUp.totalAssets, 0.001)
    }

    @Test
    fun `a method that draws on another is not listed as an account of its own`() {
        val rollUp = rollUpAccountBalances(listOf(bank, cash, upi), totals())
        assertEquals(listOf("Bank", "Cash"), rollUp.accounts.map { it.name })
    }

    @Test
    fun `a second bank account keeps its own balance`() {
        val hdfc = PaymentMethod(id = 4, name = "HDFC", openingBalance = 25_000.0)
        val hdfcUpi = PaymentMethod(id = 5, name = "HDFC UPI", fundedById = 4)

        val rollUp = rollUpAccountBalances(
            listOf(bank, cash, upi, hdfc, hdfcUpi),
            totals(
                Triple("Bank", 0.0, 1_000.0),
                Triple("HDFC", 5_000.0, 0.0),
                Triple("HDFC UPI", 0.0, 2_500.0)
            )
        )

        assertEquals(49_000.0, rollUp.balanceOf("Bank"), 0.001)
        assertEquals(27_500.0, rollUp.balanceOf("HDFC"), 0.001)
        assertEquals(2_000.0, rollUp.balanceOf("Cash"), 0.001)
        assertEquals(78_500.0, rollUp.totalAssets, 0.001)
        assertFalse(rollUp.hasUnassigned)
    }

    @Test
    fun `this is the case the old queries dropped on the floor`() {
        // Before accounts, 'HDFC' matched neither the bank bucket nor the cash one, so this
        // debit counted as spending and came off no balance at all.
        val hdfc = PaymentMethod(id = 4, name = "HDFC", openingBalance = 25_000.0)
        val rollUp = rollUpAccountBalances(
            listOf(bank, cash, upi, hdfc),
            totals(Triple("HDFC", 0.0, 3_000.0))
        )

        assertEquals(22_000.0, rollUp.balanceOf("HDFC"), 0.001)
        // 50,000 bank + 2,000 cash + 22,000 HDFC.
        assertEquals(74_000.0, rollUp.totalAssets, 0.001)
    }

    @Test
    fun `transactions on a method that no longer exists are surfaced, not swallowed`() {
        val rollUp = rollUpAccountBalances(
            listOf(bank, cash),
            totals(
                Triple("Bank", 0.0, 1_000.0),
                Triple("Paytm Wallet", 400.0, 900.0)
            )
        )

        assertTrue("a deleted method's money must not vanish quietly", rollUp.hasUnassigned)
        assertEquals(400.0, rollUp.unassignedCredits, 0.001)
        assertEquals(900.0, rollUp.unassignedDebits, 0.001)
        assertEquals(-500.0, rollUp.unassignedNet, 0.001)
        // Still excluded from the headline, which must only claim money it can place.
        assertEquals(51_000.0, rollUp.totalAssets, 0.001)
    }

    @Test
    fun `a dangling funding link leaves the method standing as its own account`() {
        val orphan = PaymentMethod(id = 6, name = "Old Card", fundedById = 99, openingBalance = 100.0)
        val rollUp = rollUpAccountBalances(
            listOf(bank, orphan),
            totals(Triple("Old Card", 0.0, 40.0))
        )

        assertEquals(60.0, rollUp.balanceOf("Old Card"), 0.001)
        assertFalse(rollUp.hasUnassigned)
    }

    @Test
    fun `method names are matched regardless of case or stray spacing`() {
        val rollUp = rollUpAccountBalances(
            listOf(bank, cash),
            totals(Triple(" bank ", 1_000.0, 0.0))
        )
        assertEquals(51_000.0, rollUp.balanceOf("Bank"), 0.001)
        assertFalse(rollUp.hasUnassigned)
    }

    @Test
    fun `an account with no transactions still reports its opening balance`() {
        val rollUp = rollUpAccountBalances(listOf(bank, cash, upi), totals())
        assertEquals(50_000.0, rollUp.balanceOf("Bank"), 0.001)
        assertEquals(52_000.0, rollUp.totalAssets, 0.001)
    }

    @Test
    fun `no accounts and no transactions is zero, not a crash`() {
        val rollUp = rollUpAccountBalances(emptyList(), emptyList())
        assertEquals(0.0, rollUp.totalAssets, 0.001)
        assertTrue(rollUp.accounts.isEmpty())
        assertFalse(rollUp.hasUnassigned)
    }

    @Test
    fun `money a friend settled in kind is not missing money`() {
        // A friend repaid by buying the food. Nothing reached an account, so no balance moves -
        // but it must not be reported as money the app could not place either, or anyone who
        // settles up this way carries a permanent warning that their totals are short.
        val rollUp = rollUpAccountBalances(
            listOf(bank, cash),
            totals(
                Triple("Bank", 0.0, 1_000.0),
                Triple(SETTLED_IN_KIND, 800.0, 0.0)
            )
        )

        assertFalse(rollUp.hasUnassigned)
        assertEquals(0.0, rollUp.unassignedNet, 0.001)
        assertEquals(49_000.0, rollUp.balanceOf("Bank"), 0.001)
        // 50,000 - 1,000 + 2,000 cash. The 800 settled in kind is in none of it.
        assertEquals(51_000.0, rollUp.totalAssets, 0.001)
    }

    @Test
    fun `a real unrecognised method is still surfaced alongside it`() {
        val rollUp = rollUpAccountBalances(
            listOf(bank, cash),
            totals(
                Triple(SETTLED_IN_KIND, 500.0, 0.0),
                Triple("Paytm Wallet", 0.0, 300.0)
            )
        )

        assertTrue("settled-in-kind must not mask a genuinely lost method", rollUp.hasUnassigned)
        assertEquals(-300.0, rollUp.unassignedNet, 0.001)
    }
}
