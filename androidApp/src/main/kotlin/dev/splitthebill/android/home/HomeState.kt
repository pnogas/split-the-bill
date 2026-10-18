package dev.splitthebill.android.home

import dev.splitthebill.android.data.BillRepository
import dev.splitthebill.android.data.Viewer
import dev.splitthebill.shared.BillBalance
import dev.splitthebill.shared.BillId
import dev.splitthebill.shared.Cents
import dev.splitthebill.shared.RecordedBill
import dev.splitthebill.shared.ViewerStanding
import dev.splitthebill.shared.balanceOf
import dev.splitthebill.shared.viewerStanding
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale
import kotlin.time.Instant

data class HomeUiState(
    val initials: String,
    val exampleData: Boolean,
    val balance: HomeBalance?,
    val bills: List<HomeBillRow>,
)

sealed interface HomeBalance {
    data class Owed(val amountLabel: String, val detail: String) : HomeBalance

    data class Owes(val amountLabel: String, val detail: String) : HomeBalance

    data object SettledUp : HomeBalance
}

sealed interface HomeBillStatus {
    val label: String

    data class Owed(override val label: String) : HomeBillStatus

    data class Owes(override val label: String) : HomeBillStatus

    data object Settled : HomeBillStatus {
        override val label: String = "Settled"
    }

    data class Unresolved(override val label: String) : HomeBillStatus
}

data class HomeBillRow(
    val id: BillId,
    val merchant: String,
    val dateLabel: String,
    val peopleLabel: String,
    val status: HomeBillStatus,
    val contentDescription: String,
)

fun homeUiState(repository: BillRepository): HomeUiState = homeUiState(
    viewer = repository.viewer,
    bills = repository.bills(),
    exampleData = repository.isExampleData,
)

fun homeUiState(
    viewer: Viewer,
    bills: List<RecordedBill>,
    exampleData: Boolean,
): HomeUiState {
    if (bills.isEmpty()) {
        return HomeUiState(
            initials = viewer.initials,
            exampleData = exampleData,
            balance = null,
            bills = emptyList(),
        )
    }

    val newestFirst = bills.sortedByDescending { it.bill.createdAt }
    val standings = newestFirst.map { viewerStanding(it, viewer.id) }
    val rows = newestFirst.zip(standings) { recorded, standing ->
        row(recorded, standing)
    }
    return HomeUiState(
        initials = viewer.initials,
        exampleData = exampleData,
        balance = presentBalance(balanceOf(standings), exampleData),
        bills = rows,
    )
}

private fun presentBalance(balance: BillBalance, exampleData: Boolean): HomeBalance {
    val detail = acrossBills(balance.openBillCount, exampleData)
    return when {
        balance.net.amount > 0 -> HomeBalance.Owed(balance.net.formatDollars(), detail)
        balance.net.amount < 0 -> HomeBalance.Owes(Cents(-balance.net.amount).formatDollars(), detail)
        else -> HomeBalance.SettledUp
    }
}

private fun acrossBills(count: Int, exampleData: Boolean): String {
    val noun = if (count == 1) "bill" else "bills"
    val base = "across $count $noun"
    return if (exampleData) "$base · example data" else base
}

private fun row(recorded: RecordedBill, standing: ViewerStanding): HomeBillRow {
    val bill = recorded.bill
    val merchant = bill.merchant ?: "Receipt"
    val people = bill.people.size
    val peopleLabel = if (people == 1) "1 person" else "$people people"
    val dateLabel = formatShortDate(bill.createdAt)
    val longDate = formatLongDate(bill.createdAt)
    val status = presentStatus(standing)
    val spoken = when (standing) {
        is ViewerStanding.Owed -> "you're owed ${standing.amount.formatDollars()}"
        is ViewerStanding.Owes -> "you owe ${standing.amount.formatDollars()}"
        ViewerStanding.Settled -> "settled"
        is ViewerStanding.Unresolved -> standing.message
    }
    return HomeBillRow(
        id = bill.id,
        merchant = merchant,
        dateLabel = dateLabel,
        peopleLabel = peopleLabel,
        status = status,
        contentDescription = "$merchant, $longDate, $peopleLabel, $spoken",
    )
}

private fun presentStatus(standing: ViewerStanding): HomeBillStatus = when (standing) {
    is ViewerStanding.Owed -> HomeBillStatus.Owed("You're owed ${standing.amount.formatDollars()}")
    is ViewerStanding.Owes -> HomeBillStatus.Owes("You owe ${standing.amount.formatDollars()}")
    ViewerStanding.Settled -> HomeBillStatus.Settled
    is ViewerStanding.Unresolved -> HomeBillStatus.Unresolved("Needs assignment")
}

internal fun Cents.formatDollars(): String {
    val abs = kotlin.math.abs(amount)
    val cents = (abs % 100).toString().padStart(2, '0')
    return "$${abs / 100}.$cents"
}

internal fun formatShortDate(instant: Instant): String {
    val date = instant.toUtcDate()
    val month = date.month.getDisplayName(TextStyle.SHORT, Locale.US)
    return "$month ${date.dayOfMonth}"
}

internal fun formatLongDate(instant: Instant): String {
    val date = instant.toUtcDate()
    val month = date.month.getDisplayName(TextStyle.FULL, Locale.US)
    return "$month ${date.dayOfMonth}"
}

private fun Instant.toUtcDate() = java.time.Instant.ofEpochMilli(toEpochMilliseconds())
    .atZone(ZoneOffset.UTC)
    .toLocalDate()
