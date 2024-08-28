package com.smallcloud.refactai

import com.google.gson.Gson
import com.smallcloud.refactai.panes.sharedchat.Events
import kotlinx.coroutines.flow.*

import kotlinx.coroutines.runBlocking


object FimCache {
    private val _events = MutableSharedFlow<Events.Fim.FimDebugPayload>();
    val events = _events.asSharedFlow();

   suspend fun subscribe(block: (Events.Fim.FimDebugPayload) -> Unit) {
       events.filterIsInstance<Events.Fim.FimDebugPayload>().collectLatest {
           block(it)
       }
   }


    fun emit(data: Events.Fim.FimDebugPayload) {
        runBlocking {
            _events.emit(data)
        }
    }

    var last: Events.Fim.FimDebugPayload? = null

    fun maybeSendFimData(res: String) {
        // println("FimCache.maybeSendFimData: $res")
        try {
            val data = Gson().fromJson(res, Events.Fim.FimDebugPayload::class.java);
            last = data;
            emit(data);
        } catch (e: Exception) {
            // ignore
        }
    }
}