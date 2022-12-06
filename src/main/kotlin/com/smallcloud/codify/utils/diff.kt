package com.smallcloud.codify.utils

import org.apache.commons.lang.StringUtils

fun difference(str1: String?, str2: String?): String? {
    return if (str1 == null) {
        str2
    } else if (str2 == null) {
        str1
    } else {
        val at = StringUtils.indexOfDifference(str1, str2)
        if (at == -1) return ""
        val str1 = str1.substring(at)
        val str2 = str2.substring(at)
        val at2 = str2.indexOf(str1)
        if (at2 > 0)
            return str2.substring(0, at2)
        else
            return str2
    }
}
