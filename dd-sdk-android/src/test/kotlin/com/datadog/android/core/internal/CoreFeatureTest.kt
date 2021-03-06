/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.core.internal

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import com.datadog.android.core.internal.net.info.BroadcastReceiverNetworkInfoProvider
import com.datadog.android.core.internal.net.info.CallbackNetworkInfoProvider
import com.datadog.android.core.internal.system.BroadcastReceiverSystemInfoProvider
import com.datadog.android.core.internal.time.NoOpTimeProvider
import com.datadog.android.log.assertj.containsInstanceOf
import com.datadog.android.log.internal.user.NoOpUserInfoProvider
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.extensions.SystemStreamExtension
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.isA
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.ConnectionSpec
import okhttp3.Protocol
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class),
    ExtendWith(SystemStreamExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CoreFeatureTest {

    lateinit var mockAppContext: Application
    @Mock
    lateinit var mockConnectivityMgr: ConnectivityManager
    lateinit var fakePackageName: String
    lateinit var fakePackageVersion: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakePackageName = forge.anAlphabeticalString()
        fakePackageVersion = forge.aStringMatching("\\d(\\.\\d){3}")

        mockAppContext = mockContext(fakePackageName, fakePackageVersion)
        whenever(mockAppContext.applicationContext) doReturn mockAppContext
        whenever(mockAppContext.getSystemService(Context.CONNECTIVITY_SERVICE))
            .doReturn(mockConnectivityMgr)
    }

    @AfterEach
    fun `tear down`() {
        CoreFeature.stop()
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun `registers broadcast receivers on initialize (Lollipop)`() {
        CoreFeature.initialize(mockAppContext, false)

        val broadcastReceiverCaptor = argumentCaptor<BroadcastReceiver>()
        verify(mockAppContext, times(3)).registerReceiver(broadcastReceiverCaptor.capture(), any())

        assertThat(broadcastReceiverCaptor.allValues)
            .containsInstanceOf(BroadcastReceiverNetworkInfoProvider::class.java)
            .containsInstanceOf(BroadcastReceiverSystemInfoProvider::class.java)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.N)
    fun `registers receivers and callbacks on initialize (Nougat)`() {
        CoreFeature.initialize(mockAppContext, false)

        val broadcastReceiverCaptor = argumentCaptor<BroadcastReceiver>()
        verify(mockAppContext, times(2)).registerReceiver(broadcastReceiverCaptor.capture(), any())
        assertThat(broadcastReceiverCaptor.allValues)
            .allMatch { it is BroadcastReceiverSystemInfoProvider }

        verify(mockConnectivityMgr)
            .registerDefaultNetworkCallback(isA<CallbackNetworkInfoProvider>())
    }

    @Test
    fun `initializes time provider`() {
        CoreFeature.initialize(mockAppContext, false)

        assertThat(CoreFeature.timeProvider)
            .isNotInstanceOf(NoOpTimeProvider::class.java)
    }

    @Test
    fun `initializes user info provider`() {
        CoreFeature.initialize(mockAppContext, false)

        assertThat(CoreFeature.userInfoProvider)
            .isNotInstanceOf(NoOpUserInfoProvider::class.java)
    }

    @Test
    fun `initializes app info`() {
        CoreFeature.initialize(mockAppContext, false)

        assertThat(CoreFeature.packageName).isEqualTo(fakePackageName)
        assertThat(CoreFeature.packageVersion).isEqualTo(fakePackageVersion)
    }

    @Test
    fun `initializes all dependencies at initialize with null version name`(
        @IntForgery(min = 0) versionCode: Int
    ) {
        mockAppContext = mockContext(fakePackageName, null, versionCode)
        whenever(mockAppContext.applicationContext) doReturn mockAppContext
        whenever(mockAppContext.getSystemService(Context.CONNECTIVITY_SERVICE))
            .doReturn(mockConnectivityMgr)

        CoreFeature.initialize(mockAppContext, false)

        assertThat(CoreFeature.packageName).isEqualTo(fakePackageName)
        assertThat(CoreFeature.packageVersion).isEqualTo(versionCode.toString())
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun `add strict network policy for https endpoints on 21+`(forge: Forge) {
        CoreFeature.initialize(mockAppContext, false)

        val okHttpClient = CoreFeature.okHttpClient
        assertThat(okHttpClient.protocols())
            .containsExactly(Protocol.HTTP_2, Protocol.HTTP_1_1)
        assertThat(okHttpClient.callTimeoutMillis())
            .isEqualTo(CoreFeature.NETWORK_TIMEOUT_MS.toInt())
        assertThat(okHttpClient.connectionSpecs())
            .containsExactly(ConnectionSpec.RESTRICTED_TLS)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.KITKAT)
    fun `add compatibility network policy for https endpoints on 19+`(forge: Forge) {
        CoreFeature.initialize(mockAppContext, false)

        val okHttpClient = CoreFeature.okHttpClient
        assertThat(okHttpClient.protocols())
            .containsExactly(Protocol.HTTP_2, Protocol.HTTP_1_1)
        assertThat(okHttpClient.callTimeoutMillis())
            .isEqualTo(CoreFeature.NETWORK_TIMEOUT_MS.toInt())
        assertThat(okHttpClient.connectionSpecs())
            .containsExactly(ConnectionSpec.MODERN_TLS)
    }

    @Test
    fun `no network policy for custom endpoints`(forge: Forge) {
        CoreFeature.initialize(mockAppContext, true)

        val okHttpClient = CoreFeature.okHttpClient
        assertThat(okHttpClient.protocols())
            .containsExactly(Protocol.HTTP_2, Protocol.HTTP_1_1)
        assertThat(okHttpClient.callTimeoutMillis())
            .isEqualTo(CoreFeature.NETWORK_TIMEOUT_MS.toInt())
        assertThat(okHttpClient.connectionSpecs())
            .containsExactly(ConnectionSpec.CLEARTEXT)
    }
}
