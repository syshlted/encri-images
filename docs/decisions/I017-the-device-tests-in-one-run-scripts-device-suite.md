# I17 — The device tests in one run: `scripts/device/suite`
*2026-11-04 · accepted (`ROADMAP.md`'s *Device tests in a run of their own*). **Extends I6.***

**One command runs them on every phone named**, one phone after another: `GateOnPhoneTest`; `SeedLibrary`, 200 photos of
160x120 in ten folders, so a run takes minutes and still measures what a walk costs a folder (I14); `WalkMeasurement`
over it; `RemoveLibrary`, which takes it away again; and `GrantChanges`. Each class goes through `scripts/device/instrument`,
so I6's rules hold, and the APKs are built once.

**Kept:** what each class logged, in `build/device-runs/<time>/<serial>/`, with a summary of every phone's outcomes
beside it. Under `build/`, so nothing of a run is committed: a release keeps its run with the release (encri-files F9's
kit), not in this repository.

**Passphrases come from a file `PASSPHRASES` names**, `<serial>=<passphrase>` a line, kept outside the repository; a
phone with none runs without, and a test that then needs one skips and says so.

**`GrantGone` is left out**, because it needs the app let into one folder and a removal run beside it (I12), by hand.

**Verified on the G6 (API 28):** all five passed, in five minutes; the logs held counts, times and outcomes and no
passphrase.
