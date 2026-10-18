package dev.splitthebill.android.flow

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import dev.splitthebill.android.theme.SplitTheBillTheme
import dev.splitthebill.shared.SplitResult
import dev.splitthebill.shared.sharesAddUp
import dev.splitthebill.shared.split
import org.junit.Rule
import org.junit.Test

class BillFlowSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1170,
            screenHeight = 2532,
            xdpi = 480,
            ydpi = 480,
        ),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun addPeople() {
        paparazzi.snapshot {
            SplitTheBillTheme {
                AddPeopleScreen(
                    merchant = "Pho Lien",
                    people = samplePeople(),
                    name = "",
                    editingId = null,
                    onName = {},
                    onSubmit = {},
                    onSelect = {},
                    onRemove = {},
                    onBack = {},
                    onContinue = {},
                )
            }
        }
    }

    @Test
    fun assignUnassigned() {
        paparazzi.snapshot {
            SplitTheBillTheme {
                AssignScreen(
                    merchant = "Pho Lien",
                    people = samplePeople(),
                    items = sampleAssignItems(),
                    message = "Assign every item. Still open: Spring Rolls.",
                    onToggle = { _, _ -> },
                    onEveryone = {},
                    onSplitRemaining = {},
                    onBack = {},
                    onContinue = {},
                )
            }
        }
    }

    @Test
    fun totals() {
        val bill = sampleTotalsBill()
        val shares = (split(bill) as SplitResult.Success).shares
        paparazzi.snapshot {
            SplitTheBillTheme {
                TotalsScreen(
                    bill = bill,
                    shares = shares,
                    addsUp = sharesAddUp(bill, shares),
                    onBack = {},
                    onShare = {},
                    onDone = {},
                )
            }
        }
    }
}
