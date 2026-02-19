var exec = require('cordova/exec');
var channel = require('cordova/channel');

function InAppBrowserRef() {
    this._eventListeners = {};
}

InAppBrowserRef.prototype = {

    close: function () {
        exec(null, null, 'SLMInAppBrowser', 'close', []);
    },

    show: function () {
        exec(null, null, 'SLMInAppBrowser', 'show', []);
    },

    hide: function () {
        exec(null, null, 'SLMInAppBrowser', 'hide', []);
    },

    executeScript: function (details, callback) {
        if (details.code) {
            exec(callback, null, 'SLMInAppBrowser', 'executeScript', [details.code]);
        } else if (details.file) {
            exec(callback, null, 'SLMInAppBrowser', 'executeScript', [details.file]);
        }
    },

    insertCSS: function (details, callback) {
        if (details.code) {
            exec(callback, null, 'SLMInAppBrowser', 'insertCSS', [details.code]);
        }
    },

    addEventListener: function (event, callback) {
        if (!this._eventListeners[event]) {
            this._eventListeners[event] = [];
        }
        this._eventListeners[event].push(callback);
    },

    removeEventListener: function (event, callback) {
        if (this._eventListeners[event]) {
            var index = this._eventListeners[event].indexOf(callback);
            if (index > -1) {
                this._eventListeners[event].splice(index, 1);
            }
        }
    },

    _dispatchEvent: function (event, data) {
        var listeners = this._eventListeners[event] || [];
        for (var i = 0; i < listeners.length; i++) {
            try {
                listeners[i](data);
            } catch (e) {
                console.error('[SLMInAppBrowser] Error in event listener: ' + e.message);
            }
        }
    }
};

var SLMInAppBrowser = {

    open: function (url, target, options) {
        target = target || '_blank';
        options = options || '';

        var ref = new InAppBrowserRef();

        exec(function (result) {
            if (result && result.type) {
                ref._dispatchEvent(result.type, result);
            }
        }, function (error) {
            ref._dispatchEvent('loaderror', { type: 'loaderror', url: url, message: error });
        }, 'SLMInAppBrowser', 'open', [url, target, options]);

        return ref;
    }
};

module.exports = SLMInAppBrowser;
