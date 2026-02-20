package com.slm.inappbrowser;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;

import java.util.Map;

public class SLMInAppBrowserWebView extends Dialog {

    private static final String TAG = "SLMInAppBrowserWebView";

    private WebView webView;
    private SLMInAppBrowser plugin;
    private Map<String, String> options;

    public SLMInAppBrowserWebView(Context context, SLMInAppBrowser plugin, Map<String, String> options) {
        super(context, android.R.style.Theme_NoTitleBar);
        this.plugin = plugin;
        this.options = options;
        setupDialog();
        setupWebView(context);
    }

    private void setupDialog() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setCancelable(true);

        if (getWindow() != null) {
            getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);

            if ("yes".equals(options.get("fullscreen"))) {
                getWindow().setFlags(
                        WindowManager.LayoutParams.FLAG_FULLSCREEN,
                        WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }

            // Extender contenido detras de la navigation bar para eliminar
            // el espacio blanco al fondo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                getWindow().setNavigationBarColor(Color.TRANSPARENT);
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            }
        }
    }

    private void setupWebView(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        webView = new WebView(context);
        webView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // Configurar WebSettings
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        if ("yes".equals(options.get("zoom"))) {
            settings.setBuiltInZoomControls(true);
            settings.setDisplayZoomControls(false);
        }

        // CRITICO: Agregar JavaScript interface para el bridge
        webView.addJavascriptInterface(new BridgeInterface(), "cordova_iab_android");

        // WebViewClient para eventos de navegacion
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                plugin.sendEvent("loadstart", url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                // CRITICO: Inyectar shim de compatibilidad para webkit.messageHandlers
                String shimJS = "if (!window.webkit) { " +
                        "window.webkit = { messageHandlers: { cordova_iab: { " +
                        "postMessage: function(msg) { " +
                        "if (typeof msg !== 'string') msg = JSON.stringify(msg); " +
                        "cordova_iab_android.postMessage(msg); " +
                        "} } } }; " +
                        "}";
                view.evaluateJavascript(shimJS, null);

                plugin.sendEvent("loadstop", url);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                plugin.sendEventError("loaderror", failingUrl, description);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
        });

        // WebChromeClient para features modernos
        webView.setWebChromeClient(new WebChromeClient());

        layout.addView(webView);
        setContentView(layout);
    }

    public void loadUrl(String url) {
        if (webView != null) {
            webView.loadUrl(url);
        }
    }

    public WebView getWebView() {
        return webView;
    }

    @Override
    public void dismiss() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.dismiss();
    }

    // CRITICO: JavaScript Interface para recibir mensajes del webapp
    private class BridgeInterface {
        @JavascriptInterface
        public void postMessage(String message) {
            Log.d(TAG, "postMessage recibido: " + (message != null ? message.substring(0, Math.min(message.length(), 100)) : "null"));
            if (plugin != null && webView != null) {
                String url = webView.getUrl() != null ? webView.getUrl() : "";
                plugin.sendMessageEvent(url, message);
            }
        }
    }
}
