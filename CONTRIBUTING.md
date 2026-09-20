# Contributing to Bunori

Thank you for your interest in contributing to Bunori! Contributions of all kinds are welcome, especially new sources, bug fixes, and improvemts to crawlers.

> [!NOTE]
> Crawler implementations and crawler-specific bug reports are maintained separately in **[BunoriSources](https://github.com/Binit06/BunoriSources)**. Please refer to that repository when contributing or reporting issues related to individual sources.

---

## What you can contribute

Some of the most useful ways to contribute are:
- Improve the core crawler architechture
- Improve the crawler API
- Fix bugs in the core application
- Improve chapter or novel handling
- Improve the Android Application
- Improve documentation
- Improve the performance or reliability
- Improve the DEX loading and crawler discovery system

For contributions, involving a speceific novel source or crawler implementation, please use [BunoriSources](https://github.com/Binit06/BunoriSources) instead.

---

## Getting started

### 1.  **Fork the repository on Github and clone your fork**
```
git clone https://github.com/<your_username>/Bunori.git
cd Bunori
``` 
### 2.  **Create a branch**

Create a branch for your contribution:

```
git checkout -b feature/my-source
```

Use a descriptive branch name such as:
- refactor/crawler-api
- docs/contributing
- feature/new-feature
- fix/new-fix

### 3.  **Build and Test**

Open the project in Android Studio and allow gradle to synchronize.

Before submitting a contribution, make sure the project builds successfully and test the affected functionality.

---

## Testing a New Crawler

Crawler implementation are maintained in [BunoriSources](https://github.com/Binit06/BunoriSources). However, you do not need to add a crawler there immediately when developing or debugging it.

If you want to test a crawller locally, you can:
- Add a crawler implementation directly to your fork of Bunori.
- Register the crawler in CrawlerFactory
- Build and run Bunori normally.
- Test and debug the crawler using the application's existing built-in crawler support.

This allows you to develop and test a crawler without having to update [BunoriSources](https://github.com/Binit06/BunoriSources) during development.

**THIS IS INTENDED FOR LOCAL TESTING ONLY.**

Once the crawler is ready and tested, it should be contributed to [BunoriSources](https://github.com/Binit06/BunoriSources) rather than being permanently added as a built-in crawler in Bunori.

---

## Adding a New Crawler

New crawler implementation should be contributed through [BunoriSources](https://github.com/Binit06/BunoriSources).

Please refer to [BunoriSources](https://github.com/Binit06/BunoriSources) README for instructions on implementing a new crawler.

### Keep Source-Specific Logic Isolated

- CSS Selectors,
- URL Handling,
- HTML Parsing,
- Source Speceific API handling
- Novel and chapter extraction

Avoid adding source-specific conditions to the shared crawler infrastructure when the behavior can be implemented inside the crawler itself.

This keeps individual sources independent and makes future contributions easier.

---

## API Compaitibilty

The :api module defines interfaces and contracts used by crawler implementations.

Changes to the API can affect **every crawler**, inlcuding crawlers that have already been built and packaged into a DEX.

>[!IMPORTANT]
>**Changes to the :api module can break existing crawlers if the new API is not backwards compatible**
> When modifying an existing API, consider how previously implemented crawlers will behave with the new version. Prefer backward-compatible changes where possible and provide graceful handling for older implementations when compatibility cannot be maintained directly.

When making API Changes:
- Check how existing crawlers use the affected API.
- Avoid unnecessarily breaking existing interfaces.
- Prefer adding new behaviour over removing or changing existing contracts.
- Provide sensible defaults when introducing new functionality.
- Handle older implementation gracefully where possible.
- Test existing crawlers against the updated APIs

If an API Change intentionally introduces a breaking change, clearly document the change and its impact on existing crawler. If needed also set a mininimum Version Requirement as mentioned in BunoriSource README.

---

## DEX Architechture

Bunori loads crawler implementations from a DEX containing the supporting sources.

The crawler implementations are packaged together, allowing the application to discover and use the available sources without coupling the core crawling logic to each individual sources.

Because crawlers depend on the contracts defined by the :api module, changes to those contracts should be made carefully and tested against existing implementations.

---

## Code Guidelines

Try to keep contributions consistent with the exisitng codebase.
- Prefer cleaner and descriptive names
- Keep functions focused on a single responsibility
- Avoid unnecessary abstractions
- Keep source speceific logic inside its crawlers
- Use Kotlin idioms where they improve readability
- Handle network and parsing failures gracefuly
- Avoid unrelated changes in the same pull batch

Most importantly **don't over-engineer a crawler**. A simple implementation that reliably handles the source is preferable to unnecessary complexity

---

## Pull Requests

Before opening a pull batch
- Make sure your changes build successfully
- Test the affected crawlers or functionality
- Check the existing functionality still works
- Remove debugging code and unnecessary changes
- Update documentation if your change requires it
- If you changed :api, test existing crawler implementations for compatibility.

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

- feat: add crawler factory support
- fix: handle missing chapter
- refactor: simplify crawler metadata
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

For crawler-specific issues, please open the issue in [BunoriSources](https://github.com/Binit06/BunoriSources).

For issues involving the core application, crawler API, DEX loading, or other functionality maintained in this repository, open the issue here.

When reporting a crawler-related issue, include:
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
