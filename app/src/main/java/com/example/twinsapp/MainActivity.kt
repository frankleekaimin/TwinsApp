package com.example.twinsapp

import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

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

        // Pad WebView so content sits below the status bar and above the nav bar
        ViewCompat.setOnApplyWindowInsetsListener(webView) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

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

        // Back press: if a chat is open, switch to chat list; otherwise exit
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (chatOpen) {
                    webView.evaluateJavascript(
                        "window._twinsGoBack && window._twinsGoBack()", null
                    )
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        webView.loadUrl("https://web.whatsapp.com")
    }

    private fun injectAll(view: WebView) {
        val js = """
            (function() {
                if (window._twinsInjected) return;
                window._twinsInjected = true;

                function getLeft()  {
                    var el = document.querySelector('._ak9p');
                    return el ? el.parentElement : null;
                }
                function getRight() {
                    var el = document.querySelector('._ajx_');
                    return el ? el.parentElement : null;
                }

                // ── BACK BUTTON ──────────────────────────────────────────
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

                // ── SIDEBAR HIDER ────────────────────────────────────────
                // WhatsApp Web has a narrow icon sidebar (nav rail) on the
                // far left.  Hide it to reclaim screen width on mobile.
                function hideSidebar() {
                    var left = getLeft();
                    if (!left) return;
                    var parent = left.parentElement;
                    if (!parent) return;
                    Array.from(parent.children).forEach(function(child) {
                        if (child === left || child === getRight()) return;
                        var rect = child.getBoundingClientRect();
                        if (rect.width > 0 && rect.width < 80 && rect.height > 200) {
                            child.style.setProperty('display', 'none', 'important');
                        }
                    });
                }

                // ── PANEL SWITCHING ──────────────────────────────────────
                // Uses flexbox to collapse/expand panels.  No position:fixed
                // tricks, so WhatsApp's popups aren't trapped by a stacking
                // context inside a panel.
                function switchPanels(open) {
                    var left  = getLeft();
                    var right = getRight();
                    if (!left || !right || left === right) return;

                    hideSidebar();

                    if (open) {
                        left.style.setProperty('flex',       '0 0 0px',  'important');
                        left.style.setProperty('max-width',  '0',        'important');
                        left.style.setProperty('min-width',  '0',        'important');
                        left.style.setProperty('overflow',   'hidden',   'important');
                        left.style.setProperty('visibility', 'hidden',   'important');

                        right.style.setProperty('flex',       '1 1 100%', 'important');
                        right.style.setProperty('max-width',  'none',     'important');
                        right.style.setProperty('min-width',  '0',        'important');
                        right.style.setProperty('overflow',   'visible',  'important');
                        right.style.setProperty('visibility', 'visible',  'important');
                    } else {
                        right.style.setProperty('flex',       '0 0 0px',  'important');
                        right.style.setProperty('max-width',  '0',        'important');
                        right.style.setProperty('min-width',  '0',        'important');
                        right.style.setProperty('overflow',   'hidden',   'important');
                        right.style.setProperty('visibility', 'hidden',   'important');

                        left.style.setProperty('flex',       '1 1 100%', 'important');
                        left.style.setProperty('max-width',  'none',     'important');
                        left.style.setProperty('min-width',  '0',        'important');
                        left.style.setProperty('overflow',   'visible',  'important');
                        left.style.setProperty('visibility', 'visible',  'important');
                    }

                    backBtn.style.setProperty('display', open ? 'block' : 'none', 'important');
                    if (window.TwinsApp) TwinsApp.setChatOpen(open);
                }

                // ── GO BACK ──────────────────────────────────────────────
                // Pure CSS panel switch.  WhatsApp Web has no concept of
                // "closing" a chat — the right panel stays loaded.  We just
                // hide it and show the chat list.
                function goBack() {
                    switchPanels(false);
                }

                backBtn.addEventListener('click', goBack);
                window._twinsGoBack = goBack;

                // ── CHAT-LIST CLICK DETECTION ────────────────────────────
                // Only trigger switchPanels(true) when the user taps inside
                // the scrollable chat list, not the header / search / filter
                // area.  We detect "scrollable ancestor" structurally: walk
                // up from the click target and check for overflow-y: auto|scroll
                // before reaching the left panel root.
                function isInChatList(target) {
                    var left = getLeft();
                    if (!left) return false;
                    var el = target;
                    while (el && el !== left) {
                        var ov = getComputedStyle(el).overflowY;
                        if (ov === 'auto' || ov === 'scroll') return true;
                        el = el.parentElement;
                    }
                    return false;
                }

                function setupClickListener() {
                    var left = getLeft();
                    if (!left) return false;
                    // Capture phase fires before WhatsApp's own handlers,
                    // so the panel switches immediately on tap.
                    left.addEventListener('click', function(e) {
                        if (isInChatList(e.target)) {
                            switchPanels(true);
                        }
                    }, true);
                    return true;
                }

                // ── INITIALISATION ───────────────────────────────────────
                // WhatsApp Web loads progressively.  Poll until both panels
                // exist, then set up the click listener and initial layout.
                function init() {
                    if (!getLeft() || !getRight()) {
                        setTimeout(init, 500);
                        return;
                    }
                    setupClickListener();
                    // If a session was restored with a chat already open,
                    // show the chat view; otherwise show the chat list.
                    var right = getRight();
                    var chatIsOpen = right && !!right.querySelector('footer');
                    switchPanels(chatIsOpen);
                }
                init();
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
    }
}
