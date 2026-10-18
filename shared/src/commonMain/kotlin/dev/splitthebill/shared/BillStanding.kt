package dev.splitthebill.shared

/**
 * A bill plus the two facts [split] does not know: who paid the table, and
 * whether everyone has already squared up.
 */
data class RecordedBill(
    val bill: Bill,
    val paidBy: PersonId,
    val settled: Boolean,
)

/**
 * Where the viewer stands on one [RecordedBill].
 *
 * A settled bill is [Settled] even when the original shares were not zero.
 * Otherwise the viewer's [PersonShare.total] from [split] decides:
 * the person who paid is owed everyone else's shares, and anyone else owes
 * their own share.
 */
sealed interface ViewerStanding {
    data class Owed(val amount: Cents) : ViewerStanding

    data class Owes(val amount: Cents) : ViewerStanding

    data object Settled : ViewerStanding

    data class Unresolved(val message: String) : ViewerStanding
}

/** Signed net across bills. Positive [net] means the viewer is owed money. */
data class BillBalance(
    val net: Cents,
    val openBillCount: Int,
)

fun viewerStanding(recorded: RecordedBill, viewerId: PersonId): ViewerStanding {
    if (recorded.settled) return ViewerStanding.Settled

    val result = split(recorded.bill)
    if (result is SplitResult.Failure) {
        return ViewerStanding.Unresolved(result.error.message)
    }
    val shares = (result as SplitResult.Success).shares
    val mine = shares.find { it.person.id == viewerId }
    val viewerPaid = recorded.paidBy == viewerId

    if (mine == null) {
        return if (viewerPaid && recorded.bill.total.amount > 0) {
            ViewerStanding.Owed(recorded.bill.total)
        } else if (viewerPaid) {
            ViewerStanding.Settled
        } else {
            ViewerStanding.Unresolved("You're not on this bill.")
        }
    }

    return if (viewerPaid) {
        val owedByOthers = recorded.bill.total - mine.total
        when {
            owedByOthers.amount > 0 -> ViewerStanding.Owed(owedByOthers)
            owedByOthers.amount < 0 -> ViewerStanding.Owes(Cents(-owedByOthers.amount))
            else -> ViewerStanding.Settled
        }
    } else if (mine.total.amount > 0) {
        ViewerStanding.Owes(mine.total)
    } else {
        ViewerStanding.Settled
    }
}

fun balanceOf(standings: List<ViewerStanding>): BillBalance {
    var net = 0L
    var open = 0
    for (standing in standings) {
        when (standing) {
            is ViewerStanding.Owed -> {
                net += standing.amount.amount
                open++
            }
            is ViewerStanding.Owes -> {
                net -= standing.amount.amount
                open++
            }
            ViewerStanding.Settled, is ViewerStanding.Unresolved -> Unit
        }
    }
    return BillBalance(net = Cents(net), openBillCount = open)
}
