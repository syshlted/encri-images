---
paths:
  - "app/src/androidTest/**"
  - "testvault/**"
  - "scripts/device/**"
---

# Device testing — component brief

The tests that run on a phone against the encri installed there: the app's own instrumented tests in
`app/src/androidTest` (`GateOnPhoneTest`, `WalkMeasurement`, `GrantGone`, `EditMemoryOnPhone`), `:testvault`, a library module whose test
APK writes to the vault (`SeedLibrary`, `RemoveLibrary`, `GrantChanges`), and `scripts/device/` (`instrument`,
`suite`, `scroll`). This brief is level 1; full records are in `docs/decisions/`.

## Rules that hold here
- **Two test APKs, because two identities.** The app's own tests run in the app's process, reach encri through its
  one `VaultLink` with its `READ` grant, and ask for nothing more. What writes runs from `:testvault`, which
  instruments itself as `com.systemhalted.encri.images.testvault.test`: a uid and a grant of its own, with `READ`,
  `WRITE`, `CREATE` and `DELETE`. Seeding from the app's own tests would have asked encri to widen the app's grant.
  (I6)
- **One class on one phone is `scripts/device/instrument`**: it builds, installs with `-r` and runs `am instrument`,
  because the Gradle task uninstalls the test APK and that drops the grant encri keyed on the install (encri-api
  L13). It wakes the phone and dismisses a keyguard that is only a swipe. (I6)
- **Every phone named is `scripts/device/suite <serial>...`**, one phone after another: `GateOnPhoneTest`;
  `SeedLibrary`, 200 photos of 160x120 in ten folders; `WalkMeasurement` over it; `RemoveLibrary`; and `GrantChanges`.
  The APKs are built once, and each class goes through `instrument`. What each logged is kept under
  `build/device-runs/<time>/<serial>/` with a summary, so nothing of a run is committed. (I17)
- **`GrantGone` is run by hand**: it needs the app let into one folder and a removal run beside it. (I12, I17)
- **`EditMemoryOnPhone` is run by hand too**: it holds the heap's size and 64 MiB more of the phone's memory for a few
  seconds, and touches nothing of the vault. It is where an edited video's memory is seen to be outside the Java
  heap, which the unit tests, on the JVM, cannot show. (I22, amended)
- **encri's screens are pressed as encri-api's device tests press them**: through the accessibility tree, only in
  encri's own window, matched on the English words encri-api's tests name. Not `UiAutomator`. (I6, encri-api L13)
- **A passphrase is the argument `encriPassphrase`**, which `instrument` takes from `ENCRI_PASSPHRASE`, and `suite`
  from the file `PASSPHRASES` names, `<serial>=<passphrase>` a line, kept outside the repository. A phone with none
  runs without, and a test that then needs one skips and says so. (I6, I17)
- **A seed is removed by the id it wrote down, never by a name.** `SeedLibrary` makes one new top folder and writes
  its id to the test package's own files before putting anything in it; `RemoveLibrary` deletes those ids and nothing
  else. encri never gives an id to a second document, so a line left behind names nothing. (I6)
- **A probe asks encri and logs what it does, rather than asserting what it should.** No test names a document in
  what it logs; the logs hold counts, times and outcomes, and no passphrase. (I12, I17)
- **CI compiles the device tests' APKs and runs none**, since CI has no encri. There is no stand-in encri: encri-api's
  way in, `ServiceBinding.connect`, is internal to the client. (I6)
- **Tests run against an encri installed on the phone**: on ~~the test phones~~ the S9, the G6 ~~and the S24~~, its
  release build 0.1.0, signed by the certificate `EncriTrust.RELEASE_CERTIFICATES` pins; on the A20, under Waydroid and,
  since 2026-11-17, on the S24, a debug build signed with this machine's debug key, since 2026-11-11 one that has built
  encri D147. ~~The S24 has no vault until its owner makes one.~~ Its owner has made the S24 one since (2026-11-18). Nothing of encri's repository is used. (I5, amended, I9,
  `docs/dev-environment.md`)
- **Measure the G6 with an opaque screen in front, such as Settings, and nothing else running.** Its live wallpaper
  takes a fifth of a core when the home screen shows, and its `interactive` governor lets anything else raise the
  clock and speed a walk up. (I9, `docs/dev-environment.md`)
- **The S24 is a phone in daily use with a test vault on it.** Read only encri's and this app's text from its screen,
  and capture nothing. (`docs/dev-environment.md`)

## Traps already paid for
- The first run on the A20 failed on a screen that had timed out, with every activity stopped. (I6)
- **The suite does not let the app in itself.** On a vault that has not let it in, a fresh one included, `suite`
  skips `WalkMeasurement` with `no session to measure: Refused`, as on the A20 and under Waydroid on 2026-11-12. Let
  the app in on encri's consent screen first. (I17, amended)
- `scripts/device/scroll` drew no frames on the S9: it draws at an override of 1080x2220 on a 1440x2960 panel, input
  is given in the override, and every fling began below the screen. It takes the override now. (I9)
- A shell loop on the G6 reading the clock every 200 ms cut a walk of 2,080 photos to a third. (I9)
- Play Protect on the S9 asks to send the test vault's APK to Google at every install, and `instrument` sits silent
  until *Don't send* is pressed on the phone. (`docs/dev-environment.md`)
- `uiautomator dump` hangs while a video plays, and a `FLAG_SECURE` window cannot be captured. A throwaway build with
  `SecureWindows` emptied is how screens were looked at; never commit one. (`docs/dev-environment.md`)
- Rejected: seeding by copying files in through encri-files' screens, which would test encri-files. (I6)

## Measured, not assumed
- `GateOnPhoneTest` passes on the G6 (API 28), the S9 (API 29) and the A20 (API 30) (I6), under Waydroid at API 33
  (I3, amended), and on an API 27 emulator with no encri, showing *encri is not installed* (I18). Under Waydroid with
  encri and no vault yet it shows *The vault is locked.* (2026-11-11; `vault-link`).
- The suite on the G6: all five passed, in five minutes; the logs held counts, times and outcomes and no passphrase.
  (I17)
- The suite on the A20 and under Waydroid, 2026-11-12: all five passed, none skipped, once the app was let in. (I17,
  amended)
- `EditMemoryOnPhone` on the G6 and the A20, 2026-11-18: 576 MiB held past a heap of 512 MiB; it failed on the G6
  against the direct buffers it replaced. (I22, amended)
- `GrantChanges` and `GrantGone` on the G6, the A20 and the S24, alike; a descriptor read across each consent on the
  S9 and the G6. (I12)
- What `WalkMeasurement` and `scroll` measured is `vault-library`'s (I8, I9, I14).
- Not measured: a vault on API 27, which needs encri on the emulator (I18); a lock or an unlock through I7's path on a
  phone (I7).

## Decisions
| | Why it matters here |
|---|---|
| I6 | Two test APKs, `instrument`, the seed by id, CI compiles them; two veto flags |
| I17 | `suite`, the kept logs, `PASSPHRASES` (extends I6) |
| I12 | The probes `GrantChanges` and `GrantGone` |
| I5 | Against an installed encri, never encri's repository |
| I9 | The test phones' encri and vaults; the G6's clock; `scroll`'s override |
| I8 | `WalkMeasurement` and `scroll` first measured |
| I14 | `WalkMeasurement`'s `atOnce` |
| I18 | The API 27 emulator |

## Open here
- Flagged for veto: the passphrase is an instrumentation argument, so it is in the phone's process list while a run
  lasts; and the seed's package is let into the whole vault with the rights to write, by pressing *Allow* itself,
  on whichever phone it runs on. (I6)
- The suite has run on the G6, the A20 and Waydroid; not on the S9 or the S24. (I17, amended)

## Read next
- Related briefs: `.claude/rules/vault-link.md`, `.claude/rules/vault-library.md`
- The machine and the phones: `docs/dev-environment.md`; full records: `docs/decisions/INDEX.md`
