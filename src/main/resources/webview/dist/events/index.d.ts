export declare interface ActionFromChat extends BaseAction {
    type: EVENT_NAMES_FROM_CHAT;
}

export declare interface ActionFromSidebar {
    type: EVENT_NAMES_FROM_SIDE_BAR;
}

export declare interface ActionFromStatistic extends BaseAction_2 {
    type: EVENT_NAMES_FROM_STATISTIC;
}

export declare type Actions = ActionToChat | ActionFromChat;

export declare interface ActionsToSideBar {
    type: EVENT_NAMES_TO_SIDE_BAR;
}

export declare interface ActionToChat extends BaseAction {
    type: EVENT_NAMES_TO_CHAT;
}

export declare interface ActionToStatistic extends BaseAction_2 {
    type: EVENT_NAMES_TO_STATISTIC;
}

export declare interface ActiveFileInfo extends ActionToChat {
    type: EVENT_NAMES_TO_CHAT.ACTIVE_FILE_INFO;
    payload: {
        id: string;
        file: Partial<FileInfo>;
    };
}

declare interface AssistantDelta extends BaseDelta {
    role: "assistant" | null;
    content: string;
}

export declare interface AssistantMessage extends BaseMessage {
    0: "assistant";
    1: string;
}

export declare interface BackUpMessages extends ActionToChat {
    type: EVENT_NAMES_TO_CHAT.BACKUP_MESSAGES;
    payload: {
        id: string;
        messages: ChatMessages;
    };
}

declare interface BaseAction {
    type: EVENT_NAMES_FROM_CHAT | EVENT_NAMES_TO_CHAT;
    payload?: {
        id: string;
        [key: string]: unknown;
    };
}

declare interface BaseAction_2 {
    type: EVENT_NAMES_FROM_STATISTIC | EVENT_NAMES_TO_STATISTIC;
    payload?: {
        data?: string;
        [key: string]: unknown;
    };
}

declare interface BaseDelta {
    role: ChatRole | null;
}

declare interface BaseMessage extends Array<string | ChatContextFile[]> {
    0: ChatRole;
    1: string | ChatContextFile[];
}

export declare type Buckets = ContextBucket[];

export declare type CapsResponse = {
    caps_version: number;
    cloud_name: string;
    code_chat_default_model: string;
    code_chat_models: Record<string, CodeChatModel>;
    code_completion_default_model: string;
    code_completion_models: Record<string, CodeCompletionModel>;
    code_completion_n_ctx: number;
    endpoint_chat_passthrough: string;
    endpoint_style: string;
    endpoint_template: string;
    running_models: string[];
    telemetry_basic_dest: string;
    telemetry_corrected_snippets_dest: string;
    tokenizer_path_template: string;
    tokenizer_rewrite_path: Record<string, unknown>;
};

export declare type CellValue = string | number;

export declare type ChatChoice = {
    delta: Delta;
    finish_reason: "stop" | "abort" | null;
    index: number;
};

export declare interface ChatClearError extends ActionToChat {
    type: EVENT_NAMES_TO_CHAT.CLEAR_ERROR;
}

export declare type ChatContextFile = {
    file_name: string;
    file_content: string;
    line1: number;
    line2: number;
    usefulness?: number;
    usefullness?: number;
};

declare interface ChatContextFileDelta extends BaseDelta {
    role: "context_file";
    content: ChatContextFile[];
}

export declare interface ChatContextFileMessage extends BaseMessage {
    0: "context_file";
    1: ChatContextFile[];
}

export declare interface ChatDoneStreaming extends ActionToChat {
    type: EVENT_NAMES_TO_CHAT.DONE_STREAMING;
    payload: {
        id: string;
    };
}

export declare interface ChatErrorStreaming extends ActionToChat {
    type: EVENT_NAMES_TO_CHAT.ERROR_STREAMING;
    payload: {
        id: string;
        message: string;
    };
}

export declare type ChatHistoryItem = {
    id: string;
    createdAt: string;
    lastUpdated: string;
    messages: ChatMessages;
    title: string;
    model: string;
};

export declare type ChatMessage = UserMessage | AssistantMessage | ChatContextFileMessage | SystemMessage;

export declare type ChatMessages = ChatMessage[];

export declare interface ChatReceiveCaps extends ActionToChat {
    type: EVENT_NAMES_TO_CHAT.RECEIVE_CAPS;
    payload: {
        id: string;
        caps: CapsResponse;
    };
}

export declare interface ChatReceiveCapsError extends ActionToChat {
    type: EVENT_NAMES_TO_CHAT.RECEIVE_CAPS_ERROR;
    payload: {
        id: string;
        message: string;
    };
}

export declare type ChatResponse = {
    choices: ChatChoice[];
    created: number;
    model: string;
    id: string;
} | ChatUserMessageResponse;

export declare type ChatRole = "user" | "assistant" | "context_file" | "system";

export declare interface ChatSetLastModelUsed extends ActionToChat {
    type: EVENT_NAMES_TO_CHAT.SET_LAST_MODEL_USED;
    payload: {
        id: string;
        model: string;
    };
}

export declare interface ChatSetSelectedSnippet extends ActionToChat {
    type: EVENT_NAMES_TO_CHAT.SET_SELECTED_SNIPPET;
    payload: {
        id: string;
        snippet: Snippet;
    };
}

export declare type ChatThread = {
    id: string;
    messages: ChatMessages;
    title?: string;
    model: string;
    attach_file?: boolean;
};

export declare type ChatUserMessageResponse = {
    id: string;
    role: "user" | "context_file";
    content: string;
};

export declare interface ClearFIMDebugError extends FIMAction {
    type: FIM_EVENT_NAMES.CLEAR_ERROR;
}

declare type CodeChatModel = {
    default_scratchpad: string;
    n_ctx: number;
    similar_models: string[];
    supports_scratchpads: Record<string, {
        default_system_message: string;
    }>;
};

declare type CodeCompletionModel = {
    default_scratchpad: string;
    n_ctx: number;
    similar_models: string[];
    supports_scratchpads: Record<string, Record<string, unknown>>;
};

export declare type ColumnName = "lang" | "refact" | "human" | "total" | "refact_impact" | "completions";

export declare type CommandCompletionResponse = {
    completions: string[];
    replace: [number, number];
    is_cmd_executable: boolean;
};

export declare type CommandPreviewContent = {
    content: string;
    role: "context_file";
};

export declare type CommandPreviewResponse = {
    messages: CommandPreviewContent[];
};

export declare type Config = {
    host: "web" | "ide" | "vscode" | "jetbrains";
    tabbed?: boolean;
    lspUrl?: string;
    dev?: boolean;
    themeProps?: ThemeProps;
    features?: {
        statistics?: boolean;
        vecdb?: boolean;
        ast?: boolean;
    };
};

export declare type ContextBucket = {
    file_path: string;
    line1: number;
    line2: number;
    name: string;
};

declare type ContextFiles = FimFile[];

export declare interface CreateNewChatThread extends ActionToChat {
    type: EVENT_NAMES_TO_CHAT.NEW_CHAT;
    payload?: {
        id: string;
        snippet?: Snippet;
    };
}

export declare type CustomPromptsResponse = {
    system_prompts: SystemPrompts;
    toolbox_commands: Record<string, unknown>;
};

export declare interface DeleteHistoryItem extends ActionFromSidebar {
    type: EVENT_NAMES_FROM_SIDE_BAR.DELETE_HISTORY_ITEM;
    payload: {
        id: string;
    };
}

declare type Delta = AssistantDelta | ChatContextFileDelta;

export declare type DetailMessage = {
    detail: string;
};

export declare enum EVENT_NAMES_FROM_CHAT {
    SAVE_CHAT = "save_chat_to_history",
    ASK_QUESTION = "chat_question",
    REQUEST_CAPS = "chat_request_caps",
    STOP_STREAMING = "chat_stop_streaming",
    BACK_FROM_CHAT = "chat_back_from_chat",
    OPEN_IN_CHAT_IN_TAB = "open_chat_in_new_tab",
    SEND_TO_SIDE_BAR = "chat_send_to_sidebar",
    READY = "chat_ready",
    NEW_FILE = "chat_create_new_file",
    PASTE_DIFF = "chat_paste_diff",
    REQUEST_AT_COMMAND_COMPLETION = "chat_request_at_command_completion",
    REQUEST_PREVIEW_FILES = "chat_request_preview_files",
    REQUEST_PROMPTS = "chat_request_prompts"
}

export declare enum EVENT_NAMES_FROM_SIDE_BAR {
    READY = "sidebar_ready",
    OPEN_CHAT_IN_SIDEBAR = "sidebar_open_chat_in_sidebar",
    OPEN_IN_CHAT_IN_TAB = "sidebar_open_chat_in_tab",
    DELETE_HISTORY_ITEM = "sidebar_delete_history_item",
    REQUEST_CHAT_HISTORY = "sidebar_request_chat_history"
}

export declare enum EVENT_NAMES_FROM_STATISTIC {
    BACK_FROM_STATISTIC = "back_from_statistic"
}

export declare enum EVENT_NAMES_TO_CHAT {
    CLEAR_ERROR = "chat_clear_error",
    RESTORE_CHAT = "restore_chat_from_history",
    CHAT_RESPONSE = "chat_response",
    BACKUP_MESSAGES = "back_up_messages",
    DONE_STREAMING = "chat_done_streaming",
    ERROR_STREAMING = "chat_error_streaming",
    NEW_CHAT = "create_new_chat",
    RECEIVE_CAPS = "receive_caps",
    RECEIVE_CAPS_ERROR = "receive_caps_error",
    SET_CHAT_MODEL = "chat_set_chat_model",
    SET_DISABLE_CHAT = "set_disable_chat",
    ACTIVE_FILE_INFO = "chat_active_file_info",
    TOGGLE_ACTIVE_FILE = "chat_toggle_active_file",
    RECEIVE_AT_COMMAND_COMPLETION = "chat_receive_at_command_completion",
    RECEIVE_AT_COMMAND_PREVIEW = "chat_receive_at_command_preview",
    SET_SELECTED_AT_COMMAND = "chat_set_selected_command",
    SET_LAST_MODEL_USED = "chat_set_last_model_used",
    SET_SELECTED_SNIPPET = "chat_set_selected_snippet",
    REMOVE_PREVIEW_FILE_BY_NAME = "chat_remove_file_from_preview",
    SET_PREVIOUS_MESSAGES_LENGTH = "chat_set_previous_messages_length",
    RECEIVE_TOKEN_COUNT = "chat_set_tokens",
    RECEIVE_PROMPTS = "chat_receive_prompts",
    RECEIVE_PROMPTS_ERROR = "chat_receive_prompts_error",
    SET_SELECTED_SYSTEM_PROMPT = "chat_set_selected_system_prompt"
}

export declare enum EVENT_NAMES_TO_CONFIG {
    UPDATE = "receive_config_update"
}

export declare enum EVENT_NAMES_TO_SIDE_BAR {
    RECEIVE_CHAT_HISTORY = "sidebar_receive_chat_history"
}

export declare enum EVENT_NAMES_TO_STATISTIC {
    REQUEST_STATISTIC_DATA = "request_statistic_data",
    RECEIVE_STATISTIC_DATA = "receive_statistic_data",
    RECEIVE_STATISTIC_DATA_ERROR = "receive_statistic_data_error",
    SET_LOADING_STATISTIC_DATA = "set_loading_statistic_data",
    SET_STATISTIC_DATA = "set_statistic_data"
}

export declare type FileInfo = {
    name: string;
    line1: number | null;
    line2: number | null;
    can_paste: boolean;
    attach: boolean;
    path: string;
    content?: string;
    usefulness?: number;
    cursor: number | null;
};

export declare enum FIM_EVENT_NAMES {
    DATA_REQUEST = "fim_debug_data_request",
    DATA_RECEIVE = "fim_debug_data_receive",
    DATA_ERROR = "fim_debug_data_error",
    READY = "fim_debug_ready",
    CLEAR_ERROR = "fim_debug_clear_error",
    BACK = "fim_debug_back"
}

export declare interface FIMAction {
    type: FIM_EVENT_NAMES;
}

declare type FimChoices = {
    code_completion: string;
    finish_reason: string;
    index: number;
}[];

export declare type FIMContext = {
    attached_files?: ContextFiles;
    bucket_declarations?: Buckets;
    bucket_usage_of_same_stuff?: Buckets;
    bucket_high_overlap?: Buckets;
    cursor_symbols?: Buckets;
    fim_ms?: number;
    n_ctx?: number;
    rag_ms?: number;
    rag_tokens_limit?: number;
};

export declare interface FIMDebugBack extends FIMAction {
    type: FIM_EVENT_NAMES.BACK;
}

export declare type FimDebugData = {
    choices: FimChoices;
    snippet_telemetry_id: number;
    model: string;
    context?: FIMContext;
    created?: number;
    elapsed?: number;
    cached?: boolean;
};

export declare interface FIMDebugReady extends FIMAction {
    type: FIM_EVENT_NAMES.READY;
}

declare type FimFile = {
    file_content: string;
    file_name: string;
    line1: number;
    line2: number;
};

export declare type FormatCellValue = (columnName: string, cellValue: string | number) => string | number;

export declare function getAtCommandCompletion(query: string, cursor: number, number: number, lspUrl?: string): Promise<CommandCompletionResponse>;

export declare function getAtCommandPreview(query: string, lspUrl?: string): Promise<ChatContextFileMessage[]>;

export declare function getCaps(lspUrl?: string): Promise<CapsResponse>;

export declare function getPrompts(lspUrl?: string): Promise<SystemPrompts>;

export declare function getStatisticData(lspUrl?: string): Promise<{
    data: string;
}>;

export declare function isAction(action: unknown): action is Actions;

export declare function isActionFromChat(action: unknown): action is ActionFromChat;

export declare function isActionFromSidebar(action: unknown): action is ActionFromSidebar;

export declare function isActionFromStatistic(action: unknown): action is ActionFromStatistic;

export declare function isActionToChat(action: unknown): action is ActionToChat;

export declare function isActionToSideBar(action: unknown): action is ActionsToSideBar;

export declare function isActionToStatistic(action: unknown): action is ActionToStatistic;

export declare function isActiveFileInfo(action: unknown): action is ActiveFileInfo;

export declare function isBackFromFIMDebug(action: unknown): action is FIMDebugBack;

export declare function isBackupMessages(action: unknown): action is BackUpMessages;

export declare function isCapsResponse(json: unknown): json is CapsResponse;

export declare function isChatClearError(action: unknown): action is ChatClearError;

export declare function isChatContextFileMessage(message: ChatMessage): message is ChatContextFileMessage;

export declare function isChatDoneStreaming(action: unknown): action is ChatDoneStreaming;

export declare function isChatErrorStreaming(action: unknown): action is ChatErrorStreaming;

export declare function isChatReceiveCaps(action: unknown): action is ChatReceiveCaps;

export declare function isChatReceiveCapsError(action: unknown): action is ChatReceiveCapsError;

export declare function isChatSetLastModelUsed(action: unknown): action is ChatSetLastModelUsed;

export declare function isChatUserMessageResponse(json: unknown): json is ChatUserMessageResponse;

export declare function isClearFIMDebugError(action: unknown): action is ClearFIMDebugError;

export declare function isCommandCompletionResponse(json: unknown): json is CommandCompletionResponse;

export declare function isCommandPreviewResponse(json: unknown): json is CommandPreviewResponse;

export declare function isCreateNewChat(action: unknown): action is CreateNewChatThread;

export declare function isCustomPromptsResponse(json: unknown): json is CustomPromptsResponse;

export declare function isDeleteChatHistory(action: unknown): action is DeleteHistoryItem;

export declare function isDetailMessage(json: unknown): json is DetailMessage;

export declare function isFIMAction(action: unknown): action is FIMAction;

export declare function isNewFileFromChat(action: unknown): action is NewFileFromChat;

export declare function isOpenChatInSidebar(action: unknown): action is OpenChatInSidebar;

export declare function isOpenChatInTab(action: unknown): action is OpenChatInTab;

export declare function isPasteDiffFromChat(action: unknown): action is PasteDiffFromChat;

export declare function isQuestionFromChat(action: unknown): action is QuestionFromChat;

export declare function isReadyMessage(action: unknown): action is ReadyMessage;

export declare function isReadyMessageFromFIMDebug(action: unknown): action is FIMDebugReady;

export declare function isReceiveAtCommandCompletion(action: unknown): action is ReceiveAtCommandCompletion;

export declare function isReceiveAtCommandPreview(action: unknown): action is ReceiveAtCommandPreview;

export declare function isReceiveChatHistory(action: unknown): action is ReceiveChatHistory;

export declare function isReceiveDataForStatistic(action: unknown): action is ReceiveDataForStatistic;

export declare function isReceiveDataForStatisticError(action: unknown): action is ReceiveDataForStatisticError;

export declare function isReceiveFIMDebugData(action: unknown): action is ReceiveFIMDebugData;

export declare function isReceiveFIMDebugError(action: unknown): action is ReceiveFIMDebugError;

export declare function isReceivePrompts(action: unknown): action is ReceivePrompts;

export declare function isReceivePromptsError(action: unknown): action is ReceivePromptsError;

export declare function isReceiveTokenCount(action: unknown): action is ReceiveTokenCount;

export declare function isRemovePreviewFileByName(action: unknown): action is RemovePreviewFileByName;

export declare function isRequestAtCommandCompletion(action: unknown): action is RequestAtCommandCompletion;

export declare function isRequestCapsFromChat(action: unknown): action is RequestCapsFromChat;

export declare function isRequestChatHistory(action: unknown): action is RequestChatHistory;

export declare function isRequestDataForStatistic(action: unknown): action is RequestDataForStatistic;

export declare function isRequestFIMData(action: unknown): action is RequestFIMData;

export declare function isRequestPreviewFiles(action: unknown): action is RequestPreviewFiles;

export declare function isRequestPrompts(action: unknown): action is RequestPrompts;

export declare function isResponseToChat(action: unknown): action is ResponseToChat;

export declare function isRestoreChat(action: unknown): action is RestoreChat;

export declare function isSaveChatFromChat(action: unknown): action is SaveChatFromChat;

export declare function isSetChatModel(action: unknown): action is SetChatModel;

export declare function isSetDisableChat(action: unknown): action is SetChatDisable;

export declare function isSetLoadingStatisticData(action: unknown): action is SetLoadingStatisticData;

export declare function isSetPreviousMessagesLength(action: unknown): action is setPreviousMessagesLength;

export declare function isSetSelectedAtCommand(action: unknown): action is SetSelectedAtCommand;

export declare function isSetSelectedSnippet(action: unknown): action is ChatSetSelectedSnippet;

export declare function isSetSelectedSystemPrompt(action: unknown): action is SetSelectedSystemPrompt;

export declare function isSetStatisticData(action: unknown): action is SetStatisticsData;

export declare function isSidebarReady(action: unknown): action is SidebarReady;

export declare function isStatisticDataResponse(json: unknown): json is {
    data: string;
};

export declare function isStopStreamingFromChat(action: unknown): action is StopStreamingFromChat;

export declare function isSystemPrompts(json: unknown): json is SystemPrompts;

export declare function isToggleActiveFile(action: unknown): action is ToggleActiveFile;

export declare function isUpdateConfigMessage(action: unknown): action is UpdateConfigMessage;

export declare function isUserMessage(message: ChatMessage): message is UserMessage;

export declare interface NewFileFromChat extends ActionFromChat {
    type: EVENT_NAMES_FROM_CHAT.NEW_FILE;
    payload: {
        id: string;
        content: string;
    };
}

export declare interface OpenChatInSidebar extends ActionFromSidebar {
    type: EVENT_NAMES_FROM_SIDE_BAR.OPEN_CHAT_IN_SIDEBAR;
    payload: {
        id: string;
    };
}

export declare interface OpenChatInTab extends ActionFromSidebar {
    type: EVENT_NAMES_FROM_SIDE_BAR.OPEN_IN_CHAT_IN_TAB;
    payload: {
        id: string;
    };
}

export declare interface PasteDiffFromChat extends ActionFromChat {
    type: EVENT_NAMES_FROM_CHAT.PASTE_DIFF;
    payload: {
        id: string;
        content: string;
    };
}

export declare interface QuestionFromChat extends ActionFromChat {
    type: EVENT_NAMES_FROM_CHAT.ASK_QUESTION;
    payload: ChatThread;
}

export declare interface ReadyMessage extends ActionFromChat {
    type: EVENT_NAMES_FROM_CHAT.READY;
    payload: {
        id: string;
    };
}

export declare interface ReceiveAtCommandCompletion extends ActionToChat {
    type: EVENT_NAMES_TO_CHAT.RECEIVE_AT_COMMAND_COMPLETION;
    payload: {
        id: string;
    } & CommandCompletionResponse;
}

export declare interface ReceiveAtCommandPreview extends ActionToChat {
    type: EVENT_NAMES_TO_CHAT.RECEIVE_AT_COMMAND_PREVIEW;
    payload: {
        id: string;
        preview: ChatContextFileMessage[];
    };
}

export declare interface ReceiveChatHistory extends ActionsToSideBar {
    type: EVENT_NAMES_TO_SIDE_BAR.RECEIVE_CHAT_HISTORY;
    payload: ChatHistoryItem[];
}

export declare interface ReceiveDataForStatistic extends ActionToStatistic {
    type: EVENT_NAMES_TO_STATISTIC.RECEIVE_STATISTIC_DATA;
    payload: {
        data: string;
    };
}

export declare interface ReceiveDataForStatisticError extends ActionToStatistic {
    type: EVENT_NAMES_TO_STATISTIC.RECEIVE_STATISTIC_DATA_ERROR;
    payload: {
        data: string;
        message: string;
    };
}

export declare interface ReceiveFIMDebugData extends FIMAction {
    type: FIM_EVENT_NAMES.DATA_RECEIVE;
    payload: FimDebugData;
}

export declare interface ReceiveFIMDebugError extends FIMAction {
    type: FIM_EVENT_NAMES.DATA_ERROR;
    payload: {
        message: string;
    };
}

export declare interface ReceivePrompts extends ActionToChat {
    type: EVENT_NAMES_TO_CHAT.RECEIVE_PROMPTS;
    payload: {
        id: string;
        prompts: SystemPrompts;
    };
}

export declare interface ReceivePromptsError extends ActionToChat {
    type: EVENT_NAMES_TO_CHAT.RECEIVE_PROMPTS_ERROR;
    payload: {
        id: string;
        error: string;
    };
}

export declare interface ReceiveTokenCount extends ActionToChat {
    type: EVENT_NAMES_TO_CHAT.RECEIVE_TOKEN_COUNT;
    payload: {
        id: string;
        tokens: number | null;
    };
}

export declare type RefactTableImpactDateObj = {
    completions: number;
    human: number;
    langs: string[];
    refact: number;
    refact_impact: number;
    total: number;
};

export declare type RefactTableImpactLanguagesRow = {
    [key in ColumnName]: string | number;
};

export declare interface RemovePreviewFileByName extends ActionToChat {
    type: EVENT_NAMES_TO_CHAT.REMOVE_PREVIEW_FILE_BY_NAME;
    payload: {
        id: string;
        name: string;
    };
}

export declare function render(element: HTMLElement, config: Config): void;

export declare function renderFIMDebug(element: HTMLElement, config: Config): void;

export declare function renderHistoryList(element: HTMLElement, config: Config): void;

export declare function renderStatistic(element: HTMLElement, config: Config): void;

export declare interface RequestAtCommandCompletion extends ActionFromChat {
    type: EVENT_NAMES_FROM_CHAT.REQUEST_AT_COMMAND_COMPLETION;
    payload: {
        id: string;
        query: string;
        cursor: number;
        number: number;
    };
}

export declare interface RequestCapsFromChat extends ActionFromChat {
    type: EVENT_NAMES_FROM_CHAT.REQUEST_CAPS;
    payload: {
        id: string;
    };
}

export declare interface RequestChatHistory extends ActionFromSidebar {
    type: EVENT_NAMES_FROM_SIDE_BAR.REQUEST_CHAT_HISTORY;
}

export declare interface RequestDataForStatistic extends ActionToStatistic {
    type: EVENT_NAMES_TO_STATISTIC.REQUEST_STATISTIC_DATA;
}

export declare interface RequestFIMData extends FIMAction {
    type: FIM_EVENT_NAMES.DATA_REQUEST;
}

export declare interface RequestPreviewFiles extends ActionFromChat {
    type: EVENT_NAMES_FROM_CHAT.REQUEST_PREVIEW_FILES;
    payload: {
        id: string;
        query: string;
    };
}

export declare interface RequestPrompts extends ActionFromChat {
    type: EVENT_NAMES_FROM_CHAT.REQUEST_PROMPTS;
    payload: {
        id: string;
    };
}

export declare interface ResponseToChat extends ActionToChat {
    type: EVENT_NAMES_TO_CHAT.CHAT_RESPONSE;
    payload: ChatResponse;
}

export declare interface RestoreChat extends ActionToChat {
    type: EVENT_NAMES_TO_CHAT.RESTORE_CHAT;
    payload: {
        id: string;
        chat: ChatThread & {
            messages: ChatThread["messages"] | [string, string][];
        };
        snippet?: Snippet;
    };
}

export declare interface SaveChatFromChat extends ActionFromChat {
    type: EVENT_NAMES_FROM_CHAT.SAVE_CHAT;
    payload: ChatThread;
}

export declare function sendChat(messages: ChatMessages, model: string, abortController: AbortController, lspUrl?: string): Promise<Response>;

export declare interface SetChatDisable extends ActionToChat {
    type: EVENT_NAMES_TO_CHAT.SET_DISABLE_CHAT;
    payload: {
        id: string;
        disable: boolean;
    };
}

export declare interface SetChatModel extends ActionToChat {
    type: EVENT_NAMES_TO_CHAT.SET_CHAT_MODEL;
    payload: {
        id: string;
        model: string;
    };
}

export declare interface SetLoadingStatisticData extends ActionToStatistic {
    type: EVENT_NAMES_TO_STATISTIC.SET_LOADING_STATISTIC_DATA;
}

export declare interface setPreviousMessagesLength extends ActionToChat {
    type: EVENT_NAMES_TO_CHAT.SET_PREVIOUS_MESSAGES_LENGTH;
    payload: {
        id: string;
        message_length: number;
    };
}

export declare interface SetSelectedAtCommand extends ActionToChat {
    type: EVENT_NAMES_TO_CHAT.SET_SELECTED_AT_COMMAND;
    payload: {
        id: string;
        command: string;
    };
}

export declare interface SetSelectedSystemPrompt extends ActionToChat {
    type: EVENT_NAMES_TO_CHAT.SET_SELECTED_SYSTEM_PROMPT;
    payload: {
        id: string;
        prompt: string;
    };
}

export declare interface SetStatisticsData extends ActionToStatistic {
    type: EVENT_NAMES_TO_STATISTIC.SET_STATISTIC_DATA;
    payload: StatisticData;
}

export declare interface SidebarReady extends ActionFromSidebar {
    type: EVENT_NAMES_FROM_SIDE_BAR.READY;
}

export declare type Snippet = {
    language: string;
    code: string;
    path: string;
    basename: string;
};

export declare type StatisticData = {
    refact_impact_dates: {
        data: {
            daily: Record<string, RefactTableImpactDateObj>;
            weekly: Record<string, RefactTableImpactDateObj>;
        };
    };
    table_refact_impact: {
        columns: string[];
        data: RefactTableImpactLanguagesRow[];
        title: string;
    };
};

export declare interface StopStreamingFromChat extends ActionFromChat {
    type: EVENT_NAMES_FROM_CHAT.STOP_STREAMING;
    payload: {
        id: string;
    };
}

export declare interface SystemMessage extends BaseMessage {
    0: "system";
    1: string;
}

export declare type SystemPrompt = {
    text: string;
    description: string;
};

export declare type SystemPrompts = Record<string, SystemPrompt>;

declare type ThemeProps = {
    children: JSX.Element;
    appearance?: "inherit" | "light" | "dark";
    accentColor?: "tomato" | "red" | "ruby" | "crimson" | "pink" | "plum" | "purple" | "violet" | "iris" | "indigo" | "blue" | "cyan" | "teal" | "jade" | "green" | "grass" | "brown" | "orange" | "sky" | "mint" | "lime" | "yellow" | "amber" | "gold" | "bronze" | "gray";
    grayColor?: "gray" | "mauve" | "slate" | "sage" | "olive" | "sand" | "auto";
    panelBackground?: "solid" | "translucent";
    radius?: "none" | "small" | "medium" | "large" | "full";
    scaling?: "90%" | "95%" | "100%" | "105%" | "110%";
};

export declare interface ToggleActiveFile extends ActionToChat {
    type: EVENT_NAMES_TO_CHAT.TOGGLE_ACTIVE_FILE;
    payload: {
        id: string;
        attach_file: boolean;
    };
}

export declare interface UpdateConfigMessage {
    type: EVENT_NAMES_TO_CONFIG.UPDATE;
    payload: Partial<Config>;
}

export declare interface UserMessage extends BaseMessage {
    0: "user";
    1: string;
}

export { }
