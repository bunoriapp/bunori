# Changelog

All notable changes to the Bunori project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---
## [2.0.2] - 2026-09-23

### Highlights
- Bunori now allows browsing novels alongside searching
- New Source Search Screen
- Similar Novel Detection while adding

### Major Fixes
- Navigation bar stays the same height across both 3 button tap and gesture based devices
- New Source Search Screen
- Metadata updates are now considered high priority jobs and happen fasted

### Added
- Search Screen for specific extension now shows novels from that page to easily browser
- New light mode themes for reader
- similar novel detection and indication before adding to library

### Improvements
- novel cards are now treated as global cards that should be used similarly across all screen
- new compact mode for novel cards

## [2.0.1] - 2026-09-22

### Highlights
- Bunori now supports amreabi-v7 device (only in slow mode)

### Major Fixes
- Fixed a bug that led to showing the activity screen while navigating rather than novel screen
- Downloaded chapters can now be backed and restored properly
- Repository URL can now be removed at the onboarding for a completely blank experience
- Storage location selected in the new app does not change even if old backup was brought back
- replaced absolute paths for storage with relative paths to support easier migration and file handling

### Added
- Brought back the cancellation box to confirm before cancelling or deleting downloaded chapters
- slow mode toasts can be hidden from Advanced Settings Screen if you plan to always use slow mode

### Improvements
- Allowed for swiping between tabs in Browse Screen
- Backup and Restore show a toast bar telling there start state
- standardized the color's of pause, resume and failed in Novel screen
- replaced sudden content block popup with a webview icon unblock at your own choice
- extension list now takes full space available in the extension tab box

## [2.0.0] - 2026-09-21

### Highlights & Major Features
- **Rebrand to Bunori**: Complete codebase, UI, and package modernization under the Bunori platform name. This will be installed as a new app and update won't be applied to the old LNCrawler
- **`.bext` Extension System**: Rebuilt crawler architecture powered by WebAssembly (WAMR bridge) with lightweight `.bext` packages and standalone extension repository management.
- **Brand New WebView Reader Engine**: Highly customizable reader supporting custom typography, font sizing, line spacing, margins, tap zones, AMOLED dark mode, and reading rulers.
- **Refactored Batch & Task Downloader**: Switched from DAG scheduling to a robust Batch & Task queue model with concurrency mutexes, source-level backoff, jitter, and retry capabilities.
- **Storage Optimization**: Chapters are now compressed and stored as `.html.gz` for significantly reduced storage footprints.
- **First-Time Onboarding**: Multi-step onboarding experience introducing app storage setup, theme selection, background permissions, and extension repositories.

### Added
- **Download Range Micro-Adjustments**: Added `+` and `-` controls in the chapter range download selector for easy fine-tuning.
- **Bandwidth Protection & Auto-Caching**: Automatic caching and offline promotion to avoid duplicate network fetches during reading.
- **Cloudflare Resolver**: Integrated WebView resolver with modern browser headers and WebKit cookie management for Cloudflare-protected sources.
- **Artifact Exporters**: Export downloaded chapters to EPUB and PDF formats with source-level scanlation filtering.
- **Background Maintenance**: Workers for automatic cache cleanup, database novel pruning, and scheduled full backup/restore routines.
- **Contextual Activity Carousel**: Active download and crawler tracking linked seamlessly with the bottom navigation bar.
- **App Update Manager**: In-app APK updater and changelog viewer.

### UI & UX Improvements
- New navigation bar animations and transitions.
- Redesigned unified Novel screen replacing preview sheets.
- Scanlation source ordering and selection persistence across restarts.
- Custom theme palette system with pure AMOLED black mode and dynamic theme previews.
- Centered onboarding layouts with crisp typography alignment.

### Fixes & Stability
- Fixed Runner mutex pool hoarding queue jobs across multiple sources.
- Fixed extension icon loading from `.bext` packages.
- Fixed chapter metadata duplication when updating source crawlers.
- Fixed screen timeout issues during active WebView text extraction.
- Improved foreground notification detail and channel handling for active downloads.

### Known Issues
- Webview Reader does not show the completion percentage clearly in paged mode
- Chapters with very small content that does not even pass the width of the phone leads to the next chapter not loading
- Issues with auto text extraction from webview in case of blocking