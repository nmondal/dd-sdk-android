package com.datadog.android.sdk.utils

internal fun String.isTracesUrl(): Boolean {
    return this.endsWith("traces")
}

internal fun String.isLogsUrl(): Boolean {
    return this.matches(Regex("(.*)/logs/(.*)"))
}
