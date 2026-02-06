## v1.3.0

### What's New

- **In-app login**: Log in to Claude.ai directly within the widget — no more copying cookies manually
- **Expandable usage details**: Click the arrow to see per-model breakdown (Sonnet, Opus, Cowork, OAuth Apps, Extra Usage)
- **Manual login fallback**: Paste your sessionKey from browser DevTools if needed

### Fixes

- Fixed login white screen caused by Claude.ai blocking Electron
- Fixed API requests being blocked by Cloudflare
- Fixed session expiration not recovering gracefully

### Under the Hood

- Replaced `axios` with hidden BrowserWindow-based fetching to bypass Cloudflare
- Chrome User-Agent spoofing to prevent Electron detection

### Downloads

| File | Description |
|------|-------------|
| `Claude.Usage.Widget.Setup.1.3.0.exe` | Installer (recommended) |
| `Claude-Usage-Widget-Portable.zip` | Portable version (no install needed) |
