# Decluttr — "Unable to load onboarding" Root Cause Report

---

## What's actually happening

`onboarding.html` loads React and ReactDOM from the **unpkg.com CDN**:

```html
<!-- onboarding.html lines 6-7 -->
<script src="https://unpkg.com/react@18/umd/react.production.min.js"></script>
<script src="https://unpkg.com/react-dom@18/umd/react-dom.production.min.js"></script>
```

If either script fails to load (no internet, slow connection, CDN timeout, WebView quirk), `React` and `ReactDOM` are `undefined` when the boot block runs:

```javascript
// onboarding.html boot block
try {
    window.__decluttrRoot = ReactDOM.createRoot(document.getElementById("root"));
    //                       ^^^ undefined if CDN failed → TypeError
    window.__decluttrRoot.render(React.createElement(DecluttrOnboarding.default));
} catch (error) {
    // ← lands here for ANY error, including CDN failure
    document.body.innerHTML =
        '<div style="color:#fff;padding:24px;font-family:sans-serif">Unable to load onboarding.</div>';
}
```

The `catch` block swallows the real error and replaces the entire screen with the "Unable to load onboarding." message. The user sees a white-on-dark error string instead of the onboarding slides.

---

## Why it fails silently so often

The WebView loads `file:///android_asset/onboarding.html`. From that origin, it fires two outbound HTTPS requests to `unpkg.com`. This fails in all of these common scenarios:

| Scenario | Result |
|---|---|
| Fresh install, first open, no WiFi | CDN unreachable → React undefined |
| Mobile data, poor signal | Request times out → React undefined |
| Corporate/school network blocking CDN | Request blocked → React undefined |
| unpkg.com rate limit or downtime | Request fails → React undefined |
| WebView loads `onboarding.js` before CDN scripts finish | Race condition → React undefined |
| App reviewed offline by Google Play team | React undefined → rejection risk |

The WebView is loading `file:///` content. CDN network calls from a `file://` origin are technically allowed (INTERNET permission is granted, `clearTextTraffic` is false so HTTPS works), but they add a **network round-trip into the critical path of a UI that contains zero dynamic content**. Every onboarding slide is static — there is no reason to fetch anything from the internet.

---

## Secondary cause: `onboarding.js` does not bundle React

`onboarding.js` was built with esbuild treating React as **external**:

```javascript
// onboarding.js line 1 — esbuild IIFE output
var DecluttrOnboarding = (() => {
  // ... all component code uses React.createElement(...) directly
  // React is assumed to already exist as a global
})();
```

Every `React.createElement(...)` call in the compiled bundle depends on `window.React` being set by the CDN scripts. If CDN loading fails or is slow, the bundle crashes on its very first render call.

---

## The fix — one esbuild command

Rebuild `onboarding.js` with React **bundled in** — no external dependencies. Then strip the CDN `<script>` tags from `onboarding.html`.

### Step 1 — rebuild the bundle

```bash
# From the directory containing decluttr-onboarding.jsx
npx esbuild decluttr-onboarding.jsx \
  --bundle \
  --format=iife \
  --global-name=DecluttrOnboarding \
  --minify \
  --outfile=app/src/main/assets/onboarding.js
```

Remove `--external:react` and `--external:react-dom` if they were in your previous build command. This bundles React 18 (~42 KB gzipped) into the single file.

### Step 2 — update `onboarding.html`

Remove the two CDN `<script>` tags and nothing else:

```html
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Decluttr Onboarding</title>
  <!-- ❌ DELETE these two lines:
  <script src="https://unpkg.com/react@18/umd/react.production.min.js"></script>
  <script src="https://unpkg.com/react-dom@18/umd/react-dom.production.min.js"></script>
  -->
  <style>
    html, body, #root {
      margin: 0; width: 100%; min-height: 100%; background: #0a0b0f;
    }
  </style>
</head>
<body>
  <div id="root"></div>
  <script src="file:///android_asset/onboarding.js"></script>
  <script>
    // setAuthLoading and click handler stay exactly as-is

    try {
      window.__decluttrRoot = ReactDOM.createRoot(document.getElementById("root"));
      window.__decluttrRoot.render(React.createElement(DecluttrOnboarding.default));
    } catch (error) {
      // Improve the error message for debugging
      document.body.innerHTML =
        '<div style="color:#fff;padding:24px;font-family:sans-serif">' +
        '<b>Unable to load onboarding</b><br><pre style="font-size:11px;opacity:0.6;margin-top:12px">' +
        error.toString() + '</pre></div>';
    }
  </script>
</body>
</html>
```

> Also update the catch block to print `error.toString()` — it will save you hours next time something breaks.

### Step 3 — verify the bundle exposes the right global

After rebuilding, check the last line of `onboarding.js`:

```bash
tail -3 app/src/main/assets/onboarding.js
```

You should see something like:
```javascript
  return __toCommonJS(decluttr_onboarding_exports);
})();
```
And the first line should be `var DecluttrOnboarding = (() => {`.

The boot script uses `DecluttrOnboarding.default` — make sure your esbuild output still exports `default`. Confirm with:
```bash
grep "default:" app/src/main/assets/onboarding.js | head -2
```
Should show: `default: () => DecluttrOnboarding`.

---

## Why `onboarding-shell.html` (the old Babel approach) also shows the same error

`onboarding-shell.html` is the old file and has the same CDN problem — it also loads React from unpkg AND loads Babel Standalone from unpkg. Two CDN dependencies instead of one. It would show the same "Unable to load onboarding." in the same failure scenarios. This file is no longer referenced by `AuthFragment` (the code now calls `webView.loadUrl("file:///android_asset/onboarding.html")`), but if you ever switch back to it, it will fail the same way.

---

## Font loading (non-blocking, but worth knowing)

`decluttr-onboarding.jsx` uses:
```javascript
@import url('https://fonts.googleapis.com/css2?family=DM+Serif+Display...')
```

This is a CDN call too, but it only affects fonts — it will **not** crash the app. On a device without internet, the browser will fall back to system fonts gracefully. This is acceptable, but if you want pixel-perfect offline rendering, download the font files and bundle them in `assets/fonts/`.

---

## Summary

| Root cause | File | Fix |
|---|---|---|
| React loaded from CDN, not bundled | `onboarding.html` lines 6–7 | Delete CDN `<script>` tags |
| `onboarding.js` built without React | build command | Re-run esbuild without `--external:react` |
| catch block hides the real error | `onboarding.html` boot script | Print `error.toString()` in catch |

*Generated April 2026 against Decluttr-main v2*
