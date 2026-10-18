package com.kaamchor

import android.content.Context
import com.kaamchor.data.AppRepository
import com.kaamchor.data.local.SessionStore
import com.kaamchor.data.realtime.SocketManager
import com.kaamchor.data.remote.ApiClient

// Manual dependency graph (no DI framework).
object Graph {
    lateinit var session: SessionStore
        private set
    lateinit var apiClient: ApiClient
        private set
    lateinit var repository: AppRepository
        private set
    lateinit var socket: SocketManager
        private set

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        session = SessionStore(appContext)
        apiClient = ApiClient(session)
        repository = AppRepository(appContext) { apiClient.api() }
        socket = SocketManager(session)
    }

    val context: Context get() = appContext
}
