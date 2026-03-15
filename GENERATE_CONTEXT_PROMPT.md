
---

You are a senior software architect and technical documentation expert.

Your task is to analyze this project and produce a complete, accurate `CONTEXT.md` file that any developer or LLM can use to instantly understand and continue work on this project.

---

### Step 1 — Project Scan

Before writing anything, silently scan and analyze:

- All files in the root directory and subdirectories
- `build.gradle`, `package.json`, `pubspec.yaml`, `pom.xml`, `Cargo.toml`, or equivalent (tech stack, dependencies, versions)
- `AndroidManifest.xml`, `Info.plist`, or platform config files (permissions, entry points)
- Main source folders and their structure (identify architectural pattern: MVVM, MVC, Clean, etc.)
- Any `README.md` or existing docs
- Recent git log if accessible (`git log --oneline -20`)
- Any `.env.example`, `config/`, or settings files
- Test files to understand what's covered
- Any TODO/FIXME comments in source files

If you cannot access the file system directly, ask me to paste:
1. The output of: `find . -type f | grep -v ".git" | grep -v "node_modules" | grep -v "build" | head -80`
2. The output of: `git log --oneline -15` (if it's a git repo)
3. Any key files you think are important

---

### Step 2 — Generate CONTEXT.md

Using everything you've gathered, produce the complete `CONTEXT.md` below.
Fill every section with real values — never leave placeholder text if the actual value can be determined.
Only use `<!-- TBD -->` if genuinely unknowable from the codebase.

---

```markdown
# Project Context
> Last Updated: [TODAY'S DATE]
> Version: [from build file or tag, else 0.1.0]
> Updated By: [ChatGPT / GPT-4o]

---

## 1. Project Overview

| Field | Value |
|---|---|
| **Project Name** | [inferred from build file, folder name, or manifest] |
| **Type** | [Android App / iOS App / Web App / CLI Tool / Library / API] |
| **Primary Language** | [Kotlin / Swift / TypeScript / Python / etc.] |
| **Platform** | [Android API XX+ / iOS XX+ / Node XX / Browser / etc.] |
| **Status** | [Prototype / In Development / Beta / Production] |
| **Short Description** | [One sentence — what does this app do for the user?] |

---

## 2. Goals & Non-Goals

### Goals
- [Core user problem this solves]
- [Key outcomes the project must achieve]

### Non-Goals
- [Anything explicitly out of scope based on code or structure]

---

## 3. Architecture

### High-Level Structure
```
[Draw a text diagram of the layer/module structure you observed]
Example:
UI Layer         → Activities / Composables / ViewModels
Domain Layer     → UseCases / Models
Data Layer       → Repositories / Room / Retrofit
```

### Key Modules / Packages
| Module / Package | Responsibility |
|---|---|
| [package or folder] | [what it does] |

### Design Patterns Used
- [MVVM / Clean Architecture / Repository Pattern / etc. — inferred from code structure]

---

## 4. Tech Stack

| Layer | Technology | Notes |
|---|---|---|
| Language | [e.g. Kotlin 2.0] | |
| UI | [e.g. Jetpack Compose / XML] | |
| Async | [e.g. Coroutines + Flow / RxJava] | |
| DI | [e.g. Hilt / Koin / Manual] | |
| Database | [e.g. Room / SQLite / Realm] | |
| Networking | [e.g. Retrofit / Ktor / None] | |
| Testing | [e.g. JUnit5 / Espresso] | |
| Build System | [e.g. Gradle KTS / AGP 8.x] | |

---

## 5. Current State

### What's Working
- [Features that exist and appear complete based on the code]

### In Progress
- [Code that appears partially implemented — incomplete methods, TODO comments, unconnected modules]

### Known Issues / Bugs
- [Any FIXME comments, crash-prone patterns, or obvious issues spotted in code]

### Not Started
- [Features mentioned in comments, README, or TODOs but not yet implemented]

---

## 6. Key Files & Entry Points

| File / Path | Purpose |
|---|---|
| [path/to/file] | [what it does] |

---

## 7. Data Models

```[language]
// Paste or summarize the most important data classes / structs / schemas
```

---

## 8. APIs & Integrations

| Service / API | Usage | Auth Method | Notes |
|---|---|---|---|
| [service] | [how it's used] | [API key / OAuth / None] | |

---

## 9. Permissions

| Permission | Why Required | When Requested |
|---|---|---|
| [PERMISSION_NAME] | [reason] | [trigger] |

---

## 10. Open Questions & Decisions

| # | Question | Status | Notes |
|---|---|---|---|
| 1 | [Any unresolved architectural or product decision visible in code] | Open | |

---

## 11. Recent Changes

| Date | Change | File(s) Affected |
|---|---|---|
| [from git log or inferred] | [description] | [file] |

---

## 12. Environment & Setup

```bash
# Prerequisites
# [List runtime, SDK, tools required]

# Build
# [Primary build command]

# Run tests
# [Test command]
```

### Environment Variables / Config
| Key | Purpose | Where Set |
|---|---|---|
| [KEY_NAME] | [purpose] | [local.properties / .env / etc.] |

---

## 13. Glossary

| Term | Definition |
|---|---|
| [domain-specific term found in codebase] | [definition] |

---

## 14. LLM Hand-off Notes

> ⚠️ Read this section first when picking up this project in a new session.

- **Current focus:** [Most active area of development based on recent files/git log]
- **Last decision made:** [Any architectural pattern or library choice that appears finalized]
- **Next immediate task:** [What logically needs to happen next based on incomplete work]
- **Watch out for:** [Gotchas, non-obvious constraints, or fragile code spotted during analysis]
- **Do NOT change:** [Core patterns or locked decisions that are load-bearing]
```

---

### Step 3 — Output Rules

- Return ONLY the raw `CONTEXT.md` content — no preamble, no explanation, no trailing commentary
- Do not truncate any section. Every section must be present and filled
- Do not use filler phrases like "based on common patterns" — only state what is actually observed
- If something cannot be determined, write `<!-- Could not determine — please fill in -->`
- Use today's date for "Last Updated"
- The file must be immediately saveable as `CONTEXT.md` in the project root

