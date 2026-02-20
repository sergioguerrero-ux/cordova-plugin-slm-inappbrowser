import UIKit
import WebKit

class SLMInAppBrowserWebViewController: UIViewController, WKNavigationDelegate, WKScriptMessageHandler {

    var webView: WKWebView!
    var options: [String: String] = [:]
    weak var delegate: SLMInAppBrowser?

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()

        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = (options["allowInlineMediaPlayback"] ?? "yes") == "yes"

        if #available(iOS 10.0, *) {
            config.mediaTypesRequiringUserActionForPlayback = []
        }

        // CRITICO: Registrar message handler para el bridge webapp <-> nativo
        config.userContentController.add(self, name: "cordova_iab")

        config.preferences.javaScriptEnabled = true

        webView = WKWebView(frame: view.bounds, configuration: config)
        webView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        webView.navigationDelegate = self
        webView.allowsBackForwardNavigationGestures = true

        // Evitar que WKWebView agregue insets automaticos por safe area
        // (esto causa el espacio blanco abajo en iPhones con home indicator)
        if #available(iOS 11.0, *) {
            webView.scrollView.contentInsetAdjustmentBehavior = .never
        }

        if #available(iOS 16.4, *) {
            webView.isInspectable = true
        }

        view.addSubview(webView)
        view.backgroundColor = .white
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let top = view.safeAreaInsets.top
        webView.frame = CGRect(x: 0, y: top, width: view.bounds.width, height: view.bounds.height - top)
    }

    func loadURL(_ urlString: String) {
        guard let url = URL(string: urlString) else { return }
        let request = URLRequest(url: url)
        webView.load(request)
    }

    // MARK: - WKNavigationDelegate

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        let url = webView.url?.absoluteString ?? ""
        delegate?.sendEvent("loadstart", data: ["url": url])
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        let url = webView.url?.absoluteString ?? ""
        delegate?.sendEvent("loadstop", data: ["url": url])
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        let url = webView.url?.absoluteString ?? ""
        delegate?.sendEvent("loaderror", data: [
            "url": url,
            "code": (error as NSError).code,
            "message": error.localizedDescription
        ])
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        let url = webView.url?.absoluteString ?? ""
        delegate?.sendEvent("loaderror", data: [
            "url": url,
            "code": (error as NSError).code,
            "message": error.localizedDescription
        ])
    }

    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        decisionHandler(.allow)
    }

    // MARK: - WKScriptMessageHandler (CRITICO para el bridge)

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        if message.name == "cordova_iab" {
            var messageData: [String: Any] = [:]

            if let body = message.body as? String {
                // JSON string
                if let data = body.data(using: .utf8),
                   let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                    messageData = json
                } else {
                    messageData = ["data": body]
                }
            } else if let dict = message.body as? [String: Any] {
                messageData = dict
            }

            // Despachar como evento "message" al JS de Cordova
            var eventData: [String: Any] = ["url": webView.url?.absoluteString ?? ""]
            eventData["data"] = messageData
            eventData["type"] = "message"

            delegate?.sendEvent("message", data: eventData)
        }
    }

    // MARK: - Cleanup

    deinit {
        webView?.configuration.userContentController.removeScriptMessageHandler(forName: "cordova_iab")
    }
}
