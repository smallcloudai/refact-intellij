package com.smallcloud.refact.modes.completion.structs


fun String.getChar(index: Int): Char? {
    if (isEmpty()) return null
    return if (index < 0) {
        this[length + index]
    } else {
        this[index]
    }
}