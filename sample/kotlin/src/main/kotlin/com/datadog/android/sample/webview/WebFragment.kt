/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */
package com.datadog.android.sample.webview

import android.annotation.TargetApi
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.datadog.android.log.Logger
import com.datadog.android.sample.BuildConfig
import com.datadog.android.sample.MainActivity
import com.datadog.android.sample.R
import io.opentracing.Scope
import io.opentracing.Span
import io.opentracing.util.GlobalTracer

class WebFragment : Fragment() {
    private lateinit var viewModel: WebViewModel
    private lateinit var webView: WebView

    lateinit var mainScope: Scope
    lateinit var mainSpan: Span

    private val logger: Logger by lazy {
        Logger.Builder()
            .setServiceName("android-sample-kotlin")
            .setLoggerName("web_fragment")
            .setNetworkInfoEnabled(true)
            .setLogcatLogsEnabled(true)
            .build()
            .apply {
                addTag("flavor", BuildConfig.FLAVOR)
                addTag("build_type", BuildConfig.BUILD_TYPE)
            }
    }

    // region Fragment Lifecycle

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_web, container, false)
        webView = rootView.findViewById(R.id.webview)
        webView.webViewClient = mWebViewClient
        webView.webChromeClient = webChromeClient
        return rootView
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(WebViewModel::class.java)
    }

    override fun onStart() {
        super.onStart()
        webView.loadUrl(viewModel.url)
    }

    override fun onResume() {
        val tracer = GlobalTracer.get()
        val mainActivitySpan = (activity as MainActivity).mainSpan
        mainSpan = tracer
            .buildSpan("WebViewFragment").asChildOf(mainActivitySpan).start()
        mainScope = tracer.activateSpan(mainSpan)
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        mainScope.close()
        mainSpan.finish()
    }

    // endregion

    // region WebViewClient

    private val mWebViewClient: WebViewClient = object : WebViewClient() {
        var onPageStartedOnPageClosedSpan: Span? = null
        override fun onPageStarted(
            view: WebView,
            url: String,
            favicon: Bitmap?
        ) {
            val tracer = GlobalTracer.get()
            onPageStartedOnPageClosedSpan = tracer
                .buildSpan("WebViewInitialisation")
                .asChildOf(tracer.activeSpan())
                .start()
            super.onPageStarted(view, url, favicon)
            logger.d(
                "onPageStarted",
                null,
                mapOf("http.url" to url)
            )
        }

        override fun onPageFinished(
            view: WebView,
            url: String
        ) {
            super.onPageFinished(view, url)
            logger.d(
                "onPageFinished",
                null,
                mapOf("http.url" to url)
            )
            onPageStartedOnPageClosedSpan?.finish()
        }

        override fun onLoadResource(
            view: WebView,
            url: String
        ) {
            super.onLoadResource(view, url)
            logger.d(
                "loading resource",
                null,
                mapOf("http.url" to url)
            )
        }

        @TargetApi(Build.VERSION_CODES.M)
        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            super.onReceivedError(view, request, error)
            onPageStartedOnPageClosedSpan?.log(
                "Received error: ${error.errorCode} for request: ${request.url}"
            )
            logger.e(
                "received error",
                null,
                mapOf(
                    "http.request.method" to request.method,
                    "http.url" to request.url,
                    "http.error.code" to error.errorCode,
                    "http.error.description" to error.description
                )
            )
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            onPageStartedOnPageClosedSpan?.log(
                "Received error: ${errorResponse.reasonPhrase}  for request: ${request.url}"
            )
            logger.e(
                "received HTTP error",
                null,
                mapOf(
                    "http.request.method" to request.method,
                    "http.url" to request.url,
                    "http.error.code" to errorResponse.statusCode,
                    "http.error.description" to errorResponse.reasonPhrase
                )
            )
        }

        override fun onReceivedSslError(
            view: WebView,
            handler: SslErrorHandler,
            error: SslError
        ) {
            super.onReceivedSslError(view, handler, error)
            onPageStartedOnPageClosedSpan?.log(
                "Received ssl error: ${error.primaryError} for request: ${error.url}"
            )

            logger.e(
                "received SSL error",
                null,
                mapOf(
                    "http.url" to error.url,
                    "http.error.code" to error.primaryError
                )
            )
        }
    }

    // endregion

    // region WebChromeClient

    private val webChromeClient: WebChromeClient = object : WebChromeClient() {

        override fun onProgressChanged(
            view: WebView,
            newProgress: Int
        ) {
            super.onProgressChanged(view, newProgress)
            logger.v("onProgressChanged")
        }

        override fun onReceivedTitle(
            view: WebView,
            title: String
        ) {
            super.onReceivedTitle(view, title)
            logger.v(
                "onReceivedTitle",
                null,
                mapOf("webview.title" to title)
            )
        }

        override fun onReceivedIcon(
            view: WebView,
            icon: Bitmap
        ) {
            super.onReceivedIcon(view, icon)
            logger.v("event: onReceivedIcon")
        }

        override fun onJsAlert(
            view: WebView,
            url: String,
            message: String,
            result: JsResult
        ): Boolean {
            logger.w(
                "onJsAlert",
                null,
                mapOf(
                    "http.url" to url,
                    "webview.alert.message" to message,
                    "webview.alert.result" to result
                )
            )
            return super.onJsAlert(view, url, message, result)
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            val level = when (consoleMessage.messageLevel()) {
                ConsoleMessage.MessageLevel.TIP -> Log.INFO
                ConsoleMessage.MessageLevel.LOG -> Log.VERBOSE
                ConsoleMessage.MessageLevel.WARNING -> Log.WARN
                ConsoleMessage.MessageLevel.ERROR -> Log.ERROR
                ConsoleMessage.MessageLevel.DEBUG,
                null -> Log.DEBUG
            }
            logger.log(
                level,
                "onConsoleMessage",
                null,
                mapOf("webview.console.message" to consoleMessage.message())
            )
            return super.onConsoleMessage(consoleMessage)
        }
    }

    // region

    companion object {
        fun newInstance(): WebFragment {
            return WebFragment()
        }
    }
}
