package com.nothing.expensetracker.data.repository

import com.nothing.expensetracker.data.local.MethodTotals
import com.nothing.expensetracker.data.local.PaymentMethod

/**
 * One account's standing: what it opened with, what has moved through it, and where it is now.
 *
 * An "account" is a payment method that actually holds money — one with no [PaymentMethod.fundedById].
 * Methods that draw on it, such as UPI drawing on a bank account, are folded in rather than
 * listed separately: the money is in one place, however it left.
 */
data class AccountBalance(
    val id: Long,
    val name: String,
    val openingBalance: Double,
    val credits: Double,
    val debits: Double
) {
    val balance: Double get() = openingBalance + credits - debits
}

/**
 * Every account, plus anything that could not be attributed to one.
 *
 * [unassigned] is not a rounding detail. Before this existed the balance queries matched the
 * literal strings 'Bank', 'UPI' and 'Cash', so a transaction on any other payment method counted
 * as spending but came off no balance at all — the totals simply drifted, silently, with nothing
 * on screen to say so. Anything that cannot be placed now lands here so the UI can say it out loud.
 */
data class AccountRollUp(
    val accounts: List<AccountBalance>,
    val unassignedCredits: Double,
    val unassignedDebits: Double
) {
    val totalAssets: Double get() = accounts.sumOf { it.balance }
    val unassignedNet: Double get() = unassignedCredits - unassignedDebits
    val hasUnassigned: Boolean get() = unassignedCredits != 0.0 || unassignedDebits != 0.0
}

/**
 * Folds per-method transaction totals into per-account balances.
 *
 * Pure, and takes both sides as plain data, so the arithmetic that decides what a user's money
 * adds up to can be tested without a database. Accounts come back in the order the methods were
 * given, which the DAO sorts by name.
 *
 * A method whose [PaymentMethod.fundedById] points at something that no longer exists is treated
 * as an account in its own right rather than being dropped — a dangling link should cost you a
 * tidy list, never a balance.
 */
fun rollUpAccountBalances(
    methods: List<PaymentMethod>,
    totals: List<MethodTotals>
): AccountRollUp {
    val byId = methods.associateBy { it.id }
    val totalsByName = totals.associateBy { it.method.trim().lowercase() }

    // Every method that holds money, in the order given.
    val accounts = linkedMapOf<Long, AccountBalance>()
    methods.forEach { method ->
        if (method.fundedById == null || byId[method.fundedById] == null) {
            accounts[method.id] = AccountBalance(
                id = method.id,
                name = method.name,
                openingBalance = method.openingBalance,
                credits = 0.0,
                debits = 0.0
            )
        }
    }

    var unassignedCredits = 0.0
    var unassignedDebits = 0.0
    val attributed = mutableSetOf<String>()

    methods.forEach { method ->
        val key = method.name.trim().lowercase()
        val moved = totalsByName[key] ?: return@forEach
        attributed.add(key)

        // Follow the funding link one hop; a method funded by a method that is itself funded is
        // not a shape the UI can create, so one hop is the whole story.
        val target = method.fundedById?.takeIf { byId[it] != null } ?: method.id
        val account = accounts[target]
        if (account == null) {
            unassignedCredits += moved.credits
            unassignedDebits += moved.debits
            return@forEach
        }
        accounts[target] = account.copy(
            credits = account.credits + moved.credits,
            debits = account.debits + moved.debits
        )
    }

    // Transactions filed under a payment method that no longer exists.
    totals.forEach { moved ->
        if (moved.method.trim().lowercase() !in attributed) {
            unassignedCredits += moved.credits
            unassignedDebits += moved.debits
        }
    }

    return AccountRollUp(
        accounts = accounts.values.toList(),
        unassignedCredits = unassignedCredits,
        unassignedDebits = unassignedDebits
    )
}
