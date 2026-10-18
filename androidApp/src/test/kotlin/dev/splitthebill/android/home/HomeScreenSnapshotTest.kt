package dev.splitthebill.android.home

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import dev.splitthebill.android.data.ExampleBillRepository
import dev.splitthebill.android.theme.SplitTheBillTheme
import org.junit.Rule
import org.junit.Test

/**
 * Renders the home screen at the mockup size (390×844 dp) without a device.
 * Record with :androidApp:recordPaparazziDebug.
 */
class HomeScreenSnapshotTest {
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
    fun home() {
        paparazzi.snapshot {
            SplitTheBillTheme {
                HomeScreen(
                    state = homeUiState(ExampleBillRepository()),
                    onMenu = {},
                    onProfile = {},
                    onBill = {},
                    onNewBill = {},
                )
            }
        }
    }

    @Test
    fun empty() {
        val repository = ExampleBillRepository()
        paparazzi.snapshot {
            SplitTheBillTheme {
                HomeScreen(
                    state = homeUiState(repository.viewer, emptyList(), exampleData = false),
                    onMenu = {},
                    onProfile = {},
                    onBill = {},
                    onNewBill = {},
                )
            }
        }
    }

    @Test
    fun dark() {
        paparazzi.snapshot {
            SplitTheBillTheme(darkTheme = true) {
                HomeScreen(
                    state = homeUiState(ExampleBillRepository()),
                    onMenu = {},
                    onProfile = {},
                    onBill = {},
                    onNewBill = {},
                )
            }
        }
    }
}
