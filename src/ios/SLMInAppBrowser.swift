import UIKit
import SafariServices
import WebKit

@objc(SLMInAppBrowser) class SLMInAppBrowser: CDVPlugin {

    private var webViewController: SLMInAppBrowserWebViewController?
    private var callbackId: String?

    // MARK: - open

    @objc(open:)
    func open(command: CDVInvokedUrlCommand) {
        let url = command.arguments[0] as? String ?? ""
        let target = command.arguments[1] as? String ?? "_blank"
        let optionsString = command.arguments[2] as? String ?? ""

        callbackId = command.callbackId

        DispatchQueue.main.async {
            if target == "_system" {
                self.openInSystemBrowser(url)
            } else {
                self.openInWebView(url, options: self.parseOptions(optionsString))
            }
        }
    }

    // MARK: - close

    @objc(close:)
    func close(command: CDVInvokedUrlCommand) {
        DispatchQueue.main.async {
            if let vc = self.webViewController {
                vc.dismiss(animated: true) {
                    self.sendEvent("exit", data: ["url": ""])
                    self.webViewController = nil
                }
            }
        }
    }

    // MARK: - show

    @objc(show:)
    func show(command: CDVInvokedUrlCommand) {
        DispatchQueue.main.async {
            if let vc = self.webViewController {
                vc.view.isHidden = false
            }
        }
    }

    // MARK: - hide

    @objc(hide:)
    func hide(command: CDVInvokedUrlCommand) {
        DispatchQueue.main.async {
            if let vc = self.webViewController {
                vc.view.isHidden = true
            }
        }
    }

    // MARK: - executeScript

    @objc(executeScript:)
    func executeScript(command: CDVInvokedUrlCommand) {
        let code = command.arguments[0] as? String ?? ""

        DispatchQueue.main.async {
            guard let vc = self.webViewController else {
                let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "Browser no abierto")
                self.commandDelegate.send(result, callbackId: command.callbackId)
                return
            }

            vc.webView.evaluateJavaScript(code) { (result, error) in
                if let error = error {
                    let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: error.localizedDescription)
                    self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
                } else {
                    var resultArray: [Any] = []
                    if let r = result {
                        resultArray.append(r)
                    }
                    let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: resultArray)
                    self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
                }
            }
        }
    }

    // MARK: - insertCSS

    @objc(insertCSS:)
    func insertCSS(command: CDVInvokedUrlCommand) {
        let cssCode = command.arguments[0] as? String ?? ""

        DispatchQueue.main.async {
            guard let vc = self.webViewController else {
                let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "Browser no abierto")
                self.commandDelegate.send(result, callbackId: command.callbackId)
                return
            }

            let js = "var _slm_style = document.createElement('style'); _slm_style.innerHTML = \(self.escapeForJS(cssCode)); document.head.appendChild(_slm_style);"
            vc.webView.evaluateJavaScript(js) { (_, error) in
                if let error = error {
                    let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: error.localizedDescription)
                    self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
                } else {
                    let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK)
                    self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
                }
            }
        }
    }

    // MARK: - Open Helpers

    private func openInSystemBrowser(_ urlString: String) {
        guard let url = URL(string: urlString) else { return }

        let safariVC = SFSafariViewController(url: url)
        viewController.present(safariVC, animated: true)

        sendEvent("loadstart", data: ["url": urlString])
    }

    private func openInWebView(_ urlString: String, options: [String: String]) {
        let vc = SLMInAppBrowserWebViewController()
        vc.options = options
        vc.delegate = self
        vc.modalPresentationStyle = .fullScreen

        let hidden = options["hidden"] == "yes"

        viewController.present(vc, animated: !hidden) {
            vc.loadURL(urlString)
            if hidden {
                vc.view.isHidden = true
            }
        }

        self.webViewController = vc
    }

    // MARK: - Event Dispatch

    func sendEvent(_ type: String, data: [String: Any]) {
        guard let callbackId = self.callbackId else { return }

        var eventData = data
        eventData["type"] = type

        let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: eventData)
        result?.setKeepCallbackAs(true)
        commandDelegate.send(result, callbackId: callbackId)
    }

    // MARK: - Options Parsing

    private func parseOptions(_ optionsString: String) -> [String: String] {
        var options: [String: String] = [:]
        let pairs = optionsString.split(separator: ",")
        for pair in pairs {
            let kv = pair.split(separator: "=", maxSplits: 1)
            if kv.count == 2 {
                let key = String(kv[0]).trimmingCharacters(in: .whitespaces)
                let value = String(kv[1]).trimmingCharacters(in: .whitespaces)
                options[key] = value
            }
        }
        return options
    }

    private func escapeForJS(_ string: String) -> String {
        let escaped = string
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "'", with: "\\'")
            .replacingOccurrences(of: "\n", with: "\\n")
            .replacingOccurrences(of: "\r", with: "\\r")
        return "'\(escaped)'"
    }
}
