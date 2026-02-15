# Repository Guidelines

## Project Structure & Module Organization
Kvaesitso is a multi-module Android project organized by responsibility:
- `app/app`: Android application entry point and build variants (`default`, `fdroid`, `nightly`).
- `app/ui`: Most UI code (Jetpack Compose).
- `core/*`: Shared foundations (models, preferences, compatibility, permissions, profiles, i18n, utilities).
- `data/*`: Lower-level feature/data providers (applications, contacts, weather, files, widgets, etc.).
- `services/*`: Higher-level business logic (search, icons, badges, backup, plugins, widgets, accounts).
- `libs/*`: Standalone/forked libraries.
- `docs/`: VitePress documentation site.

Most modules expose DI bindings in a top-level `Module.kt`.

## Build, Test, and Development Commands
Use Gradle from the repository root:
- `./gradlew :app:app:assembleDefaultDebug`: Build the default debug APK.
- `./gradlew :app:app:assembleFdroidRelease`: Build the F-Droid flavored release APK.
- `./gradlew :app:app:assembleDefaultNightly`: Build the nightly variant (used in CI).
- `./gradlew :app:app:testDefaultDebugUnitTest`: Run app unit tests for default debug.
- `./gradlew :app:app:lintDefaultDebug`: Run Android lint for the default debug variant.
- `./gradlew dokkaGenerateHtml`: Generate API docs for SDK/shared modules.

Docs site:
- `cd docs && npm run docs:dev`
- `cd docs && npm run docs:build`

## Coding Style & Naming Conventions
- Kotlin-first codebase; keep Java usage consistent with existing modules.
- Follow existing Kotlin style: 4-space indentation, concise functions, explicit null handling.
- Package prefix is `de.mm20.launcher2...`; match folder/package structure.
- Naming: `PascalCase` for classes/composables, `camelCase` for methods/properties, `snake_case` for Android resources.
- Keep module boundaries clean: prefer `core` interfaces and inject dependencies via Koin modules.

## Testing Guidelines
- Unit tests use JUnit4 (`libs.junit`); place them in `src/test/...` and suffix files with `Test` (example: `OpeningScheduleTest.kt`).
- Use `src/androidTest/...` for instrumentation/UI behavior requiring Android runtime.
- No enforced coverage threshold is configured; add focused tests for new logic and bug fixes.

## Commit & Pull Request Guidelines
- Commit messages are short, imperative, and specific (optionally prefixed, e.g. `feat:`, `chore:`).
- For larger features, open or discuss an issue before implementation; small fixes can go straight to PR.
- PRs should state what changed and why.
- PRs should list validation steps and commands that were run.
- PRs should include screenshots or video for visible UI changes.
- By contributing code, you agree to GPL-3.0-or-later licensing used by this repository.

## Security & Configuration Tips
- External API keys are intentionally not committed; builds still work with reduced feature set.
- Never commit signing credentials or tokens. CI signing uses environment variables such as `KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, and `SIGNING_KEY_PASSWORD`.
