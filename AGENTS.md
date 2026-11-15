# encri-images — agent guide

> **Level 0 of four**, loaded into every session: only what is true for every component, laid out as the suite's
> `docs/documentation.md` says. The rest loads when you touch a component or is read when needed. Keep it under 200
> lines. Dates: YYYY-MM-DD.

## What this is

**A gallery over an encri vault**: an Android app that shows the photos and videos in the vault, reached only
through encri's companion service over AIDL. It is a fork of [LineageOS Glimpse](https://github.com/LineageOS/android_packages_apps_Glimpse),
licensed Apache-2.0 as Glimpse is, and one of the two apps encri keeps open source (encri D142).

Glimpse is a system gallery over MediaStore. This app keeps its screens and replaces what is underneath them,
because a gallery is the app most likely to put a vault's pictures somewhere they should not go.

What crosses the boundary between this app and encri, and why, is decided in encri's records (D112 to D118, D120)
and in encri-api's for the wire. It is not restated here. What this repository owns is the app.

## Relationship to the other repositories

- **Decisions here are numbered I1, I2, …** The others' are cited as *encri D120*, *encri-api L12*, *encri-files F2*
  and *encri-backup K1*; encri's bugs are *encri B143*. S1 to S4 are severities, never decisions.
- **About the app, this repository's decision records win (I21).** About the contract, encri-api's do; about
  encri's internals, encri's do.
- **Upstream is Glimpse**, as the `upstream` remote. `config/upstream-fork-point` is the commit forked from (I1).
- **Nothing here comes from encri's own repository**, which is closed (I5): no source, record, document, build
  output, APK or test fixture. What this app relies on is said by encri-api or seen of an installed encri, as any app
  could. A citation such as *encri D112* names a reason given elsewhere, and is not something to go and read.
- **encri-api is built from source**, as an included build, at the commit `config/encri-api.pin` names (I1,
  encri-files F9). A developer's build uses whatever `../encri-api` holds, and the pre-push hook refuses a push while
  that is anywhere else.
- **The repositories sit side by side in the suite superproject**, whose `docs/documentation.md` sets this layout:
  `../encri-core` is encri, never copied from (I5); `../encri-api` the contract and its client; `../encri-files`
  Encri Files; `../encri-backup` Encri Backup.

## How the project's knowledge is laid out

| Level | Where | Loads | Holds |
|---|---|---|---|
| 0 | `AGENTS.md` | every session | constraints, invariants, hazards, conventions, this map |
| 1 | `.claude/rules/<component>.md` | when a file matching its `paths:` is read or edited (other tools: read it by hand) | one component's rules, traps, measurements and decisions |
| 2 | `docs/decisions/INDEX.md` | on demand | one line per decision: date, title, status, the briefs that cite it |
| 3 | `docs/decisions/Innn-<slug>.md` | on demand | the full decision record |

**Finding a decision.** `AGENTS.md I4` or `I4`, in code or older documents, means `docs/decisions/I004-*.md`: every
record lived in this file before 2026-11-11 (I21). Later decisions amend earlier ones in place, and a record's footer
lists the corrections `docs/decisions/amendments.md` holds.

**Elsewhere:** `ROADMAP.md` (what is next, and the bugs) and `CHANGELOG.md` (what happened). In `docs/`: `status.md`
(the status history), `dev-environment.md` (this machine, the phones, the emulator and Waydroid) and `conventions.md`
(in full).

## Components (level 1)

| Brief | Covers |
|---|---|
| `vault-link` | the link to encri: `VaultLink`, `VaultSession`, `ClientOpener`, and the gate in front of the gallery |
| `vault-library` | the vault as Glimpse's data source: the walk, the library in memory, the URIs, Glide and previews |
| `viewer` | a photo, zoom in tiles, a video over a descriptor, the info sheet |
| `editor` | crop and rotate, into a copy beside the original: the frame, a photo's bands, a video in memory |
| `share-and-delete` | handing to another app, deleting, and choosing several in a grid |
| `privacy-and-settings` | the manifest, secure windows, the settings screen and its two switches |
| `glimpse-screens` | the code and resources inherited from Glimpse, and how they may change |
| `device-testing` | the instrumented tests, `:testvault`, `scripts/device/` |
| `build-and-ci` | Gradle, the gates, the hooks, CI, pinned dependencies, the encri-api pin, the licence files |
| `release-and-signing` | reproducible releases, the signing kit, the upload key, the third-party notices |

Briefs overlap: `ViewActivity.kt` loads three. A brief stays under 12 KB; one that outgrows that is split.

## Hard constraints

These are external facts. They are not negotiable.

1. **encri is reached only through its companion service, with encri-api's client** (encri D112, D120). The contract
   has no query across folders, so the vault is walked a folder at a time (I4, I8).
2. **encri's events can be missed.** The client drops its oldest past 64 rather than hold encri's binder thread
   (encri-api L12), so a lock, an unlock or the end can go unsaid (I7).
3. **What the user allows on encri's consent screen replaces the grant with exactly the rights asked for, and ends
   every session the app holds.** A descriptor the old session opened fails after what it had buffered. A refusal or a
   back-out changes nothing (I12, encri-api L23).
4. **A grant whose folder is deleted is gone, and encri 0.1.0 leaves its session open and tells it nothing.** Its root
   answers `NOT_FOUND` and `refreshGrant` answers `NOT_AUTHORIZED`. encri-api L23 promises the end, which encri owes
   (I9, I12, encri B143).
5. **An open descriptor goes on serving across a lock** (encri D103), so what is open does not stop by itself (I3).
6. **A handoff grants reading alone**, and stops working at a lock or at the session's end; the app chosen keeps
   what it read, and an editor saves its copy elsewhere (I10, encri D117).
7. **`FLAG_SECURE` belongs to one window and keeps out its picture alone.** A dialog is a window of its own, and from
   API 29 any app recording the screen may capture another's audio unless it says not (I3).
8. **From API 31 `allowBackup` false stops a cloud backup and not a transfer to a new phone** (I3).
9. **Lint checks an attribute against the API level, not an enum's value.** Glimpse's cutout mode `always` stopped
   the app starting below API 30, and lint did not see it; only a run proves a level (I6, I18).
10. **encri keys a grant on the install** (encri D120): the id `com.systemhalted.encri.images` is permanent from the
    first build a user installs (I2), and uninstalling a test APK drops its grant (I6).
11. **Apache-2.0 is not a choice**: the inherited code is under it (I2).

## Invariants

Violating any of these is a design bug, not a tradeoff.

- **No permission at all.** The manifest removes `INTERNET`, `ACCESS_NETWORK_STATE` and `WAKE_LOCK`, which libraries
  merge in, so plaintext here cannot leave by a socket. `ShippedManifestTest` reads the merged manifest (I3).
- **One way in, the launcher.** Nothing else is exported, and the viewer takes its request from this app's own
  extras (I3).
- **No picture is written to disk.** Glide's disk cache is a no-op and its loads answer `DataSource.LOCAL`; ExoPlayer
  has no cache; an edited video is muxed in memory (I22); what the app does keep, its preferences and saved playback
  positions, is excluded from every backup and transfer (I3, encri D112).
- **Nothing shown while locked.** The library forgets everything at a lock or the session's end, Glide's memory and
  zoom's tiles with it; the gate hides the gallery; and the viewer closes itself (I3, I15).
- **Every window and every dialog is kept out of screenshots unless the user allows them**, through `SecureWindows`,
  and a video's sound is never another app's to record (I3, I13).
- **Nothing leaves but by encri's handoff, after a dialog that says what it costs, every time**, with encri left out
  of the chooser (I10, I16). Nothing about a picture goes to a service, a location or the platform's metrics (I3, I22).
- **A delete is said first, every time, and cannot be undone here** (I11).
- **A grid cell is encri's preview, never a decode here.** A full photo, its tiles and a video's stream are decoded
  in this process, which holds no keys (I4, I15).
- **A right is asked for when it is first used, with every right the app means to keep**, because a consent replaces
  the grant (I3, I11, I12).
- **No document's id, name or `encri-vault://` URI reaches a log, and no passphrase does** (I8, I15, I17, encri D8,
  D85).
- **Every request to encri goes through the link, and an answer counts only if the link's state has not changed
  since it was asked** (I7).

## Hazards

- **The S24 is a phone in daily use with a test vault on it.** Read only encri's and this app's text from its
  screen, and capture nothing: other apps' windows hold what is not a test's (`docs/dev-environment.md`).
- **Never commit a build with `SecureWindows` emptied**, the throwaway local build the screens were looked at with
  (`docs/dev-environment.md`).
- **The seed's package is let into the whole vault with the rights to write**, on whichever phone it runs, and the
  passphrase is in the process list while a run lasts. Test vaults only; a seed is removed by the ids it wrote down,
  never by a name (I6).
- **Run the device tests with `scripts/device/instrument` or `suite`, not Gradle's connected task**, which
  uninstalls the test APK and drops the grant encri keyed on the install (I6, I17).
- **Passphrases are kept outside the repository**, in the file `PASSPHRASES` names, and never logged (I9, I17).
- **Never run two Gradle invocations at once**, here or in a sibling: the included encri-api build is one more set
  of outputs to fight over. Gradle runs on JDK 21 (`docs/dev-environment.md`).
- **A run that sits silent may be waiting on the phone**: Play Protect on the S9 holds every install of the test
  vault's APK until *Don't send* is pressed, and Norton on the S24 has opened a *malware found* screen over one
  (`docs/dev-environment.md`).
- **The API 27 emulator dies** unless started with `-gpu swangle_indirect`, and when its output is piped through
  `head` (`docs/dev-environment.md`).
- **A signed release is never committed**: `signed/` is ignored (I21). The keys stay on the key machine, and each
  password is typed there at a terminal (I20).
- **The project's earlier name is never written**: not in code, docs, commits or any repository (encri D124).

## What this project keeps finding

- **Glimpse's outlets were safe for public pictures and are leaks here**, found one at a time: the info sheet's
  dialog, a video's sound, a transfer to a new phone, document ids in Glide's log (I3, I8). Ask of every inherited
  feature where it can send a picture.
- **Lint and unit tests miss what a phone shows**: the app not starting below API 30 (I6), the viewer opening at the
  newest item (I4), previews still shown from Glide's memory (I13). Check a change on a phone, and below API 30 when
  it touches a theme or an API level (I6, I18).
- **A test that has never failed has not been tested.** Revert the fix and watch the test fail, as I10's secure
  dialog, I11's walk filter and I13's re-apply and memory skip were checked.
- **What encri does is seen from outside, not assumed.** A probe logs what encri does rather than assert what it
  should, and a gap goes to encri-api or the user (I5, I12).
- **Measure before recording a conclusion**, and say what was not measured; an estimate is called one (I8, I9).

## Code rules in every module

- **What came from Glimpse stays in `src/main/java`**, in upstream's own formatting, edited as little as a change
  allows, so that upstream's fixes still apply. ktlint, detekt and the coverage floor do not reach it (I1); Android
  lint does, for errors (`ci.yml`, since 2026-11-04). A changed inherited file carries a copyright line of its own
  beneath The LineageOS Project's (I2).
- **What is written here goes in `src/main/kotlin`**, in the `vault`, `notices` or `edit` package, and passes ktlint,
  detekt and Kover's floor of 80% of lines (I1, I22).
- **An upstream fix comes across as a signed cherry-pick**, `git cherry-pick -x -S`, never as a merge (I1).
- **Read off the main thread**: a read of a descriptor encri serves can take a while (I4).
- **Unit tests stand in at this app's own seam, `VaultSession`**, not with a stand-in encri (I6).

## Repository conventions

Full text, with the reasons: `docs/conventions.md`.

- **Default branch is `master`**, by SystemHalted organization policy. Never rename it to `main`.
- **Commits are signed** (repo-local `commit.gpgsign`) **and Conventional**, with encri-files' hooks: signing
  preflight, ktlint and detekt before a commit; the message checked; signatures, the encri-api pin, the unit tests and
  the notices gate before a push. CI checks again every rule a hook enforces. Bootstrap a clone with `lefthook
  install`, then `scripts/ci/encri-api fetch ../encri-api`.
- **Every commit builds and passes its own tests, not just the branch tip.**
- **Merge with a merge commit or a rebase, never a squash** (encri D7).
- **Dependencies are pinned twice**, by sha256 and by lockfile (I1). Regenerate the verification metadata from an empty
  file after adding or bumping anything, and the lockfile when what a user receives changes.
- **`.claude/rules/` is committed; the rest of `.claude/`, `.vscode/` and `signed/` are not** (I21).

## Recording what is decided

1. **A settled design question becomes a decision record**, not only an answer in chat. It goes in
   `docs/decisions/Innn-<slug>.md` under the next free number, beginning `# Innn — <title>` then
   `*YYYY-MM-DD · accepted…*`. It says what was rejected and what was measured, and is *flagged for veto* where the
   user has not confirmed it.
2. **Regenerate the index and footers** with `python3 scripts/docs/decision-index.py .`, never by hand. Then check
   that the briefs still cover the tree with `python3 scripts/docs/brief-coverage.py .`.
3. **Update the brief** of every component the decision governs: add the rule and a row to its *Decisions* table.
4. **Amend earlier records in place**, with strike-through and "amended by Innn", and add a line to
   `docs/decisions/amendments.md`. What is next goes in `ROADMAP.md`; what landed goes in `CHANGELOG.md`.

**When they disagree, the decision records win**, and the briefs, `ROADMAP.md` or `CHANGELOG.md` are what get fixed.
