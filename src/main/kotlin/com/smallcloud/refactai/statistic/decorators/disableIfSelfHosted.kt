package com.smallcloud.refactai.statistic.decorators

fun disableIfSelfHosted(f: () -> Unit) {
//    if (InferenceGlobalContext.isCloud) {
        f()
//    }
}