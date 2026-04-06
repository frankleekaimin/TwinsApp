package com.example.twinsapp

import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @Volatile private var chatOpen = false

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris: Array<Uri>? = if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            when {
                data?.clipData != null -> {
                    val clip = data.clipData!!
                    Array(clip.itemCount) { clip.getItemAt(it).uri }
                }
                data?.data != null -> arrayOf(data.data!!)
                else -> WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
            }
        } else null
        fileChooserCallback?.onReceiveValue(uris)
        fileChooserCallback = null
    }

    inner class TwinsAppBridge {
        @JavascriptInterface
        fun setChatOpen(open: Boolean) { chatOpen = open }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        android.webkit.WebView.setWebContentsDebuggingEnabled(true)

        webView = findViewById(R.id.webview)
        webView.addJavascriptInterface(TwinsAppBridge(), "TwinsApp")

        webView.settings.apply {
            javaScriptEnabled = true
            userAgentString =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.6099.130 Safari/537.36"
            domStorageEnabled = true
            databaseEnabled = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            mediaPlaybackRequiresUserGesture = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                injectAll(view)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                request.grant(request.resources)
            }
            override fun onShowFileChooser(
                webView: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = callback
                try {
                    fileChooserLauncher.launch(params.createIntent())
                } catch (e: Exception) {
                    val fallback = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        addCategory(android.content.Intent.CATEGORY_OPENABLE)
                        putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                    fileChooserLauncher.launch(fallback)
                }
                return true
            }
        }

        webView.loadUrl("https://web.whatsapp.com")
    }

    private fun injectAll(view: WebView) {
        val js = """
            (function() {
                if (window._twinsInjected) return;
                window._twinsInjected = true;

                function getLeft()  { return document.querySelector('._ak9p') && document.querySelector('._ak9p').parentElement; }
                function getRight() { return document.querySelector('._ajx_') && document.querySelector('._ajx_').parentElement; }

                function isChatOpen() {
                    var right = getRight();
                    return right ? !!right.querySelector('footer') : false;
                }

                // ── BACK BUTTON ──────────────────────────────────────────────
                var backBtn = document.createElement('div');
                backBtn.id = '_twins_back';
                backBtn.innerHTML = '&#8592;';
                document.body.appendChild(backBtn);
                (function(s) {
                    s.setProperty('position',      'fixed',            'important');
                    s.setProperty('top',           '14px',             'important');
                    s.setProperty('left',          '8px',              'important');
                    s.setProperty('z-index',       '99998',            'important');
                    s.setProperty('display',       'none',             'important');
                    s.setProperty('width',         '38px',             'important');
                    s.setProperty('height',        '38px',             'important');
                    s.setProperty('line-height',   '38px',             'important');
                    s.setProperty('text-align',    'center',           'important');
                    s.setProperty('font-size',     '22px',             'important');
                    s.setProperty('color',         '#ffffff',          'important');
                    s.setProperty('background',    'rgba(0,0,0,0.45)', 'important');
                    s.setProperty('border-radius', '50%',              'important');
                    s.setProperty('cursor',        'pointer',          'important');
                })(backBtn.style);

                backBtn.addEventListener('click', goBack);
                window._twinsGoBack = goBack;

                // ── OBSERVER (created here, connected/disconnected as needed) ──
                var observer = new MutationObserver(applyLayout);
                function connectObserver() {
                    observer.observe(document.body, { childList: true, subtree: true });
                }

                function goBack() {
                    // 1. Disconnect observer so it can't snap us back
                    observer.disconnect();

                    // 2. Switch to chat list view
                    lastOpen = false;
                    switchPanels(false);
                    history.back();
                    if (window.TwinsApp) TwinsApp.setChatOpen(false);

                    // 3. Wait until WhatsApp finishes processing back-navigation
                    //    (footer gone from right panel for 500ms), then reconnect.
                    var lastSeen = Date.now();
                    var checkId = setInterval(function() {
                        var right = getRight();
                        if (right && right.querySelector('footer')) {
                            lastSeen = Date.now();
                        } else if (Date.now() - lastSeen > 500) {
                            clearInterval(checkId);
                            cleanupAndReconnect();
                        }
                    }, 100);

                    // 4. Fast-path: if user taps a chat before WhatsApp finishes,
                    //    reconnect immediately so the new chat opens.
                    var left = getLeft();
                    var onTap = null;
                    if (left) {
                        onTap = function() {
                            left.removeEventListener('click', onTap, true);
                            onTap = null;
                            clearInterval(checkId);
                            // Give WhatsApp 300ms to process the click and add footer
                            setTimeout(cleanupAndReconnect, 300);
                        };
                        left.addEventListener('click', onTap, true);
                    }

                    // 5. Safety: reconnect after 5s no matter what
                    var safetyId = setTimeout(function() {
                        clearInterval(checkId);
                        cleanupAndReconnect();
                    }, 5000);

                    var cleaned = false;
                    function cleanupAndReconnect() {
                        if (cleaned) return;
                        cleaned = true;
                        clearInterval(checkId);
                        clearTimeout(safetyId);
                        if (onTap && left) left.removeEventListener('click', onTap, true);
                        connectObserver();
                        applyLayout();
                    }
                }

                // ── SIDEBAR HIDER ─────────────────────────────────────────
                // WhatsApp Web has a narrow icon sidebar (nav rail) on the
                // far left. Hide it to reclaim screen width on mobile.
                function hideSidebar() {
                    // The sidebar is typically a narrow sibling before the panels.
                    // Find it by looking for a narrow element that's a sibling of the left panel.
                    var left = getLeft();
                    if (!left) return;
                    var parent = left.parentElement;
                    if (!parent) return;
                    Array.from(parent.children).forEach(function(child) {
                        if (child === left || child === getRight()) return;
                        var rect = child.getBoundingClientRect();
                        // Sidebar is narrow (< 80px wide) and tall
                        if (rect.width > 0 && rect.width < 80 && rect.height > 200) {
                            child.style.setProperty('display', 'none', 'important');
                        }
                    });
                }

                // ── PANEL SWITCHING ──────────────────────────────────────────
                // Uses flexbox to collapse/expand panels instead of position:fixed
                // + z-index. This avoids creating stacking contexts that trap
                // WhatsApp's popup inside the panel.
                function switchPanels(open) {
                    var left  = getLeft();
                    var right = getRight();
                    if (!left || !right || left === right) return;

                    hideSidebar();

                    if (open) {
                        // Collapse left, expand right
                        left.style.setProperty('flex',       '0 0 0px', 'important');
                        left.style.setProperty('max-width',  '0',       'important');
                        left.style.setProperty('min-width',  '0',       'important');
                        left.style.setProperty('overflow',   'hidden',  'important');
                        left.style.setProperty('visibility', 'hidden',  'important');

                        right.style.setProperty('flex',       '1 1 100%', 'important');
                        right.style.setProperty('max-width',  'none',     'important');
                        right.style.setProperty('min-width',  '0',        'important');
                        right.style.setProperty('overflow',   'visible',  'important');
                        right.style.setProperty('visibility', 'visible',  'important');
                    } else {
                        // Collapse right, expand left
                        right.style.setProperty('flex',       '0 0 0px', 'important');
                        right.style.setProperty('max-width',  '0',       'important');
                        right.style.setProperty('min-width',  '0',       'important');
                        right.style.setProperty('overflow',   'hidden',  'important');
                        right.style.setProperty('visibility', 'hidden',  'important');

                        left.style.setProperty('flex',       '1 1 100%', 'important');
                        left.style.setProperty('max-width',  'none',     'important');
                        left.style.setProperty('min-width',  '0',        'important');
                        left.style.setProperty('overflow',   'visible',  'important');
                        left.style.setProperty('visibility', 'visible',  'important');
                    }

                    backBtn.style.setProperty('display', open ? 'block' : 'none', 'important');
                    if (window.TwinsApp) TwinsApp.setChatOpen(open);
                }

                // ── LAYOUT DETECTION ─────────────────────────────────────────
                var lastOpen = null;
                function applyLayout() {
                    var open = isChatOpen();
                    if (open === lastOpen) return;
                    lastOpen = open;
                    switchPanels(open);
                }

                window.addEventListener('hashchange', applyLayout);
                window.addEventListener('popstate',   applyLayout);
                connectObserver();
                applyLayout();
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
    }

    override fun onBackPressed() {
        if (chatOpen) {
            webView.evaluateJavascript("window._twinsGoBack && window._twinsGoBack()", null)
        } else {
            super.onBackPressed()
        }
    }
}
