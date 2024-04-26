package com.smallcloud.refactai.panes.sharedchat

import com.google.gson.annotations.SerializedName
import com.google.gson.*
import com.smallcloud.refactai.lsp.LSPCapabilities
import java.io.Serializable
import java.lang.reflect.Type

// Lsp responses

enum class ChatRole(val value: String): Serializable {
    USER("user"),
    ASSISTANT("assistant"),
    CONTEXT_FILE("context_file"),
    SYSTEM("system"),
}

data class ChatContextFile(
    @SerializedName("file_name") val fileName: String,
    @SerializedName("file_content") val fileContent: String,
    val line1: Int,
    val line2: Int,
    val usefulness: Int? = null
)



abstract class ChatMessage<T>(
    @SerializedName("role")
    val role: String,
    @Transient
    @SerializedName("content")
    open val content: T
): Serializable {
}
data class UserMessage(override val content: String): ChatMessage<String>(ChatRole.USER.value, content)

data class AssistantMessage(override val content: String): ChatMessage<String>(ChatRole.ASSISTANT.value, content)

data class SystemMessage(override val content: String): ChatMessage<String>(ChatRole.SYSTEM.value, content)

data class ContentFileMessage(override val content: Array<ChatContextFile>): ChatMessage<Array<ChatContextFile>>(
    ChatRole.CONTEXT_FILE.value, content) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ContentFileMessage

        return content.contentEquals(other.content)
    }

    override fun hashCode(): Int {
        return content.contentHashCode()
    }
}


typealias ChatMessages = Array<ChatMessage<*>>

class ChatMessageDeserializer: JsonDeserializer<ChatMessage<*>> {
    override fun deserialize(p0: JsonElement?, p1: Type?, p2: JsonDeserializationContext?): ChatMessage<*>? {
        val role = p0?.asJsonObject?.get("role")?.asString
        return when(role) {
            ChatRole.USER.value -> p2?.deserialize<UserMessage>(p0, UserMessage::class.java)
            ChatRole.ASSISTANT.value -> p2?.deserialize<AssistantMessage>(p0, AssistantMessage::class.java)
            ChatRole.CONTEXT_FILE.value -> p2?.deserialize<ContentFileMessage>(p0, ContentFileMessage::class.java)
            ChatRole.SYSTEM.value -> p2?.deserialize<SystemMessage>(p0, SystemMessage::class.java)
            else -> null
        }
    }
}

class ChatMessageSerializer: JsonSerializer<ChatMessage<*>> {
    override fun serialize(p0: ChatMessage<*>, p1: Type?, p2: JsonSerializationContext?): JsonElement? {
        return when (p0) {
            is UserMessage -> p2?.serialize(p0, UserMessage::class.java)
            is AssistantMessage -> p2?.serialize(p0, AssistantMessage::class.java)
            is ContentFileMessage -> p2?.serialize(p0, ContentFileMessage::class.java)
            is SystemMessage -> p2?.serialize(p0, SystemMessage::class.java)
            else -> JsonNull.INSTANCE
        }
    }
}


abstract class Delta<T>(val role: String, val content: T)
class AssistantDelta(content: String): Delta<String>(ChatRole.ASSISTANT.value, content)
class ContextFileDelta(content: ChatMessages): Delta<ChatMessages>(ChatRole.CONTEXT_FILE.value, content)

class DeltaDeserializer: JsonDeserializer<Delta<*>> {
    override fun deserialize(p0: JsonElement?, p1: Type?, p2: JsonDeserializationContext?): Delta<*>? {
        val role = p0?.asJsonObject?.get("role")?.asString
        return when(role) {
            ChatRole.ASSISTANT.value -> p2?.deserialize(p0, AssistantDelta::class.java)
            ChatRole.CONTEXT_FILE.value -> p2?.deserialize(p0, ContextFileDelta::class.java)
            else -> null
        }
    }
}



data class ScratchPad(
    @SerializedName("default_system_message") val defaultSystemMessage: String,
)

data class ChatModel(
    @SerializedName("default_scratchpad") val defaultScratchPad: String,
    @SerializedName("n_ctx") val nCtx: Int,
    @SerializedName("similar_models") val similarModels: List<String>,
    @SerializedName("supports_scratchpads") val supportsScratchpads: Map<String, ScratchPad>
)

data class CodeCompletionModel(
    @SerializedName("default_scratchpad") val defaultScratchPad: String,
    @SerializedName("n_ctx") val nCtx: Int,
    @SerializedName("similar_models") val similarModels: List<String>,
    // Might be wrong
    @SerializedName("supports_scratchpads") val supportsScratchpads: Map<String, Unit>
)

data class CapsResponse(
    @SerializedName("caps_version") val capsVersion: Int,
    @SerializedName("cloud_name") val cloudName: String,
    @SerializedName("code_chat_default_model") val codeChatDefaultModel: String,
    @SerializedName("code_chat_models") val codeChatModels: Map<String, ChatModel>,
    @SerializedName("code_completion_default_model") val codeCompletionDefaultModel: String,
    @SerializedName("code_completion_models") val codeCompletionModels: Map<String, CodeCompletionModel>,
    @SerializedName("code_completion_n_ctx") val codeCompletionNCtx: Int,
    @SerializedName("endpoint_chat_passthrough") val endpointChatPassthrough: String,
    @SerializedName("endpoint_style") val endpointStyle: String,
    @SerializedName("endpoint_template") val endpointTemplate: String,
    @SerializedName("running_models") val runningModels: List<String>,
    @SerializedName("telemetry_basic_dest") val telemetryBasicDest: String,
    @SerializedName("telemetry_corrected_snippets_dest") val telemetryCorrectedSnippetsDest: String,
    @SerializedName("tokenizer_path_template") val tokenizerPathTemplate: String,
    @SerializedName("tokenizer_rewrite_path") val tokenizerRewritePath: Map<String, Map<String, String>>
)

data class CommandCompletionResponse(
    @SerializedName("completions") val completions: List<String>,
    // Might need to be a list
    val replace: Pair<Int, Int>,
    @SerializedName("is_cmd_executable") val isCmdExecutable: Boolean
)

data class DetailMessage(
    val detail: String
)



data class SystemPrompt(
    val test: String,
    val description: String,
)

typealias SystemPromptMap = Map<String, SystemPrompt>

data class CustomPromptsResponse(
    @SerializedName("system_prompts") val systemPrompts: SystemPromptMap,
    // Might need to update this
    @SerializedName("toolbox_commands") val toolboxCommands: Map<String, Unit>
)

// Events

class EventNames {
    enum class FromChat(val value: String) {
        SAVE_CHAT("save_chat_to_history"),
        ASK_QUESTION("chat_question"),
        REQUEST_CAPS("chat_request_caps"),
        STOP_STREAMING("chat_stop_streaming"),
        BACK_FROM_CHAT("chat_back_from_chat"),
        OPEN_IN_CHAT_IN_TAB("open_chat_in_new_tab"),
        SEND_TO_SIDE_BAR("chat_send_to_sidebar"),
        READY("chat_ready"),
        NEW_FILE("chat_create_new_file"),
        PASTE_DIFF("chat_paste_diff"),
        REQUEST_AT_COMMAND_COMPLETION("chat_request_at_command_completion"),
        REQUEST_PROMPTS("chat_request_prompts")
    }

    enum class ToChat(val value: String) {
        CLEAR_ERROR("chat_clear_error"),
        RESTORE_CHAT("restore_chat_from_history"),
        @SerializedName("chat_response")
        CHAT_RESPONSE("chat_response"),
        BACKUP_MESSAGES("back_up_messages"),
        DONE_STREAMING("chat_done_streaming"),
        ERROR_STREAMING("chat_error_streaming"),
        NEW_CHAT("create_new_chat"),
        RECEIVE_CAPS("receive_caps"),
        RECEIVE_CAPS_ERROR("receive_caps_error"),
        SET_CHAT_MODEL("chat_set_chat_model"),
        SET_DISABLE_CHAT("set_disable_chat"),
        ACTIVE_FILE_INFO("chat_active_file_info"),
        TOGGLE_ACTIVE_FILE("chat_toggle_active_file"),
        RECEIVE_AT_COMMAND_COMPLETION("chat_receive_at_command_completion"),
        RECEIVE_AT_COMMAND_PREVIEW("chat_receive_at_command_preview"),
        SET_SELECTED_AT_COMMAND("chat_set_selected_command"),
        SET_LAST_MODEL_USED("chat_set_last_model_used"),
        SET_SELECTED_SNIPPET("chat_set_selected_snippet"),
        REMOVE_PREVIEW_FILE_BY_NAME("chat_remove_file_from_preview"),
        SET_PREVIOUS_MESSAGES_LENGTH("chat_set_previous_messages_length"),
        RECEIVE_TOKEN_COUNT("chat_set_tokens"),
        RECEIVE_PROMPTS("chat_receive_prompts"),
        RECEIVE_PROMPTS_ERROR("chat_receive_prompts_error"),
        SET_SELECTED_SYSTEM_PROMPT("chat_set_selected_system_prompt")
    }
}



class Events {

    open class Payload(
        @Transient
        @SerializedName("id")
        open val id: String
    ): Serializable

    abstract class FromChat(val type: EventNames.FromChat, open val payload: Payload): Serializable

    private class FromChatDeserializer : JsonDeserializer<FromChat> {
        override fun deserialize(p0: JsonElement?, p1: Type?, p2: JsonDeserializationContext?): FromChat? {
            val type = p0?.asJsonObject?.get("type")?.asString
            val payload = p0?.asJsonObject?.get("payload")
            if(type == null || payload == null) return null

            return when(type) {
                EventNames.FromChat.READY.value -> p2?.deserialize(payload, Ready::class.java)
                EventNames.FromChat.REQUEST_PROMPTS.value -> p2?.deserialize(payload, SystemPrompts.Request::class.java)
                EventNames.FromChat.REQUEST_AT_COMMAND_COMPLETION.value -> p2?.deserialize(payload, AtCommands.Completion.Request::class.java)
                EventNames.FromChat.SAVE_CHAT.value -> {
                    val messages = JsonArray()
                    payload.asJsonObject.get("messages").asJsonArray.forEach {
                        val pair = it.asJsonArray
                        val role = pair.get(0)
                        val content = pair.get(1)
                        val obj = JsonObject()
                        obj.add("role", role)
                        obj.add("content", content)
                        messages.add(obj)
                    }

                    payload.asJsonObject.add("messages", messages)

                    return p2?.deserialize(payload, Chat.Save::class.java)
                }
                EventNames.FromChat.ASK_QUESTION.value -> {
                    val messages = JsonArray()
                    payload.asJsonObject.get("messages").asJsonArray.forEach {
                        val pair = it.asJsonArray
                        val role = pair.get(0)
                        val content = pair.get(1)
                        val obj = JsonObject()
                        obj.add("role", role)
                        obj.add("content", content)
                        messages.add(obj)
                    }

                    payload.asJsonObject.add("messages", messages)
                    return p2?.deserialize(payload, Chat.AskQuestion::class.java)
                }
                EventNames.FromChat.STOP_STREAMING.value -> p2?.deserialize(payload, Chat.Stop::class.java)
                EventNames.FromChat.NEW_FILE.value -> p2?.deserialize(payload, Editor.NewFile::class.java)
                EventNames.FromChat.PASTE_DIFF.value -> p2?.deserialize(payload, Editor.Paste::class.java)
                EventNames.FromChat.REQUEST_CAPS.value -> p2?.deserialize(payload, Caps.Request::class.java)
                else -> null
            }
        }

    }

    abstract class ToChat(
        @SerializedName("type")
        val type: EventNames.ToChat,
        @SerializedName("payload")
        open val payload: Payload
    ): Serializable


    data class Ready(val id: String): FromChat(EventNames.FromChat.READY, Payload(id))

    class SystemPrompts() {
        data class Request(val id: String): FromChat(EventNames.FromChat.REQUEST_PROMPTS, Payload(id))

        data class SystemPromptsPayload(override val id: String, val prompts: SystemPromptMap): Payload(id)
        data class Receive(val id: String, val prompts: SystemPromptMap): ToChat(EventNames.ToChat.RECEIVE_PROMPTS, SystemPromptsPayload(id, prompts))

        data class SystemPromptsErrorPayload(override val id: String, val error: String): Payload(id)
        data class Error(val id: String, val error: String): ToChat(EventNames.ToChat.RECEIVE_PROMPTS_ERROR, SystemPromptsErrorPayload(id, error))
        // set?
    }

    class AtCommands {

        class Completion {

            data class RequestPayload(
                override val id: String,
                val query: String,
                val cursor: Int,
                val number: Int = 5,
                val trigger: String? = null,
            ) : Payload(id)
            data class Request(
                val id: String,
                val query: String,
                val cursor: Int,
                val number: Int = 5,
                val trigger: String? = null,
            ) : FromChat(EventNames.FromChat.REQUEST_AT_COMMAND_COMPLETION, RequestPayload(id, query, cursor, number, trigger))

            data class CompletionPayload(
                override val id: String,
                val completions: CommandCompletionResponse
            ) : Payload(id)

            data class Receive(
                val id: String,
                val completions: CommandCompletionResponse
            ) : ToChat(EventNames.ToChat.RECEIVE_AT_COMMAND_COMPLETION, CompletionPayload(id, completions))
        }

        class Preview {
            data class PreviewContent(
                val content: String,
            ) {
                val role = ChatRole.CONTEXT_FILE
            }

            data class PreviewPayload(
                override val id: String,
                val preview: Array<PreviewContent>,
            ): Payload(id) {
                override fun equals(other: Any?): Boolean {
                    if (this === other) return true
                    if (javaClass != other?.javaClass) return false

                    other as PreviewPayload

                    if (id != other.id) return false
                    if (!preview.contentEquals(other.preview)) return false

                    return true
                }

                override fun hashCode(): Int {
                    var result = id.hashCode()
                    result = 31 * result + preview.contentHashCode()
                    return result
                }
            }

            data class Receive(
                val id: String,
                val preview: Array<PreviewContent>,
            ) : ToChat(EventNames.ToChat.RECEIVE_AT_COMMAND_PREVIEW, PreviewPayload(id, preview)) {
                override fun equals(other: Any?): Boolean {
                    if (this === other) return true
                    if (javaClass != other?.javaClass) return false

                    other as Receive

                    if (id != other.id) return false
                    if (!preview.contentEquals(other.preview)) return false

                    return true
                }

                override fun hashCode(): Int {
                    var result = id.hashCode()
                    result = 31 * result + preview.contentHashCode()
                    return result
                }
            }
        }
    }

    class Chat {
        data class ThreadPayload(
            override val id: String,
            val messages: ChatMessages,
            val model: String,
            val title: String? = null,
            @SerializedName("attach_file") val attachFile: Boolean = false,
        ): Payload(id) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as ThreadPayload

                if (id != other.id) return false
                if (!messages.contentEquals(other.messages)) return false
                if (model != other.model) return false
                if (title != other.title) return false
                if (attachFile != other.attachFile) return false

                return true
            }

            override fun hashCode(): Int {
                var result = id.hashCode()
                result = 31 * result + messages.contentHashCode()
                result = 31 * result + model.hashCode()
                result = 31 * result + (title?.hashCode() ?: 0)
                result = 31 * result + attachFile.hashCode()
                return result
            }
        }

        data class Save(
            val id: String,
            val messages: ChatMessages,
            val model: String,
            val title: String,
            val attachFile: Boolean = false,
        ): FromChat(EventNames.FromChat.SAVE_CHAT, ThreadPayload(id, messages, model, title, attachFile)) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Save

                if (id != other.id) return false
                if (!messages.contentEquals(other.messages)) return false
                if (model != other.model) return false
                if (title != other.title) return false
                if (attachFile != other.attachFile) return false

                return true
            }

            override fun hashCode(): Int {
                var result = id.hashCode()
                result = 31 * result + messages.contentHashCode()
                result = 31 * result + model.hashCode()
                result = 31 * result + (title?.hashCode() ?: 0)
                result = 31 * result + attachFile.hashCode()
                return result
            }
        }

        data class AskQuestion(
            val id: String,
            val messages: ChatMessages,
            val model: String,
            val title: String? = null,
            val attachFile: Boolean = false,
        ): FromChat(EventNames.FromChat.ASK_QUESTION, ThreadPayload(id, messages, model, title, attachFile)) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as AskQuestion

                if (id != other.id) return false
                if (!messages.contentEquals(other.messages)) return false
                if (model != other.model) return false
                if (title != other.title) return false
                if (attachFile != other.attachFile) return false

                return true
            }

            override fun hashCode(): Int {
                var result = id.hashCode()
                result = 31 * result + messages.contentHashCode()
                result = 31 * result + model.hashCode()
                result = 31 * result + (title?.hashCode() ?: 0)
                result = 31 * result + attachFile.hashCode()
                return result
            }
        }

        data class Stop(val id: String): FromChat(EventNames.FromChat.STOP_STREAMING, Payload(id))

        // receive
        class Response {
            enum class Roles(value: String) {
                @SerializedName("user") USER("user"),
                @SerializedName("context_file") CONTEXT_FILE("context_file")
            }

            abstract class ResponsePayload()
            // abstract class ChatPayload(id: String): Payload(id)
//            data class UserMessage(
//                override val id: String,
//                val role: Roles,
//                val content: String
//            ): Payload(id)

            data class UserMessage(
                @SerializedName("role")
                val role: Roles,
                @SerializedName("content")
                val content: String
            ): ResponsePayload()

            class UserMessagePayload(
                override val id: String,
                val role: Roles,
                val content: String,
            ): Payload(id)

            class UserMessageToChat(payload: UserMessagePayload): ToChat(EventNames.ToChat.CHAT_RESPONSE, payload)


            enum class FinishReasons(value: String) {
                STOP("stop"),
                ABORT("abort"),
            }

            data class Choice(
                // override val id: String,
                val delta: AssistantDelta,
                val index: Int,
                @SerializedName("finish_reason") val finishReason: FinishReasons?,
            ) // : Payload(id)

            data class Choices(
                val choices: Array<Choice>,
                val created: String,
                val model: String,
            ): ResponsePayload() {
                override fun equals(other: Any?): Boolean {
                    if (this === other) return true
                    if (javaClass != other?.javaClass) return false

                    other as Choices

                    if (!choices.contentEquals(other.choices)) return false
                    if (created != other.created) return false
                    if (model != other.model) return false

                    return true
                }

                override fun hashCode(): Int {
                    var result = choices.contentHashCode()
                    result = 31 * result + created.hashCode()
                    result = 31 * result + model.hashCode()
                    return result
                }
            }

            class ChoicesPayload(id: String, choices: Choices): Payload(id)
            class ChoicesToChat(payload: ChoicesPayload): ToChat(EventNames.ToChat.CHAT_RESPONSE, payload)


//            data class Assistant(
//                val id: String,
//                val delta: Delta,
//                val index: Int,
//                val finishReason: FinishReasons? = null,
//            ): ToChat(EventNames.ToChat.CHAT_RESPONSE, Choice(
//                // id,
//                delta,
//                index,
//                finishReason
//            ))

            companion object {
                private val gson = GsonBuilder()
                    .registerTypeAdapter(ResponsePayload::class.java, ResponseDeserializer())
                    .registerTypeAdapter(Delta::class.java, DeltaDeserializer())
                    .create()

                fun parse(str: String): ResponsePayload {
                    return gson.fromJson(str, ResponsePayload::class.java)
                }

                fun stringify(response: ResponsePayload): String {
                    return gson.toJson(response)
                }

                fun formatToChat(response: ResponsePayload, id: String): ToChat? {
                    return when (response) {
                        is Response.UserMessage -> {
                            val payload = UserMessagePayload(id, response.role, response.content)
                            return UserMessageToChat(payload)
                        }

                        is Response.Choices -> {
                            val payload = ChoicesPayload(id, response)
                            return ChoicesToChat(payload)
                        }

                        else -> null
                    }
                }
            }
        }

        class ResponseDeserializer : JsonDeserializer<Response.ResponsePayload> {
            override fun deserialize(p0: JsonElement?, p1: Type?, p2: JsonDeserializationContext?): Response.ResponsePayload? {
                val role = p0?.asJsonObject?.get("role")?.asString

                if (role == "user" || role == "context_file") {
                    return p2?.deserialize(p0, Response.UserMessage::class.java)
                }

                val choices = p0?.asJsonObject?.get("choices")?.asJsonArray

                if (choices !== null) {
                    return p2?.deserialize(p0, Response.Choices::class.java)
                }

                return p2?.deserialize(p0, Response.ResponsePayload::class.java)
                // TODO("Not yet implemented")
            }

        }


        data class BackupPayload(
            override val id: String,
            val messages: ChatMessages
        ): Payload(id) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as BackupPayload

                if (id != other.id) return false
                if (!messages.contentEquals(other.messages)) return false

                return true
            }

            override fun hashCode(): Int {
                var result = id.hashCode()
                result = 31 * result + messages.contentHashCode()
                return result
            }

        }

        data class Backup(
            val id: String,
            val messages: ChatMessages,
        ): ToChat(EventNames.ToChat.BACKUP_MESSAGES, BackupPayload(id, messages)) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Backup

                if (id != other.id) return false
                if (!messages.contentEquals(other.messages)) return false

                return true
            }

            override fun hashCode(): Int {
                var result = id.hashCode()
                result = 31 * result + messages.contentHashCode()
                return result
            }
        }

        // Error streaming

        data class ErrorPayload(
            override val id: String,
            val message: String
        ): Payload(id)

        data class Error(
            val id: String,
            val error: String
        ): ToChat(EventNames.ToChat.ERROR_STREAMING, ErrorPayload(id, error))

        // Done streaming

        data class Done (
            val id: String,
        ): ToChat(EventNames.ToChat.DONE_STREAMING, Payload(id))

        // last model used
        data class LastModelUsedPayload(
            override val id: String,
            val model: String,
        ): Payload(id)

        data class LastModelUsed(
            val id: String,
            val model: String,
        ): ToChat(EventNames.ToChat.SET_LAST_MODEL_USED, LastModelUsedPayload(id, model))

        // restore

        data class Thread(
            val id: String,
            val messages: ChatMessages,
            val model: String,
            val title: String? = null,
            @SerializedName("attach_file") val attachFile: Boolean = false,
        ) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Thread

                if (id != other.id) return false
                if (!messages.contentEquals(other.messages)) return false
                if (model != other.model) return false
                if (title != other.title) return false
                if (attachFile != other.attachFile) return false

                return true
            }

            override fun hashCode(): Int {
                var result = id.hashCode()
                result = 31 * result + messages.contentHashCode()
                result = 31 * result + model.hashCode()
                result = 31 * result + (title?.hashCode() ?: 0)
                result = 31 * result + attachFile.hashCode()
                return result
            }
        }

        data class RestorePayload(
            override val id: String,
            val chat: Thread,
            val snippet: Editor.Snippet? = null,
        ): Payload(id)

        data class Restore(
            val id: String,
            val chat: Thread,
            val snippet: Editor.Snippet? = null,
        ): ToChat(EventNames.ToChat.RESTORE_CHAT, RestorePayload(id, chat, snippet))

        // new ?
        data class NewChatPayload(
            override val id: String,
            val snippet: Editor.Snippet?
        ): Payload(id)

        data class NewChat(
            val id: String,
            val snippet: Editor.Snippet?,
        ): ToChat(EventNames.ToChat.NEW_CHAT, NewChatPayload(id, snippet))

    }

    class ActiveFile {
        data class FileInfo(
            val name: String,
            val path: String,
            @SerializedName("can_paste") val canPaste: Boolean,
            val attach: Boolean = false,
            val line1: Int? = null,
            val line2: Int? = null,
            val cursor: Int? = null,
            val content: String? = null,
            val usefulness: Int? = null,
        )

        data class FileInfoPayload(
            override val id: String,
            val file: FileInfo,
        ) : Payload(id)

        data class Send(
            val id: String,
            val file: FileInfo
        ): ToChat(EventNames.ToChat.ACTIVE_FILE_INFO, FileInfoPayload(id, file))

    }

    class Editor {
        data class ContentPayload(
            override val id: String,
            val content: String
        ): Payload(id)

        data class NewFile(
            val id: String,
            val content: String,
        ): FromChat(EventNames.FromChat.NEW_FILE, ContentPayload(id, content))

        data class Paste(
            val id: String,
            val content: String
        ): FromChat(EventNames.FromChat.PASTE_DIFF, ContentPayload(id, content))

        data class Snippet(
            val language: String,
            val code: String,
            val path: String,
            val basename: String,
        )

        data class SetSnippetPayload(
            override val id: String,
            val snippet: Snippet
        ): Payload(id)

        data class SetSelectedSnippet(
            val id: String,
            val snippet: Snippet
        ): ToChat(EventNames.ToChat.SET_SELECTED_SNIPPET, SetSnippetPayload(id, snippet))
    }

    class Caps {
        data class Request(
            val id: String
        ): FromChat(EventNames.FromChat.REQUEST_CAPS, Payload(id)) {
            constructor() : this("")
        }

        data class CapsPayload(
            override val id: String,
            val caps: LSPCapabilities
        ): Payload(id)

        data class Receive(
            val id: String,
            val caps: LSPCapabilities
        ): ToChat(EventNames.ToChat.RECEIVE_CAPS, CapsPayload(id, caps))

        data class ErrorPayload(
            override val id: String,
            val message: String
        ): Payload(id)

        data class Error(
            val id: String,
            val error: String
        ): ToChat(EventNames.ToChat.RECEIVE_CAPS_ERROR, ErrorPayload(id, error))
    }

    companion object {
        fun parse(msg: String?): FromChat? {
            // TODO: parse messages correctly

            val gson = GsonBuilder()
                .registerTypeAdapter(FromChat::class.java, FromChatDeserializer())
                .registerTypeAdapter(ChatMessage::class.java, ChatMessageDeserializer())
                .create()
            return gson.fromJson(msg, FromChat::class.java)
        }

        fun stringify(event: ToChat): String {
            // TODO: figure out how to get the attributes from the parent class
            val json = JsonObject()
            val payload = Gson().toJsonTree(event).asJsonObject
            json.addProperty("type", event.type.value)
            // will this have type added to the paylaod?
            json.add("payload", payload)
            return Gson().toJson(json)
        }
    }
}
