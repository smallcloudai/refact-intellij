package com.smallcloud.refactai.statistic.decorators

import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext

fun disableIfSelfHosted(f: () -> Unit) {
    if (InferenceGlobalContext.isCloud) {
        f()
    }
}