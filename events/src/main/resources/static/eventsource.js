/** @license
 * eventsource.js
 * Available under MIT License (MIT)
 * https://github.com/Yaffle/EventSource/
 */

/*jslint indent: 2, vars: true, plusplus: true */
/*global setTimeout, clearTimeout */

(function (global) {
  "use strict";

  const setTimeout = global.setTimeout;
  const clearTimeout = global.clearTimeout;
  let XMLHttpRequest = global.XMLHttpRequest;
  const XDomainRequest = global.XDomainRequest;
  const ActiveXObject = global.ActiveXObject;
  const NativeEventSource = global.EventSource;
  const document = global.document;
  const Promise = global.Promise;
  let fetch = global.fetch;
  const Response = global.Response;
  let TextDecoder = global.TextDecoder;
  const TextEncoder = global.TextEncoder;
  let AbortController = global.AbortController;

  if (typeof window !== "undefined" && typeof document !== "undefined" && !("readyState" in document) && document.body == null) {
    document.readyState = "loading";
    window.addEventListener("load", function () {
      document.readyState = "complete";
    }, false);
  }

  if (XMLHttpRequest == null && ActiveXObject != null) {
    XMLHttpRequest = function () {
      return new ActiveXObject("Microsoft.XMLHTTP");
    };
  }

  if (Object.create === undefined) {
    Object.create = function (C) {
      function F() {
      }

      F.prototype = C;
      return new F();
    };
  }

  if (!Date.now) {
    Date.now = function now() {
      return new Date().getTime();
    };
  }

  if (AbortController === undefined) {
    const originalFetch2 = fetch;
    fetch = function (url, options) {
      const signal = options.signal;
      return originalFetch2(url, {
        headers: options.headers,
        credentials: options.credentials,
        cache: options.cache
      }).then(function (response) {
        const reader = response.body.getReader();
        signal._reader = reader;
        if (signal._aborted) {
          signal._reader.cancel().then(r => r);
        }
        return {
          status: response.status,
          statusText: response.statusText,
          headers: response.headers,
          body: {
            getReader: function () {
              return reader;
            }
          }
        };
      });
    };
    AbortController = function () {
      this.signal = {
        _reader: null,
        _aborted: false
      };
      this.abort = function () {
        if (this.signal._reader != null) {
          this.signal._reader.cancel();
        }
        this.signal._aborted = true;
      };
    };
  }

  function TextDecoderPolyfill() {
    this.bitsNeeded = 0;
    this.codePoint = 0;
  }

  TextDecoderPolyfill.prototype.decode = function (octets) {
    function valid(codePoint, shift, octetsCount) {
      if (octetsCount === 1) {
        return codePoint >= 0x0080 >> shift && codePoint << shift <= 0x07FF;
      }
      if (octetsCount === 2) {
        return codePoint >= 0x0800 >> shift && codePoint << shift <= 0xD7FF || codePoint >= 0xE000 >> shift && codePoint << shift <= 0xFFFF;
      }
      if (octetsCount === 3) {
        return codePoint >= 0x010000 >> shift && codePoint << shift <= 0x10FFFF;
      }
      throw new Error();
    }

    function octetsCount(bitsNeeded, codePoint) {
      if (bitsNeeded === 6) {
        return codePoint >> 6 > 15 ? 3 : codePoint > 31 ? 2 : 1;
      }
      if (bitsNeeded === 6 * 2) {
        return codePoint > 15 ? 3 : 2;
      }
      if (bitsNeeded === 6 * 3) {
        return 3;
      }
      throw new Error();
    }

    const REPLACER = 0xFFFD;
    let string = "";
    let bitsNeeded = this.bitsNeeded;
    let codePoint = this.codePoint;
    for (let i = 0; i < octets.length; i += 1) {
      const octet = octets[i];
      if (bitsNeeded !== 0) {
        if (octet < 128 || octet > 191 || !valid(codePoint << 6 | octet & 63, bitsNeeded - 6, octetsCount(bitsNeeded, codePoint))) {
          bitsNeeded = 0;
          codePoint = REPLACER;
          string += String.fromCharCode(codePoint);
        }
      }
      if (bitsNeeded === 0) {
        if (octet >= 0 && octet <= 127) {
          bitsNeeded = 0;
          codePoint = octet;
        } else if (octet >= 192 && octet <= 223) {
          bitsNeeded = 6;
          codePoint = octet & 31;
        } else if (octet >= 224 && octet <= 239) {
          bitsNeeded = 6 * 2;
          codePoint = octet & 15;
        } else if (octet >= 240 && octet <= 247) {
          bitsNeeded = 6 * 3;
          codePoint = octet & 7;
        } else {
          bitsNeeded = 0;
          codePoint = REPLACER;
        }
        if (bitsNeeded !== 0 && !valid(codePoint, bitsNeeded, octetsCount(bitsNeeded, codePoint))) {
          bitsNeeded = 0;
          codePoint = REPLACER;
        }
      } else {
        bitsNeeded -= 6;
        codePoint = codePoint << 6 | octet & 63;
      }
      if (bitsNeeded === 0) {
        if (codePoint <= 0xFFFF) {
          string += String.fromCharCode(codePoint);
        } else {
          string += String.fromCharCode(0xD800 + (codePoint - 0xFFFF - 1 >> 10));
          string += String.fromCharCode(0xDC00 + (codePoint - 0xFFFF - 1 & 0x3FF));
        }
      }
    }
    this.bitsNeeded = bitsNeeded;
    this.codePoint = codePoint;
    return string;
  };

  const supportsStreamOption = function () {
    try {
      return new TextDecoder().decode(new TextEncoder().encode("test"), {stream: true}) === "test";
    } catch (error) {
      console.debug("TextDecoder does not support streaming option. Using polyfill instead: " + error);
    }
    return false;
  };

  if (TextDecoder === undefined || TextEncoder === undefined || !supportsStreamOption()) {
    TextDecoder = TextDecoderPolyfill;
  }

  const k = function () {
  };

  function XHRWrapper(xhr) {
    this.withCredentials = false;
    this.readyState = 0;
    this.status = 0;
    this.statusText = "";
    this.responseText = "";
    this.onprogress = k;
    this.onload = k;
    this.onerror = k;
    this.onreadystatechange = k;
    this._contentType = "";
    this._xhr = xhr;
    this._sendTimeout = 0;
    this._abort = k;
  }

  XHRWrapper.prototype.open = function (method, url) {
    this._abort(true);
    const that = this;
    const xhr = this._xhr;
    let state = 1;
    let timeout = 0;
    this._abort = function (silent) {
      if (that._sendTimeout !== 0) {
        clearTimeout(that._sendTimeout);
        that._sendTimeout = 0;
      }
      if (state === 1 || state === 2 || state === 3) {
        state = 4;
        xhr.onload = k;
        xhr.onerror = k;
        xhr.onabort = k;
        xhr.onprogress = k;
        xhr.onreadystatechange = k;
        xhr.abort();
        if (timeout !== 0) {
          clearTimeout(timeout);
          timeout = 0;
        }
        if (!silent) {
          that.readyState = 4;
          that.onabort(null);
          that.onreadystatechange();
        }
      }
      state = 0;
    };

    const onStart = function () {
      if (state === 1) {
        let status;
        let statusText;
        let contentType;
        if (!("contentType" in xhr)) {
          try {
            status = xhr.status;
            statusText = xhr.statusText;
            contentType = xhr.getResponseHeader("Content-Type");
          } catch (error) {
            status = 0;
            statusText = "";
            contentType = undefined;
          }
        } else {
          status = 200;
          statusText = "OK";
          contentType = xhr.contentType;
        }
        if (status !== 0) {
          state = 2;
          that.readyState = 2;
          that.status = status;
          that.statusText = statusText;
          that._contentType = contentType;
          that.onreadystatechange();
        }
      }
    };

    const onProgress = function () {
      onStart();
      if (state === 2 || state === 3) {
        state = 3;
        let responseText = "";
        try {
          responseText = xhr.responseText;
        } catch (error) {
        }
        that.readyState = 3;
        that.responseText = responseText;
        that.onprogress();
      }
    };

    const onFinish = function (type, event) {
      if (event == null || event.preventDefault == null) {
        event = {
          preventDefault: k
        };
      }
      onProgress();
      if (state === 1 || state === 2 || state === 3) {
        state = 4;
        if (timeout !== 0) {
          clearTimeout(timeout);
          timeout = 0;
        }
        that.readyState = 4;
        if (type === "load") {
          that.onload(event);
        } else if (type === "error") {
          that.onerror(event);
        } else if (type === "abort") {
          that.onabort(event);
        } else {
          throw new TypeError();
        }
        that.onreadystatechange();
      }
    };

    const onReadyStateChange = function (event) {
      if (xhr !== undefined) {
        if (xhr.readyState === 4) {
          if (!("onload" in xhr) || !("onerror" in xhr) || !("onabort" in xhr)) {
            onFinish(xhr.responseText === "" ? "error" : "load", event);
          }
        } else if (xhr.readyState === 3) {
          if (!("onprogress" in xhr)) {
            onProgress();
          }
        } else if (xhr.readyState === 2) {
          onStart();
        }
      }
    };

    const onTimeout = function () {
      timeout = setTimeout(function () {
        onTimeout();
      }, 500);
      if (xhr.readyState === 3) {
        onProgress();
      }
    };

    if ("onload" in xhr) {
      xhr.onload = function (event) {
        onFinish("load", event);
      };
    }

    if ("onerror" in xhr) {
      xhr.onerror = function (event) {
        onFinish("error", event);
      };
    }

    if ("onabort" in xhr) {
      xhr.onabort = function (event) {
        onFinish("abort", event);
      };
    }

    if ("onprogress" in xhr) {
      xhr.onprogress = onProgress;
    }

    if ("onreadystatechange" in xhr) {
      xhr.onreadystatechange = function (event) {
        onReadyStateChange(event);
      };
    }

    if ("contentType" in xhr || !("ontimeout" in XMLHttpRequest.prototype)) {
      url += (url.indexOf("?") === -1 ? "?" : "&") + "padding=true";
    }

    xhr.open(method, url, true);

    if ("readyState" in xhr) {
      timeout = setTimeout(function () {
        onTimeout();
      }, 0);
    }
  };

  XHRWrapper.prototype.abort = function () {
    this._abort(false);
  };

  XHRWrapper.prototype.getResponseHeader = function () {
    return this._contentType;
  };

  XHRWrapper.prototype.setRequestHeader = function (name, value) {
    const xhr = this._xhr;
    if ("setRequestHeader" in xhr) {
      xhr.setRequestHeader(name, value);
    }
  };

  XHRWrapper.prototype.getAllResponseHeaders = function () {
    return this._xhr.getAllResponseHeaders !== undefined ? this._xhr.getAllResponseHeaders() || "" : "";
  };

  XHRWrapper.prototype.send = function () {
    if ((!("ontimeout" in XMLHttpRequest.prototype) || (!("sendAsBinary" in XMLHttpRequest.prototype) && !("mozAnon" in XMLHttpRequest.prototype))) &&
        document !== undefined &&
        document.readyState !== undefined &&
        document.readyState !== "complete") {
      const that = this;
      that._sendTimeout = setTimeout(function () {
        that._sendTimeout = 0;
        that.send();
      }, 4);
      return;
    }

    const xhr = this._xhr;
    if ("withCredentials" in xhr) {
      xhr.withCredentials = this.withCredentials;
    }

    try {
      xhr.send(undefined);
    } catch (error1) {
      throw error1;
    }
  };

  function toLowerCase(name) {
    return name.replace(/[A-Z]/g, function (c) {
      return String.fromCharCode(c.charCodeAt(0) + 0x20);
    });
  }

  function HeadersPolyfill(all) {
    const map = Object.create(null);
    const array = all.split("\r\n");
    for (let i = 0; i < array.length; i += 1) {
      const line = array[i];
      const parts = line.split(": ");
      const name = parts.shift();
      map[toLowerCase(name)] = parts.join(": ");
    }
    this._map = map;
  }

  HeadersPolyfill.prototype.get = function (name) {
    return this._map[toLowerCase(name)];
  };

  if (XMLHttpRequest != null && XMLHttpRequest.HEADERS_RECEIVED == null) {
    XMLHttpRequest.HEADERS_RECEIVED = 2;
  }

  function XHRTransport() {
  }

  XHRTransport.prototype.open = function (xhr, onStartCallback, onProgressCallback, onFinishCallback, url, withCredentials, headers) {
    xhr.open("GET", url);

    let offset = 0;
    xhr.onprogress = function () {
      const responseText = xhr.responseText;
      const chunk = responseText.slice(offset);
      offset += chunk.length;
      onProgressCallback(chunk);
    };

    xhr.onerror = function (event) {
      event.preventDefault();
      onFinishCallback(new Error("NetworkError"));
    };

    xhr.onload = function () {
      onFinishCallback(null);
    };

    xhr.onabort = function () {
      onFinishCallback(null);
    };

    xhr.onreadystatechange = function () {
      if (xhr.readyState === XMLHttpRequest.HEADERS_RECEIVED) {
        const status = xhr.status;
        const statusText = xhr.statusText;
        const contentType = xhr.getResponseHeader("Content-Type");
        const headers = xhr.getAllResponseHeaders();
        onStartCallback(status, statusText, contentType, new HeadersPolyfill(headers));
      }
    };

    xhr.withCredentials = withCredentials;
    for (const name in headers) {
      if (Object.prototype.hasOwnProperty.call(headers, name)) {
        xhr.setRequestHeader(name, headers[name]);
      }
    }

    xhr.send();

    return xhr;
  };

  function HeadersWrapper(headers) {
    this._headers = headers;
  }

  HeadersWrapper.prototype.get = function (name) {
    return this._headers.get(name);
  };

  function FetchTransport() {
  }

  FetchTransport.prototype.open = function (xhr, onStartCallback, onProgressCallback, onFinishCallback, url, withCredentials, headers) {
    let reader = null;
    const controller = new AbortController();
    const signal = controller.signal;
    const textDecoder = new TextDecoder();
    fetch(url, {
      headers: headers,
      credentials: withCredentials ? "include" : "same-origin",
      signal: signal,
      cache: "no-store"
    }).then(function (response) {
      reader = response.body.getReader();
      onStartCallback(response.status, response.statusText, response.headers.get("Content-Type"), new HeadersWrapper(response.headers));
      return new Promise(function (resolve, reject) {
        const readNextChunk = function () {
          reader.read().then(function (result) {
            if (result.done) {
              resolve(undefined);
            } else {
              const chunk = textDecoder.decode(result.value, {stream: true});
              onProgressCallback(chunk);
              readNextChunk();
            }
          })["catch"](function (error) {
            reject(error);
          });
        };
        readNextChunk();
      });
    })["catch"](function (error) {
      if (error.name === "AbortError") {
        return undefined;
      } else {
        return error;
      }
    }).then(function (error) {
      onFinishCallback(error);
    });
    return {
      abort: function () {
        if (reader != null) {
          reader.cancel();
        }
        controller.abort();
      }
    };
  };

  function EventTarget() {
    this._listeners = Object.create(null);
  }

  function throwError(e) {
    setTimeout(function () {
      throw e;
    }, 0);
  }

  EventTarget.prototype.dispatchEvent = function (event) {
    event.target = this;
    const typeListeners = this._listeners[event.type];
    if (typeListeners !== undefined) {
      const length = typeListeners.length;
      for (let i = 0; i < length; i += 1) {
        const listener = typeListeners[i];
        try {
          if (typeof listener.handleEvent === "function") {
            listener.handleEvent(event);
          } else {
            listener.call(this, event);
          }
        } catch (e) {
          throwError(e);
        }
      }
    }
  };

  EventTarget.prototype.addEventListener = function (type, listener) {
    type = String(type);
    const listeners = this._listeners;
    let typeListeners = listeners[type];
    if (typeListeners === undefined) {
      typeListeners = [];
      listeners[type] = typeListeners;
    }
    let found = false;
    for (let i = 0; i < typeListeners.length; i += 1) {
      if (typeListeners[i] === listener) {
        found = true;
      }
    }
    if (!found) {
      typeListeners.push(listener);
    }
  };

  EventTarget.prototype.removeEventListener = function (type, listener) {
    type = String(type);
    const listeners = this._listeners;
    const typeListeners = listeners[type];
    if (typeListeners !== undefined) {
      const filtered = [];
      for (let i = 0; i < typeListeners.length; i += 1) {
        if (typeListeners[i] !== listener) {
          filtered.push(typeListeners[i]);
        }
      }
      if (filtered.length === 0) {
        delete listeners[type];
      } else {
        listeners[type] = filtered;
      }
    }
  };

  function Event(type) {
    this.type = type;
    this.target = undefined;
  }

  function MessageEvent(type, options) {
    Event.call(this, type);
    this.data = options.data;
    this.lastEventId = options.lastEventId;
  }

  MessageEvent.prototype = Object.create(Event.prototype);

  function ConnectionEvent(type, options) {
    Event.call(this, type);
    this.status = options.status;
    this.statusText = options.statusText;
    this.headers = options.headers;
  }

  ConnectionEvent.prototype = Object.create(Event.prototype);

  function ErrorEvent(type, options) {
    Event.call(this, type);
    this.error = options.error;
  }

  ErrorEvent.prototype = Object.create(Event.prototype);

  const WAITING = -1;
  const CONNECTING = 0;
  const OPEN = 1;
  const CLOSED = 2;

  const AFTER_CR = -1;
  const FIELD_START = 0;
  const FIELD = 1;
  const VALUE_START = 2;
  const VALUE = 3;

  const contentTypeRegExp = /^text\/event-stream(;.*)?$/i;

  const MINIMUM_DURATION = 1000;
  const MAXIMUM_DURATION = 18000000;

  const parseDuration = function (value, def) {
    let n = value == null ? def : parseInt(value, 10);
    if (n !== n) {
      n = def;
    }
    return clampDuration(n);
  };

  const clampDuration = function (n) {
    return Math.min(Math.max(n, MINIMUM_DURATION), MAXIMUM_DURATION);
  };

  const fire = function (that, f, event) {
    try {
      if (typeof f === "function") {
        f.call(that, event);
      }
    } catch (e) {
      throwError(e);
    }
  };

  function EventSourcePolyfill(url, options) {
    EventTarget.call(this);
    options = options || {};

    this.onopen = undefined;
    this.onmessage = undefined;
    this.onerror = undefined;

    this.url = undefined;
    this.readyState = undefined;
    this.withCredentials = undefined;
    this.headers = undefined;

    this.heartbeatTimeout = undefined;
    this.Transport = undefined;

    this._close = undefined;

    start(this, url, options);
  }

  function getBestXHRTransport() {
    return (XMLHttpRequest !== undefined && ("withCredentials" in XMLHttpRequest.prototype)) || XDomainRequest === undefined
        ? new XMLHttpRequest()
        : new XDomainRequest();
  }

  const isFetchSupported = fetch !== undefined && Response !== undefined && "body" in Response.prototype;

  function start(es, url, options) {
    url = String(url);
    const withCredentials = Boolean(options.withCredentials);

    let initialRetry = clampDuration(1000);
    let heartbeatTimeout = parseDuration(options.heartbeatTimeout, 1000 * 60 * 60 * 24);

    let lastEventId = "";
    let retry = initialRetry;
    let wasActivity = false;
    let textLength = 0;
    const headers = options.headers || {};
    const TransportOption = options.Transport;
    const xhr = isFetchSupported && TransportOption === undefined ? undefined : new XHRWrapper(TransportOption !== undefined ? new TransportOption() : getBestXHRTransport());
    const transport = TransportOption != null && typeof TransportOption !== "string" ? new TransportOption() : (xhr === undefined ? new FetchTransport() : new XHRTransport());
    let abortController = undefined;
    let timeout = 0;
    let currentState = WAITING;
    let dataBuffer = "";
    let lastEventIdBuffer = "";
    let eventTypeBuffer = "";

    let textBuffer = "";
    let state = FIELD_START;
    let fieldStart = 0;
    let valueStart = 0;

    const onStart = function (status, statusText, contentType, headers) {
      let event;
      if (currentState === CONNECTING) {
        if (status === 200 && contentType !== undefined && contentTypeRegExp.test(contentType)) {
          currentState = OPEN;
          wasActivity = Date.now();
          retry = initialRetry;
          es.readyState = OPEN;
          event = new ConnectionEvent("open", {
            status: status,
            statusText: statusText,
            headers: headers
          });
          es.dispatchEvent(event);
          fire(es, es.onopen, event);
        } else {
          let message;
          if (status !== 200) {
            if (statusText) {
              statusText = statusText.replace(/\s+/g, " ");
            }
            message = "EventSource's response has a status " + status + " " + statusText + " that is not 200. Aborting the connection.";
          } else {
            message = "EventSource's response has a Content-Type specifying an unsupported type: " + (contentType === undefined ? "-" : contentType.replace(/\s+/g, " ")) + ". Aborting the connection.";
          }
          close();
          event = new ConnectionEvent("error", {
            status: status,
            statusText: statusText,
            headers: headers
          });
          es.dispatchEvent(event);
          fire(es, es.onerror, event);
          console.error(message);
        }
      }
    };

    const onProgress = function (textChunk) {
      let c;
      if (currentState === OPEN) {
        let n = -1;
        for (let i = 0; i < textChunk.length; i += 1) {
          c = textChunk.charCodeAt(i);
          if (c === "\n".charCodeAt(0) || c === "\r".charCodeAt(0)) {
            n = i;
          }
        }
        const chunk = (n !== -1 ? textBuffer : "") + textChunk.slice(0, n + 1);
        textBuffer = (n === -1 ? textBuffer : "") + textChunk.slice(n + 1);
        if (textChunk !== "") {
          wasActivity = Date.now();
          textLength += textChunk.length;
        }
        for (let position = 0; position < chunk.length; position += 1) {
          c = chunk.charCodeAt(position);
          if (state === AFTER_CR && c === "\n".charCodeAt(0)) {
            state = FIELD_START;
          } else {
            if (state === AFTER_CR) {
              state = FIELD_START;
            }
            if (c === "\r".charCodeAt(0) || c === "\n".charCodeAt(0)) {
              if (state !== FIELD_START) {
                if (state === FIELD) {
                  valueStart = position + 1;
                }
                const field = chunk.slice(fieldStart, valueStart - 1);
                const value = chunk.slice(valueStart + (valueStart < position && chunk.charCodeAt(valueStart) === " ".charCodeAt(0) ? 1 : 0), position);
                if (field === "data") {
                  dataBuffer += "\n";
                  dataBuffer += value;
                } else if (field === "id") {
                  lastEventIdBuffer = value;
                } else if (field === "event") {
                  eventTypeBuffer = value;
                } else if (field === "retry") {
                  initialRetry = parseDuration(value, initialRetry);
                  retry = initialRetry;
                } else if (field === "heartbeatTimeout") {
                  heartbeatTimeout = parseDuration(value, heartbeatTimeout);
                  if (timeout !== 0) {
                    clearTimeout(timeout);
                    timeout = setTimeout(function () {
                      onTimeout();
                    }, heartbeatTimeout);
                  }
                }
              }
              if (state === FIELD_START) {
                if (dataBuffer !== "") {
                  lastEventId = lastEventIdBuffer;
                  if (eventTypeBuffer === "") {
                    eventTypeBuffer = "message";
                  }
                  const event = new MessageEvent(eventTypeBuffer, {
                    data: dataBuffer.slice(1),
                    lastEventId: lastEventIdBuffer
                  });
                  es.dispatchEvent(event);
                  if (eventTypeBuffer === "open") {
                    fire(es, es.onopen, event);
                  } else if (eventTypeBuffer === "message") {
                    fire(es, es.onmessage, event);
                  } else if (eventTypeBuffer === "error") {
                    fire(es, es.onerror, event);
                  }
                  if (currentState === CLOSED) {
                    return;
                  }
                }
                dataBuffer = "";
                eventTypeBuffer = "";
              }
              state = c === "\r".charCodeAt(0) ? AFTER_CR : FIELD_START;
            } else {
              if (state === FIELD_START) {
                fieldStart = position;
                state = FIELD;
              }
              if (state === FIELD) {
                if (c === ":".charCodeAt(0)) {
                  valueStart = position + 1;
                  state = VALUE_START;
                }
              } else if (state === VALUE_START) {
                state = VALUE;
              }
            }
          }
        }
      }
    };

    const onFinish = function (error) {
      if (currentState === OPEN || currentState === CONNECTING) {
        currentState = WAITING;
        if (timeout !== 0) {
          clearTimeout(timeout);
          timeout = 0;
        }
        timeout = setTimeout(function () {
          onTimeout();
        }, retry);
        retry = clampDuration(Math.min(initialRetry * 16, retry * 2));

        es.readyState = CONNECTING;
        const event = new ErrorEvent("error", {error: error});
        es.dispatchEvent(event);
        fire(es, es.onerror, event);
        if (error !== undefined) {
          console.error(error);
        }
      }
    };

    const close = function () {
      currentState = CLOSED;
      if (abortController !== undefined) {
        abortController.abort();
        abortController = undefined;
      }
      if (timeout !== 0) {
        clearTimeout(timeout);
        timeout = 0;
      }
      es.readyState = CLOSED;
    };

    const onTimeout = function () {
      timeout = 0;

      if (currentState !== WAITING) {
        if (!wasActivity && abortController !== undefined) {
          onFinish(new Error("No activity within " + heartbeatTimeout + " milliseconds." + " " + (currentState === CONNECTING ? "No response received." : textLength + " chars received.") + " " + "Reconnecting."));
          if (abortController !== undefined) {
            abortController.abort();
            abortController = undefined;
          }
        } else {
          const nextHeartbeat = Math.max((wasActivity || Date.now()) + heartbeatTimeout - Date.now(), 1);
          wasActivity = false;
          timeout = setTimeout(function () {
            onTimeout();
          }, nextHeartbeat);
        }
        return;
      }

      wasActivity = false;
      textLength = 0;
      timeout = setTimeout(function () {
        onTimeout();
      }, heartbeatTimeout);

      currentState = CONNECTING;
      dataBuffer = "";
      eventTypeBuffer = "";
      lastEventIdBuffer = lastEventId;
      textBuffer = "";
      fieldStart = 0;
      valueStart = 0;
      state = FIELD_START;

      const withCredentials = es.withCredentials;
      const requestHeaders = {};
      requestHeaders["Accept"] = "text/event-stream";
      if (lastEventId !== "") {
        requestHeaders["Last-Event-ID"] = lastEventId;
      }
      const headers = es.headers;
      if (headers !== undefined) {
        for (const name in headers) {
          if (Object.prototype.hasOwnProperty.call(headers, name)) {
            requestHeaders[name] = headers[name];
          }
        }
      }
      try {
        abortController = transport.open(xhr, onStart, onProgress, onFinish, url, withCredentials, requestHeaders);
      } catch (error) {
        close();
        throw error;
      }
    };

    es.url = url;
    es.readyState = CONNECTING;
    es.withCredentials = withCredentials;
    es.headers = headers;
    es._close = close;

    onTimeout();
  }

  EventSourcePolyfill.prototype = Object.create(EventTarget.prototype);
  // EventSourcePolyfill.prototype.CONNECTING = CONNECTING;
  EventSourcePolyfill.prototype.OPEN = OPEN;
  // EventSourcePolyfill.prototype.CLOSED = CLOSED;
  EventSourcePolyfill.prototype.close = function () {
    this._close();
  };

  EventSourcePolyfill.CONNECTING = CONNECTING;
  EventSourcePolyfill.OPEN = OPEN;
  EventSourcePolyfill.CLOSED = CLOSED;
  EventSourcePolyfill.prototype.withCredentials = undefined;

  (function (factory) {
    if (typeof module === "object" && typeof module.exports === "object") {
      const v = factory(exports);
      if (v !== undefined) module.exports = v;
    } else if (typeof define === "function" && define.amd) {
      define(["exports"], factory);
    } else {
      factory(global);
    }
  })(function (exports) {
    exports.EventSource = EventSourcePolyfill;
    exports.NativeEventSource = NativeEventSource;
  });
}(typeof globalThis === 'undefined' ? (typeof window !== 'undefined' ? window : typeof self !== 'undefined' ? self : this) : globalThis));
