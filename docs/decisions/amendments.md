# encri-images — amendments to earlier decisions

Each entry corrects a record. Its bold lead names the record possessively ("I3's …") so that
`scripts/docs/decision-index.py` copies it into that record's footer. The entries dated before 2026-11-11 were
written that day (I21), one for each amendment a record already states in its own text, which stays where it is.

## Amendments

- **I11's consent, for `READ` and `DELETE` alone, is amended by I22 (2026-11-15).** A consent asks for every right
  the grant already holds as well as the one wanted (`Rights.toAsk`), because it replaces the grant with exactly what
  it asks: `DELETE` asked alone would take away I22's `CREATE` and `WRITE`.
- **I10's *Edit*, which was *Open with* and nothing of this app's own, is amended by I22 (2026-11-15).** Crop and
  rotate are this app's own, and keep a copy beside the original in the vault. Any other edit is *Open with*, and its
  warning, as it was.
- **I3's edit, gone with Glimpse's outlets, is back by I22 (2026-11-15)**, as a design of this app's own: a photo or
  video cropped and turned here, decoded and muxed in memory, and kept as a new document beside the original.
- **I14's "the other phones", not measured, are measured on the A20, amended 2026-11-12.** Over 2,000 photos in 50
  folders, as on the G6, four at once walked in 414 ms, then 259–294, against 978, then 624–685, one at a time, and
  eight no better than four: the G6's shape. The S9, the S24 and a library of 20,000 stay unmeasured.
- **I17's suite, verified on the G6, ran on the A20 and under Waydroid, amended 2026-11-12.** All five passed, none
  skipped, once this app had been let in on encri's consent screen. The suite does not let it in itself: on a vault
  that had not, `WalkMeasurement` skipped with `no session to measure: Refused`.
- **The repository conventions' "this one is the decision log" and "this file wins" are moved by I21
  (2026-11-11).** The decision log is `docs/decisions/`, one record to a file, and it wins over `ROADMAP.md`,
  `CHANGELOG.md` and the component briefs in `.claude/rules/`. `AGENTS.md` is the always-loaded map. Struck through
  in place in `docs/conventions.md`.
- **I2's signing key, left open, is decided by I20 (2026-11-05).** It is Encri Files' arrangement: the app is signed
  with encri's app signing key, its bundle carries code transparency signed with encri's transparency key, and the
  upload key is this app's own, `encri-images-upload`, which is owed.
- **I4 and I6's account of what has run where is amended by I18 (2026-11-04).** The minified release build was walked
  through after I13 to I16, on the G6 and the A20, and the app starts on API 27, its floor, under an emulator with no
  encri.
- **I10 and I11's one photo or video at a time is amended by I16 (2026-11-04).** Several chosen in a grid are shared
  through one handoff and deleted after one question. *Open with* stays one at a time.
- **I8's zoom, which never subsampled a vault photo, is answered by I15 (2026-11-04).** `VaultSubsampling` gives
  ZoomImage a source that reads the photo through the link, so a zoomed photo is decoded in tiles from the photo
  itself, and ZoomImage no longer asks Glide for the photo as a file.
- **I4 and I9's walk of one folder at a time is amended by I14 (2026-11-04).** The walk lists a level of the tree at a
  time, four folders of it at once. I8's decision, to walk rather than ask encri-api for a query across folders,
  stands.
- **I3's screenshots, blocked always and with no setting, are amended by I13 (2026-11-04).** *Allow screenshots* is a
  setting, off by default, and every window and dialog follows it.
- **I3's secure windows kept a video's picture out of a screen recording and not its sound, amended 2026-11-04.** From
  API 29 any app recording the screen may capture another's audio unless it says not. `allowAudioPlaybackCapture` is
  false, `ShippedManifestTest` reads it, and it does not follow I13's screenshot setting.
- **I3's `allowBackup` false stopped a cloud backup and not a transfer to a new phone, amended 2026-11-04.** From API
  31 a transfer would have carried the preferences, saved playback positions among them. `data_extraction_rules.xml`
  excludes every domain from both, and `ShippedManifestTest` reads it.
- **I4's clip of no length, which spun ExoPlayer's repeat mode, is fixed, amended 2026-11-04.** `LoopWhatLasts` starts
  every clip unlooped and loops one once it is ready and lasts.
- **I1's alpha dependencies (its flag 2) are amended 2026-11-04.** Each is now the stable release of the line it was
  on, at its latest patch, and nothing in the lockfile is a pre-release.
- **I12's probes, run on the G6, were run on the A20 the same day and on the S24 on 2026-11-04.** `GrantChanges`, and
  then `GrantGone`, answered there exactly as on the G6.
- **I12's two questions for encri-api are answered the same day (2026-11-03).** The user chose both, and encri-api L23
  promises them: what the user allows replaces the grant and ends the session, and so does deleting a grant's folder.
  encri 0.1.0 does not yet end that session, so this app keeps taking a missing root for the end (I9).
- **I9's question for encri-api, what becomes of a session whose folder is deleted, is amended by I12 (2026-11-03).**
  What encri does was seen, and the question stands.
- **I11's open question, whether encri ends the old session or widens its grant in place, is answered by I12
  (2026-11-03).** encri ends it.
- **I9's G6, which waited on its vault, is amended the same day (2026-11-03).** The user gave it, and the G6 is
  measured in I9.
- **I5's test phones' encri is amended by I9 (2026-11-03).** The S9 and the G6 have encri's release build, installed
  at the user's direction.
- **I3's first version, with no share and `READ` alone, is amended by I10 and I11 (2026-11-03).** Share is back, and
  *open with*, through encri's handoff (I10); and the app asks for `DELETE` as well, the first time the user deletes
  (I11).
- **I3's `FLAG_SECURE` on every window did not reach a dialog, amended 2026-11-03.** A dialog is a window of its own,
  and the info sheet's had no flag. Every dialog now goes through `SecureWindows.secure`.
- **I1's minSdk paragraph is amended by I6 (2026-11-03).** The app did not start below API 30, which lint could not
  see, and it now starts on API 28 and 29.
