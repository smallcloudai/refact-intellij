/*! js-cookie v3.0.5 | MIT */
function y(e) {
  for (var t = 1; t < arguments.length; t++) {
    var a = arguments[t];
    for (var i in a)
      e[i] = a[i];
  }
  return e;
}
var F = {
  read: function(e) {
    return e[0] === '"' && (e = e.slice(1, -1)), e.replace(/(%[\dA-F]{2})+/gi, decodeURIComponent);
  },
  write: function(e) {
    return encodeURIComponent(e).replace(
      /%(2[346BF]|3[AC-F]|40|5[BDE]|60|7[BCD])/g,
      decodeURIComponent
    );
  }
};
function g(e, t) {
  function a(r, f, s) {
    if (!(typeof document > "u")) {
      s = y({}, t, s), typeof s.expires == "number" && (s.expires = new Date(Date.now() + s.expires * 864e5)), s.expires && (s.expires = s.expires.toUTCString()), r = encodeURIComponent(r).replace(/%(2[346B]|5E|60|7C)/g, decodeURIComponent).replace(/[()]/g, escape);
      var o = "";
      for (var u in s)
        s[u] && (o += "; " + u, s[u] !== !0 && (o += "=" + s[u].split(";")[0]));
      return document.cookie = r + "=" + e.write(f, r) + o;
    }
  }
  function i(r) {
    if (!(typeof document > "u" || arguments.length && !r)) {
      for (var f = document.cookie ? document.cookie.split("; ") : [], s = {}, o = 0; o < f.length; o++) {
        var u = f[o].split("="), h = u.slice(1).join("=");
        try {
          var p = decodeURIComponent(u[0]);
          if (s[p] = e.read(h, p), r === p)
            break;
        } catch {
        }
      }
      return r ? s[r] : s;
    }
  }
  return Object.create(
    {
      set: a,
      get: i,
      remove: function(r, f) {
        a(
          r,
          "",
          y({}, f, {
            expires: -1
          })
        );
      },
      withAttributes: function(r) {
        return g(this.converter, y({}, this.attributes, r));
      },
      withConverter: function(r) {
        return g(y({}, this.converter, r), this.attributes);
      }
    },
    {
      attributes: { value: Object.freeze(t) },
      converter: { value: Object.freeze(e) }
    }
  );
}
var $ = g(F, { path: "/" });
const D = () => $.get("api_key") ?? "", R = "/v1/chat", C = "/v1/caps", b = "/v1/get-dashboard-plots", I = "/v1/at-command-completion", S = "/v1/at-command-preview", w = "/v1/customization";
function J(e) {
  return e[0] === "user";
}
function X(e) {
  return e[0] === "context_file";
}
function Z(e) {
  return !e || typeof e != "object" || !("id" in e) || !("content" in e) || !("role" in e) ? !1 : e.role === "user" || e.role === "context_file";
}
function M(e, t, a, i) {
  const r = e.map(([h, p]) => {
    const v = typeof p == "string" ? p : JSON.stringify(p);
    return { role: h, content: v };
  }), f = JSON.stringify({
    messages: r,
    model: t,
    parameters: {
      max_new_tokens: 1e3
    },
    stream: !0
  }), s = D(), o = {
    "Content-Type": "application/json",
    ...s ? { Authorization: "Bearer " + s } : {}
  }, u = i ? `${i.replace(/\/*$/, "")}${R}` : R;
  return fetch(u, {
    method: "POST",
    headers: o,
    body: f,
    redirect: "follow",
    cache: "no-cache",
    referrer: "no-referrer",
    signal: a.signal,
    credentials: "same-origin"
  });
}
async function H(e) {
  const t = e ? `${e.replace(/\/*$/, "")}${C}` : C, a = await fetch(t, {
    method: "GET",
    credentials: "same-origin",
    headers: {
      accept: "application/json"
    }
  });
  if (!a.ok)
    throw new Error(a.statusText);
  const i = await a.json();
  if (!P(i))
    throw new Error("Invalid response from caps");
  return i;
}
function B(e) {
  return !e || typeof e != "object" || !("data" in e) ? !1 : typeof e.data == "string";
}
async function V(e) {
  const t = e ? `${e.replace(/\/*$/, "")}${b}` : b, a = await fetch(t, {
    method: "GET",
    credentials: "same-origin",
    headers: {
      accept: "application/json"
    }
  });
  if (!a.ok)
    throw new Error(a.statusText);
  const i = await a.json();
  if (!B(i))
    throw new Error("Invalid response for statistic data");
  return i;
}
function P(e) {
  return !(!e || typeof e != "object" || !("code_chat_default_model" in e) || typeof e.code_chat_default_model != "string" || !("code_chat_models" in e));
}
function G(e) {
  return !(!e || typeof e != "object" || !("completions" in e) || !("replace" in e) || !("is_cmd_executable" in e));
}
function m(e) {
  return !(!e || typeof e != "object" || !("detail" in e));
}
async function N(e, t, a, i) {
  const r = i ? `${i.replace(/\/*$/, "")}${I}` : I, f = await fetch(r, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ query: e, cursor: t, top_n: a })
  });
  if (!f.ok)
    throw new Error(f.statusText);
  const s = await f.json();
  if (!G(s) && !m(s))
    throw new Error("Invalid response from completion");
  return m(s) ? {
    completions: [],
    replace: [0, 0],
    is_cmd_executable: !1
  } : s;
}
function K(e) {
  if (!e || typeof e != "object" || !("messages" in e) || !Array.isArray(e.messages))
    return !1;
  if (!e.messages.length)
    return !0;
  const t = e.messages[0];
  return !(!t || typeof t != "object" || !("role" in t) || t.role !== "context_file" || !("content" in t) || typeof t.content != "string");
}
async function ee(e, t) {
  const a = t ? `${t.replace(/\/*$/, "")}${S}` : S, i = await fetch(a, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    redirect: "follow",
    cache: "no-cache",
    referrer: "no-referrer",
    credentials: "same-origin",
    body: JSON.stringify({ query: e })
  });
  if (!i.ok)
    throw new Error(i.statusText);
  const r = await i.json();
  if (!K(r) && !m(r))
    throw new Error("Invalid response from command preview");
  return m(r) ? [] : r.messages.map(
    ({ role: s, content: o }) => {
      const u = JSON.parse(o);
      return [s, u];
    }
  );
}
function Q(e) {
  return !(!e || typeof e != "object" || !("text" in e) || !("description" in e));
}
function L(e) {
  if (!e || typeof e != "object")
    return !1;
  for (const t of Object.values(e))
    if (!Q(t))
      return !1;
  return !0;
}
function T(e) {
  return !e || typeof e != "object" || !("system_prompts" in e) || typeof e.system_prompts != "object" || e.system_prompts === null ? !1 : L(e.system_prompts);
}
async function te(e) {
  const t = e ? `${e.replace(/\/*$/, "")}${w}` : w, a = D(), i = await fetch(t, {
    method: "GET",
    credentials: "same-origin",
    redirect: "follow",
    cache: "no-cache",
    referrer: "no-referrer",
    headers: {
      accept: "application/json",
      ...a ? { Authorization: "Bearer " + a } : {}
    }
  });
  if (!i.ok)
    throw new Error(i.statusText);
  const r = await i.json();
  return T(r) ? r.system_prompts : {};
}
var E = /* @__PURE__ */ ((e) => (e.SAVE_CHAT = "save_chat_to_history", e.ASK_QUESTION = "chat_question", e.REQUEST_CAPS = "chat_request_caps", e.STOP_STREAMING = "chat_stop_streaming", e.BACK_FROM_CHAT = "chat_back_from_chat", e.OPEN_IN_CHAT_IN_TAB = "open_chat_in_new_tab", e.SEND_TO_SIDE_BAR = "chat_send_to_sidebar", e.READY = "chat_ready", e.NEW_FILE = "chat_create_new_file", e.PASTE_DIFF = "chat_paste_diff", e.REQUEST_AT_COMMAND_COMPLETION = "chat_request_at_command_completion", e.REQUEST_PREVIEW_FILES = "chat_request_preview_files", e.REQUEST_PROMPTS = "chat_request_prompts", e))(E || {}), A = /* @__PURE__ */ ((e) => (e.CLEAR_ERROR = "chat_clear_error", e.RESTORE_CHAT = "restore_chat_from_history", e.CHAT_RESPONSE = "chat_response", e.BACKUP_MESSAGES = "back_up_messages", e.DONE_STREAMING = "chat_done_streaming", e.ERROR_STREAMING = "chat_error_streaming", e.NEW_CHAT = "create_new_chat", e.RECEIVE_CAPS = "receive_caps", e.RECEIVE_CAPS_ERROR = "receive_caps_error", e.SET_CHAT_MODEL = "chat_set_chat_model", e.SET_DISABLE_CHAT = "set_disable_chat", e.ACTIVE_FILE_INFO = "chat_active_file_info", e.TOGGLE_ACTIVE_FILE = "chat_toggle_active_file", e.RECEIVE_AT_COMMAND_COMPLETION = "chat_receive_at_command_completion", e.RECEIVE_AT_COMMAND_PREVIEW = "chat_receive_at_command_preview", e.SET_SELECTED_AT_COMMAND = "chat_set_selected_command", e.SET_LAST_MODEL_USED = "chat_set_last_model_used", e.SET_SELECTED_SNIPPET = "chat_set_selected_snippet", e.REMOVE_PREVIEW_FILE_BY_NAME = "chat_remove_file_from_preview", e.SET_PREVIOUS_MESSAGES_LENGTH = "chat_set_previous_messages_length", e.RECEIVE_TOKEN_COUNT = "chat_set_tokens", e.RECEIVE_PROMPTS = "chat_receive_prompts", e.RECEIVE_PROMPTS_ERROR = "chat_receive_prompts_error", e.SET_SELECTED_SYSTEM_PROMPT = "chat_set_selected_system_prompt", e))(A || {});
function re(e) {
  return c(e) ? e.type === "chat_ready" : !1;
}
function c(e) {
  if (!e || typeof e != "object" || !("type" in e) || typeof e.type != "string")
    return !1;
  const t = { ...E };
  return Object.values(t).includes(e.type);
}
function se(e) {
  return c(e) ? e.type === "chat_request_prompts" : !1;
}
function ne(e) {
  return c(e) ? e.type === "chat_request_at_command_completion" : !1;
}
function ie(e) {
  return c(e) ? e.type === "chat_request_preview_files" : !1;
}
function ae(e) {
  return c(e) ? e.type === "chat_create_new_file" : !1;
}
function fe(e) {
  return c(e) ? e.type === "chat_paste_diff" : !1;
}
function oe(e) {
  return j(e) ? e.type === "chat_question" : !1;
}
function ue(e) {
  return j(e) ? e.type === "save_chat_to_history" : !1;
}
function ce(e) {
  return c(e) ? e.type === "chat_request_caps" : !1;
}
function pe(e) {
  return c(e) ? e.type === "chat_stop_streaming" : !1;
}
function n(e) {
  if (!e || typeof e != "object" || !("type" in e) || typeof e.type != "string")
    return !1;
  const t = { ...A };
  return Object.values(t).includes(e.type);
}
function le(e) {
  return n(e) ? e.type === "chat_set_selected_system_prompt" : !1;
}
function _e(e) {
  return !n(e) || e.type !== "chat_receive_prompts" || !("payload" in e) || typeof e.payload != "object" || !("prompts" in e.payload) ? !1 : L(e.payload.prompts);
}
function de(e) {
  return !(!n(e) || e.type !== "chat_receive_prompts_error" || !("payload" in e) || typeof e.payload != "object" || !("id" in e.payload) || typeof e.payload.id != "string" || !("error" in e.payload) || typeof e.payload.error != "string");
}
function ye(e) {
  return n(e) ? e.type === "chat_receive_at_command_completion" : !1;
}
function me(e) {
  return n(e) ? e.type === "chat_receive_at_command_preview" : !1;
}
function he(e) {
  return n(e) ? e.type === "chat_set_selected_command" : !1;
}
function ge(e) {
  return n(e) ? e.type === "chat_toggle_active_file" : !1;
}
function ve(e) {
  return n(e) ? e.type === "chat_active_file_info" : !1;
}
function Re(e) {
  return n(e) ? e.type === "set_disable_chat" : !1;
}
function Ce(e) {
  return n(e) ? e.type === "chat_set_chat_model" : !1;
}
function be(e) {
  return n(e) ? e.type === "chat_response" : !1;
}
function Ie(e) {
  return n(e) ? e.type === "back_up_messages" : !1;
}
function Se(e) {
  return n(e) ? e.type === "restore_chat_from_history" : !1;
}
function we(e) {
  return n(e) ? e.type === "create_new_chat" : !1;
}
function De(e) {
  return n(e) ? e.type === "chat_done_streaming" : !1;
}
function Pe(e) {
  return !(!n(e) || e.type !== "chat_error_streaming" || !("payload" in e) || typeof e.payload != "object" || !("id" in e.payload) || typeof e.payload.id != "string" || !("message" in e.payload) || typeof e.payload.message != "string");
}
function Le(e) {
  return n(e) ? e.type === "chat_clear_error" : !1;
}
function Ee(e) {
  return !n(e) || !("payload" in e) || typeof e.payload != "object" || !("caps" in e.payload) || !P(e.payload.caps) ? !1 : e.type === "receive_caps";
}
function Ae(e) {
  return n(e) ? e.type === "receive_caps_error" : !1;
}
function j(e) {
  return c(e) || n(e);
}
function je(e) {
  return n(e) ? e.type === "chat_set_last_model_used" : !1;
}
function Oe(e) {
  return n(e) ? e.type === "chat_set_selected_snippet" : !1;
}
function Ue(e) {
  return n(e) && e.type === "chat_remove_file_from_preview";
}
function qe(e) {
  return n(e) ? e.type === "chat_set_previous_messages_length" : !1;
}
function ke(e) {
  return n(e) ? e.type === "chat_set_tokens" : !1;
}
var O = /* @__PURE__ */ ((e) => (e.RECEIVE_CHAT_HISTORY = "sidebar_receive_chat_history", e))(O || {});
function Y(e) {
  if (!e || typeof e != "object" || !("type" in e) || typeof e.type != "string")
    return !1;
  const t = {
    ...O
  };
  return Object.values(t).includes(e.type);
}
function xe(e) {
  return Y(e) ? e.type === "sidebar_receive_chat_history" : !1;
}
var U = /* @__PURE__ */ ((e) => (e.READY = "sidebar_ready", e.OPEN_CHAT_IN_SIDEBAR = "sidebar_open_chat_in_sidebar", e.OPEN_IN_CHAT_IN_TAB = "sidebar_open_chat_in_tab", e.DELETE_HISTORY_ITEM = "sidebar_delete_history_item", e.REQUEST_CHAT_HISTORY = "sidebar_request_chat_history", e))(U || {});
function _(e) {
  if (!e || typeof e != "object" || !("type" in e) || typeof e.type != "string")
    return !1;
  const t = {
    ...U
  };
  return Object.values(t).includes(e.type);
}
function Fe(e) {
  return _(e) ? e.type === "sidebar_ready" : !1;
}
function $e(e) {
  return _(e) && e.type === "sidebar_open_chat_in_sidebar";
}
function Be(e) {
  return _(e) ? e.type === "sidebar_open_chat_in_tab" : !1;
}
function Ge(e) {
  return _(e) ? e.type === "sidebar_delete_history_item" : !1;
}
function Ke(e) {
  return _(e) ? e.type === "sidebar_request_chat_history" : !1;
}
var q = /* @__PURE__ */ ((e) => (e.BACK_FROM_STATISTIC = "back_from_statistic", e))(q || {}), k = /* @__PURE__ */ ((e) => (e.REQUEST_STATISTIC_DATA = "request_statistic_data", e.RECEIVE_STATISTIC_DATA = "receive_statistic_data", e.RECEIVE_STATISTIC_DATA_ERROR = "receive_statistic_data_error", e.SET_LOADING_STATISTIC_DATA = "set_loading_statistic_data", e.SET_STATISTIC_DATA = "set_statistic_data", e))(k || {});
function Qe(e) {
  if (!e || typeof e != "object" || !("type" in e) || typeof e.type != "string")
    return !1;
  const t = {
    ...q
  };
  return Object.values(t).includes(e.type);
}
function d(e) {
  if (!e || typeof e != "object" || !("type" in e) || typeof e.type != "string")
    return !1;
  const t = {
    ...k
  };
  return Object.values(t).includes(e.type);
}
function Te(e) {
  return d(e) ? e.type === "request_statistic_data" : !1;
}
function Ye(e) {
  return !(!d(e) || e.type !== "receive_statistic_data" || !("payload" in e) || !e.payload || typeof e.payload != "object" || !("data" in e.payload) || typeof e.payload.data != "string");
}
function We(e) {
  return d(e) ? e.type === "receive_statistic_data_error" : !1;
}
function ze(e) {
  return d(e) ? e.type === "set_statistic_data" : !1;
}
function Je(e) {
  return d(e) ? e.type === "set_loading_statistic_data" : !1;
}
var W = /* @__PURE__ */ ((e) => (e.UPDATE = "receive_config_update", e))(W || {});
function Xe(e) {
  return !(!e || typeof e != "object" || !("type" in e) || e.type !== "receive_config_update" || !("payload" in e) || typeof e.payload != "object");
}
var x = /* @__PURE__ */ ((e) => (e.DATA_REQUEST = "fim_debug_data_request", e.DATA_RECEIVE = "fim_debug_data_receive", e.DATA_ERROR = "fim_debug_data_error", e.READY = "fim_debug_ready", e.CLEAR_ERROR = "fim_debug_clear_error", e.BACK = "fim_debug_back", e))(x || {});
const z = Object.values(x);
function l(e) {
  return !e || typeof e != "object" || !("type" in e) || typeof e.type != "string" ? !1 : z.includes(e.type);
}
function Ze(e) {
  return l(e) ? e.type === "fim_debug_ready" : !1;
}
function Me(e) {
  return l(e) ? e.type === "fim_debug_data_request" : !1;
}
function He(e) {
  return l(e) ? e.type === "fim_debug_clear_error" : !1;
}
function Ve(e) {
  return l(e) ? e.type === "fim_debug_data_receive" : !1;
}
function Ne(e) {
  return !l(e) || e.type !== "fim_debug_data_error" || !("payload" in e) || typeof e.payload != "object" || e.payload === null || !("message" in e.payload) ? !1 : typeof e.payload.message == "string";
}
function et(e) {
  return l(e) ? e.type === "fim_debug_back" : !1;
}
export {
  E as EVENT_NAMES_FROM_CHAT,
  U as EVENT_NAMES_FROM_SIDE_BAR,
  q as EVENT_NAMES_FROM_STATISTIC,
  A as EVENT_NAMES_TO_CHAT,
  W as EVENT_NAMES_TO_CONFIG,
  O as EVENT_NAMES_TO_SIDE_BAR,
  k as EVENT_NAMES_TO_STATISTIC,
  x as FIM_EVENT_NAMES,
  N as getAtCommandCompletion,
  ee as getAtCommandPreview,
  H as getCaps,
  te as getPrompts,
  V as getStatisticData,
  j as isAction,
  c as isActionFromChat,
  _ as isActionFromSidebar,
  Qe as isActionFromStatistic,
  n as isActionToChat,
  Y as isActionToSideBar,
  d as isActionToStatistic,
  ve as isActiveFileInfo,
  et as isBackFromFIMDebug,
  Ie as isBackupMessages,
  P as isCapsResponse,
  Le as isChatClearError,
  X as isChatContextFileMessage,
  De as isChatDoneStreaming,
  Pe as isChatErrorStreaming,
  Ee as isChatReceiveCaps,
  Ae as isChatReceiveCapsError,
  je as isChatSetLastModelUsed,
  Z as isChatUserMessageResponse,
  He as isClearFIMDebugError,
  G as isCommandCompletionResponse,
  K as isCommandPreviewResponse,
  we as isCreateNewChat,
  T as isCustomPromptsResponse,
  Ge as isDeleteChatHistory,
  m as isDetailMessage,
  l as isFIMAction,
  ae as isNewFileFromChat,
  $e as isOpenChatInSidebar,
  Be as isOpenChatInTab,
  fe as isPasteDiffFromChat,
  oe as isQuestionFromChat,
  re as isReadyMessage,
  Ze as isReadyMessageFromFIMDebug,
  ye as isReceiveAtCommandCompletion,
  me as isReceiveAtCommandPreview,
  xe as isReceiveChatHistory,
  Ye as isReceiveDataForStatistic,
  We as isReceiveDataForStatisticError,
  Ve as isReceiveFIMDebugData,
  Ne as isReceiveFIMDebugError,
  _e as isReceivePrompts,
  de as isReceivePromptsError,
  ke as isReceiveTokenCount,
  Ue as isRemovePreviewFileByName,
  ne as isRequestAtCommandCompletion,
  ce as isRequestCapsFromChat,
  Ke as isRequestChatHistory,
  Te as isRequestDataForStatistic,
  Me as isRequestFIMData,
  ie as isRequestPreviewFiles,
  se as isRequestPrompts,
  be as isResponseToChat,
  Se as isRestoreChat,
  ue as isSaveChatFromChat,
  Ce as isSetChatModel,
  Re as isSetDisableChat,
  Je as isSetLoadingStatisticData,
  qe as isSetPreviousMessagesLength,
  he as isSetSelectedAtCommand,
  Oe as isSetSelectedSnippet,
  le as isSetSelectedSystemPrompt,
  ze as isSetStatisticData,
  Fe as isSidebarReady,
  B as isStatisticDataResponse,
  pe as isStopStreamingFromChat,
  L as isSystemPrompts,
  ge as isToggleActiveFile,
  Xe as isUpdateConfigMessage,
  J as isUserMessage,
  M as sendChat
};
