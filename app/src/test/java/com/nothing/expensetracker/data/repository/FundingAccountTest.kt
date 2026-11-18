package com.nothing.expensetracker.data.repository

import com.nothing.expensetracker.data.local.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Reading a funding account out of a payment method's name.
 *
 * The convention people already use — "UPI-HDFC", "Card SBI" — is taken as the answer, so adding
 * a rail for a second bank is one field instead of two. It is only ever a suggestion the user can
 * override, which is the licence these tests take: they pin what it guesses, not what it decides.
 */
class FundingAccountTest {

    private val hdfc = PaymentMethod(id = 1, name = "HDFC")
    private val sbi = PaymentMethod(id = 2, name = "SBI")
    private val cash = PaymentMethod(id = 3, name = "Cash")
    private val hdfcBank = PaymentMethod(id = 4, name = "HDFC Bank")
    private val upi = PaymentMethod(id = 5, name = "UPI", fundedById = 1)

    private val accounts = listOf(hdfc, sbi, cash)

    @Test
    fun `a dash separates the rail from the account`() {
        assertEquals(hdfc, inferFundingAccount("UPI-HDFC", accounts))
    }

    @Test
    fun `so does a space`() {
        assertEquals(sbi, inferFundingAccount("Card SBI", accounts))
        assertEquals(sbi, inferFundingAccount("UPI SBI", accounts))
    }

    @Test
    fun `case and punctuation do not matter`() {
        assertEquals(hdfc, inferFundingAccount("upi_hdfc", accounts))
        assertEquals(hdfc, inferFundingAccount("UPI / hdfc", accounts))
        assertEquals(sbi, inferFundingAccount("Debit Card — sbi", accounts))
    }

    @Test
    fun `the account's own name is a new account, not a rail for itself`() {
        assertNull(inferFundingAccount("HDFC", accounts))
        assertNull(inferFundingAccount("hdfc", accounts))
    }

    @Test
    fun `a name matching nothing is left alone`() {
        assertNull(inferFundingAccount("Paytm Wallet", accounts))
        assertNull(inferFundingAccount("UPI", accounts))
        assertNull(inferFundingAccount("", accounts))
    }

    @Test
    fun `matching is on whole words, so Cashback is not a Cash card`() {
        // A raw substring match would have put this spending against the cash balance.
        assertNull(inferFundingAccount("Cashback", accounts))
        assertNull(inferFundingAccount("Cashbook Card", accounts))
        // The real thing still matches.
        assertEquals(cash, inferFundingAccount("Cash Envelope", accounts))
    }

    @Test
    fun `the most specific account wins`() {
        val both = listOf(hdfc, hdfcBank, sbi)
        assertEquals(hdfcBank, inferFundingAccount("UPI HDFC Bank", both))
        assertEquals(hdfc, inferFundingAccount("UPI HDFC", both))
    }

    @Test
    fun `a method that draws on something else is never offered as the funder`() {
        // One hop only: a rail cannot fund another rail.
        assertNull(inferFundingAccount("Card UPI", listOf(upi, cash)))
    }

    @Test
    fun `no accounts yet means no guess`() {
        assertNull(inferFundingAccount("UPI-HDFC", emptyList()))
    }
}
