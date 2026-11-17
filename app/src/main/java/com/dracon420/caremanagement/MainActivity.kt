package com.dracon420.caremanagement

import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.view.WindowManager
import android.webkit.*
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

class MainActivity : AppCompatActivity() {

    companion object {
        const val NETLIFY_URL            = "https://YOUR-APP.workers.dev"   // set to where you host care-schedule
        const val NOTIF_PERMISSION_REQUEST = 1001
        const val RC_SIGN_IN             = 9001
        const val RC_AUTH_RECOVERY       = 9002
    }

    private lateinit var webView: WebView

    // Track open auth popup so back-press can dismiss it
    private var authDialog: Dialog? = null

    // ── Google Sign-In client (lazy so Activity is fully ready) ──────────
    private val googleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(this, gso)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webView)

        setupWebView()
        requestNotificationPermission()

        webView.loadUrl(buildUrl(intent))
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        webView.loadUrl(buildUrl(intent))
    }

    private fun buildUrl(intent: Intent?): String {
        val action = intent?.getStringExtra("action")
        return if (action == "start") "$NETLIFY_URL?action=start" else NETLIFY_URL
    }

    // ── Called from WebAppInterface bridge (background-safe) ─────────────
    fun startNativeGoogleSignIn() {
        runOnUiThread {
            // Sign out the cached account first so the picker always appears
            // and the user can choose which Google account to use
            googleSignInClient.signOut().addOnCompleteListener {
                @Suppress("DEPRECATION")
                startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            RC_SIGN_IN        -> handleSignInResult(data)
            RC_AUTH_RECOVERY  -> {
                // User just granted scope consent — retry token fetch
                if (resultCode == RESULT_OK) {
                    val account = GoogleSignIn.getLastSignedInAccount(this)
                    if (account != null) fetchCalendarToken(account)
                } else {
                    jsSignInError("Consent denied — please try signing in again.")
                }
            }
        }
    }

    private fun handleSignInResult(data: Intent?) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            fetchCalendarToken(account)
        } catch (e: ApiException) {
            jsSignInError("Google sign-in failed (code ${e.statusCode})")
        }
    }

    /**
     * Fetch an OAuth access token for the Calendar scope.
     * Must run off the main thread — GoogleAuthUtil.getToken() is blocking.
     */
    private fun fetchCalendarToken(account: GoogleSignInAccount) {
        Thread {
            try {
                val scope = "oauth2:https://www.googleapis.com/auth/calendar"
                val androidAccount = account.account
                    ?: throw Exception("Could not read Android account")
                val token = GoogleAuthUtil.getToken(this, androidAccount, scope)
                // Token lasts ~1 hour; pass expiresIn=3600 to the web app
                runOnUiThread {
                    webView.evaluateJavascript(
                        "window.onAndroidGoogleToken('${token}', 3600)", null
                    )
                }
            } catch (e: UserRecoverableAuthException) {
                // User hasn't yet granted Calendar scope — show consent screen
                val recoveryIntent = e.intent ?: return@Thread
                runOnUiThread {
                    @Suppress("DEPRECATION")
                    startActivityForResult(recoveryIntent, RC_AUTH_RECOVERY)
                }
            } catch (e: Exception) {
                jsSignInError(e.message ?: "Unknown error fetching token")
            }
        }.start()
    }

    private fun jsSignInError(msg: String) {
        val safe = msg.replace("'", "\\'")
        runOnUiThread {
            webView.evaluateJavascript("window.onAndroidSignInError('$safe')", null)
        }
    }

    // ── WebView setup ─────────────────────────────────────────────────────
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled        = true
            domStorageEnabled        = true
            databaseEnabled          = true
            cacheMode                = WebSettings.LOAD_DEFAULT
            allowFileAccess          = false
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode         = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

            // Required for any residual OAuth popup flows (GIS on PWA side)
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)

            // Strip the "; wv)" WebView marker
            userAgentString = userAgentString
                .replace("; wv)", ")")
                .replace("; wv;", ";")
        }

        webView.addJavascriptInterface(WebAppInterface(this), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            private val allowedHosts = listOf(
                "workers.dev",
                "googleapis.com",
                "accounts.google.com",
                "gstatic.com",
                "fonts.googleapis.com"
            )

            override fun onPageFinished(view: WebView, url: String) {
                // Inject the real top system inset (status bar + display cutout) as a
                // CSS custom property so the header clears the camera on any device.
                val insets = ViewCompat.getRootWindowInsets(window.decorView)
                val top = insets?.getInsets(
                    WindowInsetsCompat.Type.statusBars() or
                    WindowInsetsCompat.Type.displayCutout()
                )?.top ?: 0
                view.evaluateJavascript(
                    "document.documentElement.style.setProperty('--safe-top','${top}px')", null
                )
            }

            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean {
                val host = request.url.host ?: return false
                return if (allowedHosts.none { host.contains(it) }) {
                    // External URL (e.g. GitHub release page) — open in the system browser
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(request.url.toString()))) }
                    catch (e: Exception) { /* no browser installed */ }
                    true
                } else {
                    false
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                request.grant(request.resources)
            }

            // Handles any popup window the web page opens (kept for completeness)
            override fun onCreateWindow(
                view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?
            ): Boolean {
                val popupWebView = WebView(this@MainActivity).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString   = view.settings.userAgentString
                }
                val dialog = Dialog(
                    this@MainActivity,
                    android.R.style.Theme_Black_NoTitleBar_Fullscreen
                )
                val container = FrameLayout(this@MainActivity)
                container.addView(
                    popupWebView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                dialog.setContentView(container)
                authDialog = dialog

                popupWebView.webChromeClient = object : WebChromeClient() {
                    override fun onCloseWindow(window: WebView) {
                        dialog.dismiss(); authDialog = null
                    }
                }
                popupWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, req: WebResourceRequest) = false
                }

                dialog.show()

                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = popupWebView
                resultMsg?.sendToTarget()
                return true
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    NOTIF_PERMISSION_REQUEST
                )
            }
        }
    }

    override fun onBackPressed() {
        if (authDialog?.isShowing == true) {
            authDialog?.dismiss(); authDialog = null; return
        }
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
