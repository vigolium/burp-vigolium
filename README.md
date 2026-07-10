# Vigolium Burp Suite Extension

A Burp Suite extension for sending HTTP traffic to the Vigolium security scanning engine, reviewing the resulting findings, and synchronizing traffic in both directions. It supports explicit dispatch from Burp, automatic Proxy forwarding, Target Site map snapshots, and an optional loopback-only live bridge for CLI and server integrations.

- **Version:** `0.2.0`
- **GitHub:** [github.com/vigolium/vigolium](https://github.com/vigolium/vigolium)
- **Docs:** [docs.vigolium.com](https://docs.vigolium.com/)
- **Site:** [www.vigolium.com](https://www.vigolium.com/)

| Vigolium Burp Integration 1 | Vigolium Burp Integration 2 |
|:---:|:---:|
| ![Vigolium Burp Integration 1](https://github.com/vigolium/docs/blob/main/images/vigolium-burp-extension-1.png?raw=true) | ![Vigolium Burp Integration 2](https://github.com/vigolium/docs/blob/main/images/vigolium-burp-extension-2.png?raw=true) |

## Architecture

```
Selected or Proxy traffic ──► Vigolium Extension ──► Vigolium API ──► Scan Engine
                                      ▲                    │
                                      └──── Records ◄──────┘

Burp Target Site map ── snapshot upload ───────────► Vigolium API
Vigolium CLI/server  ◄── loopback live bridge ────► Burp Proxy history / Site map
```

## Features

- **Three dispatch workflows** — Send selected Burp traffic to ingestion, a native scan, or an agentic scan
- **Direct context-menu actions** — Dispatch from Proxy History, Target Site map, Repeater, and other supported Burp request views without opening a nested menu
- **Proxy Mode** — Automatically forward Proxy traffic with configurable inclusion, exclusion, and in-scope rules
- **Target Site Map snapshots** — Synchronize the current Target Site map on demand with `Ctrl+Alt+S`, or automatically on a configurable interval
- **Bidirectional live bridge** — Query live Burp history from Vigolium or copy Vigolium traffic into Burp's Target Site map over an opt-in, loopback-only listener
- **Findings workflow** — Filter and sort findings, switch between multiple evidence tabs, inspect messages in Burp editors, reveal the full description, and copy a complete finding as Markdown
- **HTTP record workflow** — Filter, paginate, and sort every data column while reviewing stored request and response records
- **Scan tracking** — Review native and agentic scans through consistent toolbars, pagination, refresh controls, and log views
- **Keyboard-driven refresh** — Use `Ctrl+Alt+R` in any record view to activate its Refresh button
- **Integrated diagnostics** — Test the Vigolium server and local Bridge connections, inspect request counters, and follow activity in the Logs tab

## Tabs

| Tab | Purpose |
|-----|---------|
| **Findings Records** | Searchable and sortable findings, evidence tabs, request/response editors, descriptions, and Markdown copy |
| **HTTP Records** | Filterable and sortable request/response records synchronized with Vigolium |
| **Scanning Records** | Native and agentic scan history, pagination, auto-refresh, and scan logs |
| **Bridge** | Target Site Map snapshots, the live loopback listener, Proxy forwarding, and filter rules |
| **Settings** | Extension version, server connection, scan options, request statistics, and configurable hotkeys |
| **Logs** | Timestamped activity log (INFO/WARN/ERROR) |

## Tech Stack

| Component | Choice |
|-----------|--------|
| Burp API | Montoya API |
| Build | Gradle (Kotlin DSL) + Shadow plugin |
| Java | 21 |
| HTTP Client | OkHttp |
| JSON | Gson |

## Installation

Download the pre-built jar from [burp-vigolium.jar](https://github.com/vigolium/burp-vigolium/blob/main/burp-vigolium.jar) and load it in Burp via **Extensions > Add**.

After loading the extension:

1. Open **Vigolium → Settings**.
2. Enter the Vigolium **Server URL** and **API Key** on the Server Connection row.
3. Select **Test Connection** and confirm that the server is reachable.
4. Use a Burp context-menu action or one of the configured shortcuts to send traffic.

The installed extension version is displayed in the upper-right corner of the Settings view. Release builds read this value from the JAR manifest; local IDE runs display `development`.

## Build (from source)

```bash
./gradlew spotlessCheck test shadowJar
```

The output jar is built via Shadow plugin at `build/libs/burp-vigolium.jar`.

## Configuration

First, start the Vigolium server so the extension has an API to connect to:

```bash
vigolium server -A
```

Then retrieve the API key to enter in the extension:

```bash
vigolium config ls server.auth_api_key --force
```

### Server connection and scan options

The default server URL is `http://127.0.0.1:9002`. Server URL and API Key are stored in Burp's extension preferences and used for authenticated API requests. The **Test Connection** button calls the server health endpoint without blocking the Burp UI.

Under **Scan Options**, optionally provide a comma-separated module list and a timeout such as `30s` or `2m`. Leaving either value blank uses the server default. **Scan All HTTP Records** submits every stored HTTP record using these options.

### Record views

The Findings Records and HTTP Records tables support click-to-sort headers for every data column. Click once for ascending order, again for descending order, and a third time to return to the default ordering. The `#` column remains a row-position indicator rather than a sortable data field.

Selecting a finding opens its primary request and response in Burp's message editors. Additional evidence appears as adjacent tabs, making it possible to compare evidence without using a drop-down. **Show Description** expands the finding summary, while the orange **Copy Finding as Markdown** action copies the metadata, description, matched URLs, requests, responses, and additional evidence.

### Keyboard shortcuts

Action shortcuts can be changed under **Settings → Keyboard Shortcuts**. The defaults are:

| Action | Default shortcut |
|--------|------------------|
| Send to ingestion | `Ctrl+Alt+V` |
| Send to native scan | `Ctrl+Alt+N` |
| Send to agentic scan | `Ctrl+Alt+A` |
| Snapshot Target Site map | `Ctrl+Alt+S` |
| Refresh active record view | `Ctrl+Alt+R` |

The refresh shortcut is contextual: while focus is inside Findings Records, HTTP Records, Native Scans, or Agentic Scans, it activates the Refresh button for that view.

### Target Site Map snapshots

Open **Vigolium → Bridge → Target Site Map Snapshot** to run a snapshot immediately or enable periodic snapshots. Periodic snapshots are disabled by default; when enabled, the default interval is five minutes. Use **In-scope only** when the snapshot should exclude out-of-scope traffic. The default shortcut is `Ctrl+Alt+S` and can be changed under **Settings → Keyboard Shortcuts**.

Snapshots are incremental during the current Burp session and idempotent on the server. Requests and available responses are uploaded in bounded chunks; unchanged records are not duplicated.

### Live bridge

The live bridge is disabled by default. To enable it, open **Vigolium → Bridge**, select **Enable live bridge**, and enter the listener URL (default `http://127.0.0.1:9009`). The extension starts an embedded HTTP server on that loopback address; it refuses non-loopback bind addresses. Select **In-scope items only** to prevent bridge searches from returning traffic outside Burp's Target scope. The filter is disabled by default. The **Test Connection** button checks the listener's health endpoint.

The listener does not require credentials. It only binds to a validated loopback address, rejects unexpected Host and Origin headers, and remains opt-in and disabled by default.

Use the listener as an additional source in Vigolium's ordinary traffic views:

```bash
export VIGOLIUM_BURP_BRIDGE_URL="http://127.0.0.1:9009"

# CLI: merge the local database with live Burp Proxy history
vigolium traffic

# Server: merge live Burp rows into GET /api/http-records
vigolium server

# Persist all bridge-visible Proxy history into Vigolium's database
vigolium import

# Persist the selected traffic page (add --all for every matching record)
vigolium traffic --save-to-vigolium-db

# Copy selected Vigolium database traffic into Burp's Target Site map
vigolium traffic --save-to-burp

# Save a mutated replay and its fresh response into Burp's Target Site map
vigolium replay --record-uuid <uuid> --save-to-burp
```

Live rows are labelled with `source: burp`. The same traffic filters, sorting,
pagination, JSON output, and record-detail workflow continue to apply. The
listener URL can alternatively be set with `VIGOLIUM_BURP_BRIDGE_URL`.

Bridge imports are idempotent: newly observed requests are inserted, changed
responses refresh the existing database row, and unchanged traffic is skipped.

Search and inspect remain read-only. The internal Site map write route accepts
the same `burp_base64` request/response fields as Vigolium's `/api/ingest-http`
and calls Montoya's `SiteMap.add`; users do not need to call it directly.

Disabling the setting or unloading the extension stops the listener and expires its temporary result references.

#### Import traffic into Burp with `curl`

With the live bridge enabled, the following request imports one HTTP exchange into Burp's **Target → Site map** through the loopback listener:

```bash
curl --silent --show-error \
  --request POST 'http://127.0.0.1:9009/api/burp-bridge/sitemap' \
  --header 'Content-Type: application/json' \
  --data '{
    "input_mode": "burp_base64",
    "url": "https://example.com/imported",
    "source": "curl",
    "http_request_base64": "R0VUIC9pbXBvcnRlZCBIVFRQLzEuMQ0KSG9zdDogZXhhbXBsZS5jb20NCkFjY2VwdDogKi8qDQoNCg==",
    "http_response_base64": "SFRUUC8xLjEgMjAwIE9LDQpDb250ZW50LUxlbmd0aDogMg0KDQpPSw=="
  }'
```

The request body contains a Base64-encoded `GET /imported` request and a small `200 OK` response. `http_request_base64` is required; `http_response_base64` is optional when only a request is available. A successful response contains `"added":1`. This listener does not use the Vigolium API key or any separate bridge credentials.

## API endpoints used by the extension

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/health` | Test the Vigolium server connection |
| `POST` | `/api/ingest-http` | Store selected or automatically forwarded Burp traffic |
| `POST` | `/api/scan-request` | Start a native scan for selected traffic |
| `POST` | `/api/agent/run/swarm` | Start an agentic scan for selected traffic |
| `POST` | `/api/scan-all-records` | Scan all stored HTTP records with the configured options |
| `POST` | `/api/burp/sitemap/snapshot` | Upload an idempotent Target Site map snapshot chunk |
| `GET` | `/api/findings` | List and filter findings |
| `GET` | `/api/http-records` | List and filter stored HTTP records |
| `GET` | `/api/scans` | List native scan runs and retrieve their logs |
| `GET` | `/api/agent/sessions` | List agentic scan sessions and retrieve their logs |

All Vigolium API requests use `Authorization: Bearer {API_KEY}`.

The loopback bridge listener is separate from the Vigolium API:

| Method | Bridge endpoint | Purpose |
|--------|-----------------|---------|
| `GET` | `/health` | Report listener health, capabilities, and in-scope mode |
| `POST` | `/api/burp-bridge/search` | Search Burp Proxy history or the Target Site map |
| `POST` | `/api/burp-bridge/inspect` | Retrieve request/response data for a temporary search reference |
| `POST` | `/api/burp-bridge/sitemap` | Add a Base64-encoded request/response item to Burp's Target Site map |

Its bidirectional transport is unauthenticated and restricted to the local machine. The listener rejects unexpected Host and Origin headers. Search results use temporary references that expire when the listener restarts or the extension unloads.

## License

Vigolium is made with ♥ by [@j3ssie](https://twitter.com/j3ssie), with [@theblackturtle](https://github.com/theblackturtle) as a core contributor.
