package com.smallcloud.codify.struct

import com.google.gson.annotations.SerializedName
import com.intellij.util.xmlb.annotations.OptionTag
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.memberProperties


class SupportLanguages(regexStrings: List<String>) {
    private val regexes = regexStrings.map { it.replace("*", "\\w*").toRegex() }
    fun match(text: String): Boolean {
        return regexes.any { it.matches(text) }
    }
}

data class LongthinkFunctionEntry(
    var entryName: String = "",
    @OptionTag @SerializedName("function_name") var functionName: String = "",
    @OptionTag @SerializedName("label") var label: String = "",
    var intent: String = "",

    @OptionTag @SerializedName("supports_highlight") val supportHighlight: Boolean = true,
    @OptionTag @SerializedName("supports_selection") val supportSelection: Boolean = true,

    @OptionTag @SerializedName("selected_lines_min") val selectedLinesMin: Int = 0,
    @OptionTag @SerializedName("selected_lines_max") val selectedLinesMax: Int = 99999,

    @OptionTag @SerializedName("function_highlight") var functionHighlight: String? = null,
    @OptionTag @SerializedName("function_hl_click") var functionHlClick: String? = null,
    @OptionTag @SerializedName("function_selection") var functionSelection: String? = null,

    @OptionTag @SerializedName("catch_all_selection") var catchAllSelection: Boolean = false,
    @OptionTag @SerializedName("catch_all_hl") var catchAllHighlight: Boolean = false,
    @OptionTag @SerializedName("catch_question_mark") var catchQuestionMark: Boolean = false,

    @OptionTag @SerializedName("third_party") val thirdParty: Boolean = false,
    @OptionTag @SerializedName("mini_html") var miniHtml: String = "",

    @OptionTag val model: String? = null,
    @OptionTag @SerializedName("model_fixed_intent") var modelFixedIntent: String = "",

    @OptionTag val metering: Int = 0,
    @OptionTag var likes: Int = 0,
    @OptionTag @SerializedName("supports_languages") var supportsLanguages: SupportLanguages =
            SupportLanguages(listOf("*.*")),
    @OptionTag @SerializedName("is_liked") var isLiked: Boolean = false,
    @OptionTag @SerializedName("custom_intent") var customIntent: Boolean = false,
    var isBookmarked: Boolean = false
) {
    fun catchAny(): Boolean {
        return catchAllSelection || catchAllHighlight || catchQuestionMark
    }

    fun mergeLocalInfo(localInfo: LocalLongthinkInfo?): LongthinkFunctionEntry {
        if (localInfo == null) return this
        return this.copy().apply {
            localInfo::class.memberProperties.forEach { localField ->
                val property = this::class.memberProperties.find { it.name == localField.name }
                if (property is KMutableProperty<*>) {
                    property.setter.call(this, localField.getter.call(localInfo))
                }
            }
        }
    }
    fun mergeShortInfo(shorInfo: ShortLongthinkHistoryInfo?): LongthinkFunctionEntry {
        if (shorInfo == null) return this
        return this.copy().apply {
            shorInfo::class.memberProperties.forEach { localField ->
                val property = this::class.memberProperties.find { it.name == localField.name }
                if (property is KMutableProperty<*>) {
                    property.setter.call(this, localField.getter.call(shorInfo))
                }
            }
        }
    }
}