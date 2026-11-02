package com.nothing.expensetracker.ui.history

import com.nothing.expensetracker.data.repository.ExpenseRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the category rules that are expressed as bare strings in more than one place.
 *
 * These are the rules most likely to drift, because nothing in the type system connects a name in
 * [TransactionConstants] to the same name hardcoded inside a Room `@Query`.
 */
class TransactionConstantsTest {

    @Test
    fun `transfer is not spending`() {
        assertTrue(TransactionConstants.isNonSpendingCategory("Debit", "Transfer"))
        assertTrue(TransactionConstants.isNonSpendingCategory("Credit", "Transfer"))
    }

    /** A SIP is money you still own, so it must not land in any spending total. */
    @Test
    fun `investment is not spending`() {
        assertTrue(TransactionConstants.isNonSpendingCategory("Debit", "Investment"))
    }

    /** Lending to a friend is a real expense; only the Friends tab nets it off. */
    @Test
    fun `friend lending still counts as spending`() {
        assertFalse(TransactionConstants.isNonSpendingCategory("Debit", "Friend"))
        assertFalse(TransactionConstants.isNonSpendingCategory("Debit", "Friends"))
    }

    @Test
    fun `ordinary categories count as spending`() {
        listOf("Food", "Home", "Bills", "Shopping", "Other").forEach {
            assertFalse(it, TransactionConstants.isNonSpendingCategory("Debit", it))
        }
    }

    /** Case matters, and should: "transfer" typed by hand is a different category row. */
    @Test
    fun `match is exact`() {
        assertFalse(TransactionConstants.isNonSpendingCategory("Debit", "transfer"))
        assertFalse(TransactionConstants.isNonSpendingCategory("Debit", "Investments"))
    }

    /**
     * The aggregate queries in ExpenseDao exclude non-spending categories in SQL, where they
     * cannot call [TransactionConstants.isNonSpendingCategory]. Adding a name to the set without
     * updating those queries would leave the Dashboard breakdown disagreeing with its own total,
     * so this reads the DAO and checks every name is accounted for.
     */
    @Test
    fun `dao queries exclude every non-spending category`() {
        val dao = File("src/main/java/com/nothing/expensetracker/data/local/ExpenseDao.kt")
        assertTrue("ExpenseDao.kt not found at ${dao.absolutePath}", dao.exists())
        val source = dao.readText()

        val exclusions = Regex("""category NOT IN \(([^)]*)\)""").findAll(source).toList()
        assertTrue("no category exclusion found in ExpenseDao", exclusions.isNotEmpty())

        exclusions.forEach { match ->
            val names = match.groupValues[1]
                .split(",")
                .map { it.trim().trim('\'') }
                .toSet()
            assertEquals(
                "exclusion list in ExpenseDao is out of step with NON_SPENDING_CATEGORIES",
                TransactionConstants.NON_SPENDING_CATEGORIES,
                names
            )
        }
    }

    /**
     * The credit fallback is only used for the frame or two before the database Flow emits, so a
     * mismatch here shows as the category picker flickering a stale list.
     */
    @Test
    fun `credit fallback mirrors the seeded defaults`() {
        assertEquals(
            ExpenseRepository.DEFAULT_CREDIT_CATEGORIES.sorted(),
            TransactionConstants.FALLBACK_CREDIT_CATEGORIES.sorted()
        )
    }

    /** Anything the app looks up by name must be seeded, or the lookup silently never matches. */
    @Test
    fun `protected categories are seeded`() {
        val seeded = (ExpenseRepository.DEFAULT_DEBIT_CATEGORIES +
            ExpenseRepository.DEFAULT_CREDIT_CATEGORIES).toSet()
        // "Friends" is the pre-link spelling, kept only for installs that already have it.
        ExpenseRepository.PROTECTED_CATEGORIES.minus("Friends").forEach {
            assertTrue("$it is protected but not seeded", it in seeded)
        }
    }

    /** A trimmed default must not be left in both lists, or it comes straight back. */
    @Test
    fun `retired defaults are not also seeded`() {
        val seeded = (ExpenseRepository.DEFAULT_DEBIT_CATEGORIES +
            ExpenseRepository.DEFAULT_CREDIT_CATEGORIES).toSet()
        listOf("Entertainment", "Medical", "Travel", "Investment", "Refund").forEach {
            assertFalse("$it was retired but is still seeded", it in seeded)
        }
    }
}
