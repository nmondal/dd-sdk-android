/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.tracing.internal

import android.content.Context
import android.os.HandlerThread
import com.datadog.android.DatadogConfig
import com.datadog.android.DatadogEndpoint
import com.datadog.android.core.internal.data.upload.DataUploadHandlerThread
import com.datadog.android.core.internal.domain.NoOpPersistenceStrategy
import com.datadog.android.core.internal.domain.PersistenceStrategy
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.net.NoOpDataUploader
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.tracing.internal.domain.TracingFileStrategy
import com.datadog.android.tracing.internal.net.TracesOkHttpUploader
import datadog.opentracing.DDSpan
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.OkHttpClient

internal object TracesFeature {

    internal val initialized = AtomicBoolean(false)

    internal var clientToken: String = ""
    internal var endpointUrl: String = DatadogEndpoint.TRACES_US
    internal var serviceName: String = DatadogConfig.DEFAULT_SERVICE_NAME

    internal var persistenceStrategy: PersistenceStrategy<DDSpan> = NoOpPersistenceStrategy()
    internal var uploader: DataUploader = NoOpDataUploader()
    internal var uploadHandlerThread: HandlerThread = HandlerThread("NoOp")

    @Suppress("LongParameterList")
    fun initialize(
        appContext: Context,
        config: DatadogConfig.FeatureConfig,
        okHttpClient: OkHttpClient,
        networkInfoProvider: NetworkInfoProvider,
        userInfoProvider: UserInfoProvider,
        systemInfoProvider: SystemInfoProvider,
        timeProvider: TimeProvider
    ) {
        if (initialized.get()) {
            return
        }

        clientToken = config.clientToken
        endpointUrl = config.endpointUrl
        serviceName = config.serviceName
        val envSuffix = if (config.envName.isEmpty()) "" else ", \"env\": \"${config.envName}\""

        persistenceStrategy = TracingFileStrategy(
            appContext,
            timeProvider,
            networkInfoProvider,
            userInfoProvider,
            envSuffix = envSuffix
        )
        setupUploader(endpointUrl, okHttpClient, networkInfoProvider, systemInfoProvider)

        initialized.set(true)
    }

    fun stop() {
        if (initialized.get()) {
            uploadHandlerThread.quitSafely()

            persistenceStrategy = NoOpPersistenceStrategy()
            uploadHandlerThread = HandlerThread("Test")
            clientToken = ""
            endpointUrl = DatadogEndpoint.TRACES_US
            serviceName = DatadogConfig.DEFAULT_SERVICE_NAME

            initialized.set(false)
        }
    }

    // region Internal

    private fun setupUploader(
        endpointUrl: String,
        okHttpClient: OkHttpClient,
        networkInfoProvider: NetworkInfoProvider,
        systemInfoProvider: SystemInfoProvider
    ) {
        uploader = TracesOkHttpUploader(endpointUrl, clientToken, okHttpClient)

        uploadHandlerThread = DataUploadHandlerThread(
            TRACES_UPLOAD_THREAD_NAME,
            persistenceStrategy.getReader(),
            uploader,
            networkInfoProvider,
            systemInfoProvider
        )
        uploadHandlerThread.start()
    }

    // endregion

    // region Constants

    internal const val TRACES_UPLOAD_THREAD_NAME = "ddog-traces-upload"

    // endregion
}
