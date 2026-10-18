package dev.splitthebill.android.data

import dev.splitthebill.shared.db.BillStore

/** The process-wide bill database, created in [android.app.Activity.onCreate] before composition. */
object BillGraph {
    lateinit var store: BillStore
        private set

    fun install(store: BillStore) {
        if (!this::store.isInitialized) {
            this.store = store
        }
    }
}
