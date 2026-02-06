## Summary

This PR fixes the broken login flow and replaces the HTTP request layer to work around Cloudflare blocking.

### Problems

1. **Login white screen**: Claude.ai detects Electron's embedded browser and blocks rendering, causing infinite login loops
2. **API requests blocked by Cloudflare**: `axios` HTTP requests get intercepted by Cloudflare's "Just a moment..." challenge page, returning no data
3. **Session expiration not handled gracefully**: When the sessionKey expires, the widget had no clean recovery path

### Changes

**API layer: axios → hidden BrowserWindow**
- Replaced all `axios` HTTP calls with `fetchViaWindow()`, which creates a hidden `BrowserWindow`, navigates to the API URL, and extracts the JSON response via `executeJavaScript`
- This uses a real browser context that passes Cloudflare checks automatically
- Set session-level User-Agent to Chrome to prevent Electron detection

**Login flow: BrowserWindow popup login**
- Opens a visible `BrowserWindow` to `claude.ai/login` where the user logs in directly
- Electron's session captures the `sessionKey` cookie automatically via `cookies.on('changed')`
- Manual sessionKey paste kept as fallback (Step 2)
- Removed the old silent login / auto-login mechanism entirely

**Expandable widget**
- Added expand/collapse toggle to show per-model usage breakdown (Sonnet, Opus, Cowork, OAuth Apps, Extra Usage)
- Widget dynamically resizes based on expanded state

**Cleanup**
- Removed `axios` dependency
- Removed unused auto-login UI state and debug features
- Simplified settings overlay (removed raw API response viewer)
