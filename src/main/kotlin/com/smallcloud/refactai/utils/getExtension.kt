package com.smallcloud.refactai.utils

fun getExtension(fileName: String): String {
    val dotIndex = fileName.lastIndexOf(".")
    return fileName.substring(dotIndex + 1)
}