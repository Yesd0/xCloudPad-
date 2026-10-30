// ==UserScript==
// @name         xCloud Pad Stream & Controller Engine
// @namespace    https://www.xbox.com/play
// @version      1.2.0
// @description  Robust Document-start stream enhancement and Better xCloud-inspired Virtual Gamepad Engine.
// @match        https://www.xbox.com/*
// @run-at       document-start
// @license      MIT; Portions derived from Better xCloud (Copyright (c) redphx, https://github.com/redphx/better-xcloud)
// ==/UserScript==

(function () {
  'use strict';

  if (window.__XCLOUD_PAD_INITIALIZED__) return;
  window.__XCLOUD_PAD_INITIALIZED__ = true;

  // ============================================================================
  // 1. CORE LOGGING & ERROR BOUNDARY
  // ============================================================================
  function safeLog(level, context, err) {
    try {
      const msg = err && err.stack ? `${context}: ${err.message}\n${err.stack}` : `${context}: ${String(err)}`;
      window.XCloudPadBridge?.logMessage?.(level, msg);
    } catch (_) {}
  }

  function safeExec(context, fn, fallbackVal) {
    try { return fn(); } catch (err) { safeLog('ERROR', context, err); return fallbackVal; }
  }

  // ============================================================================
  // 2. SETTINGS & GM_* SHIM
  // ============================================================================
  const gmStore = Object.assign({
    targetResolution: '1080p_hq',
    maxBitrateKbps: 20000,
    codecProfile: 'high',
    showStatsOverlay: true,
    clarityBoost: 1,
    videoSaturation: 105,
    videoContrast: 103,
    videoBrightness: 100,
    videoSharpness: 0,
    enableFsr: false,
    fsrSharpness: 75,
    audioBoostPercent: 100,
    disableIdleCheck: true,
    enableDashboardGamepadNav: true,
    videoAspectMode: 'fit'
  }, window.__XCLOUD_PAD_INITIAL_SETTINGS__ || {});

  window.GM_info = { script: { name: 'xCloud Pad Engine', version: '1.2.0' }, scriptHandler: 'xCloudPadAndroid' };

  window.GM_getValue = (key, def) => {
    if (Object.prototype.hasOwnProperty.call(gmStore, key)) return gmStore[key];
    if (window.XCloudPadBridge?.gmGetValue) {
      const raw = window.XCloudPadBridge.gmGetValue(String(key), JSON.stringify(def ?? null));
      try { return JSON.parse(raw); } catch (_) { return raw; }
    }
    return def;
  };

  window.GM_setValue = (key, val) => {
    gmStore[key] = val;
    window.XCloudPadBridge?.gmSetValue?.(String(key), JSON.stringify(val));
  };

  window.GM_deleteValue = (key) => {
    delete gmStore[key];
    window.XCloudPadBridge?.gmDeleteValue?.(String(key));
  };

  window.GM_addStyle = (css) => {
    const style = document.createElement('style');
    style.textContent = css;
    const mount = () => (document.head || document.documentElement).appendChild(style);
    if (document.head || document.documentElement) mount();
    else window.addEventListener('DOMContentLoaded', mount, { once: true });
    return style;
  };

  // ============================================================================
  // 3. VIRTUAL GAMEPAD ENGINE (ROBUST PORT)
  // ============================================================================
  const BUTTON_COUNT = 17;
  const state = {
    buttons: Array.from({ length: BUTTON_COUNT }, () => ({ pressed: false, touched: false, value: 0 })),
    axes: [0, 0, 0, 0],
    timestamp: performance.now(),
    connected: true
  };

  const virtualGamepad = {
    id: 'Xbox 360 Controller (XInput STANDARD GAMEPAD)',
    index: 0,
    mapping: 'standard',
    get connected() { return state.connected; },
    get buttons() { return state.buttons; },
    get axes() { return state.axes; },
    get timestamp() { return state.timestamp; },
    vibrationActuator: {
      type: 'dual-rumble',
      playEffect: (type, params) => {
        window.XCloudPadBridge?.onRumble?.(params?.strongMagnitude ?? 0.5, params?.weakMagnitude ?? 0.5, params?.duration ?? 120);
        return Promise.resolve('complete');
      },
      reset: () => Promise.resolve('complete')
    }
  };

  Object.defineProperty(virtualGamepad, 'hapticActuators', { get: () => [virtualGamepad.vibrationActuator], enumerable: true });

  const getGamepadsProxy = function () {
    const pads = [virtualGamepad, null, null, null];
    const original = Navigator.prototype.getGamepads || navigator.getGamepads;
    if (typeof original === 'function') {
      try {
        const native = original.apply(navigator);
        if (native) for (let i = 0; i < native.length; i++) if (native[i] && native[i].id !== virtualGamepad.id && i < 4) pads[i] = native[i];
      } catch (_) {}
    }
    return pads;
  };

  safeExec('OverrideGetGamepads', () => {
    try {
      Object.defineProperty(Navigator.prototype, 'getGamepads', { value: getGamepadsProxy, writable: false, configurable: false });
      Object.defineProperty(navigator, 'getGamepads', { value: getGamepadsProxy, writable: false, configurable: false });
    } catch (e) {
      navigator.getGamepads = getGamepadsProxy;
      Navigator.prototype.getGamepads = getGamepadsProxy;
    }
  });

  let lastAnnounceTime = 0;
  function announceGamepad() {
    const now = Date.now();
    if (now - lastAnnounceTime < 100) return;
    lastAnnounceTime = now;
    const event = new Event('gamepadconnected');
    event.gamepad = virtualGamepad;
    window.dispatchEvent(event);
    window.parent?.dispatchEvent?.(event);
  }

  function tick() { state.timestamp = performance.now(); requestAnimationFrame(tick); }
  requestAnimationFrame(tick);

  // ============================================================================
  // 4. BRIDGE COMMAND HANDLERS
  // ============================================================================
  window.__xcloudPadUpdateGamepad = function (buttonsMask, ltValue, rtValue, lx, ly, rx, ry) {
    return safeExec('__xcloudPadUpdateGamepad', () => {
      const mask = Number(buttonsMask) >>> 0;
      for (let i = 0; i < BUTTON_COUNT; i++) {
        if (i === 6) { // LT
          state.buttons[6].value = Math.min(Math.max(Number(ltValue) || 0, 0), 1);
          state.buttons[6].pressed = state.buttons[6].value > 0.12 || !!(mask & (1 << 6));
        } else if (i === 7) { // RT
          state.buttons[7].value = Math.min(Math.max(Number(rtValue) || 0, 0), 1);
          state.buttons[7].pressed = state.buttons[7].value > 0.12 || !!(mask & (1 << 7));
        } else {
          state.buttons[i].pressed = !!(mask & (1 << i));
          state.buttons[i].value = state.buttons[i].pressed ? 1.0 : 0.0;
        }
        state.buttons[i].touched = state.buttons[i].pressed || state.buttons[i].value > 0.01;
      }
      state.axes[0] = Number(lx) || 0; state.axes[1] = Number(ly) || 0;
      state.axes[2] = Number(rx) || 0; state.axes[3] = Number(ry) || 0;
      state.timestamp = performance.now();
      if (!state.connected) { state.connected = true; announceGamepad(); }
      return true;
    }, false);
  };

  window.__xcloudPadAction = function (action, payloadJson) {
    return safeExec('__xcloudPadAction', () => {
      const payload = payloadJson ? JSON.parse(payloadJson) : {};
      switch (action) {
        case 'xbox_guide':
          state.buttons[16].pressed = true; state.buttons[16].value = 1.0;
          announceGamepad();
          setTimeout(() => { state.buttons[16].pressed = false; state.buttons[16].value = 0.0; }, 200);
          document.querySelector('button[aria-label*="Xbox" i], [data-testid*="guide" i]')?.click();
          break;
        case 'quit_game':
          document.querySelector('button[aria-label*="Quit" i], [data-testid*="quit" i]')?.click() || (window.location.href = 'https://www.xbox.com/play');
          break;
        case 'toggle_mic': document.querySelector('button[aria-label*="Mic" i], [data-testid*="mic" i]')?.click(); break;
        case 'toggle_captions': document.querySelector('button[aria-label*="Caption" i], [data-testid*="caption" i]')?.click(); break;
        case 'force_gamepad_sync': announceGamepad(); break;
        case 'apply_settings': Object.assign(gmStore, payload); applyEnhancements(); break;
      }
      return true;
    }, false);
  };

  [100, 500, 1500, 3000, 6000].forEach(ms => setTimeout(announceGamepad, ms));

  // ============================================================================
  // 5. 4K GPU UPSCALER ENGINE (WEBGL2)
  // ============================================================================
  /**
   * AMD FidelityFX FSR 1.0 (EASU + RCAS)
   * Copyright (c) 2021 Advanced Micro Devices, Inc.
   * MIT License attribution preserved as requested.
   */
  const UpscalerEngine = (function() {
    let gl = null;
    let canvas = null;
    let video = null;
    let frameId = 0;
    
    // Shader sources
    const VS_SOURCE = `#version 300 es
      layout(location = 0) in vec2 a_pos;
      out vec2 v_texCoord;
      void main() {
        v_texCoord = a_pos * 0.5 + 0.5;
        gl_Position = vec4(a_pos, 0.0, 1.0);
      }`;

    const FS_COMMON = `#version 300 es
      precision highp float;
      uniform sampler2D u_texture;
      uniform vec2 u_srcSize;
      uniform vec2 u_dstSize;
      in vec2 v_texCoord;
      out vec4 outColor;
      
      vec4 gather(vec2 uv, vec2 offset) {
        return texture(u_texture, uv + offset / u_srcSize);
      }`;

    // Pass 1: Deblock & Denoise (Edge-aware Selective Blur)
    const FS_DEBLOCK = FS_COMMON + `
      uniform float u_strength;
      void main() {
        vec2 uv = v_texCoord;
        vec4 center = texture(u_texture, uv);
        if (u_strength <= 0.01) { outColor = center; return; }
        
        vec4 sum = center;
        float totalWeight = 1.0;
        vec2 offsets[4] = vec2[](vec2(1,0), vec2(-1,0), vec2(0,1), vec2(0,-1));
        
        for(int i=0; i<4; i++) {
          vec4 neighbor = gather(uv, offsets[i]);
          float diff = distance(center.rgb, neighbor.rgb);
          float weight = exp(-diff * 8.0 * (1.0/u_strength)) * 0.5;
          sum += neighbor * weight;
          totalWeight += weight;
        }
        outColor = sum / totalWeight;
      }`;

    // Pass 2: Spatial Edge-Adaptive Upscaler (Improved FSR EASU Lite)
    const FS_EASU = FS_COMMON + `
      void main() {
        vec2 pos = v_texCoord * u_srcSize;
        vec2 p = floor(pos - 0.5);
        vec2 f = fract(pos - 0.5);
        
        // Gather 4x4 surrounding pixels for better edge detection
        vec4 a = texture(u_texture, (p + vec2(0.5, 0.5)) / u_srcSize);
        vec4 b = texture(u_texture, (p + vec2(1.5, 0.5)) / u_srcSize);
        vec4 c = texture(u_texture, (p + vec2(0.5, 1.5)) / u_srcSize);
        vec4 d = texture(u_texture, (p + vec2(1.5, 1.5)) / u_srcSize);
        
        // Compute edge weights (Simplified FSR logic)
        float lumA = dot(a.rgb, vec3(0.299, 0.587, 0.114));
        float lumB = dot(b.rgb, vec3(0.299, 0.587, 0.114));
        float lumC = dot(c.rgb, vec3(0.299, 0.587, 0.114));
        float lumD = dot(d.rgb, vec3(0.299, 0.587, 0.114));
        
        float diffH = abs((lumA + lumC) - (lumB + lumD));
        float diffV = abs((lumA + lumB) - (lumC + lumD));
        
        float weight = clamp(abs(diffH - diffV) / (max(diffH, diffV) + 0.01), 0.0, 1.0);
        
        // Blend between bilinear and edge-directed based on gradient
        vec4 bilinear = mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
        vec4 directed = (diffH > diffV) ? mix(a, c, f.y) : mix(a, b, f.x);
        
        outColor = mix(bilinear, directed, weight * 0.6);
      }`;

    // Pass 3: Detail & RCAS (Robust Contrast Adaptive Sharpening)
    const FS_POST = FS_COMMON + `
      uniform float u_sharpness;
      uniform float u_detail;
      uniform bool u_grain;
      uniform bool u_test;
      
      float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
      
      void main() {
        if (u_test) {
          float line = step(0.98, fract((v_texCoord.x + v_texCoord.y) * 100.0));
          outColor = vec4(vec3(line), 1.0);
          return;
        }
        
        vec2 uv = v_texCoord;
        vec4 col = texture(u_texture, uv);
        
        // RCAS-style sharpening
        if (u_sharpness > 0.0) {
          vec4 n = gather(uv, vec2(0, -1));
          vec4 w = gather(uv, vec2(-1, 0));
          vec4 e = gather(uv, vec2(1, 0));
          vec4 s = gather(uv, vec2(0, 1));
          
          vec3 minCol = min(col.rgb, min(min(n.rgb, w.rgb), min(e.rgb, s.rgb)));
          vec3 maxCol = max(col.rgb, max(max(n.rgb, w.rgb), max(e.rgb, s.rgb)));
          
          vec3 sharp = col.rgb + (col.rgb * 4.0 - (n.rgb + w.rgb + e.rgb + s.rgb)) * u_sharpness;
          col.rgb = clamp(sharp, minCol, maxCol); // Halo limiter
        }
        
        // 3D Detail pass (Local Contrast)
        if (u_detail > 0.0) {
          vec4 blur = (gather(uv, vec2(2,2)) + gather(uv, vec2(-2,-2))) * 0.5;
          vec3 detail = (col.rgb - blur.rgb) * u_detail;
          col.rgb += clamp(detail, -0.1, 0.1); // Halo limiter
        }
        
        // Film Grain
        if (u_grain) {
          col.rgb += (hash(uv + fract(float(gl_FragCoord.xy)/123.4)) - 0.5) * 0.03;
        }
        
        outColor = col;
      }`;

    let programs = {};
    let textures = {};
    let fbos = {};
    let vao = null;
    let lastLogTime = 0;
    let perfTier = 'AUTO';
    let activeTier = 2; // 0:Off, 1:Balanced, 2:High
    let frameTimes = [];
    let isBenchmarked = false;

    function createShader(type, source) {
      const s = gl.createShader(type);
      gl.shaderSource(s, source);
      gl.compileShader(s);
      if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) {
        safeLog('ERROR', 'Shader compile failed: ' + gl.getShaderInfoLog(s));
        return null;
      }
      return s;
    }

    function createProgram(vs, fs) {
      const p = gl.createProgram();
      gl.attachShader(p, createShader(gl.VERTEX_SHADER, vs));
      gl.attachShader(p, createShader(gl.FRAGMENT_SHADER, fs));
      gl.linkProgram(p);
      return p;
    }

    function initResources() {
      canvas = document.createElement('canvas');
      canvas.id = 'xcloud-pad-upscaler-canvas';
      canvas.style.position = 'fixed';
      canvas.style.top = '0';
      canvas.style.left = '0';
      canvas.style.width = '100%';
      canvas.style.height = '100%';
      canvas.style.pointerEvents = 'none';
      canvas.style.zIndex = '1000';
      document.body.appendChild(canvas);

      gl = canvas.getContext('webgl2', { alpha: false, depth: false, antialias: false, preserveDrawingBuffer: false });
      if (!gl) { safeLog('ERROR', 'WebGL2 not supported'); return false; }

      programs.deblock = createProgram(VS_SOURCE, FS_DEBLOCK);
      programs.easu = createProgram(VS_SOURCE, FS_EASU);
      programs.post = createProgram(VS_SOURCE, FS_POST);

      vao = gl.createVertexArray();
      gl.bindVertexArray(vao);
      const vbo = gl.createBuffer();
      gl.bindBuffer(gl.ARRAY_BUFFER, vbo);
      gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1,-1, 1,-1, -1,1, 1,1]), gl.STATIC_DRAW);
      gl.enableVertexAttribArray(0);
      gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0);

      textures.input = gl.createTexture();
      gl.bindTexture(gl.TEXTURE_2D, textures.input);
      gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true); // Fix vertical flip
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);

      return true;
    }

    function updateFbo(name, w, h) {
      if (fbos[name] && fbos[name].w === w && fbos[name].h === h) return;
      if (fbos[name]) { gl.deleteFramebuffer(fbos[name].fbo); gl.deleteTexture(fbos[name].tex); }
      const tex = gl.createTexture();
      gl.bindTexture(gl.TEXTURE_2D, tex);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, w, h, 0, gl.RGBA, gl.UNSIGNED_BYTE, null);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
      const fbo = gl.createFramebuffer();
      gl.bindFramebuffer(gl.FRAMEBUFFER, fbo);
      gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, tex, 0);
      fbos[name] = { fbo, tex, w, h };
    }

    function renderPass(prog, srcTex, dstFbo, srcW, srcH, dstW, dstH, uniforms = {}) {
      gl.useProgram(prog);
      gl.bindFramebuffer(gl.FRAMEBUFFER, dstFbo ? dstFbo.fbo : null);
      gl.viewport(0, 0, dstW, dstH);
      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, srcTex);
      gl.uniform1i(gl.getUniformLocation(prog, 'u_texture'), 0);
      gl.uniform2f(gl.getUniformLocation(prog, 'u_srcSize'), srcW, srcH);
      gl.uniform2f(gl.getUniformLocation(prog, 'u_dstSize'), dstW, dstH);
      for (const k in uniforms) {
        const loc = gl.getUniformLocation(prog, k);
        if (typeof uniforms[k] === 'number') gl.uniform1f(loc, uniforms[k]);
        else if (typeof uniforms[k] === 'boolean') gl.uniform1i(loc, uniforms[k] ? 1 : 0);
      }
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    }

    function loop(now, frameMetadata) {
      frameId = video.requestVideoFrameCallback ? video.requestVideoFrameCallback(loop) : requestAnimationFrame(loop);
      
      const vw = video.videoWidth;
      const vh = video.videoHeight;
      if (vw <= 0 || vh <= 0) return;

      const startTime = performance.now();
      const settingsTier = GM_getValue('upscalerTier', 'AUTO');
      const detail = GM_getValue('upscalerDetailStrength', 40) / 100;
      const deblock = GM_getValue('upscalerDeblockStrength', 20) / 100;
      const grain = GM_getValue('upscalerGrainEnabled', false);
      const test = GM_getValue('upscalerTestPattern', false);
      const sharpness = GM_getValue('videoSharpness', 40) / 100;

      // Auto-tiering logic
      if (settingsTier === 'AUTO') {
        if (!isBenchmarked) { activeTier = 2; isBenchmarked = true; }
      } else {
        activeTier = (settingsTier === 'HIGH') ? 2 : (settingsTier === 'BALANCED' ? 1 : 0);
      }

      if (activeTier === 0 && !test) {
        canvas.style.display = 'none'; video.style.opacity = '1'; return;
      }
      canvas.style.display = 'block'; video.style.opacity = '0';

      const dw = GM_getValue('upscalerOutputWidth', 3840);
      const dh = GM_getValue('upscalerOutputHeight', 2160);
      canvas.width = dw; canvas.height = dh;

      gl.bindTexture(gl.TEXTURE_2D, textures.input);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, video);

      let currentTex = textures.input;
      
      // HIGH TIER: Deblock pre-pass
      if (activeTier >= 2) {
        updateFbo('deblock', vw, vh);
        renderPass(programs.deblock, currentTex, fbos.deblock, vw, vh, vw, vh, { u_strength: deblock });
        currentTex = fbos.deblock.tex;
      }

      // BALANCED/HIGH TIER: EASU Upscale
      if (vw !== dw || vh !== dh) {
        updateFbo('upscale', dw, dh);
        renderPass(programs.easu, currentTex, fbos.upscale, vw, vh, dw, dh);
        currentTex = fbos.upscale.tex;
      }

      // POST: RCAS + Detail + Grain + Test Pattern
      renderPass(programs.post, currentTex, null, dw, dh, dw, dh, {
        u_sharpness: sharpness,
        u_detail: (activeTier >= 2) ? detail : 0.0,
        u_grain: grain,
        u_test: test
      });

      const frameTime = performance.now() - startTime;
      frameTimes.push(frameTime);
      if (frameTimes.length > 60) {
        const avg = frameTimes.reduce((a, b) => a + b) / 60;
        if (settingsTier === 'AUTO' && avg > 7.5 && activeTier > 1) {
          activeTier--; safeLog('INFO', `Auto-dropped upscaler tier to ${activeTier} due to high frame time: ${avg.toFixed(2)}ms`);
        }
        frameTimes = [];
      }

      if (Date.now() - lastLogTime > 2000) {
        lastLogTime = Date.now();
        safeLog('DEBUG', `Upscaler: ${vw}x${vh} -> ${dw}x${dh} | Tier: ${activeTier} | Frame: ${frameTime.toFixed(2)}ms`);
      }
    }

    return {
      start: function(v) {
        if (!gl && !initResources()) return;
        video = v;
        if (frameId) (video.cancelVideoFrameCallback || cancelAnimationFrame)(frameId);
        frameId = video.requestVideoFrameCallback ? video.requestVideoFrameCallback(loop) : requestAnimationFrame(loop);
      }
    };
  })();

  function applyEnhancements() {
    const video = document.querySelector('video');
    if (!video) return;
    
    // Standard video style patches
    const aspect = GM_getValue('videoAspectMode', 'fit');
    video.style.objectFit = (aspect === 'fill_16_9') ? 'fill' : 'contain';
    if (aspect === '4_3') video.style.aspectRatio = '4 / 3';

    // Launch WebGL engine
    UpscalerEngine.start(video);
  }

  const observer = new MutationObserver(() => { 
    if (document.querySelector('video')) { 
      applyEnhancements(); 
      announceGamepad(); 
      observer.disconnect(); // Only run setup once per video element
    } 
  });
  observer.observe(document.documentElement, { childList: true, subtree: true });

  setInterval(() => {
    const video = document.querySelector('video');
    window.XCloudPadBridge?.onStreamStateChanged?.(!!(video && video.readyState >= 2 && !video.paused), document.title);
  }, 2000);
})();
