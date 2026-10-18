package dev.splitthebill.shared.db

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

fun createAndroidBillStore(context: Context): BillStore {
    val driver = AndroidSqliteDriver(BillDatabase.Schema, context, "bills.db")
    return SqlBillStore(BillDatabase(driver))
}
