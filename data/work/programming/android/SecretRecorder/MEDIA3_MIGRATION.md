# Media3 Upgrade Notes (Migration Reference)

> **Status: DO NOT UPGRADE — stays pinned at 1.8.0 for now.**
> Current recordings seek correctly and are "perfect". There is no pressing need
> to upgrade. This doc captures everything needed to do the migration safely
> later. When starting the migration, do fresh research (release notes, patch
> rebase) — the details below are the starting context, not a complete plan.

## Why media3 is pinned at 1.8.0

- The app uses a **patched media3 fork** (`media3-patched` repo, configured in
  `settings.gradle.kts` as a composite build) that substitutes:
  - `media3-muxer` → `lib-muxer`
  - `media3-common` → `lib-common`
  - `media3-container` → `lib-container`
- The patch contains **custom MP4 muxer fixes** that are essential:
  - Hybrid MP4 finalization (appended `moov`, `stsz`/`trun` correctness)
  - AVCC conversion / Annex-B detection fixes
  - Abandoned-file recovery (OEM kill recovery, HEVC support)
- The patch is built against **1.8.0 internals**. Bumping media3 without
  rebasing the patch breaks compilation and/or the muxer fixes.

## What the app uses media3 for

- **Playback** (`PlaybackService`, `VideoPlayerActivity`, `PlayerHolder`,
  `FaditorEditorActivity` etc.)
- **Custom seekable fMP4 source** (`FragmentedMp4SeekableMediaSource`,
  `SeekableFragmentedMp4MediaSourceFactory`) — wraps recordings in a
  `ClippingMediaSource` to enforce correct duration + enable seeking on hybrid
  MP4 files (fragmented files without `sidx`).
- **Faditor (video editor)** — `media3-transformer` + `media3-effect` for
  editing/export (including waveform generation via MediaCodec).
- **Export service** (`ExportService`) — transformer-based export with progress
  broadcasts.
- Custom `MediaSource.Factory` implementation (was `MediaSourceFactory`, renamed
  in our deprecation cleanup — API unchanged).

## Current state (verified)

- Recordings use **hybrid MP4**: fragmented during capture (crash-safe), then a
  `moov` is appended on finalize (hybrid finalization patch).
- **Seeking works correctly today** on the current build. No user-facing
  seeking bug exists. This is why there is no urgency.
- The `FragmentedMp4SeekableMediaSource` workaround is in place because hybrid
  files without `sidx`/`mfra` would otherwise be unseekable. It enforces the
  correct duration via `ClippingMediaSource`.
- App `minSdk = 24`.

## What newer media3 versions offer (context from 1.9/1.10/1.11 release notes)

> Researched Aug 2026 — re-verify when starting the migration.

### 1.9.0 (Dec 2025)
- **fMP4 seeking via `mfra` box** (`FLAG_READ_MFRA_FOR_SEEK_MAP`) — seeks
  fragmented MP4 without `sidx`. Directly relevant to our custom source.
- `MediaMuxerCompat` (drop-in for framework MediaMuxer), `WebmMuxer`, `OggMuxer`
- New `:media3-inspector` module (metadata/frame extraction)
- Muxer: correct sample flags for audio in fragmented MP4
- ExoPlayer: scrubbing mode, wake-lock by default, local-file load control
  tuning
- minSdk raised 21 → 23 (no impact, our minSdk is 24)

### 1.10.0 (Mar 2026)
- VVC MP4 support, Dolby Vision Profile 10
- `InAppMp4Muxer` as default transformer muxer,
  `setAttemptStreamableOutputEnabled`
- `MediaSessionService`/`MediaLibraryService` become `LifecycleService`
- Various concurrency/audio-session bug fixes

### 1.11.0 (Aug 2026)
- **fMP4 `mfra` seeking enabled by default** in `DefaultExtractorsFactory`
- **MP4 chapter metadata support (Nero `chpl` + QuickTime `©nam`)** — reads
  chapter markers from third-party files; exposed as `Chapter` entries in track
  metadata. QuickTime preferred when both present.
- HAGC (HDR) timed metadata, `Format.channelMask`
- `OggMuxer`/`WavMuxer`, `tref` track references in `Mp4Muxer`
- Transformer: export progress polling + cancel, `ExportResult.fileSizeBytes`
  fix
- Muxer/container: many concurrency + parsing fixes

## Notes on chapters vs. our bookmark feature

- **User bookmarks** (PR under review) are app-side data (stored positions,
  jump via `player.seekTo`). Works **today, no media3 upgrade needed**.
- **MP4 chapter metadata** (1.11) is reading **pre-existing chapters baked into
  third-party files** (movies/audiobooks/podcasts). Not needed for our own
  recordings (which have no chapters) and unrelated to user bookmarks.
- Conclusion: do **not** upgrade media3 for the bookmark feature.

## Seeking on old phones (clarification)

- The `mfra`-seeking feature is **library-side** (media3 parses the box itself),
  NOT an OS feature. It works on **all Android versions**, old phones included,
  once the new media3 ships inside the app.
- So a future upgrade would fix fMP4 seeking for Android 7 phones too — but
  since current recordings seek fine, this is not a current problem.

## Migration checklist (do this when we decide to upgrade)

1. **Fresh research** — read the actual release notes for 1.9, 1.10, 1.11
   (and whatever is current then); check for breaking changes in muxer,
   transformer, session APIs.
2. **Rebase the patch** — in `media3-patched`, fetch upstream, rebase the
   custom muxer commits onto the target version's tag. Verify the patch still
   applies; expect conflicts in `Mp4Muxer`/`FragmentedMp4Muxer` (internals
   changed: sample-flag handling in 1.8, BufferInfo API, new track-reference
   API in 1.11).
3. **Bump `media3` in `gradle/libs.versions.toml`** only after the patch builds.
4. **Verify recording regression** (critical — the patch touches muxer
   correctness):
   - Record a video, pull it, run the AGENTS.md playbook verification:
     - `ffmpeg -v error -i file.mp4 -f null -` → exit 0, no "Invalid NAL unit
       size"
     - `ffprobe` duration ≠ `-1ms`
     - Box-walk: appended `moov` `stsz` entries must EXACTLY match the fragment
       `trun` sample sizes for both tracks (0 mismatches = PASS)
   - Test crash-recovery (kill app mid-record, verify file is finalized/playable)
   - Test seeking on hybrid files (with and without the custom source)
5. **Verify playback/editor** — normal playback, Faditor open/export, playback
   position notifications.
6. If native `mfra` seeking works, evaluate **removing
   `FragmentedMp4SeekableMediaSource`** (the workaround may become redundant).
7. Re-run the deprecation cleanup (`-Xlint:deprecation` via the init script) —
   newer media3 deprecates more APIs.

## Open questions for the migration

- Does the hybrid finalization patch apply cleanly to 1.9/1.10/1.11? (Expected
  conflicts — the muxer internals moved.)
- Do our finalized hybrid files already seek without the custom source (they
  have a proper `stsz`)? If yes, the `mfra` feature is irrelevant and the only
  upgrade value is bug fixes / transformer improvements.
- Is the transformer-based Faditor export worth the upgrade risk? (1.10/1.11
  changed transformer muxer defaults and export APIs.)
