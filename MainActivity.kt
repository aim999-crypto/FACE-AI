package com.mahfuz.faceai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import android.view.View
import android.view.WindowManager
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.os.Build
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var web: WebView
    private var tts: TextToSpeech? = null
    private var speech: SpeechRecognizer? = null
    private val main = Handler(Looper.getMainLooper())
    private var pageReady = false
    private var pendingPermissionRequest: PermissionRequest? = null
    private lateinit var assetLoader: WebViewAssetLoader

    private val needed = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    private val requestCode = 410

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersive()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        tts = TextToSpeech(this, this)
        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        web = WebView(this)
        web.setBackgroundColor(0xFF000000.toInt())
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.settings.allowFileAccess = true
        web.settings.allowContentAccess = true
        web.settings.javaScriptCanOpenWindowsAutomatically = false
        web.settings.setSupportMultipleWindows(false)
        web.addJavascriptInterface(FaceBridge(), "FaceBridge")
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: android.webkit.WebResourceRequest): android.webkit.WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                pageReady = true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val cameraOk = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    val micOk = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    val allowed = request.resources.filter {
                        (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE && cameraOk) ||
                        (it == PermissionRequest.RESOURCE_AUDIO_CAPTURE && micOk)
                    }.toTypedArray()
                    if (allowed.isNotEmpty()) request.grant(allowed) else request.deny()
                }
            }
        }
        setContentView(web)

        if (needed.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermissions(needed, requestCode)
        } else {
            loadApp()
        }
    }

    private fun loadApp() {
        if (!pageReady) web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == this.requestCode) loadApp()
    }

    private fun enterImmersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    inner class FaceBridge {
        @JavascriptInterface fun speak(message: String) {
            main.post {
                val text = message.take(1200)
                val lang = if (text.any { it in '\u0980'..'\u09FF' }) Locale("bn", "BD") else Locale.US
                tts?.language = lang
                tts?.setSpeechRate(0.96f)
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "faceai-utterance")
                web.evaluateJavascript("window.faceSpeakingStarted && window.faceSpeakingStarted()", null)
            }
        }

        @JavascriptInterface fun startListening() {
            main.post { beginSpeechRecognition() }
        }

        @JavascriptInterface fun stopListening() {
            main.post { try { speech?.cancel() } catch (_: Exception) {} }
        }
    }

    private fun beginSpeechRecognition() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            jsError(); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { jsError(); return }
        try {
            speech?.destroy()
            speech = SpeechRecognizer.createSpeechRecognizer(this)
            speech?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) { jsError() }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (text.isNullOrBlank()) jsError() else {
                        val safe = org.json.JSONObject.quote(text)
                        web.evaluateJavascript("window.onNativeSpeechResult && window.onNativeSpeechResult($safe)", null)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speech?.startListening(intent)
        } catch (_: Exception) { jsError() }
    }

    private fun jsError() {
        web.evaluateJavascript("window.onNativeSpeechError && window.onNativeSpeechError()", null)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("bn", "BD")
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    main.post { web.evaluateJavascript("window.faceSpeakingEnded && window.faceSpeakingEnded()", null) }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    main.post { web.evaluateJavascript("window.faceSpeakingEnded && window.faceSpeakingEnded()", null) }
                }
            })
        }
    }

    override fun onDestroy() {
        try { speech?.destroy() } catch (_: Exception) {}
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        try { web.destroy() } catch (_: Exception) {}
        super.onDestroy()
    }
}
