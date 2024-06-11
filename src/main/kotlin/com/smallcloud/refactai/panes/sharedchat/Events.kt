package com.smallcloud.refactai.panes.sharedchat

import com.google.gson.annotations.SerializedName
import com.google.gson.*
import com.smallcloud.refactai.lsp.LSPCapabilities
import com.smallcloud.refactai.lsp.Tool
import com.smallcloud.refactai.panes.sharedchat.Events.Chat.Response.ResponsePayload
import com.smallcloud.refactai.panes.sharedchat.Events.Chat.ResponseDeserializer
import java.io.Serializable
import java.lang.reflect.Type

// Lsp responses

enum class ChatRole(val value: String): Serializable {
    @SerializedName("user") USER("user"),
    @SerializedName("assistant") ASSISTANT("assistant"),
    @SerializedName("context_file") CONTEXT_FILE("context_file"),
    @SerializedName("system") SYSTEM("system"),
    @SerializedName("tool") TOOL("tool"),
}

data class ChatContextFile(
    @SerializedName("file_name") val fileName: String,
    @SerializedName("file_content") val fileContent: String,
    val line1: Int,
    val line2: Int,
    val usefulness: Double? = null
)



abstract class ChatMessage<T>(
    @SerializedName("role")
    val role: ChatRole,
    @Transient
    @SerializedName("content")
    open val content: T,
    @Transient
    @SerializedName("tool_calls")
    open val toolCalls: Array<ToolCall>? = null,
): Serializable {
}
data class UserMessage(override val content: String): ChatMessage<String>(ChatRole.USER, content)

data class AssistantMessage(
    override val content: String?,
    @SerializedName("tool_calls")
    override val toolCalls: Array<ToolCall>? = null
): ChatMessage<String?>(ChatRole.ASSISTANT, content, toolCalls) {

    override fun hashCode(): Int {
        var result = content.hashCode()
        result = 31 * result + (toolCalls?.contentHashCode() ?: 0)
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AssistantMessage

        if (content != other.content) return false
        if (toolCalls != null) {
            if (other.toolCalls == null) return false
            if (!toolCalls.contentEquals(other.toolCalls)) return false
        } else if (other.toolCalls != null) return false

        return true
    }
}

data class SystemMessage(override val content: String): ChatMessage<String>(ChatRole.SYSTEM, content)

data class ToolMessageContent(
    @SerializedName("tool_call_id")
    val toolCallId: String,
    val content: String,
    @SerializedName("finish_reason")
    val finishReason: String?
)


data class ToolMessage(
    override val content: ToolMessageContent
): ChatMessage<ToolMessageContent>(ChatRole.TOOL, content = content)


data class ContentFileMessage(override val content: Array<ChatContextFile>): ChatMessage<Array<ChatContextFile>>(
    ChatRole.CONTEXT_FILE, content) {
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
            ChatRole.TOOL.value -> p2?.deserialize(p0, ToolMessage::class.java)
            else -> null
        }
    }
}

class ChatHistorySerializer: JsonSerializer<ChatMessage<*>> {
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


abstract class Delta<T>(
    val role: ChatRole?,
    open val content: T,
    @SerializedName("tool_calls")
    val toolCalls: Array<ToolCall>? = null,
    val id: String? = null,
    @SerializedName("tool_call_id")
    val toolCallId: String? = null,
    @SerializedName("finish_reason")
    val finishReason: String? = null,
)
class AssistantDelta(
    role: ChatRole?,
    content: String?,
    toolCalls: Array<ToolCall>? = null
): Delta<String?>(role, content, toolCalls)

class ContextFileDelta(content: ChatMessages): Delta<ChatMessages>(ChatRole.CONTEXT_FILE, content)

data class TooCallFunction(val arguments: String, val name: String?)
class ToolCall(val function: TooCallFunction, val index: Int, val type: String?, val id: String?) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ToolCall

        if (function != other.function) return false
        if (index != other.index) return false
        if (type != other.type) return false
        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        var result = function.hashCode()
        result = 31 * result + index
        result = 31 * result + (type?.hashCode() ?: 0)
        result = 31 * result + (id?.hashCode() ?: 0)
        return result
    }
}

class ToolResult(
    id: String,
    toolCallId: String,
    content: String,
    toolCalls: Array<ToolCall>?
): Delta<String>(ChatRole.TOOL, content, id=id, toolCallId = toolCallId, toolCalls = toolCalls )

class DeltaDeserializer: JsonDeserializer<Delta<*>> {
    override fun deserialize(p0: JsonElement?, p1: Type?, p2: JsonDeserializationContext?): Delta<*>? {
        val role = p0?.asJsonObject?.get("role")?.asString
        val content = p0?.asJsonObject?.get("content")?.asString
        val hasToolCalls = p0?.asJsonObject?.has("tool_calls")

        if(role == null && content is String) {
            return p2?.deserialize(p0, AssistantDelta::class.java)
        }

        if(hasToolCalls == true) {
            return p2?.deserialize(p0, AssistantDelta::class.java)
        }
        return when(role) {
            ChatRole.ASSISTANT.value -> p2?.deserialize(p0, AssistantDelta::class.java)
            ChatRole.CONTEXT_FILE.value -> p2?.deserialize(p0, ContextFileDelta::class.java)
            ChatRole.TOOL.value -> p2?.deserialize(p0, ToolResult::class.java)
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
    @SerializedName("completions") val completions: Array<String>,
    val replace: Array<Int>,
    @SerializedName("is_cmd_executable") val isCmdExecutable: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CommandCompletionResponse

        if (!completions.contentEquals(other.completions)) return false
        if (!replace.contentEquals(other.replace)) return false
        if (isCmdExecutable != other.isCmdExecutable) return false

        return true
    }

    override fun hashCode(): Int {
        var result = completions.contentHashCode()
        result = 31 * result + replace.contentHashCode()
        result = 31 * result + isCmdExecutable.hashCode()
        return result
    }
}

data class DetailMessage(
    val detail: String
)



data class SystemPrompt(
    val text: String,
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
        REQUEST_PREVIEW_FILES("chat_request_preview_files"),
        REQUEST_PROMPTS("chat_request_prompts")
    }

    enum class ToChat(val value: String) {
        CLEAR_ERROR("chat_clear_error"),
        @SerializedName("restore_chat_from_history") RESTORE_CHAT("restore_chat_from_history"),
        @SerializedName("chat_response") CHAT_RESPONSE("chat_response"),
        BACKUP_MESSAGES("back_up_messages"),
        @SerializedName("chat_done_streaming") DONE_STREAMING("chat_done_streaming"),
        @SerializedName("chat_error_streaming") ERROR_STREAMING("chat_error_streaming"),
        NEW_CHAT("create_new_chat"),
        @SerializedName("receive_caps") RECEIVE_CAPS("receive_caps"),
        @SerializedName("receive_caps_error") RECEIVE_CAPS_ERROR("receive_caps_error"),
        SET_CHAT_MODEL("chat_set_chat_model"),
        SET_DISABLE_CHAT("set_disable_chat"),
        @SerializedName("chat_active_file_info") ACTIVE_FILE_INFO("chat_active_file_info"),
        TOGGLE_ACTIVE_FILE("chat_toggle_active_file"),
        @SerializedName("chat_receive_at_command_completion") RECEIVE_AT_COMMAND_COMPLETION("chat_receive_at_command_completion"),
        @SerializedName("chat_receive_at_command_preview") RECEIVE_AT_COMMAND_PREVIEW("chat_receive_at_command_preview"),
        SET_SELECTED_AT_COMMAND("chat_set_selected_command"),
        SET_LAST_MODEL_USED("chat_set_last_model_used"),

        @SerializedName("chat_set_selected_snippet") SET_SELECTED_SNIPPET("chat_set_selected_snippet"),
        REMOVE_PREVIEW_FILE_BY_NAME("chat_remove_file_from_preview"),
        SET_PREVIOUS_MESSAGES_LENGTH("chat_set_previous_messages_length"),
        RECEIVE_TOKEN_COUNT("chat_set_tokens"),
        @SerializedName("chat_receive_prompts") RECEIVE_PROMPTS("chat_receive_prompts"),
        @SerializedName("chat_receive_prompts_error") RECEIVE_PROMPTS_ERROR("chat_receive_prompts_error"),
        SET_SELECTED_SYSTEM_PROMPT("chat_set_selected_system_prompt"),
        @SerializedName("receive_config_update") RECEIVE_CONFIG_UPDATE("chat_receive_config_update"),
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
                EventNames.FromChat.REQUEST_PREVIEW_FILES.value -> p2?.deserialize(payload, AtCommands.Preview.Request::class.java)
                EventNames.FromChat.SAVE_CHAT.value -> {
                    val messages = JsonArray()
                    payload.asJsonObject.get("messages").asJsonArray.forEach {
                        val pair = it.asJsonArray
                        val role = pair.get(0)
                        val content = pair.get(1)
                        val obj = JsonObject()
                        obj.add("role", role)
                        obj.add("content", content)
                        if (role.asString == "assistant" && pair.size() == 3) {
                            obj.add("tool_calls", pair.get(2))
                        }
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
                        if (role.asString == "assistant" && pair.size() == 3) {
                            obj.add("tool_calls", pair.get(2))
                        }
                        messages.add(obj)
                    }

                    payload.asJsonObject.add("messages", messages)
                    return p2?.deserialize<Chat.AskQuestion>(payload, Chat.AskQuestion::class.java)
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
        class Receive(payload: SystemPromptsPayload): ToChat(EventNames.ToChat.RECEIVE_PROMPTS, payload)

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
                val completions: Array<String>,
                val replace: Array<Int>,
                @SerializedName("is_cmd_executable")
                val isCmdExecutable: Boolean = false
            ) : Payload(id) {
                override fun equals(other: Any?): Boolean {
                    if (this === other) return true
                    if (javaClass != other?.javaClass) return false

                    other as CompletionPayload

                    if (id != other.id) return false
                    if (!completions.contentEquals(other.completions)) return false
                    if (!replace.contentEquals(other.replace)) return false
                    if (isCmdExecutable != other.isCmdExecutable) return false

                    return true
                }

                override fun hashCode(): Int {
                    var result = id.hashCode()
                    result = 31 * result + completions.contentHashCode()
                    result = 31 * result + replace.contentHashCode()
                    result = 31 * result + isCmdExecutable.hashCode()
                    return result
                }
            }

            class Receive( payload: CompletionPayload ) : ToChat(EventNames.ToChat.RECEIVE_AT_COMMAND_COMPLETION, payload)
        }

        class Preview {
            data class RequestPayload(override val id: String, val query: String): Payload(id)
            data class Request( val id: String, val query: String): FromChat(EventNames.FromChat.REQUEST_PREVIEW_FILES, RequestPayload(id, query))

            data class Response(
                val messages: Array<ContentFileMessage>,
            ) {
                override fun equals(other: Any?): Boolean {
                    if (this === other) return true
                    if (javaClass != other?.javaClass) return false

                    other as Response

                    return messages.contentEquals(other.messages)
                }

                override fun hashCode(): Int {
                    return messages.contentHashCode()
                }
            }

            class ResponseDeserializer: JsonDeserializer<Events.AtCommands.Preview.Response> {
                override fun deserialize(
                    p0: JsonElement?,
                    p1: Type?,
                    p2: JsonDeserializationContext?
                ): Events.AtCommands.Preview.Response? {
                    val messages = p0?.asJsonObject?.get("messages")?.asJsonArray
                    val arr = JsonArray()
                    messages?.forEach {
                        val contentAsString = it.asJsonObject.get("content").asString
                        val a = Gson().fromJson(contentAsString, JsonArray::class.java)
                        it.asJsonObject.add("content", a)
                        arr.add(it)
                    }

                    p0?.asJsonObject?.add("messages", arr)
                    return Gson().fromJson(p0, Events.AtCommands.Preview.Response::class.java)
                }
            }


            data class PreviewPayload(
                override val id: String,
                val preview: Array<ContentFileMessage>,
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

            class Receive(
                payload: PreviewPayload
            ) : ToChat(EventNames.ToChat.RECEIVE_AT_COMMAND_PREVIEW, payload)
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
                result = 31 * result + title.hashCode()
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
                @SerializedName("context_file") CONTEXT_FILE("context_file"),
                @SerializedName("tool") TOOL("tool")
            }

            abstract class ResponsePayload()

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

            class ToolMessage(
                val role: Roles,
                val content: String,
                @SerializedName("tool_call_id")
                val toolCallId: String,
                @SerializedName("tool_calls")
                val toolCalls: Array<ToolCall>? = null,
            ): ResponsePayload()

            class ToolMessagePayload(
                override val id: String,
                val role: Roles,
                val content: String, // maybe this ?
                @SerializedName("tool_call_id")
                val toolCallId: String
            ): Payload(id)

            class ToolMessageToChat(
                payload: ToolMessagePayload,
            ): ToChat(EventNames.ToChat.CHAT_RESPONSE, payload)


            enum class FinishReasons(value: String) {
                STOP("stop"),
                ABORT("abort"),
            }

            data class Choice(
                val delta: AssistantDelta, // tool_calls deleta
                val index: Int,
                @SerializedName("finish_reason") val finishReason: FinishReasons?,
            )

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

            class ChoicesPayload(
                override val id: String,
                val choices: Array<Choice>,
                val created: String,
                val model: String,
            ): Payload(id)

            class ChoicesToChat(payload: ChoicesPayload): ToChat(EventNames.ToChat.CHAT_RESPONSE, payload)

            data class ChatDone(val message: String? = null): ResponsePayload()

            class ChatDonePayload(
                override val id: String,
                val message: String?,
            ): Payload(id)

            class ChatDoneToChat(payload: ChatDonePayload): ToChat(EventNames.ToChat.DONE_STREAMING, payload)

            data class ChatError(val message: JsonElement): ResponsePayload()

            class ChatErrorPayload(
                override val id: String,
                val message: String,
            ): Payload(id)

            class ChatErrorStreamingToChat(payload: ChatErrorPayload): ToChat(EventNames.ToChat.ERROR_STREAMING, payload)

            data class ChatFailedStream(val message: Throwable?): ResponsePayload()

            // detail
            data class DetailMessage(val detail: String): ResponsePayload()

            companion object {
                private val gson = GsonBuilder()
                    .registerTypeAdapter(ResponsePayload::class.java, ResponseDeserializer())
                    .registerTypeAdapter(Delta::class.java, DeltaDeserializer())
                    // .serializeNulls()
                    .create()

                fun parse(str: String): ResponsePayload {
                    return gson.fromJson(str, ResponsePayload::class.java)
                }


                fun formatToChat(response: ResponsePayload, id: String): ToChat? {
                    return when (response) {
                        is Response.UserMessage -> {
                            val payload = UserMessagePayload(id, response.role, response.content)
                            return UserMessageToChat(payload)
                        }

                        is Response.Choices -> {
                            val payload = ChoicesPayload(id, response.choices, response.created, response.model)
                            return ChoicesToChat(payload)
                        }

                        is Response.ChatDone -> {
                            val payload = ChatDonePayload(id, response.message)
                            return ChatDoneToChat(payload)
                        }

                        is Response.ToolMessage -> {
                            val payload = ToolMessagePayload(id, response.role, response.content, response.toolCallId)
                            return ToolMessageToChat(payload)
                        }

                        is Response.DetailMessage -> {
                            val payload = ChatErrorPayload(id, response.detail)
                            return ChatErrorStreamingToChat(payload)
                        }

                        is Response.ChatError -> {
                            val maybeDetail = response.message.asJsonObject.get("detail").asString
                            val message = maybeDetail ?: response.message.toString()
                            val payload = ChatErrorPayload(id, message)
                            return ChatErrorStreamingToChat(payload)
                        }

                        is Response.ChatFailedStream -> {
                            val message = "Failed during stream: ${response.message?.message}"
                            val payload = ChatErrorPayload(id, message)
                            return ChatErrorStreamingToChat(payload)
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

                if(role == "tool") {
                    return p2?.deserialize(p0, Response.ToolMessage::class.java)
                }

                val choices = p0?.asJsonObject?.get("choices")?.asJsonArray

                if (choices !== null) {
                    return p2?.deserialize(p0, Response.Choices::class.java)
                }

                val detail = p0?.asJsonObject?.has("detail")
                if(detail == true) {
                    return p2?.deserialize<Response.DetailMessage>(p0, Response.DetailMessage::class.java)
                }

                return p2?.deserialize(p0, Response.ResponsePayload::class.java)

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

        class RestoreToChat(
            payload: RestorePayload
        ): ToChat(EventNames.ToChat.RESTORE_CHAT, payload)

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
            val name: String = "",
            val path: String = "",
            @SerializedName("can_paste") val canPaste: Boolean = false,
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

        class ActiveFileToChat(payload: FileInfoPayload): ToChat(EventNames.ToChat.ACTIVE_FILE_INFO, payload)

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
            val language: String = "",
            val code: String = "",
            val path: String = "",
            val basename: String = "",
        )

        data class SetSnippetPayload(
            override val id: String,
            val snippet: Snippet
        ): Payload(id)

        class SetSnippetToChat(payload: SetSnippetPayload): ToChat(EventNames.ToChat.SET_SELECTED_SNIPPET, payload)

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

        class Receive(
            id: String,
            caps: LSPCapabilities
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

    class Config {
        abstract class BaseFeatures()

        data class Features(val ast: Boolean, val vecdb: Boolean): BaseFeatures()

        data class ThemeProps(val mode: String, val hasBackground: Boolean = false, val scale: String = "90%",  val accentColor: String ="gray")

        data class UpdatePayload(override val id: String, val features: BaseFeatures, val themeProps: ThemeProps?): Payload(id)

        class Update(id: String, features: BaseFeatures, themeProps: ThemeProps?): ToChat(EventNames.ToChat.RECEIVE_CONFIG_UPDATE, UpdatePayload(id, features, themeProps))

    }

    companion object {

        private class MessageSerializer: JsonSerializer<ChatMessage<*>> {
            override fun serialize(src: ChatMessage<*>, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
                return when(src) {
                    is UserMessage -> {
                        val role = context.serialize(src.role, ChatRole::class.java)
                        val arr = JsonArray()
                        arr.add(role)
                        arr.add(src.content)
                        return arr
                    }
                    is SystemMessage -> {
                        val role = context.serialize(src.role, ChatRole::class.java)
                        val arr = JsonArray()
                        arr.add(role)
                        arr.add(src.content)
                        return arr
                    }
                    is AssistantMessage-> {
                        val role = context.serialize(src.role, ChatRole::class.java)
                        val arr = JsonArray()
                        arr.add(role)
                        arr.add(src.content)
                        arr.add(Gson().toJson(src.toolCalls))
                        return arr
                    }

                    is ToolMessage -> {
                        val role = context.serialize(src.role, ChatRole::class.java)
                        val arr = JsonArray()
                        arr.add(role)
                        arr.add(Gson().toJson(src.content))
                        return arr;
                    }
                    is ContentFileMessage -> {
                        val role = context.serialize(src.role, ChatRole::class.java)
                        val arr = JsonArray()
                        arr.add(role)
                        val fileArray = arrayOf(ChatContextFile("", "", 0, 0))
                        val contextFile = context.serialize(src.content, fileArray::class.java)
                        arr.add(contextFile)
                        return arr
                    }

                    else -> JsonArray()
                }
            }
        }

        val gson = GsonBuilder()
             .registerTypeAdapter(FromChat::class.java, FromChatDeserializer())
            .registerTypeAdapter(AtCommands.Preview.Response::class.java, AtCommands.Preview.ResponseDeserializer())
             .registerTypeAdapter(ChatMessage::class.java, ChatMessageDeserializer())
             .registerTypeHierarchyAdapter(ChatMessage::class.java, MessageSerializer())
            // .serializeNulls()
           .create()

        fun parse(msg: String?): FromChat? {
            return gson.fromJson(msg, FromChat::class.java)
        }

        fun stringify(event: ToChat): String {
            return gson.toJson(event)
        }
    }
}
