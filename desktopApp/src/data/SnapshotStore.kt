package com.pomodoro.data

import com.pomodoro.domain.ProductState

interface SnapshotStore {
    fun load(): ProductState
    fun save(snapshot: ProductState)
}
