// ==UserScript==
// @name         xCloud Pad Stream & Controller Engine
// @namespace    https://www.xbox.com/play
// @version      1.0.0
// @description  Document-start stream enhancement, WebRTC SDP bitrate/codec control, stats overlay, and standard gamepad input engine for xbox.com/play.
// @match        https://www.xbox.com/*
// @run-at       document-start
// @license      MIT; Portions derived from Better xCloud (Copyright (c) redphx, https://github.com/redphx/better-xcloud)
// ==/UserScript==

(function () {
  'use strict';

  if (window.__XCLOUD_PAD_INITIALIZED__) return;
  window.__XCLOUD_PAD_INITIALIZED__ = true;

  // ============================================================================
  // 1. SAFE ERROR BOUNDARY & LOGGING
  // ============================================================================
  function safeLog(level, context, err) {
    try {
      const msg = err && err.stack ? `${context}: ${err.message}\n${err.stack}` : `${context}: ${String(err)}`;
      if (window.XCloudPadBridge && typeof window.XCloudPadBridge.logMessage === 'function') {
        window.XCloudPadBridge.logMessage(level, msg);
      }
    } catch (_) {}
  }

  function safeExec(context, fn, fallbackVal) {
    try {
      return fn();
    } catch (err) {
      safeLog('ERROR', context, err);
      return fallbackVal;
    }
  }

  // ============================================================================
  // 2. GM_* USERSCRIPT API SHIM BACKED BY ANDROID JAVASCRIPT INTERFACE
  // ============================================================================
  const gmStore = Object.assign(
    {
      targetResolution: '1080p_hq',
      maxBitrateKbps: 20000,
      codecProfile: 'high',
      showStatsOverlay: true,
      clarityBoost: 1,
      videoSaturation: 105,
      videoContrast: 103,
      videoBrightness: 100,
      audioBoostPercent: 100,
      preferIpv6: false,
      disableIdleCheck: true,
      enableDashboardGamepadNav: true,
      videoAspectMode: 'fit'
    },
    window.__XCLOUD_PAD_INITIAL_SETTINGS__ || {}
  );

  window.GM_info = {
    script: {
      name: 'xCloud Pad Engine',
      version: '1.0.0',
      description: 'Stream & Controller Engine for xCloud Pad'
    },
    scriptHandler: 'xCloudPadAndroid',
    version: '1.0.0'
  };

  window.GM_getValue = function (key, defaultValue) {
    return safeExec('GM_getValue', () => {
      if (Object.prototype.hasOwnProperty.call(gmStore, key)) {
        return gmStore[key];
      }
      if (window.XCloudPadBridge && typeof window.XCloudPadBridge.gmGetValue === 'function') {
        const raw = window.XCloudPadBridge.gmGetValue(String(key), JSON.stringify(defaultValue ?? null));
        if (raw !== null && raw !== undefined) {
          try {
            const parsed = JSON.parse(raw);
            gmStore[key] = parsed;
            return parsed;
          } catch (_) {
            return raw;
          }
        }
      }
      return defaultValue;
    }, defaultValue);
  };

  window.GM_setValue = function (key, value) {
    safeExec('GM_setValue', () => {
      gmStore[key] = value;
      if (window.XCloudPadBridge && typeof window.XCloudPadBridge.gmSetValue === 'function') {
        window.XCloudPadBridge.gmSetValue(String(key), JSON.stringify(value));
      }
    });
  };

  window.GM_deleteValue = function (key) {
    safeExec('GM_deleteValue', () => {
      delete gmStore[key];
      if (window.XCloudPadBridge && typeof window.XCloudPadBridge.gmDeleteValue === 'function') {
        window.XCloudPadBridge.gmDeleteValue(String(key));
      }
    });
  };

  window.GM_listValues = function () {
    return Object.keys(gmStore);
  };

  window.GM_addStyle = function (css) {
    return safeExec('GM_addStyle', () => {
      const style = document.createElement('style');
      style.setAttribute('data-xcloud-pad-style', 'true');
      style.textContent = css;
      const mount = () => {
        const target = document.head || document.documentElement;
        if (target) target.appendChild(style);
      };
      if (document.head || document.documentElement) {
        mount();
      } else {
        window.addEventListener('DOMContentLoaded', mount, { once: true });
      }
      return style;
    }, null);
  };

  window.GM_xmlhttpRequest = function (details) {
    safeExec('GM_xmlhttpRequest', () => {
      if (details && typeof details.onload === 'function') {
        setTimeout(() => details.onload({ status: 200, responseText: '{}' }), 0);
      }
    });
  };

  // ============================================================================
  // 3. FOCUS & VISIBILITY SPOOFING (KEEPS CLOUD CONTROLLER INPUT ACTIVE ALWAYS)
  // ============================================================================
  safeExec('FocusVisibilityAndDesktopPatch', () => {
    Document.prototype.hasFocus = function () {
      return true;
    };
    Object.defineProperty(Document.prototype, 'visibilityState', {
      get: function () {
        return 'visible';
      },
      configurable: true
    });
    Object.defineProperty(Document.prototype, 'hidden', {
      get: function () {
        return false;
      },
      configurable: true
    });

    try {
      Object.defineProperty(Navigator.prototype, 'platform', {
        get: () => 'Win32',
        configurable: true
      });
      Object.defineProperty(Navigator.prototype, 'userAgentData', {
        get: () => ({
          brands: [
            { brand: 'Google Chrome', version: '131' },
            { brand: 'Chromium', version: '131' },
            { brand: 'Not_A Brand', version: '24' }
          ],
          mobile: false,
          platform: 'Windows',
          getHighEntropyValues: async () => ({
            architecture: 'x86',
            bitness: '64',
            brands: [
              { brand: 'Google Chrome', version: '131' },
              { brand: 'Chromium', version: '131' },
              { brand: 'Not_A Brand', version: '24' }
            ],
            mobile: false,
            model: '',
            platform: 'Windows',
            platformVersion: '15.0.0',
            uaFullVersion: '131.0.0.0'
          }),
          toJSON: () => ({
            brands: [
              { brand: 'Google Chrome', version: '131' },
              { brand: 'Chromium', version: '131' },
              { brand: 'Not_A Brand', version: '24' }
            ],
            mobile: false,
            platform: 'Windows'
          })
        }),
        configurable: true
      });
    } catch (_) {}

    const origWinAddEvent = window.addEventListener;
    window.addEventListener = function (type, listener, options) {
      if (type === 'blur' || type === 'visibilitychange' || type === 'pagehide') {
        if (GM_getValue('disableIdleCheck', true)) {
          return;
        }
      }
      const res = origWinAddEvent.call(this, type, listener, options);
      if (type === 'gamepadconnected') {
        setTimeout(() => dispatchGamepadConnected(), 10);
        setTimeout(() => dispatchGamepadConnected(), 150);
      }
      return res;
    };

    if (window.EventTarget && EventTarget.prototype && EventTarget.prototype.addEventListener) {
      const origTargetAddEvent = EventTarget.prototype.addEventListener;
      EventTarget.prototype.addEventListener = function (type, listener, options) {
        if (
          (this === window || this === document) &&
          (type === 'blur' || type === 'visibilitychange' || type === 'pagehide' || type === 'freeze') &&
          GM_getValue('disableIdleCheck', true)
        ) {
          return;
        }
        const res = origTargetAddEvent.call(this, type, listener, options);
        if (type === 'gamepadconnected' && (this === window || this === globalThis)) {
          setTimeout(() => dispatchGamepadConnected(), 10);
        }
        return res;
      };
    }

    const origDocAddEvent = Document.prototype.addEventListener;
    Document.prototype.addEventListener = function (type, listener, options) {
      if (type === 'visibilitychange' || type === 'blur' || type === 'freeze') {
        if (GM_getValue('disableIdleCheck', true)) {
          return;
        }
      }
      return origDocAddEvent.call(this, type, listener, options);
    };
  });

  // ============================================================================
  // 4. STANDARD 17-BUTTON, 4-AXIS GAMEPAD EMULATION & CLOUD INPUT ENGINE
  // ============================================================================
  const BUTTON_COUNT = 17;
  const virtualButtons = [];
  for (let i = 0; i < BUTTON_COUNT; i++) {
    virtualButtons.push({ pressed: false, touched: false, value: 0.0 });
  }
  const virtualAxes = [0.0, 0.0, 0.0, 0.0];
  let lastGamepadTimestamp = performance.now();
  let lastConnectedDispatchTime = 0;
  let prevButtonsMask = 0;

  const silentNoOpActuator = Object.freeze({
    type: 'dual-rumble',
    playEffect: function () {
      return Promise.resolve('complete');
    },
    reset: function () {
      return Promise.resolve('complete');
    }
  });

  function hasActiveGamepadInput() {
    for (let i = 0; i < BUTTON_COUNT; i++) {
      if (virtualButtons[i].pressed || virtualButtons[i].value > 0.01) return true;
    }
    for (let a = 0; a < 4; a++) {
      if (Math.abs(virtualAxes[a]) > 0.01) return true;
    }
    return false;
  }

  function buildStandardGamepadSnapshot() {
    if (hasActiveGamepadInput()) {
      lastGamepadTimestamp = performance.now();
    }
    const btns = new Array(BUTTON_COUNT);
    for (let i = 0; i < BUTTON_COUNT; i++) {
      const b = virtualButtons[i];
      const btnObj = {
        pressed: Boolean(b.pressed),
        touched: Boolean(b.touched || b.pressed || b.value > 0.01),
        value: Number(b.value)
      };
      if (typeof window.GamepadButton === 'function' && window.GamepadButton.prototype) {
        try {
          Object.setPrototypeOf(btnObj, window.GamepadButton.prototype);
        } catch (_) {}
      }
      btns[i] = btnObj;
    }

    const snap = {
      id: 'Xbox 360 Controller (XInput STANDARD GAMEPAD)',
      index: 0,
      connected: true,
      mapping: 'standard',
      axes: [
        Number(virtualAxes[0]),
        Number(virtualAxes[1]),
        Number(virtualAxes[2]),
        Number(virtualAxes[3])
      ],
      buttons: btns,
      timestamp: lastGamepadTimestamp,
      vibrationActuator: silentNoOpActuator,
      hapticActuators: []
    };

    if (typeof window.Gamepad === 'function' && window.Gamepad.prototype) {
      try {
        Object.setPrototypeOf(snap, window.Gamepad.prototype);
      } catch (_) {}
    }
    return snap;
  }

  const origGetGamepads =
    (Navigator.prototype && Navigator.prototype.getGamepads) ||
    (navigator && navigator.getGamepads);

  function customGetGamepads() {
    const snap = buildStandardGamepadSnapshot();
    const result = [snap, null, null, null];
    if (typeof origGetGamepads === 'function') {
      try {
        const nativePads = origGetGamepads.call(navigator);
        if (nativePads) {
          for (let i = 0; i < nativePads.length; i++) {
            const np = nativePads[i];
            if (
              np &&
              np.connected &&
              np.mapping === 'standard' &&
              np.buttons &&
              np.buttons.length >= 12 &&
              !String(np.id || '').toLowerCase().includes('uinput') &&
              !String(np.id || '').toLowerCase().includes('sensor')
            ) {
              for (let b = 0; b < Math.min(np.buttons.length, BUTTON_COUNT); b++) {
                if (np.buttons[b] && (np.buttons[b].pressed || np.buttons[b].value > snap.buttons[b].value)) {
                  snap.buttons[b] = {
                    pressed: np.buttons[b].pressed,
                    touched: np.buttons[b].touched || np.buttons[b].pressed,
                    value: np.buttons[b].value
                  };
                }
              }
              for (let a = 0; a < Math.min(np.axes.length, 4); a++) {
                if (Math.abs(np.axes[a]) > Math.abs(snap.axes[a]) && Math.abs(np.axes[a]) > 0.08) {
                  snap.axes[a] = np.axes[a];
                }
              }
            }
          }
        }
      } catch (_) {}
    }
    return result;
  }

  safeExec('OverrideGetGamepads', () => {
    try {
      if (Navigator.prototype) {
        Object.defineProperty(Navigator.prototype, 'getGamepads', {
          value: customGetGamepads,
          configurable: true,
          writable: true
        });
        Object.defineProperty(Navigator.prototype, 'webkitGetGamepads', {
          value: customGetGamepads,
          configurable: true,
          writable: true
        });
      }
    } catch (_) {
      if (Navigator.prototype) {
        Navigator.prototype.getGamepads = customGetGamepads;
        Navigator.prototype.webkitGetGamepads = customGetGamepads;
      }
    }
    try {
      Object.defineProperty(navigator, 'getGamepads', {
        value: customGetGamepads,
        configurable: true,
        writable: true
      });
    } catch (_) {
      try {
        navigator.getGamepads = customGetGamepads;
      } catch (_) {}
    }
  });

  function createGamepadConnectedEvent(snap) {
    let ev;
    if (typeof window.GamepadEvent === 'function') {
      try {
        ev = new window.GamepadEvent('gamepadconnected', {
          bubbles: false,
          cancelable: false,
          gamepad: snap
        });
        if (ev.gamepad === snap) return ev;
      } catch (_) {}
    }
    try {
      ev = new Event('gamepadconnected', { bubbles: false, cancelable: false });
    } catch (_) {
      ev = document.createEvent('Event');
      ev.initEvent('gamepadconnected', false, false);
    }
    try {
      Object.defineProperty(ev, 'gamepad', {
        value: snap,
        enumerable: true,
        configurable: true
      });
    } catch (_) {
      ev.gamepad = snap;
    }
    return ev;
  }

  function dispatchGamepadConnected() {
    safeExec('dispatchGamepadConnected', () => {
      lastGamepadTimestamp = performance.now();
      lastConnectedDispatchTime = lastGamepadTimestamp;
      const snap = buildStandardGamepadSnapshot();
      const ev = createGamepadConnectedEvent(snap);
      window.dispatchEvent(ev);
      if (typeof window.ongamepadconnected === 'function') {
        try {
          window.ongamepadconnected(ev);
        } catch (_) {}
      }
    });
  }

  // Dashboard spatial navigation helper when browsing xbox.com/play before stream starts
  let prevNavButtons = new Array(BUTTON_COUNT).fill(false);
  function triggerDashboardNavigationIfNeeded() {
    if (!GM_getValue('enableDashboardGamepadNav', true)) return;
    const isLaunchPath = window.location.pathname.includes('/play/launch/');
    const activeVideo = document.querySelector('video');
    const isStreamingNow = Boolean(
      isLaunchPath && activeVideo && activeVideo.readyState >= 2 && !activeVideo.paused && activeVideo.videoWidth > 0
    );
    if (isStreamingNow) return;

    const stickUp = virtualAxes[1] < -0.55;
    const stickDown = virtualAxes[1] > 0.55;
    const stickLeft = virtualAxes[0] < -0.55;
    const stickRight = virtualAxes[0] > 0.55;

    const keyMap = [
      { idx: 0, active: Boolean(virtualButtons[0].pressed), key: 'Enter', code: 'Enter', keyCode: 13 },
      { idx: 1, active: Boolean(virtualButtons[1].pressed), key: 'Escape', code: 'Escape', keyCode: 27 },
      { idx: 12, active: Boolean(virtualButtons[12].pressed || stickUp), key: 'ArrowUp', code: 'ArrowUp', keyCode: 38 },
      { idx: 13, active: Boolean(virtualButtons[13].pressed || stickDown), key: 'ArrowDown', code: 'ArrowDown', keyCode: 40 },
      { idx: 14, active: Boolean(virtualButtons[14].pressed || stickLeft), key: 'ArrowLeft', code: 'ArrowLeft', keyCode: 37 },
      { idx: 15, active: Boolean(virtualButtons[15].pressed || stickRight), key: 'ArrowRight', code: 'ArrowRight', keyCode: 39 }
    ];

    for (const item of keyMap) {
      const isDown = item.active;
      const wasDown = prevNavButtons[item.idx];
      if (isDown && !wasDown) {
        const target = document.activeElement || document.body || document.documentElement;
        if (target) {
          target.dispatchEvent(new KeyboardEvent('keydown', { key: item.key, code: item.code, keyCode: item.keyCode, bubbles: true }));
        }
      } else if (!isDown && wasDown) {
        const target = document.activeElement || document.body || document.documentElement;
        if (target) {
          target.dispatchEvent(new KeyboardEvent('keyup', { key: item.key, code: item.code, keyCode: item.keyCode, bubbles: true }));
        }
      }
      prevNavButtons[item.idx] = isDown;
    }
  }

  // Called directly on every touch state change from Android without batching
  window.__xcloudPadUpdateGamepad = function (buttonsMask, ltValue, rtValue, lx, ly, rx, ry) {
    return safeExec('__xcloudPadUpdateGamepad', () => {
      const mask = Number(buttonsMask) >>> 0;
      for (let i = 0; i < BUTTON_COUNT; i++) {
        if (i === 6) {
          const v = Math.min(Math.max(Number(ltValue) || 0, 0), 1);
          virtualButtons[6].value = v;
          virtualButtons[6].pressed = v > 0.12 || Boolean((mask & (1 << 6)) !== 0);
          virtualButtons[6].touched = v > 0.02 || virtualButtons[6].pressed;
        } else if (i === 7) {
          const v = Math.min(Math.max(Number(rtValue) || 0, 0), 1);
          virtualButtons[7].value = v;
          virtualButtons[7].pressed = v > 0.12 || Boolean((mask & (1 << 7)) !== 0);
          virtualButtons[7].touched = v > 0.02 || virtualButtons[7].pressed;
        } else {
          const pressed = (mask & (1 << i)) !== 0;
          virtualButtons[i].pressed = pressed;
          virtualButtons[i].touched = pressed;
          virtualButtons[i].value = pressed ? 1.0 : 0.0;
        }
      }

      virtualAxes[0] = Math.min(Math.max(Number(lx) || 0, -1), 1);
      virtualAxes[1] = Math.min(Math.max(Number(ly) || 0, -1), 1);
      virtualAxes[2] = Math.min(Math.max(Number(rx) || 0, -1), 1);
      virtualAxes[3] = Math.min(Math.max(Number(ry) || 0, -1), 1);
      lastGamepadTimestamp = performance.now();

      const hasNewButtonPress = (mask & ~prevButtonsMask) !== 0;
      prevButtonsMask = mask;
      if (hasNewButtonPress || lastGamepadTimestamp - lastConnectedDispatchTime > 1200) {
        dispatchGamepadConnected();
      }

      try {
        if (typeof window.focus === 'function') window.focus();
      } catch (_) {}

      triggerDashboardNavigationIfNeeded();

      try {
        for (let f = 0; f < window.frames.length; f++) {
          const fw = window.frames[f];
          if (fw && fw !== window && typeof fw.__xcloudPadUpdateGamepad === 'function') {
            fw.__xcloudPadUpdateGamepad(buttonsMask, ltValue, rtValue, lx, ly, rx, ry);
          }
        }
      } catch (_) {}
      return true;
    }, false);
  };

  // Periodically ensure gamepadconnected is announced when page scripts load
  setTimeout(dispatchGamepadConnected, 100);
  setTimeout(dispatchGamepadConnected, 600);
  setTimeout(dispatchGamepadConnected, 1500);
  setTimeout(dispatchGamepadConnected, 3500);
  window.addEventListener('DOMContentLoaded', () => setTimeout(dispatchGamepadConnected, 200));
  window.addEventListener('load', () => setTimeout(dispatchGamepadConnected, 300));
  window.addEventListener('popstate', () => setTimeout(dispatchGamepadConnected, 200));

  // ============================================================================
  // 5. WEBRTC STREAM BITRATE, CODEC, RESOLUTION & STATS ENGINE
  // ============================================================================
  const activePeerConnections = new Set();

  function patchSdpBitrateAndCodec(sdp) {
    if (!sdp || typeof sdp !== 'string') return sdp;
    return safeExec('patchSdpBitrateAndCodec', () => {
      const maxKbps = Number(GM_getValue('maxBitrateKbps', 20000)) || 20000;
      const minKbps = Math.max(1000, Math.floor(maxKbps * 0.35));
      const startKbps = Math.max(2000, Math.floor(maxKbps * 0.75));
      let lines = sdp.split('\r\n');
      let inVideo = false;
      const out = [];

      for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        if (line.startsWith('m=video')) {
          inVideo = true;
          out.push(line);
          out.push(`b=AS:${maxKbps}`);
          out.push(`b=TIAS:${maxKbps * 1000}`);
          continue;
        } else if (line.startsWith('m=')) {
          inVideo = false;
        }

        if (inVideo && (line.startsWith('b=AS:') || line.startsWith('b=TIAS:'))) {
          continue;
        }

        if (inVideo && line.startsWith('a=fmtp:')) {
          if (!line.includes('x-google-max-bitrate')) {
            out.push(`${line};x-google-max-bitrate=${maxKbps};x-google-min-bitrate=${minKbps};x-google-start-bitrate=${startKbps}`);
            continue;
          }
        }
        out.push(line);
      }
      return out.join('\r\n');
    }, sdp);
  }

  safeExec('PatchRTCPeerConnection', () => {
    const OrigPeerConnection = window.RTCPeerConnection || window.webkitRTCPeerConnection;
    if (!OrigPeerConnection) return;

    // Codec profile preference via RTCRtpReceiver.getCapabilities
    if (window.RTCRtpReceiver && typeof window.RTCRtpReceiver.getCapabilities === 'function') {
      const origGetCapabilities = window.RTCRtpReceiver.getCapabilities.bind(window.RTCRtpReceiver);
      window.RTCRtpReceiver.getCapabilities = function (kind) {
        const caps = origGetCapabilities(kind);
        if (kind === 'video' && caps && Array.isArray(caps.codecs)) {
          safeExec('ReorderVideoCodecs', () => {
            const pref = String(GM_getValue('codecProfile', 'high')).toLowerCase();
            const profileMatch = pref === 'high' ? '4d' : pref === 'low' ? '4200' : '42e0';
            const preferred = [];
            const others = [];
            for (const c of caps.codecs) {
              const fmtp = String(c.sdpFmtpLine || '').toLowerCase();
              const mime = String(c.mimeType || '').toLowerCase();
              if (mime.includes('h264') && fmtp.includes(`profile-level-id=${profileMatch}`)) {
                preferred.push(c);
              } else {
                others.push(c);
              }
            }
            if (preferred.length > 0) {
              caps.codecs = preferred.concat(others);
            }
          });
        }
        return caps;
      };
    }

    const origSetLocalDesc = OrigPeerConnection.prototype.setLocalDescription;
    OrigPeerConnection.prototype.setLocalDescription = function (description) {
      activePeerConnections.add(this);
      if (description && description.sdp) {
        description = {
          type: description.type,
          sdp: patchSdpBitrateAndCodec(description.sdp)
        };
      }
      return origSetLocalDesc.call(this, description);
    };

    const origSetRemoteDesc = OrigPeerConnection.prototype.setRemoteDescription;
    OrigPeerConnection.prototype.setRemoteDescription = function (description) {
      activePeerConnections.add(this);
      if (description && description.sdp) {
        description = {
          type: description.type,
          sdp: patchSdpBitrateAndCodec(description.sdp)
        };
      }
      return origSetRemoteDesc.call(this, description);
    };
  });

  // Intercept fetch for target resolution negotiation (720p / 1080p / 1080p HQ)
  safeExec('PatchFetchResolution', () => {
    const origFetch = window.fetch;
    if (typeof origFetch !== 'function') return;

    window.fetch = async function (input, init) {
      try {
        const url = typeof input === 'string' ? input : input && input.url ? input.url : '';
        if (url.includes('/sessions/cloud/play') && init && typeof init.body === 'string') {
          const resSetting = String(GM_getValue('targetResolution', '1080p_hq'));
          const bodyObj = JSON.parse(init.body);
          if (bodyObj && bodyObj.clientStreamingConfigOverrides) {
            bodyObj.clientStreamingConfigOverrides.videoConfiguration = Object.assign(
              bodyObj.clientStreamingConfigOverrides.videoConfiguration || {},
              resSetting.startsWith('720p')
                ? { maxWidth: 1280, maxHeight: 720, targetFrameRate: 60 }
                : { maxWidth: 1920, maxHeight: 1080, targetFrameRate: 60 }
            );
          }
          init.body = JSON.stringify(bodyObj);
        }
      } catch (_) {}
      return origFetch.call(this, input, init);
    };
  });

  // ============================================================================
  // 6. VIDEO CLARITY FILTERS, ASPECT RATIO & AUDIO BOOST
  // ============================================================================
  let audioCtx = null;
  let audioGainNode = null;
  const hookedMediaElements = new WeakSet();

  function ensureClaritySvgFilter() {
    if (document.getElementById('xcp-svg-filters')) return;
    const svgNS = 'http://www.w3.org/2000/svg';
    const svg = document.createElementNS(svgNS, 'svg');
    svg.id = 'xcp-svg-filters';
    svg.setAttribute(' style', 'position:absolute;width:0;height:0;pointer-events:none;');
    svg.innerHTML = `
      <defs>
        <filter id="xcp-clarity-1">
          <feConvolveMatrix order="3" kernelMatrix="0 -0.25 0 -0.25 2 -0.25 0 -0.25 0" preserveAlpha="true"/>
        </filter>
        <filter id="xcp-clarity-2">
          <feConvolveMatrix order="3" kernelMatrix="0 -0.45 0 -0.45 2.8 -0.45 0 -0.45 0" preserveAlpha="true"/>
        </filter>
      </defs>
    `;
    (document.body || document.documentElement).appendChild(svg);
  }

  function applyVideoAndAudioEnhancements() {
    safeExec('applyVideoAndAudioEnhancements', () => {
      ensureClaritySvgFilter();
      const clarity = Number(GM_getValue('clarityBoost', 1)) || 0;
      const sat = Number(GM_getValue('videoSaturation', 105)) || 100;
      const con = Number(GM_getValue('videoContrast', 103)) || 100;
      const bri = Number(GM_getValue('videoBrightness', 100)) || 100;
      const aspectMode = String(GM_getValue('videoAspectMode', 'fit'));
      const audioBoost = Number(GM_getValue('audioBoostPercent', 100)) || 100;

      const filterParts = [];
      if (sat !== 100) filterParts.push(`saturate(${sat}%)`);
      if (con !== 100) filterParts.push(`contrast(${con}%)`);
      if (bri !== 100) filterParts.push(`brightness(${bri}%)`);
      if (clarity === 1) filterParts.push('url(#xcp-clarity-1)');
      if (clarity >= 2) filterParts.push('url(#xcp-clarity-2)');

      const videos = document.querySelectorAll('video');
      videos.forEach((video) => {
        video.style.filter = filterParts.join(' ');
        if (aspectMode === 'fill_16_9') {
          video.style.objectFit = 'fill';
          video.style.aspectRatio = '16 / 9';
        } else if (aspectMode === '4_3') {
          video.style.objectFit = 'contain';
          video.style.aspectRatio = '4 / 3';
        } else if (aspectMode === 'integer') {
          video.style.objectFit = 'none';
          video.style.imageRendering = 'pixelated';
        } else {
          video.style.objectFit = 'contain';
          video.style.imageRendering = 'auto';
        }

        if (audioBoost > 100 && !hookedMediaElements.has(video)) {
          try {
            const Ctx = window.AudioContext || window.webkitAudioContext;
            if (Ctx) {
              if (!audioCtx) {
                audioCtx = new Ctx();
                audioGainNode = audioCtx.createGain();
                audioGainNode.connect(audioCtx.destination);
              }
              const src = audioCtx.createMediaElementSource(video);
              src.connect(audioGainNode);
              hookedMediaElements.add(video);
            }
          } catch (_) {}
        }
      });

      if (audioGainNode) {
        audioGainNode.gain.value = Math.max(0.5, Math.min(3.0, audioBoost / 100));
      }
    });
  }

  // ============================================================================
  // 7. STREAM STATS HUD OVERLAY & ANDROID LIVE TELEMETRY
  // ============================================================================
  let prevBytesReceived = 0;
  let prevStatsTimestamp = 0;
  let prevFramesDecoded = 0;
  let wasStreamingState = false;

  function ensureStatsOverlayElement() {
    let el = document.getElementById('xcp-stream-stats-bar');
    if (!el && document.body) {
      el = document.createElement('div');
      el.id = 'xcp-stream-stats-bar';
      el.style.cssText = [
        'position:fixed',
        'top:8px',
        'left:50%',
        'transform:translateX(-50%)',
        'z-index:2147483640',
        'background:rgba(9, 9, 11, 0.88)',
        'backdrop-filter:blur(10px)',
        'border:1px solid rgba(255, 255, 255, 0.28)',
        'color:#FFFFFF',
        'font-family:ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
        'font-size:11px',
        'font-weight:600',
        'padding:4px 12px',
        'border-radius:999px',
        'pointer-events:none',
        'white-space:nowrap',
        'box-shadow:0 4px 16px rgba(0,0,0,0.65)',
        'display:none'
      ].join(';');
      document.body.appendChild(el);
    }
    return el;
  }

  function detectCurrentGameTitle() {
    try {
      const m = window.location.pathname.match(/\/play\/launch\/([^/]+)/i);
      if (m && m[1]) {
        return decodeURIComponent(m[1]).replace(/-/g, ' ');
      }
      const titleEl = document.querySelector('[data-testid="product-title"], h1');
      if (titleEl && titleEl.textContent) {
        return titleEl.textContent.trim().slice(0, 48);
      }
    } catch (_) {}
    return 'Cloud Session';
  }

  async function pollStreamStats() {
    safeExec('pollStreamStats', async () => {
      const video = document.querySelector('video');
      const isStreaming = Boolean(video && video.readyState >= 2 && video.videoWidth > 0 && !video.paused);
      const gameTitle = isStreaming ? detectCurrentGameTitle() : '';

      if (isStreaming !== wasStreamingState) {
        wasStreamingState = isStreaming;
        if (window.XCloudPadBridge && typeof window.XCloudPadBridge.onStreamStateChanged === 'function') {
          window.XCloudPadBridge.onStreamStateChanged(isStreaming, gameTitle);
        }
        if (isStreaming) {
          applyVideoAndAudioEnhancements();
        }
      }

      let fps = 0;
      let width = video ? video.videoWidth || 0 : 0;
      let height = video ? video.videoHeight || 0 : 0;
      let bitrateKbps = 0;
      let rttMs = 0;
      let decodeMs = 0;
      let framesDropped = 0;
      let packetLossPct = 0;

      for (const pc of Array.from(activePeerConnections)) {
        if (pc.connectionState === 'closed' || pc.connectionState === 'failed') {
          activePeerConnections.delete(pc);
          continue;
        }
        try {
          const stats = await pc.getStats();
          stats.forEach((report) => {
            if (report.type === 'inbound-rtp' && report.kind === 'video') {
              const now = report.timestamp || performance.now();
              const bytes = report.bytesReceived || 0;
              const frames = report.framesDecoded || 0;
              if (prevStatsTimestamp > 0 && now > prevStatsTimestamp) {
                const dtSec = (now - prevStatsTimestamp) / 1000;
                if (dtSec > 0) {
                  bitrateKbps = Math.round(((bytes - prevBytesReceived) * 8) / 1000 / dtSec);
                  fps = Math.round((frames - prevFramesDecoded) / dtSec);
                }
              }
              prevBytesReceived = bytes;
              prevFramesDecoded = frames;
              prevStatsTimestamp = now;

              if (report.frameWidth) width = report.frameWidth;
              if (report.frameHeight) height = report.frameHeight;
              if (report.framesPerSecond) fps = Math.round(report.framesPerSecond);
              framesDropped = report.framesDropped || 0;
              if (report.totalDecodeTime && frames > 0) {
                decodeMs = Number(((report.totalDecodeTime / frames) * 1000).toFixed(1));
              }
              const lost = report.packetsLost || 0;
              const recv = report.packetsReceived || 0;
              if (lost + recv > 0) {
                packetLossPct = Number(((lost / (lost + recv)) * 100).toFixed(2));
              }
            } else if (report.type === 'candidate-pair' && report.state === 'succeeded' && report.currentRoundTripTime !== undefined) {
              rttMs = Math.round(report.currentRoundTripTime * 1000);
            }
          });
        } catch (_) {}
      }

      const detectedPads = (navigator.getGamepads ? Array.from(navigator.getGamepads()).filter(Boolean).length : 1);
      const telemetry = {
        isStreaming,
        gameTitle,
        fps: fps || (isStreaming ? 60 : 0),
        width: width || (isStreaming ? 1920 : 0),
        height: height || (isStreaming ? 1080 : 0),
        bitrateKbps,
        rttMs,
        decodeMs,
        framesDropped,
        packetLossPct,
        detectedGamepads: detectedPads
      };

      if (window.XCloudPadBridge && typeof window.XCloudPadBridge.onStreamTelemetry === 'function') {
        window.XCloudPadBridge.onStreamTelemetry(JSON.stringify(telemetry));
      }

      const bar = ensureStatsOverlayElement();
      if (bar) {
        const showOverlay = Boolean(GM_getValue('showStatsOverlay', true));
        if (showOverlay && isStreaming) {
          const mbps = (bitrateKbps / 1000).toFixed(1);
          bar.style.display = 'block';
          bar.textContent = `${telemetry.width}x${telemetry.height} • ${telemetry.fps} FPS • ${mbps} Mbps • Ping ${rttMs}ms • Dec ${decodeMs}ms • Loss ${packetLossPct}%`;
        } else {
          bar.style.display = 'none';
        }
      }
    });
  }

  setInterval(pollStreamStats, 1000);

  // ============================================================================
  // 8. IN-PAGE ENGINE SETTINGS MODAL & XBOX STREAM ACTIONS
  // ============================================================================
  function toggleInPageEngineModal() {
    safeExec('toggleInPageEngineModal', () => {
      let modal = document.getElementById('xcp-engine-modal');
      if (modal) {
        modal.remove();
        return;
      }
      modal = document.createElement('div');
      modal.id = 'xcp-engine-modal';
      modal.style.cssText = [
        'position:fixed',
        'inset:0',
        'z-index:2147483645',
        'background:rgba(0, 0, 0, 0.82)',
        'backdrop-filter:blur(12px)',
        'display:flex',
        'align-items:center',
        'justify-content:center',
        'font-family:system-ui,-apple-system,sans-serif',
        'color:#FFFFFF'
      ].join(';');

      const res = GM_getValue('targetResolution', '1080p_hq');
      const bitrate = GM_getValue('maxBitrateKbps', 20000);
      const codec = GM_getValue('codecProfile', 'high');
      const clarity = GM_getValue('clarityBoost', 1);
      const sat = GM_getValue('videoSaturation', 105);
      const con = GM_getValue('videoContrast', 103);
      const showStats = GM_getValue('showStatsOverlay', true);

      modal.innerHTML = `
        <div style="width:min(92vw,440px);background:#09090B;border:1px solid rgba(255,255,255,0.28);border-radius:18px;padding:18px;box-shadow:0 20px 50px rgba(0,0,0,0.85);">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
            <div style="font-size:16px;font-weight:700;color:#FFFFFF;">xCloud Pad Video & Stream Engine</div>
            <button id="xcp-modal-close" style="background:rgba(255,255,255,0.12);border:none;color:#FFFFFF;border-radius:999px;padding:6px 12px;cursor:pointer;font-weight:600;">Close</button>
          </div>
          <div style="display:flex;flex-direction:column;gap:10px;font-size:13px;">
            <label style="display:flex;justify-content:space-between;align-items:center;">
              <span>Target Resolution</span>
              <select id="xcp-sel-res" style="background:#18181B;color:#FFFFFF;border:1px solid #3F3F46;border-radius:8px;padding:6px 10px;">
                <option value="720p" ${res === '720p' ? 'selected' : ''}>720p (60 FPS)</option>
                <option value="1080p" ${res === '1080p' ? 'selected' : ''}>1080p (Standard)</option>
                <option value="1080p_hq" ${res === '1080p_hq' ? 'selected' : ''}>1080p (High Bitrate HQ)</option>
              </select>
            </label>
            <label style="display:flex;justify-content:space-between;align-items:center;">
              <span>Max Bitrate (kbps)</span>
              <input id="xcp-inp-bitrate" type="number" min="2000" max="35000" step="1000" value="${bitrate}" style="width:110px;background:#18181B;color:#FFFFFF;border:1px solid #3F3F46;border-radius:8px;padding:6px 10px;"/>
            </label>
            <label style="display:flex;justify-content:space-between;align-items:center;">
              <span>H.264 Codec Profile</span>
              <select id="xcp-sel-codec" style="background:#18181B;color:#FFFFFF;border:1px solid #3F3F46;border-radius:8px;padding:6px 10px;">
                <option value="high" ${codec === 'high' ? 'selected' : ''}>High (Best Quality)</option>
                <option value="main" ${codec === 'main' ? 'selected' : ''}>Main (Balanced)</option>
                <option value="low" ${codec === 'low' ? 'selected' : ''}>Low (Lowest Latency)</option>
              </select>
            </label>
            <label style="display:flex;justify-content:space-between;align-items:center;">
              <span>Sharpness (Clarity Boost)</span>
              <select id="xcp-sel-clarity" style="background:#18181B;color:#FFFFFF;border:1px solid #3F3F46;border-radius:8px;padding:6px 10px;">
                <option value="0" ${Number(clarity) === 0 ? 'selected' : ''}>Off</option>
                <option value="1" ${Number(clarity) === 1 ? 'selected' : ''}>Medium</option>
                <option value="2" ${Number(clarity) >= 2 ? 'selected' : ''}>High</option>
              </select>
            </label>
            <label style="display:flex;justify-content:space-between;align-items:center;">
              <span>Saturation (${sat}%)</span>
              <input id="xcp-inp-sat" type="range" min="50" max="200" step="1" value="${sat}" style="width:140px;accent-color:#FFFFFF;"/>
            </label>
            <label style="display:flex;justify-content:space-between;align-items:center;">
              <span>Contrast (${con}%)</span>
              <input id="xcp-inp-con" type="range" min="50" max="200" step="1" value="${con}" style="width:140px;accent-color:#FFFFFF;"/>
            </label>
            <label style="display:flex;justify-content:space-between;align-items:center;">
              <span>Show Stream Stats Bar</span>
              <input id="xcp-chk-stats" type="checkbox" ${showStats ? 'checked' : ''} style="width:18px;height:18px;accent-color:#FFFFFF;"/>
            </label>
            <button id="xcp-modal-save" style="margin-top:6px;background:#FFFFFF;color:#000000;border:none;border-radius:10px;padding:10px;font-weight:700;cursor:pointer;">Apply Settings Now</button>
          </div>
        </div>
      `;

      document.body.appendChild(modal);
      document.getElementById('xcp-modal-close').onclick = () => modal.remove();
      document.getElementById('xcp-modal-save').onclick = () => {
        GM_setValue('targetResolution', document.getElementById('xcp-sel-res').value);
        GM_setValue('maxBitrateKbps', Number(document.getElementById('xcp-inp-bitrate').value) || 20000);
        GM_setValue('codecProfile', document.getElementById('xcp-sel-codec').value);
        GM_setValue('clarityBoost', Number(document.getElementById('xcp-sel-clarity').value) || 0);
        GM_setValue('videoSaturation', Number(document.getElementById('xcp-inp-sat').value) || 100);
        GM_setValue('videoContrast', Number(document.getElementById('xcp-inp-con').value) || 100);
        GM_setValue('showStatsOverlay', Boolean(document.getElementById('xcp-chk-stats').checked));
        applyVideoAndAudioEnhancements();
        pollStreamStats();
        modal.remove();
      };
    });
  }

  window.__xcloudPadAction = function (action, payloadJson) {
    return safeExec('__xcloudPadAction', () => {
      const payload = payloadJson ? JSON.parse(payloadJson) : {};
      switch (action) {
        case 'xbox_guide': {
          // Pulse Xbox Nexus button[16] and also click HUD guide if visible
          virtualButtons[16].pressed = true;
          virtualButtons[16].touched = true;
          virtualButtons[16].value = 1.0;
          lastGamepadTimestamp = performance.now();
          setTimeout(() => {
            virtualButtons[16].pressed = false;
            virtualButtons[16].touched = false;
            virtualButtons[16].value = 0.0;
            lastGamepadTimestamp = performance.now();
          }, 180);
          const guideBtn = document.querySelector(
            'button[aria-label*="Xbox" i], button[aria-label*="Guide" i], [data-testid*="nexus" i], [data-testid*="guide" i]'
          );
          if (guideBtn) guideBtn.click();
          break;
        }
        case 'quit_game': {
          const quitBtn = document.querySelector(
            'button[aria-label*="Quit" i], button[aria-label*="Disconnect" i], [data-testid*="quit" i]'
          );
          if (quitBtn) {
            quitBtn.click();
          } else {
            window.location.href = 'https://www.xbox.com/play';
          }
          break;
        }
        case 'toggle_mic': {
          const micBtn = document.querySelector(
            'button[aria-label*="Mic" i], button[aria-label*="Mute" i], [data-testid*="mic" i]'
          );
          if (micBtn) micBtn.click();
          break;
        }
        case 'toggle_captions': {
          const ccBtn = document.querySelector(
            'button[aria-label*="Caption" i], button[aria-label*="Subtitle" i], [data-testid*="caption" i]'
          );
          if (ccBtn) ccBtn.click();
          break;
        }
        case 'open_stream_menu': {
          const moreBtn = document.querySelector(
            'button[aria-label*="More" i], button[aria-label*="Menu" i], [data-testid*="hud" i] button'
          );
          if (moreBtn) moreBtn.click();
          break;
        }
        case 'open_engine_settings': {
          toggleInPageEngineModal();
          break;
        }
        case 'apply_settings': {
          Object.keys(payload).forEach((k) => {
            gmStore[k] = payload[k];
          });
          applyVideoAndAudioEnhancements();
          pollStreamStats();
          break;
        }
      }
      return true;
    }, false);
  };

  // Inject clean UI stylesheet for xbox.com/play
  GM_addStyle(`
    /* Hide mobile app install promotional banners & keep video clean */
    [data-testid="install-app-banner"],
    [class*="InstallAppBanner"],
    [class*="UnsupportedBrowserBanner"] {
      display: none !important;
    }
    video {
      background-color: #000 !important;
    }
  `);

  window.addEventListener('DOMContentLoaded', () => {
    applyVideoAndAudioEnhancements();
    const obs = new MutationObserver(() => {
      applyVideoAndAudioEnhancements();
    });
    if (document.documentElement) {
      obs.observe(document.documentElement, { childList: true, subtree: true });
    }
  });
})();
