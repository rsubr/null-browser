# Null Browser

A tiny, single-purpose Android app that turns any web app into a standalone,
fullscreen, incognito-only "app" pinned to your home screen — with zero
shared cookies, history, or state with your regular browser, and none
shared between the different sites you add.

## Why use this

Some web apps are better used like native apps: fullscreen, no address bar,
their own icon, launched with one tap. But you often don't want them sharing
your everyday browser's login sessions, cookies, autofill, or history — and
you don't want to install a heavyweight, tracking-laden "wrapper" app from an
app store just to get that.

Null Browser is the minimal version of that idea:

- **Fullscreen, chrome-free** — the web page fills the entire screen, no
  browser UI at all.
- **Always incognito** — cookies, cache, local storage, and history for a
  site are wiped every time you close it. Nothing persists across sessions.
- **Isolated per site** — each site you add gets its own Android process and
  its own WebView storage directory. Two sites added to your home screen
  (say, `gemini.google.com` and some other tool) can never see each other's
  cookies or session state, even though they're the same app under the hood.
- **Looks like a real app** — each site gets its own home-screen icon, using
  the site's actual favicon, and its own title that you choose.
- **Camera, microphone, and voice typing just work** — useful for web apps
  that rely on `getUserMedia` (voice input, video calls) or the on-screen
  keyboard's built-in voice typing / Gboard.

A good example: [gemini.google.com/app](https://gemini.google.com/app) —
add it once, get a fullscreen "Gemini" icon on your home screen that never
mixes its session with your regular browser, and forgets everything each
time you close it.

## How it works

The app has two kinds of screens:

1. **`Null Browser` (the app-drawer icon)** — opening this doesn't show a
   browser at all. It immediately asks you for a **title** and a **URL**,
   then creates a new home-screen shortcut for that site and opens it. This
   is a one-shot "add a new site" tool, not something you use day to day.

2. **A site shortcut (what you actually use)** — each site you add is
   assigned one of 10 fixed "slots". Each slot is declared in the Android
   manifest as its own `<activity>` running in its own OS process
   (`:site0` … `:site9`). Android's WebView keeps cookies, local storage,
   and cache separated by process + data directory
   (`WebView.setDataDirectorySuffix`), so each slot's browsing data is
   physically isolated from every other slot's — this is what makes
   cross-site data leakage impossible, rather than just "hidden."

When a site's favicon loads, the app registers or updates a pinned
home-screen shortcut (`ShortcutManager`) using that favicon as the icon and
your chosen title as the label. Long-press-free: there's a small,
translucent gear icon in the bottom-right corner of every site's fullscreen
view that opens a native settings menu:

- **Edit site** — change the title or URL for that shortcut in place.
- **Clear data & restart** — wipes that site's cookies/cache/storage and
  reloads the same URL, without touching the URL itself.
- **Close** — closes the site's window. Its process is killed and all
  browsing data for that site is cleared, so the next time you open it, it
  starts from a clean slate.

If you try to add an 11th site while all 10 slots are full, the app shows a
list of your existing sites and lets you pick one to evict — the picked
site's shortcut is disabled and its slot is reassigned to the new site.

There is no separate "browser" UI, no tabs, no bookmarks, no settings screen
beyond the per-site menu above — the entire codebase is a handful of small
Kotlin files.

## Installing

Grab the latest signed APK from this repo's
[Releases page](https://github.com/rsubr/null-browser/releases) and
sideload it (enable "install unknown apps" for whatever app you download it
with, then open the APK).

## Building locally

Requires JDK 17 and the Android SDK (`compileSdk`/`targetSdk` 35, `minSdk`
28).

```bash
./gradlew assembleRelease
```

A release build must be signed. For local builds, create a keystore and a
`keystore.properties` file in the project root (not committed — see
`.gitignore`):

```properties
storeFile=release-key.jks
storePassword=...
keyAlias=incognito
keyPassword=...
```

## CI/CD

`.github/workflows/release.yml` builds a signed release APK and publishes
it as a GitHub Release automatically on every push to `main` (including
merged pull requests) and on manual runs (`workflow_dispatch`). The signing
keystore is stored as a base64-encoded repository secret
(`RELEASE_KEYSTORE_BASE64`, plus `KEYSTORE_PASSWORD`/`KEY_ALIAS`/
`KEY_PASSWORD`) and is never committed to the repo.
