# Contributing to AMBR

Thanks for helping! A few notes:

## Bugs and ideas
Use the [issue templates](https://github.com/amruthwo/AMBR/issues/new/choose). For bugs, include your phone model, Android version, AMBR version (Settings → About) and steps to reproduce. For download problems, include the exact error text (please blank out anything personal).

## Pull requests
1. Fork, branch from `main`, keep changes focused.
2. `./gradlew assembleDebug` must build. CI checks every PR.
3. Match the existing style (Kotlin, Jetpack Compose, Material 3 Expressive).
4. Describe what you changed and how you tested it (device/emulator, Android version).
5. Add user-facing changes to `CHANGELOG.md` under an "Unreleased" heading.

By contributing you agree your work is licensed under GPL-3.0.

## Translations
Translations will be managed on Weblate (link coming soon) so you can translate in your browser without touching code. Until then, open an issue to volunteer.

## Never commit
Keystores, `keystore.properties`, passwords, cookies, or personal videos.
