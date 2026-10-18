package dev.splitthebill.shared.db

import app.cash.sqldelight.driver.native.NativeSqliteDriver

fun createIosBillStore(): BillStore {
    val driver = NativeSqliteDriver(BillDatabase.Schema, "bills.db")
    return SqlBillStore(BillDatabase(driver))
}
