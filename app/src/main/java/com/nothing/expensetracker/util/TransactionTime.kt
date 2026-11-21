package com.nothing.expensetracker.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Moves a transaction to a different day without disturbing its time of day.
 *
 * Material's date picker reports the chosen day as **UTC midnight**. Assigning that value
 * straight to the transaction's timestamp - which is what the edit screen did - threw away the
 * time along with the date, and two things followed from it:
 *
 *  * every such transaction displayed as `05:30`, because 00:00 UTC is half past five in India;
 *  * every transaction on a day collapsed to the *same instant*, so "which came first today" was
 *    no longer recorded anywhere, and the history could not order them.
 *
 * The second is the worse one. A wrong time can be read past; a lost ordering cannot be
 * recovered, because the information is simply gone from the row.
 *
 * @param pickedUtcMillis the picker's value: midnight UTC on the chosen day
 * @param existingMillis the timestamp being edited, whose time of day is kept
 * @param zone the zone the clock on the wall is in
 */
fun applyPickedDate(
    pickedUtcMillis: Long,
    existingMillis: Long,
    zone: ZoneId = ZoneId.systemDefault()
): Long {
    val pickedDay = Instant.ofEpochMilli(pickedUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val existingTime = Instant.ofEpochMilli(existingMillis).atZone(zone).toLocalTime()
    return pickedDay.atTime(existingTime).atZone(zone).toInstant().toEpochMilli()
}
