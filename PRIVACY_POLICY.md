# Privacy Policy

**Last updated: February 8, 2026**

## Overview

Claude Usage Widget ("the App") is an open-source utility that displays your Claude.ai usage information. This policy explains how the App handles your data.

## Data Collection

The App does **not** collect, store, or transmit any personal data to third-party servers.

### What the App stores locally on your device:
- **Session key**: Used to authenticate with Claude.ai API. Stored in Android SharedPreferences (device-only).
- **Organization ID**: Used to fetch your usage data. Stored locally.
- **Display preferences**: Your metric visibility and notification settings.

### What the App does NOT do:
- Does not collect analytics or telemetry
- Does not use tracking SDKs
- Does not share data with third parties
- Does not store your usage data permanently — it is fetched on demand and displayed in real-time

## Network Requests

The App communicates **only** with `claude.ai` to fetch:
- `/api/organizations/{id}/usage` — your current usage metrics
- `/api/organizations/{id}/overage_spend_limit` — spending limit info
- `/api/organizations/{id}/prepaid/credits` — prepaid balance

No other network requests are made.

## Data Security

- Your session key is stored locally on your device only
- All network communication uses HTTPS
- No data is sent to any server other than `claude.ai`

## Third-Party Services

This App is **not affiliated with, endorsed by, or officially connected to Anthropic** in any way. It uses the same API endpoints that the Claude.ai web interface uses.

## Children's Privacy

This App is not intended for use by children under 13.

## Changes

This policy may be updated occasionally. Changes will be posted to this repository.

## Contact

If you have questions about this policy, please open an issue on [GitHub](https://github.com/CUN-bjy/claude-usage-widget).

---

Developer: **dion**
