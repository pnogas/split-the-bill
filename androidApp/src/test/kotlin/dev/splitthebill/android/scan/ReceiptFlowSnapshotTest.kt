package dev.splitthebill.android.scan

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import dev.splitthebill.android.review.ReviewScreen
import dev.splitthebill.android.review.sampleReview
import dev.splitthebill.android.theme.SplitTheBillTheme
import dev.splitthebill.shared.Cents
import org.junit.Rule
import org.junit.Test

class ReceiptFlowSnapshotTest {
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
    fun cameraPermission() {
        snapshotScan(CameraAccess.NeedsRequest)
    }

    @Test
    fun cameraDenied() {
        snapshotScan(CameraAccess.Denied)
    }

    @Test
    fun cameraBlocked() {
        snapshotScan(CameraAccess.Blocked)
    }

    @Test
    fun cameraReady() {
        snapshotScan(CameraAccess.Granted)
    }

    @Test
    fun review() {
        paparazzi.snapshot {
            SplitTheBillTheme {
                ReviewScreen(
                    state = sampleReview(),
                    onBack = {},
                    onName = { _, _ -> },
                    onPrice = { _, _ -> },
                    onQuantity = { _, _ -> },
                    onDelete = {},
                    onAdd = {},
                    onTax = {},
                    onTip = {},
                    onAddPeople = {},
                )
            }
        }
    }

    @Test
    fun reviewSubtotalMismatch() {
        paparazzi.snapshot {
            SplitTheBillTheme {
                ReviewScreen(
                    state = sampleReview().copy(receiptSubtotal = Cents(4_000)),
                    onBack = {},
                    onName = { _, _ -> },
                    onPrice = { _, _ -> },
                    onQuantity = { _, _ -> },
                    onDelete = {},
                    onAdd = {},
                    onTax = {},
                    onTip = {},
                    onAddPeople = {},
                )
            }
        }
    }

    private fun snapshotScan(access: CameraAccess) {
        paparazzi.snapshot {
            SplitTheBillTheme {
                ScanScreen(
                    access = access,
                    reading = false,
                    message = null,
                    onBack = {},
                    onAllowCamera = {},
                    onOpenSettings = {},
                    onChoosePhoto = {},
                    onCapture = {},
                    preview = { ViewfinderPlaceholder() },
                )
            }
        }
    }
}
