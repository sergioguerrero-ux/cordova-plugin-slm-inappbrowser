package com.slm.inappbrowser;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.browser.customtabs.CustomTabsIntent;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class SLMInAppBrowser extends CordovaPlugin {

    private static final String TAG = "SLMInAppBrowser";

    private SLMInAppBrowserWebView webViewDialog;
    private CallbackContext eventCallbackContext;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        switch (action) {
            case "open":
                open(args.getString(0), args.getString(1), args.optString(2, ""), callbackContext);
                return true;
            case "close":
                close();
                return true;
            case "show":
                show();
                return true;
            case "hide":
                hide();
                return true;
            case "executeScript":
                executeScript(args.getString(0), callbackContext);
                return true;
            case "insertCSS":
                insertCSS(args.getString(0), callbackContext);
                return true;
            default:
                return false;
        }
    }

    // ============================================
    // open
    // ============================================

    private void open(String url, String target, String optionsString, CallbackContext callbackContext) {
        eventCallbackContext = callbackContext;

        if ("_system".equals(target)) {
            openInSystemBrowser(url);
        } else {
            openInWebView(url, parseOptions(optionsString));
        }
    }

    private void openInSystemBrowser(String url) {
        cordova.getActivity().runOnUiThread(() -> {
            try {
                CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
                builder.setShowTitle(true);
                CustomTabsIntent customTabsIntent = builder.build();
                customTabsIntent.launchUrl(cordova.getActivity(), Uri.parse(url));
                sendEvent("loadstart", url);
            } catch (Exception e) {
                // Fallback al browser del sistema
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                cordova.getActivity().startActivity(intent);
                sendEvent("loadstart", url);
            }
        });
    }

    private void openInWebView(String url, Map<String, String> options) {
        cordova.getActivity().runOnUiThread(() -> {
            try {
                boolean hidden = "yes".equals(options.get("hidden"));

                webViewDialog = new SLMInAppBrowserWebView(cordova.getActivity(), this, options);
                webViewDialog.show();
                webViewDialog.loadUrl(url);

                if (hidden) {
                    webViewDialog.hide();
                }
            } catch (Exception e) {
                Log.e(TAG, "openInWebView error: " + e.getMessage());
                sendEventError("loaderror", url, e.getMessage());
            }
        });
    }

    // ============================================
    // close
    // ============================================

    private void close() {
        cordova.getActivity().runOnUiThread(() -> {
            if (webViewDialog != null) {
                webViewDialog.dismiss();
                webViewDialog = null;
                sendEvent("exit", "");
            }
        });
    }

    // ============================================
    // show / hide
    // ============================================

    private void show() {
        cordova.getActivity().runOnUiThread(() -> {
            if (webViewDialog != null) {
                webViewDialog.show();
            }
        });
    }

    private void hide() {
        cordova.getActivity().runOnUiThread(() -> {
            if (webViewDialog != null) {
                webViewDialog.hide();
            }
        });
    }

    // ============================================
    // executeScript
    // ============================================

    private void executeScript(String code, CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(() -> {
            if (webViewDialog == null || webViewDialog.getWebView() == null) {
                callbackContext.error("Browser no abierto");
                return;
            }

            webViewDialog.getWebView().evaluateJavascript(code, value -> {
                try {
                    JSONArray result = new JSONArray();
                    if (value != null && !"null".equals(value)) {
                        result.put(value);
                    }
                    callbackContext.success(result);
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            });
        });
    }

    // ============================================
    // insertCSS
    // ============================================

    private void insertCSS(String cssCode, CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(() -> {
            if (webViewDialog == null || webViewDialog.getWebView() == null) {
                callbackContext.error("Browser no abierto");
                return;
            }

            String escapedCSS = cssCode.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r");
            String js = "var _slm_style = document.createElement('style'); _slm_style.innerHTML = '" + escapedCSS + "'; document.head.appendChild(_slm_style);";

            webViewDialog.getWebView().evaluateJavascript(js, value -> {
                callbackContext.success();
            });
        });
    }

    // ============================================
    // Event Dispatch
    // ============================================

    public void sendEvent(String type, String url) {
        if (eventCallbackContext == null) return;

        try {
            JSONObject event = new JSONObject();
            event.put("type", type);
            event.put("url", url);

            PluginResult result = new PluginResult(PluginResult.Status.OK, event);
            result.setKeepCallback(true);
            eventCallbackContext.sendPluginResult(result);
        } catch (JSONException e) {
            Log.e(TAG, "sendEvent error: " + e.getMessage());
        }
    }

    public void sendEventError(String type, String url, String message) {
        if (eventCallbackContext == null) return;

        try {
            JSONObject event = new JSONObject();
            event.put("type", type);
            event.put("url", url);
            event.put("message", message);

            PluginResult result = new PluginResult(PluginResult.Status.OK, event);
            result.setKeepCallback(true);
            eventCallbackContext.sendPluginResult(result);
        } catch (JSONException e) {
            Log.e(TAG, "sendEventError error: " + e.getMessage());
        }
    }

    public void sendMessageEvent(String url, String messageData) {
        if (eventCallbackContext == null) return;

        try {
            JSONObject event = new JSONObject();
            event.put("type", "message");
            event.put("url", url);

            // Intentar parsear como JSON
            try {
                JSONObject data = new JSONObject(messageData);
                event.put("data", data);
            } catch (JSONException e) {
                event.put("data", messageData);
            }

            PluginResult result = new PluginResult(PluginResult.Status.OK, event);
            result.setKeepCallback(true);
            eventCallbackContext.sendPluginResult(result);
        } catch (JSONException e) {
            Log.e(TAG, "sendMessageEvent error: " + e.getMessage());
        }
    }

    // ============================================
    // Options Parsing
    // ============================================

    private Map<String, String> parseOptions(String optionsString) {
        Map<String, String> options = new HashMap<>();
        if (optionsString == null || optionsString.isEmpty()) return options;

        String[] pairs = optionsString.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                options.put(kv[0].trim(), kv[1].trim());
            }
        }
        return options;
    }

    @Override
    public void onDestroy() {
        if (webViewDialog != null) {
            webViewDialog.dismiss();
            webViewDialog = null;
        }
        super.onDestroy();
    }
}
