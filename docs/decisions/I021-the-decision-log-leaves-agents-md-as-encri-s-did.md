# I21 — The decision log leaves AGENTS.md, as encri's did (encri D127)

*2026-11-11 · accepted on the user's direction ("all of the AGENTS.md files … need to be evaluated to place the
task maps, to-do lists, bugs, artifacts, etc. in their appropriate places"; "make sure the documentation … for all of
the apps is written and formatted similarly"), and **flagged for veto on five points** (below).*

**Amends the repository conventions** (`docs/conventions.md`), which named `AGENTS.md` as the decision log and said it
wins. The layout is the suite's `docs/documentation.md`, which is encri D127's.

#### What moved where

`AGENTS.md` was 940 lines and 72,808 bytes: *What this is*, *Status*, *Relationship to the others*, *Repo conventions*,
*Local development environment*, and the records I1 to I20. Claude Code loads a root `AGENTS.md` into every session,
whole.

- **Each of I1 to I20 is `docs/decisions/Innn-<slug>.md`**, its heading `#` where it was `###` and its text otherwise
  word for word. The generator adds a footer between markers, copying the corrections `amendments.md` holds.
- **The status is `docs/status.md`, the development environment `docs/dev-environment.md`, and *Relationship to the
  others* with *Repo conventions* is `docs/conventions.md`**, each unchanged under a title and a note of where it came
  from. The development environment stays this repository's own, and does not point to encri's (I5).
- ***What this is* stays in `AGENTS.md` word for word.** The rest of `AGENTS.md` is new, in the suite's order: the
  other repositories, the map, the components, and the constraints, invariants and hazards distilled from the records,
  each citing its record. It is 198 lines and 14,504 bytes, and keeps no running status.

**The move is lossless, and that was checked rather than assumed.** A script gave each of the old file's 940 lines
exactly one destination and wrote them; a second rebuilt the old file from what was written and compared it byte for
byte, and it matched. Lines not carried: the old title block (5), the bare `## Decisions` heading and its blank line
(2), the three `---` rules (3), and 27 blank lines that separated sections and records, which the rebuild puts back.
Before comparing, the rebuild undoes a record's heading level, its generated footer, each moved file's title and note,
and the two strike-throughs below, and nothing else.

**The conventions are amended in place.** *"This one is the decision log"*, and *"this file wins"*, which both
sections said, are struck through: the decision records win, over the briefs, `ROADMAP.md` and `CHANGELOG.md`. The
corrections the records already state in their own text are entered in `amendments.md`, so each record's footer
lists them; I3's holds five.

#### The components

Nine briefs in `.claude/rules/`, by feature, as encri D127 chose. The `vault` package divides by what each file
serves: `vault-link` (the session and the gate), `vault-library` (the walk, the URIs, Glide), `viewer`,
`share-and-delete`, and `privacy-and-settings` (I3's outlets and I13's switches, one subject: what leaves the process
and its screens). The inherited code has `glimpse-screens`, because how it may change at all (I1, I2) holds for every
file in `src/main/java` and `src/main/res`. `device-testing`, `build-and-ci` and `release-and-signing` are encri's
divisions. Globs overlap, so `ViewActivity.kt` loads three. The briefs are 5.4 to 9.0 KB, 63 KB together.

#### Scripts and `.gitignore`

`scripts/docs/decision-index.py` and `brief-coverage.py` are encri-files' (its F12), an open source I5 allows, reading
I numbers. One change beyond the letter: the generator reads `I[0-9]*.md`, since `I*.md` would take `INDEX.md` for a
record. Neither regex for an I number matches a word without digits after it, nor "API", whose I follows a letter.
*A first draft took them from encri-backup, whose copies are encri's, which I5 rules out; the user asked for that to be
put right before anything was committed (2026-11-11).*

`.gitignore` narrows `.claude/` to `.claude/*` with `!.claude/rules/`, adds `signed/`, where a release's signed files
are kept, with encri-backup's reason, and adds `.vscode/`, whose per-machine setting was untracked in the tree.

`README.md`, `ROADMAP.md` and `CHANGELOG.md` take the suite's titles, preambles and sections, and point to
`docs/decisions/` where they pointed to `AGENTS.md`; no entry's substance changed. `ROADMAP.md`'s S2 bug cites encri
B143, and the *Send its name* tick (encri D148, encri-api L24, L25) is queued there.

#### Verified

The lossless rebuild above. `brief-coverage.py` finds every one of the 298 files tracked or about to be covered by a
brief, documentation and generated files excepted, and no glob that matches nothing. Every brief is under 12 KB. The
index generator, run twice, changes nothing the second time. No gate reads Markdown or Python; the two scripts carry the
SPDX lines that `scripts/device/` and `scripts/release/` carry, though no check asks for them.

#### Flagged for veto

1. **`.claude/rules/` is committed, in a public repository.** The briefs are documentation that loads by path, and a
   brief on one machine alone would be lost with it. They cite encri's records by number, as the records do, and hold
   nothing of encri's repository.
2. **Code and older documents keep citing `AGENTS.md Inn`**: thirteen Kotlin files, the device scripts, two workflows,
   the version catalog and `settings.gradle.kts`. `AGENTS.md` says how to resolve one (*Finding a decision*), and
   rewriting them would touch code and build files to change a pointer.
3. **`CHANGELOG.md`'s entries each have a heading of their own**, taken from their bold leads, under *Unreleased*,
   as the suite's `docs/documentation.md` records for the smaller changelogs. Done at the user's direction; the
   paragraphs are word for word.
4. **The components are features, not modules**: nine briefs, with globs that overlap.
5. **The old *Relationship to the others* went to `docs/conventions.md`**, not to `AGENTS.md`, whose section of that
   name is written new, so that level 0 says only what is true now.

*Rejected:* the old relationship bullets in `AGENTS.md` with strike-throughs, at level 0; a nested `AGENTS.md` per
module, for encri D127's reasons; shortening any record.
