---
paths:
  - "scripts/release/**"
  - "config/release-certificates"
  - ".github/workflows/reproducible.yml"
  - "app/src/main/assets/**"
  - "app/src/**/notices/**"
---

# Releases, signing and notices — component brief

How a release is built twice, carried to the key machine, signed and checked: `scripts/release/` (`reproduce.sh`,
`compare-builds.py`, `kit.sh`, `sign.sh`, `bundletool.init.gradle.kts`, `upload-key.sh`),
`config/release-certificates` and `.github/workflows/reproducible.yml`; and the third-party notices every APK owes:
`app/src/main/assets/third-party-notices.txt`, its gate `:app:checkThirdPartyNotices`, and the `notices` package that
shows it. Encri Files' arrangement, carried over whole. This brief is level 1; full records are in `docs/decisions/`.

## Rules that hold here
- **The keys are Encri Files' arrangement.** The app is signed with encri's app signing key, and its bundle carries
  code transparency signed with encri's transparency key (encri D115, D140), both of which encri-api's
  `FINGERPRINTS.md` publishes and `config/release-certificates` names. The upload key is this app's own,
  `encri-images-upload`, and is owed: until its certificate is named there, `kit.sh` refuses to make a kit. (I20)
- **A release is built twice from one commit, and must come out the same.** `reproduce.sh` builds in two clones at
  different paths, under different umask, timezone, locale and Gradle home, each with encri-api cloned beside it at
  the pin, with the pinned Gradle and every pinned dependency. It refuses a commit without the notices, without the
  wrapper pinned to what gradle.org publishes, or from before the encri-api pin. (I20)
- **`reproducible.yml` runs it on every `v*` tag, weekly and by hand**, and keeps the unsigned APK and bundle for 90
  days; the maintainer's run must give the same SHA-256s. (I20)
- **Signing is done from a kit, on the key machine.** `kit.sh` packs the reproduced build with apksigner and
  bundletool, each pinned, and rehearses itself with throwaway keys before it is kept. `sign.sh` asks for each
  password at a terminal, signs, holds the signed files to the build, and keeps nothing signed by a key
  `config/release-certificates` does not name. `sign.sh --verify`, here, checks what comes back. (I20)
- **The upload key is made once, by `upload-key.sh` on the key machine**, into the directory that holds encri's two
  keystores: RSA 4096 in PKCS#12, valid 10,000 days, under the alias `sign.sh` looks for. keytool asks for the
  password, so the script never holds it; an existing keystore is never written over; it leaves the certificate as a
  `.pem` for Play and prints the `upload=` line. `--rehearse` does it all with a throwaway key and keeps nothing. (I20)
- **On Play, the listing's automatic protection is off**, because the bundle carries code transparency. (I20, encri
  D144)
- **A release's signed files are kept in `signed/`, which is ignored and never committed.** (I21)
- **The notices are owed before any APK leaves this machine**, the internal testing track included. (I19)
- **Every module an APK carries is named in the notices, at its version, and nothing else is.**
  `:app:checkThirdPartyNotices` reads each variant's runtime classpath against the lines holding only
  `group:artifact:version`, so a version bump fails until the new release has been read. It runs in CI, beside the
  unit tests before a push, and in `reproduce.sh`'s refusal. (I19)
- **The notices are written for this app from each library's own files at the version that ships**: its POM, what
  its artifact bundles, and its repository at the tag, read for licence, NOTICE and copyright lines and for code it
  carries from elsewhere. One entry per project, AndroidX by library family; Encri Files' entries for the Kotlin
  standard library, kotlinx.coroutines and JetBrains' annotations word for word; the Apache License once, at the end,
  and every other licence in full in its entry. (I19)
- **`NoticesActivity` draws the asset as Encri Files' does**, selectable, under this app's toolbar, opened from the
  settings whatever state the vault is in. Its window follows the screenshot setting, and it is not exported. (I19,
  I13)
- **The release build is minified**: R8 shrinks and renames, and Glide and ZoomImage find code at run time, so what is
  added is walked through on the release build before a release. (I4, I18)

## Traps already paid for
- The viewer opening at the newest item was found by walking the build through by hand, not by the unit tests. (I4)
- Adobe's licence page for XMP Core, which its POM names, is gone; its text is from an archived copy, Adobe's BSD
  3-clause. Glide's and Okio's ASF-headed code points to a NOTICE neither project ships, so the ASF's notice line is
  given once. (I19)
- Encri Files' repository holds no upload-key script, and encri's records are not read here (I5), so `upload-key.sh`
  is written to what `sign.sh` expects and rehearses with. (I20)

## Measured, not assumed
- `1d957f4` built the same twice, against encri-api `c27f139`: APK `4c8c77fa…`, bundle `8213a9b4…`. `kit.sh` refused
  it, naming no upload certificate. With a placeholder upload line, never committed, it made a kit of 29 MB that
  rehearsed itself through every step with throwaway keys; the kit was deleted and the line taken out. (I20)
- `upload-key.sh --rehearse` passed, and refused a second run over its key. A real run through a pseudo-terminal, into
  a scratch directory, asked three times, left the keystore mode 600, and printed the SHA-256 `keytool -list` reads;
  the scratch key was deleted. (I20)
- The minified release build is 3.3 MB against the debug build's 12, not debuggable, and was walked through on the G6
  and the A20 with no crash and no vault URI in the log. (I18)
- 117 modules ship, the same in both variants. The gate passes on both, and fails with a module's line removed and
  with a version moved, naming both. On the S9 the settings row opened the notices to the end of the Apache License, in
  a `SECURE` window. (I19)

## Decisions
| | Why it matters here |
|---|---|
| I20 | Built twice from a tag; the kit; the upload key; CI (amends I2); one veto flag |
| I19 | The notices, the gate, the screen |
| I18 | The release build walked through after I13 to I16 |
| I2 | The id, versions and licence; the signing key it left open |
| I4 | The release build first walked through |
| I21 | `signed/` is ignored |

## Open here
- Owed before a release: the upload key, made by `upload-key.sh` and its `upload=` line put in
  `config/release-certificates`; then a kit carried to the key machine, signed, verified here, and the bundle
  uploaded with the listing's protection off (I20, `ROADMAP.md`).
- Not yet: a real signing, and `reproducible.yml` on GitHub, which needs the push. (I20)
- Whether encri's registry lists this app, and whether it joins encri D136's joint release: encri-api's and the
  user's to say (I2, `ROADMAP.md`). The launcher icon is still Glimpse's (I2).
- Flagged for veto: a kit of this app's own rather than one for all three apps, so a fix to the release scripts is owed
  in three repositories. (I20)
- Guava's public-suffix data is MPL-2.0 and is credited with its source; R8 strips that package from the release
  build. (I19)

## Read next
- Related briefs: `.claude/rules/build-and-ci.md`, `.claude/rules/privacy-and-settings.md` (the notices row)
- Full records: `docs/decisions/INDEX.md`
