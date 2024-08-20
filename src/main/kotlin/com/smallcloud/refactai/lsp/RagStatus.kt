package com.smallcloud.refactai.lsp

import com.google.gson.annotations.SerializedName


data class RagStatus(
    @SerializedName("ast") val ast: AstStatus? = null,
    @SerializedName("ast_alive") val astAlive: String? = null,
    @SerializedName("vecdb") val vecdb: VecDbStatus? = null,
    @SerializedName("vecdb_alive") val vecdbAlive: String? = null,
    @SerializedName("vec_db_error") val vecDbError: String
)

data class AstStatus(
    @SerializedName("files_unparsed") val filesUnparsed: Int,
    @SerializedName("files_total") val filesTotal: Int,
    @SerializedName("ast_index_files_total") val astIndexFilesTotal: Int,
    @SerializedName("ast_index_symbols_total") val astIndexSymbolsTotal: Int,
    @SerializedName("state") val state: String,
    @SerializedName("ast_max_files_hit") val astMaxFilesHit: Boolean
)

data class VecDbStatus(
    @SerializedName("files_unprocessed") val filesUnprocessed: Int,
    @SerializedName("files_total") val filesTotal: Int,
    @SerializedName("requests_made_since_start") val requestsMadeSinceStart: Int,
    @SerializedName("vectors_made_since_start") val vectorsMadeSinceStart: Int,
    @SerializedName("db_size") val dbSize: Int,
    @SerializedName("db_cache_size") val dbCacheSize: Int,
    @SerializedName("state") val state: String,
    @SerializedName("vecdb_max_files_hit") val vecdbMaxFilesHit: Boolean
)