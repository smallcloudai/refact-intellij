package com.smallcloud.codify.utils

import org.jetbrains.annotations.NotNull

fun dispatch(@NotNull runnable: Runnable) {
    runnable.run()
}