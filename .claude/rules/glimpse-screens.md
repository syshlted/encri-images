---
paths:
  - "app/src/main/java/**"
  - "app/src/main/res/**"
---

# Glimpse's screens — component brief

Everything inherited from LineageOS Glimpse: the activities, fragments, view models, adapters, extensions, layouts,
navigation, drawables, themes and strings in `app/src/main/java` and `app/src/main/res`. The features built on them
have briefs of their own (`vault-link`, `vault-library`, `viewer`, `share-and-delete`, `privacy-and-settings`); this
one says how inherited code may change at all. This brief is level 1; full records are in `docs/decisions/`.

## Rules that hold here
- **What came from Glimpse stays in `src/main/java`, in upstream's own formatting, edited as little as a change
  allows**, so that a fix from Glimpse can be picked across. Reformatting would turn every upstream fix into a
  conflict, for no behaviour. (I1)
- **What a change adds goes in `src/main/kotlin`**, in the `vault` package, which passes ktlint, detekt and the
  coverage floor; the inherited screen calls it. ktlint excludes `src/main/java`, detekt does not read it, and Kover
  does not measure it. (I1)
- **An upstream fix comes across as a signed cherry-pick**, `git cherry-pick -x -S`, never as a merge: a merge would
  bring unsigned commits into a range the signature checks walk. (I1)
- **A changed inherited file carries a copyright line of its own beneath The LineageOS Project's**, section 4(b) of
  the Apache License; in the source, `SPDX-FileCopyrightText: SystemHalted` under LineageOS's. (I2)
- **The launcher name is "Encri Images" in every language.** Glimpse's translations of its own name were removed so
  that none overrides it. (I2)
- **Glimpse's strings are left as upstream wrote them**, so that its fixes still apply (I1). This app's own strings
  follow the suite's *Text in the apps* (`docs/documentation.md` in the superproject).
- **What Glimpse had that is gone stays gone until it has a design of its own**: the picker, *Set as wallpaper*,
  showing media over the keyguard, the `Geocoder` lookup, *use as*, favourites, trash and MediaStore itself. Share,
  delete, and crop and rotate came back by designs of their own. (I3, I10, I11, I22)
- **minSdk is 27, down from Glimpse's 30**, encri's floor. (I1)
- **The cutout mode**: `values/themes.xml` asks for `shortEdges`, and `values-v30/` keeps Glimpse's `always`, which
  API 28 and 29 refuse when an activity starts. A change to a theme, or to anything an API level decides, is checked
  on a phone below API 30, since lint checks an attribute against the API level and not an enum's value. (I6, I18)
- **A chosen cell is drawn as Glimpse drew it**: blurred from API 31, behind a scrim before that, and checked. The
  selection under it is this app's own. (I16)
- **AndroidX stays**, where encri-files has none: its screens are the reason to fork Glimpse. (I1, encri-files F1)

## Traps already paid for
- The app did not start below API 30: Glimpse's theme asked for the cutout mode `always`, which API 30 added. Lint
  found no `NewApi` in what remained, and could not see this. (I1, I6)
- The viewer opened at the newest item rather than the one tapped, a race MediaStore's slower load had hidden. (I4)
- Glimpse's info sheet gave a photo's coordinates to `Geocoder`, on most phones a network service, without asking.
  (I3)
- Glimpse's selection reached into `recyclerview-selection`'s private fields by reflection. (I16)
- The 54 `ImpliedQuantity` lint errors in the inherited code were all in strings for what this app no longer has, and
  went with 11 drawables and the rest of what nothing used; the inherited compile warnings were cleared, and a new one
  now fails the build (`CHANGELOG.md`, 2026-11-04).
- Glimpse's alpha dependencies were moved to the stable release of each line before anything shipped. (I1, amended
  2026-11-04)

## Measured, not assumed
- encri's ktlint style finds 965 violations in the inherited code, and an android_studio style 434 (2026-11-01). (I1)
- The fork keeps Glimpse's 384 commits, from `config/upstream-fork-point`. (I1)
- With the stable dependencies, the gallery, a photo and the settings opened on the S9 and the A20. (I1, amended)
- Reels drops most frames on the G6, 59–88% janky at its 1440x2880 and 48% at 1080x2160: the phone's CPU and GPU at
  its own resolution, rather than something the app does on that phone alone. (I9)

## Decisions
| | Why it matters here |
|---|---|
| I1 | Two source trees and the gates that follow them; cherry-picks; minSdk 27; AndroidX; three veto flags |
| I2 | The id, the launcher name, the licence and the copyright line; two veto flags |
| I3 | What Glimpse had that is gone, and why |
| I6 | The cutout theme below API 30 (amends I1) |
| I4 | Glimpse's data-source seam; the viewer's start |
| I16 | The selection drawn as Glimpse drew it, and held by this app |
| I18 | API 27 starts |
| I22 | The viewer's *Edit*, a screen of this app's own beside Glimpse's |

## Open here
- The inherited screens have no coverage and no ktlint or detekt; flagged (I1). Android lint now runs over them in
  CI, for errors (`ci.yml`, since 2026-11-04), which I1's flag predates and does not mark.
- The launcher icon is still Glimpse's; a release should have its own, so that nobody takes this for LineageOS's
  gallery (I2, `ROADMAP.md`). The copyright holder is written "SystemHalted" (I2). Both flagged.

## Read next
- Related briefs: `.claude/rules/viewer.md`, `.claude/rules/vault-library.md`, `.claude/rules/build-and-ci.md`
- Full records: `docs/decisions/INDEX.md`
