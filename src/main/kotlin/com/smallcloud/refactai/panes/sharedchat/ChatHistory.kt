package com.smallcloud.refactai.panes.sharedchat

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Transient
import com.intellij.util.xmlb.annotations.XMap
import com.smallcloud.refactai.panes.sharedchat.events.ChatMessage
import com.smallcloud.refactai.panes.sharedchat.events.ChatMessageSerializer
import com.smallcloud.refactai.panes.sharedchat.events.ChatMessages


// @Service


data class ChatHistoryItem(
    @Property
    val id: String,
    // @Property // maybe @Collection or maybe a custom class?
    @Property
    val messages: ChatMessages,
    @Property
    val model: String,
    @Property
    val title: String? = null,
    @Property
    val updatedAt: Long = System.currentTimeMillis(),
    @Property
    val createdAt: Long = System.currentTimeMillis(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ChatHistoryItem

        if (id != other.id) return false
        if (!messages.contentEquals(other.messages)) return false
        if (model != other.model) return false
        if (title != other.title) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + messages.contentHashCode()
        result = 31 * result + model.hashCode()
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + createdAt.hashCode()
        return result
    }
}

@State(name = "com.smallcloud.refactai.panes.sharedchat.RefactChatHistory", storages = [
    Storage("refactaiChatHistory.xml"),
    // Storage(StoragePathMacros.WORKSPACE_FILE)
])
class ChatHistory: PersistentStateComponent<ChatHistory> {
    @MapAnnotation
    var chatHistory: MutableMap<String, String> = emptyMap<String, String>().toMutableMap()

    private var gson = GsonBuilder().registerTypeAdapter(ChatMessage::class.java, ChatMessageSerializer()).create()

    fun setItem(item: ChatHistoryItem) {
        val json = gson.toJson(item)
        println("### setItem ###")
        println(item)
        chatHistory[item.id] = json
    }

    fun save(id: String, messages: ChatMessages, model: String, title: String) {
        val maybeItem = this.getItem(id);
        if (maybeItem == null) {
            val newItem = ChatHistoryItem(id, messages, model, title)
            setItem(newItem)
        } else {
            val item = maybeItem.copy(
                messages = messages,
                updatedAt = System.currentTimeMillis())
            this.setItem(item)
        }

    }
    fun removeItem(id: String?) {
        chatHistory.remove(id)
    }

    fun getItem(id: String): ChatHistoryItem? {
        val json = chatHistory[id]
        return gson.fromJson(json, ChatHistoryItem::class.java)
    }

    fun getAll(): List<ChatHistoryItem> {
       return chatHistory.map { gson.fromJson(it.value, ChatHistoryItem::class.java) }
    }

    override fun getState(): ChatHistory {
        return this
    }

    override fun loadState(state: ChatHistory) {
        println("\n### loadState ###")
        println(state)
        XmlSerializerUtil.copyBean(state, this)
    }


    companion object {
        @JvmStatic
        val instance: ChatHistory
            get() = ApplicationManager.getApplication().getService(ChatHistory::class.java)
    }
}
