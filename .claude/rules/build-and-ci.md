---
paths:
  - "*.gradle.kts"
  - "*/build.gradle.kts"
  - "*/gradle.lockfile"
  - "gradle/**"
  - "gradle.properties"
  - "app/lint.xml"
  - "**/proguard-rules.pro"
  - "app/.gitignore"
  - "config/detekt/**"
  - "config/encri-api.pin"
  - "config/upstream-fork-point"
  - ".github/**"
  - "scripts/ci/**"
  - "scripts/hooks/**"
  - "lefthook.yml"
  - "LICENSE"
  - "LICENSES/**"
  - "NOTICE"
  - "REUSE.toml"
---

# Build and CI — component brief

The Gradle build (`settings.gradle.kts`, the root, `:app` and `:testvault` build files, `gradle/libs.versions.toml`,
`gradle/verification-metadata.xml`, the lockfiles), the gates it runs, the lefthook hooks in `scripts/hooks/`, the
checks in `scripts/ci/`, the workflows in `.github/workflows/`, the encri-api pin, the fork point, and the licence
files. Releases are `release-and-signing`'s. This brief is level 1; full records are in `docs/decisions/`.

## Rules that hold here
- **The toolchain is encri-files', version for version**: Gradle 9.7.1, AGP 9.4.1, Kotlin 2.4.21 on the root
  classpath, JDK 21, ktlint 1.8.0, detekt 1.23.8, Kover 0.9.9, JUnit 5.14.4 and Robolectric 4.17, with the same hooks,
  pinned action SHAs and encri-api pin. A bump is made in all of them together. (I1)
- **The gates follow the two source trees.** ktlint excludes `src/main/java`; detekt reads `src/main/kotlin`,
  `src/test/kotlin` and `src/androidTest/kotlin`; Kover measures the `vault` and `notices` packages, with a floor of
  80% of lines, and CI holds a pull request's changed lines to 85%. A floor over the whole module would measure
  Glimpse having no tests. Android lint runs over the whole app, errors only. (I1; `ci.yml`)
- **Dependencies are pinned twice**, by sha256 in `gradle/verification-metadata.xml` and by the modules' lockfiles in
  STRICT mode. Regenerate the metadata from an empty file over every task a workflow runs, after adding or bumping
  anything; `./gradlew :app:dependencies --write-locks` when what a user receives changes. (I1, encri D7,
  `docs/conventions.md`)
- **encri-api is an included build**, at `../encri-api` or `-Pencri.api.dir=<path>`. CI, `reproducible.yml` and
  `reproduce.sh` put it at the commit `config/encri-api.pin` names with `scripts/ci/encri-api`, and the pre-push hook
  refuses a push while `../encri-api` is anywhere else. Bump the pin in the commit that needs it, and push encri-api
  first: CI fetches the commit from GitHub. (I1, encri-files F9)
- **The Gradle wrapper is held to the checksums gradle.org publishes** (`scripts/ci/gradle-wrapper-check`), before
  anything runs `./gradlew` in CI. (I20, encri-files F9)
- **`allUnitTests`, never `testDebugUnitTest`**: a name-matched task silently skips every module that lacks it. The
  notices gate rides in the same pre-push invocation, because two Gradle runs at once fight over the included
  encri-api build's outputs. (encri D11, I19)
- **Every rule a hook enforces is enforced again in `.github/workflows/`**, because `--no-verify` always exists.
  Every action is pinned to a full commit SHA. `commit-policy.yml` checks signatures only after
  `config/upstream-fork-point`, so Glimpse's own history is never asked for a signature it could not have. (I1, encri
  D7)
- **CI compiles the device tests' APKs and runs none.** (I6)
- **The application id is `com.systemhalted.encri.images`**, permanent from the first build a user installs, and
  versions follow encri D6's scheme: 0.1.0 is 1000. encri keys a grant on both. (I2, encri D120)
- **minSdk 27**, encri's floor (encri D14); compileSdk and targetSdk 36. (I1)
- **Apache-2.0, which is not a choice**: `LICENSE`, `LICENSES/`, `REUSE.toml`, and a `NOTICE` that says where this
  came from and claims no endorsement by LineageOS. (I2)
- **The build reads nothing of encri's repository**: encri-api and encri-files are the open sources. (I5)

## Traps already paid for
- Glimpse's alpha dependencies (`core`, `activity`, `lifecycle`, `material`, `exifinterface`) were moved to the
  stable release of each line, which took `navigationevent` and `savedstate` off their alphas and added one jar of
  annotations. Nothing in the lockfile is a pre-release. (I1, amended 2026-11-04)
- Lint checks an attribute against the API level, not an enum's value, which is how the cutout mode passed I1's
  check and stopped the app below API 30. (I6)
- Two Gradle invocations at once, here or in a sibling, fight over the included encri-api build's outputs.
  (`docs/dev-environment.md`)
- A squash merge re-signs with GitHub's web-flow key, so a squashed commit reports verified-by-GitHub rather than
  verified-by-author. (encri D7, `docs/conventions.md`)

## Measured, not assumed
- At the fork, 510 components were pinned by hash, and the `vault` package had 61 tests covering 84.6% of its lines;
  five guarantees were checked by reverting each and watching its test fail (`CHANGELOG.md`, 2026-11-01).
- encri's ktlint style finds 965 violations in the inherited code, an android_studio style 434. (I1)

## Decisions
| | Why it matters here |
|---|---|
| I1 | The toolchain, the gates, the pins, the fork point, cherry-picks, minSdk; three veto flags |
| I2 | The id, versions, the licence and `NOTICE`; two veto flags |
| I5 | Nothing from encri's closed repository |
| I6 | CI compiles the device tests' APKs |
| I19 | The notices gate in CI and before a push |
| I20 | CI is Encri Files'; the wrapper's checksums |
| I21 | What `.gitignore` keeps out, and `.claude/rules/` committed; two veto flags |

## Open here
- Nothing in GitHub protects `master` yet: changing a repository's rules is the user's to say (I1, `ROADMAP.md`).
- The inherited screens have no coverage and no ktlint or detekt; flagged (I1). Android lint's CI step came after the
  flag, and the flag does not mark it.
- The workflows have not run on GitHub: the fork there holds only Glimpse's history (`docs/status.md`, I20).
- A stale pointer left as it is: `commit-policy.yml` points to "AGENTS.md, *Repo conventions*", now
  `docs/conventions.md`. `scripts/ci/diff-coverage.py` cites encri-files' F1, which I1 takes on.
- `.claude/rules/` is committed in a public repository; flagged (I21).

## Read next
- Related briefs: `.claude/rules/release-and-signing.md`, `.claude/rules/glimpse-screens.md`
- In full: `docs/conventions.md`; full records: `docs/decisions/INDEX.md`
