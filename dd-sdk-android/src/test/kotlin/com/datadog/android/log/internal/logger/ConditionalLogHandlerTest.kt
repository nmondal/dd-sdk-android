/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.logger

import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ConditionalLogHandlerTest {

    lateinit var testedHandler: LogHandler

    @Mock
    lateinit var mockHandler: LogHandler

    lateinit var fakeServiceName: String
    lateinit var fakeLoggerName: String
    lateinit var fakeMessage: String
    lateinit var fakeTags: Set<String>
    lateinit var fakeAttributes: Map<String, Any?>

    var fakeLevel: Int = 0

    var condition = false

    @Forgery
    lateinit var fakeThrowable: Throwable

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeServiceName = forge.anAlphabeticalString()
        fakeLoggerName = forge.anAlphabeticalString()
        fakeMessage = forge.anAlphabeticalString()
        fakeLevel = forge.anInt(2, 8)
        fakeAttributes = forge.aMap { anAlphabeticalString() to anInt() }
        fakeTags = forge.aList { anAlphabeticalString() }.toSet()

        testedHandler = ConditionalLogHandler(mockHandler) { _, _ ->
            condition
        }
    }

    @Test
    fun `forwards log (condition true)`() {
        condition = true

        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )

        verify(mockHandler).handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )
    }

    @Test
    fun `forwards log on background thread (condition true)`(forge: Forge) {
        condition = true
        val threadName = forge.anAlphabeticalString()
        val countDownLatch = CountDownLatch(1)
        val thread = Thread({
            testedHandler.handleLog(
                fakeLevel,
                fakeMessage,
                fakeThrowable,
                fakeAttributes,
                fakeTags
            )
            countDownLatch.countDown()
        }, threadName)

        thread.start()
        countDownLatch.await(1, TimeUnit.SECONDS)

        verify(mockHandler).handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )
    }

    @Test
    fun `forwards minimal log (condition true)`() {
        condition = true

        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            null,
            emptyMap(),
            emptySet()
        )

        verify(mockHandler).handleLog(
            fakeLevel,
            fakeMessage,
            null,
            emptyMap(),
            emptySet()
        )
    }

    @Test
    fun `forwards log (condition false)`() {
        condition = false

        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )

        verifyZeroInteractions(mockHandler)
    }

    @Test
    fun `forwards log on background thread (condition false)`(forge: Forge) {
        condition = false
        val threadName = forge.anAlphabeticalString()
        val countDownLatch = CountDownLatch(1)
        val thread = Thread({
            testedHandler.handleLog(
                fakeLevel,
                fakeMessage,
                fakeThrowable,
                fakeAttributes,
                fakeTags
            )
            countDownLatch.countDown()
        }, threadName)

        thread.start()
        countDownLatch.await(1, TimeUnit.SECONDS)

        verifyZeroInteractions(mockHandler)
    }

    @Test
    fun `forwards minimal log (condition false)`() {
        condition = false

        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            null,
            emptyMap(),
            emptySet()
        )

        verifyZeroInteractions(mockHandler)
    }
}
