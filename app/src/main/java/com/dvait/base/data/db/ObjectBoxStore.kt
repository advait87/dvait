package com.dvait.base.data.db

import android.content.Context
import io.objectbox.BoxStore
import com.dvait.base.data.model.MyObjectBox

object ObjectBoxStore {
    lateinit var store: BoxStore
        private set

    fun init(context: Context) {
        store = MyObjectBox.builder()
            .androidContext(context.applicationContext)
            .build()
    }
}
