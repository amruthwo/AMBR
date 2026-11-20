# Publishing playbook

How AMBR goes from a folder on your computer to GitHub, GitHub Releases, Weblate and IzzyOnDroid.
It's written to be reused for other apps (e.g. Worm): swap the names in **bold** for yours.

---

## 0. One-time setup on your computer

- `git` and the GitHub CLI `gh`, logged in: `gh auth login` (check with `gh auth status`).
- JDK 21 + Android SDK (for local builds). Gradle's JDK path lives in `~/.gradle/gradle.properties`
  (`org.gradle.java.home=…`), **not** in the project, so CI isn't affected.

## 1. Signing key hygiene (do this before the first commit)

Android only installs an update if it's signed with the **same key** as the installed app. Lose
the key and users must uninstall to update. Leak it and someone could ship fake updates.

1. Keep the keystore **outside git**: `keystore/**app**.jks`.
2. Put passwords in `keystore.properties` (git-ignored):
   ```properties
   storeFile=keystore/app.jks
   storePassword=…
   keyAlias=…
   keyPassword=…
   ```
3. In `app/build.gradle.kts`, read `keystore.properties` **or** environment variables (for CI),
   and leave the build unsigned if neither exists (see the top of AMBR's `app/build.gradle.kts`).
4. `.gitignore` must contain: `keystore/`, `keystore.properties`, `*.jks`, `*.keystore`,
   `local.properties`, `dist/`, `*.apk`, plus any personal files.
5. **Back up** the keystore and `keystore.properties` somewhere safe and offline (password manager,
   encrypted USB). Check: `apksigner verify --print-certs app.apk` shows the same SHA-256 every release.

## 2. Repository layout

```
README.md                 ← the front page (format below)
CHANGELOG.md              ← human-readable history
CONTRIBUTING.md           ← how to report bugs / send PRs / translate
LICENSE                   ← GPL-3.0 (required if you bundle GPL code like youtubedl-android)
art/                      ← logo source files
docs/PUBLISHING.md        ← this file
fastlane/metadata/android/en-US/
    title.txt               (≤ 50 chars)
    short_description.txt   (≤ 80 chars)
    full_description.txt    (simple HTML: <p>, <b>, <i>, <ul>/<li>)
    changelogs/<versionCode>.txt   (≤ 500 chars, one per release)
    images/icon.png                 (512×512)
    images/featureGraphic.png       (1024×500)
    images/phoneScreenshots/1_*.png … (portrait, from a demo setup, nothing personal)
.github/
    ISSUE_TEMPLATE/{bug_report,feature_request,translation}.yml, config.yml
    PULL_REQUEST_TEMPLATE.md
    workflows/ci.yml        (build every push/PR)
    workflows/release.yml   (tag → signed APKs → GitHub Release)
```

The `fastlane/` folder is what IzzyOnDroid and F-Droid read for the store listing.

### README format
1. Banner (`featureGraphic.png`) + one-line pitch + badges (release, Android version, license)
2. **What is it?** (2–3 sentences) + a row of 3–4 screenshots
3. **Features** grouped by theme (bold group names, short bullets)
4. **Install** (release APK table: which file for which phone; store links)
5. **Permissions / Privacy** (why each sensitive permission is needed; what never leaves the phone)
6. **Build from source** (requirements + 3-line command block)
7. **Contributing & translations** (links to templates, CONTRIBUTING.md, Weblate)
8. **Credits** (people + libraries with links) and **License**

### Screenshots without personal data
Use the emulator with demo content, never your own phone:
```bash
# neutral demo videos/books, then:
adb -e shell settings put global device_name "Demo phone"
adb -e shell settings put global sysui_demo_allowed 1
adb -e shell am broadcast -a com.android.systemui.demo -e command enter
adb -e shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200
adb -e shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb -e shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
adb -e shell screencap -p > shot.png
```

## 3. First push (the GitHub repo already exists)

```bash
git init -b main
git remote add origin https://github.com/OWNER/REPO.git
git fetch origin && git reset origin/main     # keep GitHub's initial commit (e.g. LICENSE)
git checkout -- LICENSE                       # restore files that only existed on GitHub
git status                                    # CHECK: no keystore, keystore.properties, apk, personal files
git add -A && git commit -m "Initial public release"
git push -u origin main
```

## 4. Releasing a version

1. Bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts`.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` and update `CHANGELOG.md`.
3. Commit, then **either**:
   - **Local release** (key never leaves your computer):
     ```bash
     ./gradlew assembleRelease
     V=3.0.2
     cp app/build/outputs/apk/release/app-arm64-v8a-release.apk   AMBR-$V-arm64.apk
     cp app/build/outputs/apk/release/app-armeabi-v7a-release.apk AMBR-$V-armv7.apk
     git tag v$V && git push origin main v$V
     gh release create v$V AMBR-$V-*.apk --title "AMBR $V" --notes-file fastlane/metadata/android/en-US/changelogs/6.txt
     ```
   - **CI release**: push a `vX.Y.Z` tag; `release.yml` builds, signs and publishes. One-time
     setup in GitHub → Settings → Secrets and variables → Actions:
     - `AMBR_KEYSTORE_BASE64` = output of `base64 -w0 keystore/app.jks`
     - `AMBR_KEYSTORE_PASSWORD`, `AMBR_KEY_ALIAS`, `AMBR_KEY_PASSWORD`

     (This uploads your key to GitHub's encrypted secrets. Convenient, but it's a decision to make
     consciously. The local release above avoids it.) If you skip CI releases, delete
     `.github/workflows/release.yml` so a tag push doesn't create a failed run.

## 5. Translations with Weblate

1. **Move every user-visible string** into `app/src/main/res/values/strings.xml`. What AMBR did:
   - Add an app-wide helper (`util/Format.kt`): `fun str(@StringRes id: Int, vararg args: Any?) = App.instance.getString(id, *args)`
     (set `instance` in `Application.onCreate`). It works in Compose, ViewModels, workers and
     notifications alike, so no context plumbing. Plurals: `plural(R.plurals.x, n)`.
   - Run the extractor (kept in `~/tools/extract_strings.py`, dry run first):
     ```bash
     python3 ~/tools/extract_strings.py --src app/src/main/java --pkg com.example.app \
       --res app/src/main/res --helper com.example.app.util.str \
       --exclude-file 'Server|Client|ui/theme|util/' --dry-run
     ```
     It turns `"Moved to ${name}"` into `str(R.string.x_moved_to_x, name)` + `Moved to %1$s` and
     skips logs, regexes, JSON/pref keys and URLs.
   - Fix by hand: words chosen in code (`if (n == 1) "copy" else "copies"` → `<plurals>`), and
     lowercase single words the script skips (grep for `"[a-z]+"` in UI files).
   - Verify with **pseudo-locales**: `debug { isPseudoLocalesEnabled = true }`, then
     `adb shell cmd locale set-app-locales <debug app id> --locales en-XA`. Every translatable
     string turns `[Åççéñţéð]`; anything still plain English is hard-coded.
   - `androidResources { generateLocaleConfig = true }` + `res/resources.properties`
     (`unqualifiedResLocale=en-US`) lists each translated language in Android 13+'s per-app language setting.
2. Apply for free hosting at <https://hosted.weblate.org/hosting/> (Libre plan for open-source).
3. Create a project + component:
   - Source code repository: `https://github.com/OWNER/REPO.git`, branch `main`
   - File format: **Android String Resource**
   - File mask: `app/src/main/res/values-*/strings.xml`
   - Monolingual base language file: `app/src/main/res/values/strings.xml`
   - Template for new translations: same as the base file
4. Let Weblate push changes as pull requests: in Weblate, set *Repository push URL* to your fork
   or enable the GitHub integration, and add the Weblate webhook to the GitHub repo
   (Settings → Webhooks → `https://hosted.weblate.org/hooks/github/`).
5. Put the Weblate widget/link in the README and CONTRIBUTING. Android language codes: `values-es`,
   `values-pt-rBR`, etc. Android 13+ per-app language picker: add `res/xml/locales_config.xml`
   and `android:localeConfig` in the manifest.

## 6. IzzyOnDroid (F-Droid–compatible repo)

Why Izzy first: the main F-Droid repo builds everything from source and forbids prebuilt binaries.
AMBR bundles prebuilt Python/yt-dlp/FFmpeg via youtubedl-android, so it can't go to F-Droid main
without a downloader-free flavor. Izzy publishes the APKs from your GitHub Releases instead.

Requirements:
- Open-source license (GPL-3.0 ✓), source public on GitHub ✓
- A **GitHub Release with the APK attached** (Izzy picks up new releases automatically)
- `fastlane/metadata/android/en-US/` in the repo for the listing ✓
- No proprietary tracking/ads libraries (AMBR uses none)
- Izzy prefers a single APK per release. With ABI splits, tell them which file to use
  (`AMBR-x.y.z-arm64.apk`), or add a universal APK.

Submit: open an issue at <https://gitlab.com/IzzyOnDroid/repo/-/issues> using the
**"Request for Packaging (RFP)"** template, with the repo URL and a short description. Izzy runs
scanners (libraries, permissions, anti-features) and may ask questions. Expect to explain
`MANAGE_EXTERNAL_STORAGE` (it organizes files in a folder you choose) and the downloader
(uses yt-dlp; anti-feature "NonFreeNet" for downloading from non-free services).

## 7. F-Droid main repo

F-Droid builds every app from source on its own servers and won't ship prebuilt binaries, so AMBR
has two **product flavors**:
- `full`: GitHub build, with youtubedl-android (prebuilt Python/yt-dlp/FFmpeg). Its code lives in
  `app/src/full/`, and its dependencies are `"fullImplementation"(…)`.
- `fdroid`: no link downloader. `app/src/fdroid/` has a stand-in `YtDlp` with `available = false`,
  and the UI hides link-download features when it's false.

Also required (already done): no Google dependency-info block (`dependenciesInfo { includeInApk = false }`),
fastlane metadata, only free libraries, no Google Play Services.

How to submit:
1. Release a version tag (e.g. `v3.1.0`) so F-Droid can build that exact commit.
2. Fork <https://gitlab.com/fdroid/fdroiddata>, add `metadata/com.ambr.reloaded.yml` (template in
   `docs/fdroid/`), and open a merge request. F-Droid's CI test-builds it; reviewers comment there.
   (Simpler alternative: open a "Request For Packaging" issue at <https://gitlab.com/fdroid/rfp/-/issues>
   and a volunteer writes the metadata.)
3. Once merged, new `v*` tags are picked up automatically (`UpdateCheckMode: Tags`).

F-Droid signs with **its own key**, so F-Droid installs and GitHub installs can't update each other
(users must uninstall to switch, and hidden folders are deleted on uninstall). Reproducible builds
would let F-Droid ship your signature instead; that's an option for later.

**Obtainium** (<https://github.com/ImranR98/Obtainium>) is the easy middle ground: it installs and
updates straight from GitHub Releases. The README has an "Add to Obtainium" badge.

## 8. Checklist for every release
- [ ] versionCode +1, versionName bumped
- [ ] changelog `<versionCode>.txt` + `CHANGELOG.md`
- [ ] `./gradlew assembleRelease` passes; APK installs over the previous version (same key)
- [ ] tag `vX.Y.Z` pushed; GitHub Release has the APKs
- [ ] screenshots still accurate (update `phoneScreenshots/` if the UI changed)
