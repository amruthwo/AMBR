# Changelog

All notable changes to AMBR. Versions follow `versionName` (versionCode in brackets).

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
