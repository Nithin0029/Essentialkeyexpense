package com.nothing.expensetracker.data.repository

import com.nothing.expensetracker.data.local.MethodTotals
import com.nothing.expensetracker.data.local.PaymentMethod

/**
 * The payment method for a debt a friend settled without money changing hands — they bought the
 * food, they paid for the thing. It is deliberately not an account: nothing reached a balance,
 * so nothing should move.
 *
 * It has to be named here, in the arithmetic, rather than only in the picker that offers it.
 * [rollUpAccountBalances] treats anything it cannot place as unassigned and says so in red, and
 * this money is not missing — it never existed in an account to go missing from. Without this,
 * anyone who settles up this way carries a permanent warning that their totals are short.
 */
const val SETTLED_IN_KIND = "RAS"

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
        val key = moved.method.trim().lowercase()
        if (key in attributed) return@forEach
        // Settled in kind is not money the app failed to place - it never entered an account to
        // be placed. Counting it here would put a permanent "your totals are short" warning in
        // front of anyone who settles up with a friend this way. If a user has actually named an
        // account this, the loop above already claimed it and this is never reached.
        if (key == SETTLED_IN_KIND.lowercase()) return@forEach
        unassignedCredits += moved.credits
        unassignedDebits += moved.debits
    }

    return AccountRollUp(
        accounts = accounts.values.toList(),
        unassignedCredits = unassignedCredits,
        unassignedDebits = unassignedDebits
    )
}

/**
 * Works out which account a newly named payment method spends from, by reading its name.
 *
 * "UPI-HDFC" draws on HDFC, "Card SBI" draws on SBI. The convention people reach for anyway is
 * taken as the answer, so adding a rail for a second bank is one field rather than two.
 *
 * Matched on whole words, never on raw substrings: "Cashback" is not a Cash card, and a user who
 * names an account that way should not find their spending quietly landing in the wrong balance.
 * Where several accounts match, the most specific wins — with both "HDFC" and "HDFC Bank" on file,
 * "UPI HDFC Bank" belongs to the latter.
 *
 * Returns null when the name *is* an account's name, which means a new account rather than a rail
 * for an existing one, and null when nothing matches. This is a suggestion: the caller shows it
 * and lets the user override, because a guess from a name should never be the last word on where
 * someone's money lives.
 */
fun inferFundingAccount(name: String, accounts: List<PaymentMethod>): PaymentMethod? {
    val typed = nameTokens(name)
    if (typed.isEmpty()) return null

    var best: PaymentMethod? = null
    var bestTokens = 0
    accounts.forEach { account ->
        if (!account.isAccount) return@forEach
        val target = nameTokens(account.name)
        // An equal or longer name cannot be a qualifier inside this one, which also rules out
        // the case where the two names are the same.
        if (target.isEmpty() || target.size >= typed.size) return@forEach
        if (!containsRun(typed, target)) return@forEach
        if (target.size > bestTokens) {
            best = account
            bestTokens = target.size
        }
    }
    return best
}

/** Lower-cased words, with punctuation and separators treated as spaces: "UPI-HDFC" to [upi, hdfc]. */
private fun nameTokens(value: String): List<String> =
    value.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

/** Whether [whole] contains [run] as consecutive words. */
private fun containsRun(whole: List<String>, run: List<String>): Boolean =
    whole.windowed(run.size, 1, partialWindows = false).any { it == run }
