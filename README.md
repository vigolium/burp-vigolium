# Vigolium Burp Suite Extension

A Burp Suite extension that bridges HTTP requests to the Vigolium server-side security scanning engine. Captures requests from Burp, forwards them to Vigolium's API for vulnerability scanning, and displays findings directly in Burp.

## Architecture

```
Burp Suite ──► Vigolium Extension ──► Vigolium API Server ──► Scan Engine
                     ▲                        │
                     └── Poll Findings ◄──────┘
```

## Features

- **Proxy Mode** — Automatically forwards proxy traffic to Vigolium with configurable filter rules
- **Context Menu** — Right-click "Send to Vigolium" from any Burp tool (Proxy History, Site Map, Repeater, etc.)
- **Findings Tab** — Displays scan results with severity, request/response detail via Burp's message editor
- **Logs Tab** — Real-time activity log with level filtering

## Tabs

| Tab | Purpose |
|-----|---------|
| **Findings** | Split pane: findings table + request/response detail |
| **Settings** | Server connection, proxy toggle, filter rules, hotkeys |
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

## Build (from source)

```bash
./gradlew spotlessApply build
```

The output jar is built via Shadow plugin at `build/libs/burp-vigolium.jar`.

## Configuration

1. Go to **Vigolium > Settings > Server Connection**
2. Enter your Vigolium server URL and API key
3. Click **Test Connection**
4. Enable **Proxy Mode** to start auto-forwarding traffic
5. Configure filter rules and scan modules as needed

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/health` | Connection test |
| `GET` | `/api/modules` | List scan modules |
| `POST` | `/api/scan` | Submit batch scan tasks |
| `GET` | `/api/findings?session_id=X&since=T` | Poll new findings |

All requests use `Authorization: Bearer {API_KEY}`.

## License

Vigolium is made with ♥ by [@j3ssie](https://twitter.com/j3ssie), with [@theblackturtle](https://github.com/theblackturtle) as a core contributor.
