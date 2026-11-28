# DESIGN.md — TMC Visual System

## Aesthetic Direction
- **Theme:** Enterprise Dark-Sidebar with crisp light workspace canvas.
- **Accent:** Modern Indigo (`#4F46E5`) for primary actions, active navigation, focus rings, and interactive emphasis.
- **Surface Character:** Crisp, structured, and data-forward — 8–10px border radii, clean 1px hairlines (`#E2E8F0`), minimal elevation reserved for floating modals/login shell.

## Color Palette
| Token | Hex | Usage |
|---|---|---|
| `--bg` | `#F8FAFC` | Application workspace canvas (Slate 50) |
| `--surface` | `#FFFFFF` | Primary cards, tables, topbar, inputs |
| `--surface-soft` | `#F1F5F9` | Subtle secondary fill, table headers (Slate 100) |
| `--surface-tint` | `#EEF2FF` | Subtle Indigo hover/highlight tint (Indigo 50) |
| `--sidebar-bg` | `#0F172A` | Dark Enterprise sidebar & welcome banner (Slate 900) |
| `--sidebar-ink` | `#CBD5E1` | Sidebar navigation text (Slate 300) |
| `--sidebar-muted` | `#94A3B8` | Sidebar metadata & captions (Slate 400) |
| `--sidebar-active-bg` | `rgba(99, 102, 241, 0.18)` | Active sidebar item fill |
| `--sidebar-active-ink` | `#A5B4FC` | Active sidebar item text & icon (Indigo 300) |
| `--ink` | `#0F172A` | Primary body & heading text (Slate 900) |
| `--ink-soft` | `#334155` | Secondary labels & subtitles (Slate 700) |
| `--muted` | `#64748B` | Captions, hints, metadata (Slate 500) |
| `--border` | `#E2E8F0` | Standard hairline border (Slate 200) |
| `--border-strong` | `#CBD5E1` | Input & button border (Slate 300) |
| `--accent` | `#4F46E5` | Primary CTA, links, active tabs (Indigo 600) |
| `--accent-hover` | `#4338CA` | Primary CTA hover (Indigo 700) |
| `--accent-soft` | `#EEF2FF` | Accent pill & avatar fill (Indigo 50) |
| `--accent-ink` | `#3730A3` | High-contrast accent text on soft fill (Indigo 800) |
| `--success-bg` / `--success-ink` | `#ECFDF5` / `#065F46` | Connected status, `ALLOW`, `APPROVED` |
| `--warning-bg` / `--warning-ink` | `#FFFBEB` / `#92400E` | Offline badge, `PENDING`, warnings |
| `--danger-bg` / `--danger-ink` | `#FEF2F2` / `#991B1B` | `DENY`, `FAILED`, emergency actions |

## Typography
- **UI & Headings:** `Be Vietnam Pro`, system-ui, sans-serif (optimized for Vietnamese diacritics).
- **Data, Codes & Timers:** `JetBrains Mono`, monospace with `font-variant-numeric: tabular-nums`.
- **Fixed Scale (Operate Mode):** `H1 1.5rem (24px)`, `H2 1.125rem (18px)`, `H3 0.9375rem (15px)`, `Body 0.875rem (14px)`.

## Radii & Elevation
- `--radius-sm: 6px` (inputs, buttons)
- `--radius-md: 8px` (cards, tables, panels)
- `--radius-lg: 10px` (hero banner, kiosk viewfinder, modals)
- `--radius-xl: 12px` (QR pass card, login shell)
- Elevation is declared via crisp `1px solid var(--border)` on workspace surfaces; soft shadow `--shadow-float` is used only on floating modals and the login shell.
