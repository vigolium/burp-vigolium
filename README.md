# Vigolium Burp Suite Extension

A Burp Suite extension for sending HTTP traffic to the Vigolium security scanning engine, reviewing the resulting findings, and synchronizing traffic in both directions. It supports explicit dispatch from Burp, automatic Proxy forwarding, Target Site map snapshots, and an optional loopback-only live bridge for CLI and server integrations.

- **Version:** `0.2.3`
- **GitHub:** [github.com/vigolium/vigolium](https://github.com/vigolium/vigolium)
- **Docs:** [docs.vigolium.com](https://docs.vigolium.com/)
- **Site:** [www.vigolium.com](https://www.vigolium.com/)

| Vigolium Burp Integration 1 | Vigolium Burp Integration 2 |
|:---:|:---:|
| ![Vigolium Burp Integration 1](https://github.com/vigolium/docs/blob/main/images/vigolium-burp-extension-1.png?raw=true) | ![Vigolium Burp Integration 2](https://github.com/vigolium/docs/blob/main/images/vigolium-burp-extension-2.png?raw=true) |
| ![Vigolium Burp Integration 3](https://github.com/vigolium/docs/blob/main/images/burp-extension/vigolium-burp-ext-3.png?raw=true) | ![Vigolium Burp Integration 4](https://github.com/vigolium/docs/blob/main/images/burp-extension/vigolium-burp-ext-1.png?raw=true) |


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
and calls Montoya's `SiteMap.add`; users do not need to call it directly. The
Repeater route takes the same request fields and calls Montoya's
`Repeater.sendToRepeater` on the event dispatch thread. The send route hands the
request to Montoya's `Http.sendRequest` — Burp's own HTTP stack — so malformed
requests (deliberate `Content-Length`, request smuggling, unusual methods) go on
the wire byte-for-byte instead of being normalised by an ordinary HTTP client.
The organizer route calls `Organizer.sendToOrganizer` with a request/response
pair — the one Burp tool that keeps both together and can forward to Repeater,
since a Repeater tab itself is request-only.

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

#### Send a request to Repeater for manual testing

Site map imports are for bulk traffic; **Repeater** is for the one request you want to hand-test. This route opens a Repeater tab directly:

```bash
curl --silent --show-error \
  --request POST 'http://127.0.0.1:9009/api/burp-bridge/repeater' \
  --header 'Content-Type: application/json' \
  --data '{
    "url": "https://example.com/imported",
    "tab_name": "idor-1",
    "http_request_base64": "R0VUIC9pbXBvcnRlZCBIVFRQLzEuMQ0KSG9zdDogZXhhbXBsZS5jb20NCkFjY2VwdDogKi8qDQoNCg=="
  }'
```

A successful response contains `"sent":1`. `tab_name` is optional and defaults to `vigolium` (trimmed to 64 characters). No response field is accepted — a Repeater tab only carries a request; its response pane fills when you click **Send** inside Burp. To keep a request *and* its response together, use [`/organizer`](#keep-a-request-and-its-response-together-for-manual-work-organizer) instead.

You can also replay an item already found through `search` by passing its `ref` instead of `url` plus `http_request_base64`:

```bash
curl --request POST 'http://127.0.0.1:9009/api/burp-bridge/repeater' \
  --header 'Content-Type: application/json' \
  --data '{"ref": "<ref from /api/burp-bridge/search>", "tab_name": "replay"}'
```

Because every call opens a visible tab, this route is capped more tightly than the Site map route: **30 tabs per minute** (a sliding window; the limit is reported as `repeater_tabs_per_minute` on `/health`) and **1 MiB** per request. Exceeding the rate returns HTTP `429` and sends nothing.

**Stage the tab *and* have Burp send it.** Add `"send": true` to also issue the request through Burp's HTTP stack (accepting the same `http_mode` / `timeout_ms` options as `/send`) and return the response in the reply:

```bash
curl --request POST 'http://127.0.0.1:9009/api/burp-bridge/repeater' \
  --header 'Content-Type: application/json' \
  --data '{"url":"https://example.com/imported","tab_name":"probe","send":true,"http_mode":"http1",
           "http_request_base64":"R0VUIC9pbXBvcnRlZCBIVFRQLzEuMQ0KSG9zdDogZXhhbXBsZS5jb20NCkFjY2VwdDogKi8qDQoNCg=="}'
# → {"sent":1,"tab_name":"probe","executed":true,"status_code":200,"response_base64":"…","elapsed_ms":87,…}
```

`sent:1` means the tab was staged; `executed:true` and the response fields report Burp's own send. **The fetched response is returned here, not painted into the Repeater tab** — Burp's API can't preload a tab's response pane, so to see it live in Burp you still click **Send** in the tab. When the Bridge's **In-scope items only** setting is on and the target is out of scope, the tab is still staged but the auto-send is skipped (`executed:false` with an `error` note) rather than failing the call. A target-side failure returns `executed:false` with an `error`; the tab still opens so you can retry by hand.

#### Send a request through Burp and get the response back

`/repeater` only stages a tab for you to drive by hand. To have Burp actually **issue** the request — using its HTTP engine, not an external client — and return the response, use `/send`. This is the route for replaying or fuzzing malformed requests, because Burp puts your exact bytes on the wire:

```bash
curl --silent --show-error \
  --request POST 'http://127.0.0.1:9009/api/burp-bridge/send' \
  --header 'Content-Type: application/json' \
  --data '{
    "url": "https://example.com/imported",
    "http_mode": "http1",
    "timeout_ms": 15000,
    "add_to_sitemap": false,
    "http_request_base64": "R0VUIC9pbXBvcnRlZCBIVFRQLzEuMQ0KSG9zdDogZXhhbXBsZS5jb20NCkFjY2VwdDogKi8qDQoNCg=="
  }'
```

A successful response looks like `{"sent":1,"status_code":200,"response_base64":"…","response_length":1234,"response_truncated":false,"elapsed_ms":87,"http_mode":"HTTP_1","added_to_sitemap":false}`.

| Field | Default | Notes |
|-------|---------|-------|
| `http_request_base64` / `ref` | — | Supply raw request bytes, or a `ref` from `/search` to replay it |
| `http_mode` | `auto` | `auto`, `http1`, `http2`, `http2_ignore_alpn`. Use `http1` for classic request-smuggling payloads — `auto` may negotiate HTTP/2 and reframe them |
| `timeout_ms` | `30000` | Response timeout, capped at `120000` |
| `add_to_sitemap` | `false` | When `true`, the sent request/response is also recorded in **Target → Site map** (the response is always returned regardless) |

Scope: when the Bridge's **In-scope items only** setting is enabled, `/send` refuses out-of-scope targets with HTTP `403` (reported as `send_respects_in_scope_only` on `/health`); with the setting off, any host is allowed. A target-side failure (connection refused, timeout) is not a bridge error — it returns HTTP `200` with `"sent":0` and an `error` field so per-request outcomes stay uniform when fuzzing. The response body is returned up to 4 MiB (`response_truncated` flags the cut); the full length is always in `response_length`.

#### Keep a request *and* its response together for manual work (Organizer)

A Burp Repeater tab only ever holds a **request** — there is no API to preload a response into it. To keep a request **and** its response side by side in a Burp tool you can revisit and re-send, use the **Organizer**: `/organizer` calls Montoya's `Organizer.sendToOrganizer`, which stores the pair. From the Organizer tab you can right-click an item and **Send to Repeater** for hands-on testing.

Supply a response you already have (for example from an earlier `/send`):

```bash
curl --request POST 'http://127.0.0.1:9009/api/burp-bridge/organizer' \
  --header 'Content-Type: application/json' \
  --data '{"url":"https://example.com/imported",
           "http_request_base64":"R0VUIC9pbXBvcnRlZCBIVFRQLzEuMQ0KSG9zdDogZXhhbXBsZS5jb20NCkFjY2VwdDogKi8qDQoNCg==",
           "http_response_base64":"SFRUUC8xLjEgMjAwIE9LDQpDb250ZW50LUxlbmd0aDogMg0KDQpPSw=="}'
# → {"added":1,"url":"…","request_hash":"…","has_response":true,"message":"added 1 item to Burp Organizer"}
```

Or send just the request and let Burp fetch the response, then store the whole exchange in one call — this is the "request in, response back, imported for manual testing" flow:

```bash
curl --request POST 'http://127.0.0.1:9009/api/burp-bridge/organizer' \
  --header 'Content-Type: application/json' \
  --data '{"url":"https://example.com/imported","send":true,"http_mode":"http1",
           "http_request_base64":"R0VUIC9pbXBvcnRlZCBIVFRQLzEuMQ0KSG9zdDogZXhhbXBsZS5jb20NCkFjY2VwdDogKi8qDQoNCg=="}'
# → {"added":1,…,"has_response":true,"status_code":200,"response_base64":"…","elapsed_ms":87}
```

`http_response_base64` takes precedence; `send:true` only fetches a response when none was supplied. The send honours the same **In-scope items only** gate as `/send` (out-of-scope + setting on → HTTP `403`, nothing organized), and `http_mode` / `timeout_ms` behave exactly as they do there. A supplied response is optional — with neither a response nor `send`, the item is stored request-only.

**Labelling items.** Burp's Organizer is a flat list — it has no collections or per-item title, and `sendToOrganizer` takes no name (unlike Repeater's `tab_name`). The one editable label is the item's **Notes** (shown in the Organizer's Notes column), plus a **highlight colour** you can use to group a batch visually. Both are optional:

```bash
curl --request POST 'http://127.0.0.1:9009/api/burp-bridge/organizer' \
  --header 'Content-Type: application/json' \
  --data '{"url":"https://example.com/imported","notes":"recon-batch-1","highlight":"red",
           "http_request_base64":"R0VUIC9pbXBvcnRlZCBIVFRQLzEuMQ0KSG9zdDogZXhhbXBsZS5jb20NCkFjY2VwdDogKi8qDQoNCg=="}'
```

`notes` (≤200 chars, single line) overrides the default `Imported from {source} via Vigolium bridge` note and is echoed back in the reply. `highlight` accepts `none`, `red`, `orange`, `yellow`, `green`, `cyan`, `blue`, `pink`, `magenta`, `gray` (an unknown colour returns HTTP `400`, nothing organized). Both also work alongside `send:true`.

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
| `POST` | `/api/burp-bridge/repeater` | Open a Base64-encoded request (or a search `ref`) in a Burp Repeater tab |
| `POST` | `/api/burp-bridge/send` | Issue a request through Burp's HTTP stack (preserving malformed bytes) and return the response |
| `POST` | `/api/burp-bridge/organizer` | Store a request + response pair in Burp's Organizer (optionally sending first) for manual follow-up |

Its bidirectional transport is unauthenticated and restricted to the local machine. The listener rejects unexpected Host and Origin headers. Search results use temporary references that expire when the listener restarts or the extension unloads.

## License

Vigolium is made with ♥ by [@j3ssie](https://twitter.com/j3ssie), with [@theblackturtle](https://github.com/theblackturtle) as a core contributor.
