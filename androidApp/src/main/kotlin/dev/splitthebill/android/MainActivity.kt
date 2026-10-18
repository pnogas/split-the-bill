package dev.splitthebill.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.splitthebill.android.data.BillGraph
import dev.splitthebill.android.theme.SplitTheBillTheme
import dev.splitthebill.shared.db.createAndroidBillStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BillGraph.install(createAndroidBillStore(applicationContext))
        enableEdgeToEdge()
        setContent {
            SplitTheBillTheme {
                SplitTheBillApp()
            }
        }
    }
}
