package com.smallcloud.refactai.panes.sharedchat

import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import com.smallcloud.refactai.settings.Host
import com.smallcloud.refactai.settings.HostDeserializer
import com.smallcloud.refactai.struct.ChatMessage
import java.io.Serializable
import java.lang.reflect.Type


class EventNames {
    enum class FromChat(val value: String) {
        NEW_FILE("ide/newFile"),
        PASTE_DIFF("ide/diffPasteBack"),
        OPEN_SETTINGS("ide/openSettings"),
        OPEN_HOTKEYS("ide/openHotKeys"), // Will this work?
        OPEN_FILE("ide/openFile"),
        OPEN_CHAT_IN_TAB("ide/openChatInNewTab"), // Will this work?
        SETUP_HOST("setup_host"),
        OPEN_EXTERNAL_URL("open_external_url"),
        LOG_OUT("log_out"),
        FIM_READY("fim/ready"),
        FIM_REQUEST("fim/request"),

        // Adding
        START_ANIMATION("ide/animateFile/start"),
        STOP_ANIMATION("ide/animateFile/stop"),
        DIFF_PREVIEW("ide/diffPreview"),
        WRITE_RESULTS_TO_FILE("ide/writeResultsToFile"),
        IS_CHAT_STREAMING("ide/isChatStreaming"),
        CHAT_PAGE_CHANGE("ide/chatPageChange"),
        IDE_TOOL_EDIT("ide/toolEdit"),
        FORCE_RELOAD_FILE_BY_PATH("ide/forceReloadFileByPath"),
        FORCE_RELOAD_PROJECT_TREE_FILES("ide/forceReloadProjectTreeFiles"),
    }

    enum class ToChat(val value: String) {
        @SerializedName("config/update")
        UPDATE_CONFIG("config/update"),
        @SerializedName("selected_snippet/set")
        SET_SELECTED_SNIPPET("selected_snippet/set"),
        @SerializedName("activeFile/setFileInfo")
        SET_ACTIVE_FILE_INFO("activeFile/setFileInfo"),
        @SerializedName("fim/error")
        FIM_ERROR("fim/error"),
        @SerializedName("fim/receive")
        FIM_RECEIVE("fim/receive"),
        @SerializedName("chatThread/new")
        NEW_CHAT("chatThread/new"),

        // codelens
        @SerializedName("textarea/replace")
        CODE_LENS_EXEC("textarea/replace"),
        // logout, open external url, setup host

        @SerializedName("ide/toolEditResponse")
        IDE_TOOL_EDIT_RESPONSE("ide/toolEditResponse")
    }
}


class Events {

    open class Payload : Serializable

    abstract class FromChat(val type: EventNames.FromChat, open val payload: Serializable?) : Serializable

    private class FromChatDeserializer : JsonDeserializer<FromChat> {
        override fun deserialize(p0: JsonElement?, p1: Type?, p2: JsonDeserializationContext?): FromChat? {
            val type = p0?.asJsonObject?.get("type")?.asString
            // events without payload
            if (type == EventNames.FromChat.LOG_OUT.value) {
                return Setup.LogOut()
            }

            val payload = p0?.asJsonObject?.get("payload")
            if (type == null) return null

            return when (type) {
                EventNames.FromChat.NEW_FILE.value -> payload?.asString?.let { Editor.NewFile(it) }
                EventNames.FromChat.OPEN_SETTINGS.value -> OpenSettings()
                EventNames.FromChat.SETUP_HOST.value -> {
                    val host = p2?.deserialize<Host>(payload, Host::class.java) ?: return null
                    Setup.SetupHost(host)
                }

                EventNames.FromChat.OPEN_EXTERNAL_URL.value -> {
                    val url = payload?.asJsonObject?.get("url")?.asString ?: return null
                    Setup.OpenExternalUrl(url)
                }

                EventNames.FromChat.PASTE_DIFF.value -> payload?.asString?.let { Events.Editor.PasteDiff(it) }


                // EventNames.FromChat.FIM_READY.value -> p2?.deserialize(payload, Fim.Ready::class.java)
                EventNames.FromChat.FIM_REQUEST.value -> Fim.Request()
                EventNames.FromChat.OPEN_HOTKEYS.value -> OpenHotKeys()
                EventNames.FromChat.IS_CHAT_STREAMING.value -> {
                    IsChatStreaming(payload?.asBoolean ?: false)
                }

                EventNames.FromChat.CHAT_PAGE_CHANGE.value -> {
                    ChatPageChange(payload?.asString ?: "")
                }

                EventNames.FromChat.OPEN_FILE.value -> {
                    val file: OpenFilePayload = p2?.deserialize(payload, OpenFilePayload::class.java) ?: return null
                    OpenFile(file)
                }

                EventNames.FromChat.START_ANIMATION.value -> Animation.Start(payload?.asString ?: "")

                EventNames.FromChat.STOP_ANIMATION.value -> Animation.Stop(payload?.asString ?: "")

                EventNames.FromChat.DIFF_PREVIEW.value -> Patch.Show(
                    p2?.deserialize(
                        payload,
                        Patch.ShowPayload::class.java
                    ) ?: return null
                )

                EventNames.FromChat.WRITE_RESULTS_TO_FILE.value -> {
                    val results = mutableListOf<Patch.PatchResult>()
                    val items = p2?.deserialize(payload, results::class.java) ?: results
                    val applyPayload = Patch.ApplyPayload(items)
                    return Patch.Apply(applyPayload)
                }

                EventNames.FromChat.IDE_TOOL_EDIT.value -> {
                    val toolCallPayload = p2?.deserialize<IdeAction.ToolCallPayload>(payload, IdeAction.ToolCallPayload::class.java)
                        ?: return null
                    // Ensure that the edit field is properly deserialized
                    val editJson = payload?.asJsonObject?.get("edit")
                    val edit = p2.deserialize<ToolEditResult>(editJson, ToolEditResult::class.java)
                    // Create a new ToolCallPayload with the deserialized edit
                    val updatedPayload = IdeAction.ToolCallPayload(toolCallPayload.toolCall, toolCallPayload.chatId, edit ?: toolCallPayload.edit)
                    return IdeAction.ToolCall(updatedPayload)

                }
                EventNames.FromChat.FORCE_RELOAD_FILE_BY_PATH.value -> {
                    val filePath = payload?.asString ?: return null
                    return Editor.ForceReloadFileByPath(filePath)
                }

                EventNames.FromChat.FORCE_RELOAD_PROJECT_TREE_FILES.value -> {
                    return Editor.ForceReloadProjectTreeFiles()
                }

                else -> null
            }
        }

    }

    class Animation {
        data class AnimationPayload(val payload: String) : Payload();
        data class Start(val fileName: String) :
            FromChat(EventNames.FromChat.START_ANIMATION, AnimationPayload(fileName))

        data class Stop(val fileName: String) : FromChat(EventNames.FromChat.STOP_ANIMATION, AnimationPayload(fileName))
    }

    class Patch {
        data class PatchResult(
            @SerializedName("file_text") val fileText: String,
            @SerializedName("file_name_edit") val fileNameEdit: String?,
            @SerializedName("file_name_delete") val fileNameDelete: String?,
            @SerializedName("file_name_add") val fileNameAdd: String?,
        )

        data class PatchState(
            @SerializedName("chunk_id") val chunkId: Int,
            @SerializedName("applied") val applied: Boolean,
            @SerializedName("can_unapply") val canUnapply: Boolean,
            @SerializedName("success") val success: Boolean,
            @SerializedName("detail") val detail: String?,
        )

        data class ShowPayload(
            val currentPin: String,
            val allPins: List<String>,
            val results: List<PatchResult>,
            val state: List<PatchState>,
        ) : Payload()

        class Show(override val payload: ShowPayload) : FromChat(EventNames.FromChat.DIFF_PREVIEW, payload)

        class ApplyPayload(val items: List<PatchResult>) : Payload()

        // typealias ApplyPayload: Payload()
        class Apply(override val payload: ApplyPayload) : FromChat(EventNames.FromChat.WRITE_RESULTS_TO_FILE, payload)
    }

    class Fim {
        class Ready : FromChat(EventNames.FromChat.FIM_READY, null)
        class Request : FromChat(EventNames.FromChat.FIM_REQUEST, null)

        class Choice(
            @SerializedName("code_completion")
            val codeCompletion: String,
            @SerializedName("finish_reason")
            val finishReason: String,
            val index: Int
        )

        data class File(
            @SerializedName("file_content")
            val fileContent: String,
            @SerializedName("file_name")
            val fileName: String,
            val line1: Int,
            val line2: Int,
        )

        data class Bucket(
            @SerializedName("file_path")
            val filePath: String,
            val line1: Int,
            val line2: Int,
            val name: String,
        )

        data class Context(
            @SerializedName("attached_files")
            val attachedFiles: Array<File>?,
            @SerializedName("bucket_declarations")
            val bucketDeclarations: Array<Bucket>?,
            @SerializedName("bucket_usage_of_same_stuff")
            val bucketUsageOfSameStuff: Array<Bucket>?,
            @SerializedName("bucket_high_overlap")
            val bucketHighOverlap: Array<Bucket>?,
            @SerializedName("cursor_symbols")
            val cursorSymbols: Array<Bucket>?,
            @SerializedName("fim_ms")
            val fimMs: Int?,
            @SerializedName("n_ctx")
            val nCtx: Int?,
            @SerializedName("rag_ms")
            val ragMs: Int?,
            @SerializedName("rag_tokens_limit")
            val ragTokensLimit: Int?,
        ) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Context

                if (attachedFiles != null) {
                    if (other.attachedFiles == null) return false
                    if (!attachedFiles.contentEquals(other.attachedFiles)) return false
                } else if (other.attachedFiles != null) return false
                if (bucketDeclarations != null) {
                    if (other.bucketDeclarations == null) return false
                    if (!bucketDeclarations.contentEquals(other.bucketDeclarations)) return false
                } else if (other.bucketDeclarations != null) return false
                if (bucketUsageOfSameStuff != null) {
                    if (other.bucketUsageOfSameStuff == null) return false
                    if (!bucketUsageOfSameStuff.contentEquals(other.bucketUsageOfSameStuff)) return false
                } else if (other.bucketUsageOfSameStuff != null) return false
                if (bucketHighOverlap != null) {
                    if (other.bucketHighOverlap == null) return false
                    if (!bucketHighOverlap.contentEquals(other.bucketHighOverlap)) return false
                } else if (other.bucketHighOverlap != null) return false
                if (cursorSymbols != null) {
                    if (other.cursorSymbols == null) return false
                    if (!cursorSymbols.contentEquals(other.cursorSymbols)) return false
                } else if (other.cursorSymbols != null) return false
                if (fimMs != other.fimMs) return false
                if (nCtx != other.nCtx) return false
                if (ragMs != other.ragMs) return false
                if (ragTokensLimit != other.ragTokensLimit) return false

                return true
            }

            override fun hashCode(): Int {
                var result = attachedFiles?.contentHashCode() ?: 0
                result = 31 * result + (bucketDeclarations?.contentHashCode() ?: 0)
                result = 31 * result + (bucketUsageOfSameStuff?.contentHashCode() ?: 0)
                result = 31 * result + (bucketHighOverlap?.contentHashCode() ?: 0)
                result = 31 * result + (cursorSymbols?.contentHashCode() ?: 0)
                result = 31 * result + (fimMs?.hashCode() ?: 0)
                result = 31 * result + (nCtx?.hashCode() ?: 0)
                result = 31 * result + (ragMs?.hashCode() ?: 0)
                result = 31 * result + (ragTokensLimit?.hashCode() ?: 0)
                return result
            }
        }

        // Debug data

        data class FimDebugPayload(
            val choices: Array<Choice>,
            @SerializedName("snippet_telemetry_id")
            val snippetTelemetryId: Number,
            val model: String,
            val context: Context?,
            val created: Number?,
            val elapsed: Number?,
            val cached: Boolean?,
        ) : Payload()

        class Receive(payload: FimDebugPayload) : ToChat<Payload>(EventNames.ToChat.FIM_RECEIVE, payload)

        class Error(payload: String) : ToChat<String>(EventNames.ToChat.FIM_ERROR, payload)
    }

    abstract class ToChat<T : Any>(
        @SerializedName("type")
        val type: EventNames.ToChat,
        @SerializedName("payload")
        open val payload: T
    ) : Serializable


    class IsChatStreaming(val isStreaming: Boolean) : FromChat(EventNames.FromChat.IS_CHAT_STREAMING, isStreaming)
    class ChatPageChange(val currentPage: String) : FromChat(EventNames.FromChat.CHAT_PAGE_CHANGE, currentPage)
    class OpenSettings : FromChat(EventNames.FromChat.OPEN_SETTINGS, null)

    class OpenHotKeys : FromChat(EventNames.FromChat.OPEN_HOTKEYS, null)

    data class OpenFilePayload(
        @SerializedName("file_name")
        val fileName: String,
        val line: Int?
    ) : Payload()

    class OpenFile(override val payload: OpenFilePayload) : FromChat(EventNames.FromChat.OPEN_FILE, payload)

    class ActiveFile {
        data class FileInfo(
            val name: String = "",
            val path: String = "",
            @SerializedName("can_paste") val canPaste: Boolean = false,
            val line1: Int? = null,
            val line2: Int? = null,
            val cursor: Int? = null,
            val content: String? = null,
            val usefulness: Int? = null,
        )

        class ActiveFileToChat(payload: FileInfo) : ToChat<FileInfo>(EventNames.ToChat.SET_ACTIVE_FILE_INFO, payload)

    }

    class Setup {
        data class SetupHostPayload(
            val host: Host
        ) : Payload()

        data class SetupHost(val host: Host) : FromChat(EventNames.FromChat.SETUP_HOST, SetupHostPayload(host))

        data class UrlPayload(val url: String) : Payload()
        data class OpenExternalUrl(val url: String) : FromChat(EventNames.FromChat.OPEN_EXTERNAL_URL, UrlPayload(url))

        class LogOut : FromChat(EventNames.FromChat.LOG_OUT, null)
    }

    class Editor {
        data class ContentPayload(
            val payload: String
        ) : Payload()

        data class NewFile(
            val content: String,
        ) : FromChat(EventNames.FromChat.NEW_FILE, ContentPayload(content))

        data class PasteDiff(
            val content: String
        ) : FromChat(EventNames.FromChat.PASTE_DIFF, ContentPayload(content))

        data class Snippet(
            val language: String = "",
            val code: String = "",
            val path: String = "",
            val basename: String = "",
        ) : Payload()

        data class SetSnippetPayload(
            val snippet: Snippet
        ) : Payload()

        class SetSnippetToChat(payload: Snippet) : ToChat<Payload>(EventNames.ToChat.SET_SELECTED_SNIPPET, payload)
        class ForceReloadFileByPath(val path: String) : FromChat(EventNames.FromChat.FORCE_RELOAD_FILE_BY_PATH, path)
        class ForceReloadProjectTreeFiles(): FromChat(EventNames.FromChat.FORCE_RELOAD_PROJECT_TREE_FILES, null)
    }

    object NewChat : ToChat<Unit>(EventNames.ToChat.NEW_CHAT, Unit)
    data class CodeLensCommandPayload(
        val value: String = "",
        @SerializedName("send_immediately") val sendImmediately: Boolean = false,
        val messages: Array<ChatMessage>
    ) : Payload() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as CodeLensCommandPayload

            return messages.contentEquals(other.messages)
        }

        override fun hashCode(): Int {
            return messages.contentHashCode()
        }
    }

    class CodeLensCommand(payload: CodeLensCommandPayload) : ToChat<Payload>(EventNames.ToChat.CODE_LENS_EXEC, payload)



    class Config {
        abstract class BaseFeatures()

        data class Features(val ast: Boolean, val vecdb: Boolean, val images: Boolean? = true) : BaseFeatures()

        data class ThemeProps(
            val appearance: String,
            val hasBackground: Boolean = false,
            val scale: String = "90%",
            val accentColor: String = "gray"
        )

        data class KeyBindings(val completeManual: String)

        data class UpdatePayload(
            val features: Config.Features,
            val themeProps: Config.ThemeProps?,
            val lspPort: Int,
            val apiKey: String?,
            val addressURL: String?,
            val keyBindings: Config.KeyBindings,
            val tabbed: Boolean? = false,
            val host: String? = "jetbrains"
        ) : Payload()

        class Update(payload: UpdatePayload) : ToChat<Payload>(EventNames.ToChat.UPDATE_CONFIG, payload)

    }

    class IdeAction {
        data class ToolCallPayload(
            val toolCall: TextDocToolCall,
            val chatId: String,
            val edit: ToolEditResult,
        ): Payload()  {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as ToolCallPayload

                if (toolCall != other.toolCall) return false
                if (chatId != other.chatId) return false
                if (edit != other.edit) return false

                return true
            }

            override fun hashCode(): Int {
                var result = toolCall.hashCode()
                result = 31 * result + chatId.hashCode()
                result = 31 * result + edit.hashCode()
                return result
            }
        }

        data class ToolCall(override val payload: ToolCallPayload): FromChat(EventNames.FromChat.IDE_TOOL_EDIT, payload) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false
                return true
            }

            override fun hashCode(): Int {
                return javaClass.hashCode()
            }
        }

        data class ToolCallResponsePayload(
            val toolCallId: String,
            val chatId: String,
            val accepted: Boolean
        ) : Payload() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as ToolCallResponsePayload

                if (accepted != other.accepted) return false
                if (toolCallId != other.toolCallId) return false
                if (chatId != other.chatId) return false

                return true
            }

            override fun hashCode(): Int {
                var result = accepted.hashCode()
                result = 31 * result + toolCallId.hashCode()
                result = 31 * result + chatId.hashCode()
                return result
            }
        }

        class ToolCallResponse(payload: ToolCallResponsePayload): ToChat<ToolCallResponsePayload>(EventNames.ToChat.IDE_TOOL_EDIT_RESPONSE, payload)
    }


    companion object {

        val gson = GsonBuilder()
            .registerTypeAdapter(FromChat::class.java, FromChatDeserializer())
            .registerTypeAdapter(Host::class.java, HostDeserializer())
            .registerTypeAdapter(TextDocToolCall::class.java, TextDocToolCallDeserializer())
            .create()


        fun parse(msg: String?): FromChat? {
            val result = gson.fromJson(msg, FromChat::class.java)
            return result
        }

        fun stringify(event: ToChat<*>): String {
            return gson.toJson(event)
        }
    }
}

interface TextDocToolCall {

    data class CreateTextDocToolCall(
        val id: String,
        val function: Function
    ) : TextDocToolCall {
        data class Function(
            val name: String = "create_textdoc",
            val arguments: Arguments
        ) {
            data class Arguments(
                val path: String,
                val content: String
            )
        }
    }

    data class UpdateTextDocToolCall(
        val id: String,
        val function: Function
    ) : TextDocToolCall {
        data class Function(
            val name: String = "update_textdoc",
            val arguments: Arguments
        ) {
            data class Arguments(
                val path: String,
                val old_str: String,
                val replacement: String,
                val multiple: Boolean
            )
        }
    }

    data class ReplaceTextDocToolCall(
        val id: String,
        val function: Function
    ) : TextDocToolCall {
        data class Function(
            val name: String = "replace_textdoc",
            val arguments: Arguments
        ) {
            data class Arguments(
                val path: String,
                val replacement: String
            )
        }
    }

    data class UpdateRegexTextDocToolCall(
        val id: String,
        val function: Function
    ) : TextDocToolCall {
        data class Function(
            val name: String = "update_textdoc_regex",
            val arguments: Arguments
        ) {
            data class Arguments(
                val path: String,
                val pattern: String,
                val replacement: String,
                val multiple: Boolean
            )
        }
    }
}

data class ToolEditResult(
    @SerializedName("file_before")
    val fileBefore: String,
    @SerializedName("file_after")
    val fileAfter: String,
    val chunks: List<DiffChunk>
)
data class DiffChunk(
    val fileName: String,
    val fileAction: String,
    val line1: Int,
    val line2: Int,
    val linesRemove: String,
    val linesAdd: String,
    val fileNameRename: String? = null,
    val applicationDetails: String? = null
)

private class TextDocToolCallDeserializer : JsonDeserializer<TextDocToolCall> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): TextDocToolCall? {
        val jsonObject = json?.asJsonObject ?: return null
        val functionObject = jsonObject.getAsJsonObject("function")
        val functionName = functionObject.get("name").asString
        val id = jsonObject.get("id").asString // seems to be nulls

        return when (functionName) {
            "create_textdoc" -> {
                val arguments = context?.deserialize<TextDocToolCall.CreateTextDocToolCall.Function.Arguments>(functionObject.get("arguments"), TextDocToolCall.CreateTextDocToolCall.Function.Arguments::class.java)
                TextDocToolCall.CreateTextDocToolCall(id, TextDocToolCall.CreateTextDocToolCall.Function(arguments = arguments!!))
            }
            "update_textdoc" -> {
                val arguments = context?.deserialize<TextDocToolCall.UpdateTextDocToolCall.Function.Arguments>(functionObject.get("arguments"), TextDocToolCall.UpdateTextDocToolCall.Function.Arguments::class.java)
                TextDocToolCall.UpdateTextDocToolCall(id, TextDocToolCall.UpdateTextDocToolCall.Function(arguments = arguments!!))
            }
            "replace_textdoc" -> {
                val arguments = context?.deserialize<TextDocToolCall.ReplaceTextDocToolCall.Function.Arguments>(functionObject.get("arguments"), TextDocToolCall.ReplaceTextDocToolCall.Function.Arguments::class.java)
                TextDocToolCall.ReplaceTextDocToolCall(id, TextDocToolCall.ReplaceTextDocToolCall.Function(arguments = arguments!!))
            }
            "update_textdoc_regex" -> {
                val arguments = context?.deserialize<TextDocToolCall.UpdateRegexTextDocToolCall.Function.Arguments>(functionObject.get("arguments"), TextDocToolCall.UpdateRegexTextDocToolCall.Function.Arguments::class.java)
                TextDocToolCall.UpdateRegexTextDocToolCall(id, TextDocToolCall.UpdateRegexTextDocToolCall.Function(arguments = arguments!!))
            }
            else -> null
        }
    }
}
