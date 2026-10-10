# Cleanup Walk

An Android app that turns screen time into a neighborhood cleanup.

Pick **"30-minute cleanup"**, follow a walking loop that prioritizes places with visible problems, pick up litter or report what you find, and at the end:

> You cleaned 1.8 km of your neighborhood and removed 14 pieces of litter.

The app's job isn't to entertain you — it converts screen interaction into physical action.

## Screenshots

| 1 · Home | 2 · Choose a duration | 3 · Walk in progress |
|:---:|:---:|:---:|
| <img src="docs/screenshots/01-home.png" width="250" alt="Home screen"> | <img src="docs/screenshots/02-choose-duration.png" width="250" alt="Choose how long to clean"> | <img src="docs/screenshots/03-walk-in-progress.png" width="250" alt="Walk screen with timer, distance, litter count and direction to the next stop"> |
| Start a walk or scan an issue; open issues listed below. | 15, 30, 45 or 60 minutes. The app gets your location and plans the loop. | Time left, distance, litter count, and an arrow to the next problem spot. |

| 4 · At a problem spot | 5 · Scan | 6 · AI suggestion |
|:---:|:---:|:---:|
| <img src="docs/screenshots/04-arrived-litter-count.png" width="250" alt="Arrived at a litter spot, choosing how many pieces were picked up"> | <img src="docs/screenshots/05-scan-camera.png" width="250" alt="Camera photo of litter"> | <img src="docs/screenshots/06-ai-detected.png" width="250" alt="Detected litter x3, 91% sure"> |
| "Done here" asks how many pieces you picked up. | Take a photo; the on-device model finds the litter in it. | The model's suggestion comes first — one tap to confirm or correct. |

| 7 · Clean or report | 8 · Summary | 9 · Home after a few walks |
|:---:|:---:|:---:|
| <img src="docs/screenshots/07-clean-or-report.png" width="250" alt="Did you clean it up? Report only or Cleaned it up"> | <img src="docs/screenshots/08-walk-summary.png" width="250" alt="Cleanup complete summary"> | <img src="docs/screenshots/09-home-with-issues.png" width="250" alt="Home screen with totals and open issues"> |
| Cleaned items count toward your total; reported ones are routed on future walks. | "You cleaned 1.8 km of your neighborhood and removed 14 pieces of litter." | Lifetime totals, plus open issues you can mark resolved or delete. |

## What it does

- **Spots problems with the camera.** Take a photo and **Gemma 3n**, Google's open-weight multimodal model running entirely on the phone, finds litter, overflowing bins, damaged public infrastructure, and fallen branches, with a count. Confirm or correct with one tap. Photos never leave the phone and are deleted right after analysis.
- **Plans a time-boxed route.** Choose 15, 30, 45, or 60 minutes. The planner builds a loop from your location that visits known problem spots within that time, weighting damaged infrastructure and overflowing bins highest, then fallen branches and litter, and preferring spots with more litter and fresher reports. Spare time becomes exploration stops in a new direction so you find new problems.
- **Guides the walk.** Time left, distance walked, litter removed, plus the next stop's distance and direction. Exploration stops tick off automatically when you reach them; at problem spots you tap **Done here** (resolving the issue) or **Skip**. A big **+1 litter** button counts everything you pick up along the way.
- **Remembers.** Open issues are saved so future walks route past them. Walks survive the app being closed. Lifetime totals show on the home screen.

Everything works offline, including the AI. There is no account, server, API key, or Google Play Services dependency.

## Build

Requirements: a recent Android Studio, or JDK 17 + Android SDK 36.

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # route planner, distance tracking, Gemma reply parsing, walk manager
```

Min SDK 26 (Android 8.0). For AI detection, use a phone with an arm64 CPU and about 6 GB of RAM or more; the emulator can run the app but not the model.

## On-device AI: Gemma 3n

Scans are analyzed by **[Gemma 3n E2B](https://huggingface.co/google/gemma-3n-E2B-it-litert-lm)**, an open-weight multimodal model, running on the phone through **[LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM)** (Apache 2.0). The camera photo and a fixed prompt go in; Gemma replies with a count per category, the main problem, and how sure it is:

```
litter: 3
overflowing_bin: 0
damaged_infrastructure: 1
fallen_branch: 0
main: litter
confidence: high
```

`domain/GemmaScan.kt` holds the prompt and a forgiving parser (it copes with markdown, different casing and missing lines), covered by unit tests. The first scan loads the model (a few seconds) and it stays loaded for the session. The vision encoder runs on the GPU when available and falls back to the CPU.

### Adding the model

The model is a free download, but it isn't bundled: it's a few GB and gated behind Google's Gemma license.

1. Sign in at Hugging Face, open [google/gemma-3n-E2B-it-litert-lm](https://huggingface.co/google/gemma-3n-E2B-it-litert-lm), and accept the license.
2. Download `gemma-3n-E2B-it-int4.litertlm` to the phone.
3. In the app, tap **⋮ → Import Gemma model** and pick the file. Or copy it over USB into `Android/data/app.cleanupwalk/files/models/`.

Until a model is added, scanning still works: you pick what you found after each photo. To try a different model, such as the larger Gemma 3n E4B or a fine-tuned version, import that `.litertlm` file instead. No rebuild is needed.

## How the route planner works

Choosing which spots to visit within a time limit is an *orienteering problem*. The planner (`domain/CleanupRoutePlanner.kt`):

1. Scores each open issue: `priority × (1 + ln(count)) × freshness`, where freshness halves every 14 days (floor 0.25).
2. Keeps issues reachable and returnable within the budget at 1.25 m/s, with straight-line distances × 1.3 to allow for streets.
3. Greedily inserts the issue with the best score-per-extra-second at its cheapest position in the loop, improving the loop with 2-opt after each insertion, until nothing else fits.
4. If more than 15% of the time is left, adds an octagonal exploration loop sized to the remaining time, heading away from the visited issues, and inserts its points the same way.

The estimated time never exceeds the time you chose (covered by a randomized test).

Directions are straight-line distance and compass direction, not turn-by-turn. Stay on safe, legal paths.

## Project layout

```
app/src/main/java/app/cleanupwalk/
├── domain/       Pure Kotlin: route planner, distance tracker, Gemma prompt + reply parser, walk progress
├── data/         Room database (issues, walks) and repository
├── detection/    Gemma 3n on LiteRT-LM, model import, photo capture
├── location/     GPS via the platform LocationManager
├── walk/         WalkManager - the active walk's state and persistence
└── ui/           Jetpack Compose screens (home, walk, scan flow)

docs/screenshots/ README screenshots
```

## Privacy

- Photos are analyzed by Gemma on the device and deleted immediately.
- Locations of issues and walk totals are stored only in the app's private database.
- Nothing is uploaded anywhere.
