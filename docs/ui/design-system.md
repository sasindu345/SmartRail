# SmartRail design system

Shared visual rules for the Control Center. **Owner: Member 3** (`ui/theme/`, `ui/components/`).
Other members compose these components; they do not invent their own colours, fonts or borders.

Read `control-center-spec.md` first — it defines the screens this styling serves.

## 1. Look and feel: FlatLaf

FlatLaf is the project's one runtime dependency. It supplies flat styling, rounded controls and
modern table/scrollbar defaults, which is why the UI needs very little hand-painting.

```xml
<dependency>
    <groupId>com.formdev</groupId>
    <artifactId>flatlaf</artifactId>
    <version>3.5.4</version>
</dependency>
```

Installed once, before any window is created, in `Theme.install()` (called from
`ControlCenterApp.main`). If installation fails the app must still start on the default Swing
look-and-feel — styling is never allowed to break the demo.

**No other UI dependency may be added.** Every other restriction in `AGENTS.md` §3.1 still applies.

## 2. Colour tokens

Defined once as constants in `ui/theme/Theme.java`. Never write a literal `new Color(...)` in a panel.

| Token | Value | Used for |
|---|---|---|
| `PRIMARY` | `#14532D` | brand bar, selected nav item, train chips |
| `PRIMARY_SOFT` | `#D1FAE5` | occupied section fill, selected-train card background |
| `OK` | `#16A34A` | running / connected / service up |
| `WARN` | `#F59E0B` | held / reserved / waiting |
| `DANGER` | `#DC2626` | emergency stop, failed service, denied reservation |
| `INACTIVE` | `#9CA3AF` | inactive train, free section, stopped service |
| `BG` | `#F5F6F7` | window background |
| `SURFACE` | `#FFFFFF` | cards and tables |
| `BORDER` | `#E5E7EB` | hairline card and table borders |
| `TEXT` | `#111827` | primary text |
| `TEXT_MUTED` | `#6B7280` | labels, captions, secondary text |

Status colours carry meaning and must be used consistently: a green dot always means "this network
thing is working", amber "waiting or held", red "refused or failed", grey "not started".

## 3. Typography

`SF Pro` and `Segoe UI` are **not** visible to the JVM on macOS (verified), so the stack is resolved
at runtime in `Theme`, first match wins:

```
Helvetica Neue  →  Segoe UI  →  Dialog (JDK logical font)
```

| Style | Size / weight | Used for |
|---|---|---|
| `title` | 20 bold | screen heading |
| `subtitle` | 13 plain, `TEXT_MUTED` | screen description |
| `cardTitle` | 14 bold | card heading |
| `body` | 13 plain | tables, labels |
| `mono` | 12 `Font.MONOSPACED` | event log, train consoles, raw payloads |

## 4. Components (`ui/components/`)

| Component | Purpose |
|---|---|
| `Card` | white rounded panel, hairline border, optional title — the base container for every screen block |
| `StatusPill` | small rounded badge with a coloured dot and label (`Connected (TLS)`, `Hold`, `INACTIVE`) |
| `NavSidebar` | the left navigation, drives the `CardLayout` |
| `TopBar` | product mark, title, `Simulation Online/Offline` pill, live clock |
| `SimulationControlBar` | Start/Stop Server, `+TR01/02/03`, Stop All |
| `NetworkStatusBar` | the six per-service dots along the bottom |
| `TableStyles` | shared `JTable` setup: row height, header style, alternating rows, status-dot cell renderer |

Spacing uses an 8px grid (`Theme.GAP` = 8, `Theme.PAD` = 16). Cards are 10px radius.

## 5. Rules for everyone

1. **Compose, don't restyle.** Use `Card`, `StatusPill` and `TableStyles`; do not set your own
   colours, fonts or borders in a panel. If a component is missing, ask M3 to add it.
2. **Every widget shows a network fact.** No decorative panels, no filler.
3. **Never block the EDT.** Network work via `UiThreads.runInBackground`, UI updates via
   `UiThreads.onEdt`.
4. **Nothing hard-coded.** Ports, hosts, timeouts, train/section IDs come from `NetworkConfig` and
   `RailwayData`, exactly as in the backend.
5. **Degrade honestly.** If a service is not implemented or failed to start, show it grey/red with
   the reason — never hide it and never fake data.
6. **No animation**, no hover effects beyond a simple colour change, no icons that need an external
   asset. Simple `Graphics2D` shapes only.
