# Contributing to Bunori

Thank you for your interest in contributing to Bunori! Contributions of all kinds are welcome, especially new sources, bug fixes, and improvemts to sources.

> [!NOTE]
> Source implementations and source-specific bug reports are maintained separately in **[extensions](https://github.com/bunoriapp/extensions)**. Please refer to that repository when contributing or reporting issues related to individual sources.

---

## Table of Contents
- [What You Can Contribute](#what-you-can-contribute)
- [Project Architecture](#project-architecture)
- [Getting Started](#getting-started)
- [Testing a New Source](#testing-a-new-source)
- [Adding a New Source](#adding-a-new-source)
- [Source Compatibilty](#source-compatibilty)
- [BEXT Architechture](#bext-architechture)
- [Code Guidelines](#code-guidelines)
- [Pull Requests](#pull-requests)
- [Reporting Issues](#reporting-issues)
- [Code of Conduct](#code-of-conduct)
---

## What you can contribute

Some of the most useful ways to contribute are:
- Improve the core download architechture
- Improve the sources(`:extenstion-api`) API
- Optimising the WebAssembly Micro Runtime (WAMR) JNI Bridge and assembly fallback layers
- Improve Webview Reader Engine
- Improve the Database storage methods and usage
- Improve Headless Webview Resolver
- Resolve UI Crashes, Memory Leaks, Cookie Management and support for various User-Agent Headers
- Expand Setup Guide, code comments and contribution workflows

For contributions, involving a speceific novel source or source implementation, please use [extensions](https://github.com/bunoriapp/extensions) instead.

---

## Getting started

### 1.  **Fork the repository on Github and clone your fork**
```
git clone https://github.com/<your_username>/bunori.git
cd bunori
``` 

### 2. Configure Your Environment

- **Android Studio**: Android Studio Ladybug (2024.2+) or newer.
- **JDK**: Java 17 (Eclipse Temurin 17 recommended).
- **Android NDK**: Version 30.0.16248370 (configured automatically via Gradle).
- **CMake**: Version 3.22.1+.

### 3.  **Create a branch**

Create a branch for your contribution:

```
git checkout -b feature/my-source
```

Use a descriptive branch name such as:
- `feature/` — for new features or capabilities (e.g. feature/tap-zone-customization)
- `fix/` — for bug fixes (e.g. fix/novel-pruning-cascade)
- `refactor/` — for code restructuring or cleanup (e.g. refactor/download-mutex)
- `docs/` — for documentation updates (e.g. docs/update-contributing)

### 4.  **Build and Test**

Open the project in Android Studio, allow Gradle to synchronize, and run a test build:

```
./gradlew compileDebugKotlin
./gradlew test
```

---

## Testing a New Source

Sources implementation are maintained in [extensions](https://github.com/bunoriapp/extensions). However, you do not need to add a source there immediately when developing or debugging it.

If you want to test a source locally, you can:
- Implement a new source by following the guide in [extensions](https://github.com/bunoriapp/extensions)
- Genearate the `.bext` file locally and send it over to your device
- Go to `More -> Extensions -> Load from .bext file` to test the extension directly on the device

This allows you to develop and test a source without having to update [extensions](https://github.com/bunoriapp/extensions) during development.

Once the source is ready and tested, it can be contributed to [extensions](https://github.com/bunoriapp/bunori) by following the contribution guidelines over there.

---

## Adding a New Source

New source implementation should be contributed through [extensions](https://github.com/bunoriapp/extensions).

Please refer to [extensions](https://github.com/bunoriapp/extensions) README for instructions on implementing a new source.

---

## Source Compatibilty

The sources are built and published individually and have backward compatibility

The `:extension-api` handles the job for loading the `.bext` files and etracting the manifest fields over to the app as well as loading the .aot from the package into the app.

If a change to `:extension-api` is made it may directly affects how the app loads `.bext` files, clearly document the change and its impact on existing loading system.

---

## BEXT Architechture

Bunori loads source implementations from a BEXT (**B**unori **Ext**ensions).

The source is package into a .bext file containing
- `manifest.json`
- `icon.webp`
- `source.wasm`
- `artifacts/arm64-v8a/extension.aot`
- `artifacts/x86_64/extension.aot`

---

## Code Guidelines

Try to keep contributions consistent with the exisitng codebase.
- Prefer cleaner and descriptive names
- Keep functions focused on a single responsibility
- Avoid unnecessary abstractions
- Keep source speceific logic inside its sources
- Use Kotlin idioms where they improve readability
- Handle network and parsing failures gracefuly
- Avoid unrelated changes in the same pull batch

Most importantly **don't over-engineer**. A simple implementation that reliably handles the something is preferable to unnecessary complexity

---

## Pull Requests

Before opening a pull batch
- Make sure your changes build successfully
- Test the affected sources or functionality
- Check the existing functionality still works
- Remove debugging code and unnecessary changes
- Update documentation if your change requires it
- If you changed :api, test existing source implementations for compatibility.

When opening a pull batch, briefly describe:
- What you changed
- Why the change was needed
- How you tested it
- Any limitation or known issues
- Any API compatibility considerations, if applicable

---

## Commit Messages

Keep commit messages concise and descriptive

Examples:

- feat: add new job factory support
- fix: handle missing chapter
- refactor: add new field to source metadata
- docs: improve contributing guide

Avoid commits such as:

- stuff
- changes
- update
- final
- fixed

---

## Reporting Issues

If you find a bug, please open an issue with enough infomation to reproduce it.

For source-specific issues, please open the issue in [extensions](https://github.com/bunoriapp/extensions).

For issues involving the core application, source API, BEXT loading, or other functionality maintained in this repository, open the issue here.

When reporting a source-related issue, include:
- Source Name
- Novel URL, if Applicable
- Chapter URL, if Applicable
- What you expected to happen
- What actually happened
- Relevant logs or error messages

Please avoid posting personal information or unnecessary sensitive data in issues.

---

## Code of Conduct

Please be respectful and constructive when interacting with other contributors.

Contributions are evaluated based on their technical merits, regardless of who submitted them.

---

## Thank You

Every Contribution makes Bunori better.

Whether you are adding a new source, fixing a small bug, improving the documentation, or simply reporting an issue, thank you for contributing to Bunori.
