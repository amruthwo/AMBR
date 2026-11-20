# Changelog

All notable changes to AMBR. Versions follow `versionName` (versionCode in brackets).

## 3.2.0 (9)
### Add folders from your phone
- **+ → Add a folder from your phone** links an existing folder (TV, Movies, Download, a channel…) without copying or moving anything. Pick Big Brain or Brain Rot; sub-folders become seasons (Big Brain) or their own feeds (Brain Rot).
- **"Each folder inside is its own show"** turns a TV/channels folder into a library: every show is its own Big Brain tile, and new shows are picked up automatically.
- Works on **SD cards**. Where Android blocks direct writes, AMBR asks once per folder through Android's folder picker, then rename, move, delete and Cram work there too.
- Added folders show a 🔗 badge; manage them in **Settings → Folders from your phone**. Removing one never deletes its videos. "Hide from galleries" and "Hidden folder" stay limited to AMBR's own folder.
### Big Brain episodes
- Long-press an episode (or tap ⋮) for **Favorite, Share, Rename, Move to…, Cram, Delete**, plus Mark watched.
- **Move to…** lists every folder: this show's seasons, Brain Rot folders, other shows and their seasons.
### Also
- Big Brain player: transport controls moved to the bottom, above the progress bar; in landscape they share one row with it.
- Big Brain folders: sort episodes by Name, Date added, Length or Size (tap again to reverse; remembered per folder).
- Big Brain folders: **New season** button, and **Move to…** on each episode; the regular Move sheet now lists seasons too.
- Continue/Play button shows the episode title on its own line.
- Toolbar: search is a plain magnifying-glass icon, styled like the others.
- AMBR Lite is now a separate app (`com.ambr.reloaded.lite`, "AMBR Lite"), so it installs side by side with AMBR instead of replacing it.

## 3.1.0 (8)
### Big Brain & Brain Rot
- Every folder now has a mode. **Brain Rot** (default) is the shuffled, looping short-video feed. **Big Brain** (long-press a folder) plays in order with full controls: previous/next, ±10s, play/pause, speed, rotate, mute, scrubbing; no looping; auto-advances.
- Big Brain folders open a show screen: sub-folders become seasons (tabs), episodes sort naturally (2 before 10), with lengths, resume progress and watched marks, plus a Continue button.
- Resumes each video where you left off.
### Also new
- Video size options for both modes: Auto, Fit, Fill (crop), Stretch, Original (Settings, the Big Brain top bar, or the Brain Rot ▾ menu).
- Search across all videos: filter Brain Rot / Big Brain, sort by name, size or folder.
- Toolbar: the layout button cycles list / 2 / 4 per row; Search sits in the middle.
- About: link to the GitHub project for news and updates.
- New **F-Droid / lite build** (4.6 MB) without the link downloader; GitHub releases include it as `AMBR-x.y.z-lite.apk`.

## 3.0.3 (7)
- AMBR is translatable: all text moved to `strings.xml`, translations on [Weblate](https://hosted.weblate.org/engage/ambr/)
- Proper plural forms; Android 13+ per-app language list generated from available translations
- APK no longer contains Google's encrypted dependency-metadata block (IzzyOnDroid / F-Droid requirement)

## 3.0.2 (6) – first public release

### Watching
- Swipeable, shuffled feed per folder, plus **Shuffle All** across every folder
- Gestures: tap to mute, double-tap left/right to seek (keep tapping to stack +10s, +20s…), double-tap the middle to favorite, hold for 2× (configurable), pinch to zoom
- Squiggly Material 3 Expressive progress bar, shape-morphing play button, split button for mute and video actions
- Videos always fit the screen width; near-screen-shaped videos fill it

### Organizing
- List / 2-per-row / 4-per-row folder views
- Create, rename and delete folders; rename, move, delete, share and favorite videos
- Hidden folders in app-private storage, unlocked with a PIN or fingerprint; screenshots blocked while unlocked
- Videos hidden from other gallery apps by default (`.nomedia`), with per-folder overrides
- Duplicate finder (size → partial hash → full SHA-256)
- Brain scan: video count, space used, watch time, space per folder, phone storage, biggest videos

### Cram Session
- Re-encode videos to 480p / 720p / 1080p with the phone's hardware H.264 encoder
- Persistent queue: pause, resume, survives restarts; optional "only while charging"
- Originals deleted only after the new file is verified; files that wouldn't shrink are kept

### Mind Meld
- Phone-to-phone sharing over the same Wi-Fi or a private hotspot, joined by QR code or nearby discovery + 6-digit code
- Browse each other's folders; take or send single videos or whole folders; resumable transfers
- Web portal for computers: browse, stream, download (single or zipped folder), drag-and-drop upload

### Save to AMBR
- Share videos or links from any app; links download in the background with yt-dlp
- Site sign-ins for Reddit, Instagram and X; Reddit videos read straight from the post page
- In-app yt-dlp updates
