# encri-images — Project Facts & Decision Log

> Living document. Append decisions as they're made; mark superseded ones rather than deleting them.
> Date format: YYYY-MM-DD.

## What this is

**A gallery over an encri vault**: an Android app that shows the photos and videos in the vault, reached only
through encri's companion service over AIDL. It is a fork of [LineageOS Glimpse](https://github.com/LineageOS/android_packages_apps_Glimpse),
licensed Apache-2.0 as Glimpse is, and one of the two apps encri keeps open source (encri D142).

Glimpse is a system gallery over MediaStore. This app keeps its screens and replaces what is underneath them,
because a gallery is the app most likely to put a vault's pictures somewhere they should not go.

What crosses the boundary between this app and encri, and why, is decided in encri's records (D112 to D118, D120)
and in encri-api's for the wire. It is not restated here. What this file owns is the app.

## Status

**The first version reads and shows, and nothing else (2026-11-01).** It connects to encri, asks to read, unlocks
through encri's screen, shows Reels, Albums and Library from the vault, opens photos, and plays videos with seeking.
It changes nothing in the vault and hands nothing to another app. Checked by hand on the A20 (API 30); see I4. Since
2026-11-03 it starts on API 28 and 29 as well (I6), and the A20 is measured at 5,000 photos (I8).
The fork on GitHub still holds only Glimpse's history, and nothing is released. `ROADMAP.md` says what is next.

## Relationship to the others

- **Decisions here are numbered I1, I2, …**, and the others' are cited as *encri D120*, *encri-api L12* and
  *encri-files F2*. Four logs each numbered from 1 would make "D3" mean four things.
- **About the app, this file wins.** About the contract, encri-api's does; about encri's internals, encri's does.
- **Upstream is Glimpse**, as the `upstream` remote. `config/upstream-fork-point` is the commit forked from.
- **Nothing here comes from encri's own repository**, which is closed (I5). encri-api and encri-files are the open
  sources to work from.

---

## Repo conventions

**Three files, and they do not overlap.** This one is the decision log, `ROADMAP.md` is what is next and in what
order, and `CHANGELOG.md` is what happened. When they disagree, **this file wins**.

**Default branch is `master`**, a SystemHalted organization-wide policy. Glimpse's `lineage-24.0` was renamed to it
when the fork was made. Do not rename it to `main`.

**Commits are signed and Conventional**, with the hooks encri-files uses: `signing-preflight`, `ktlint` and `detekt`
before a commit, `conventional-commit` on the message, and `verify-signatures`, `encri-api-pin` and `allUnitTests`
before a push. Bootstrap a clone with `lefthook install`, then `scripts/ci/encri-api fetch ../encri-api`. Every rule
a hook enforces is enforced again in `.github/workflows/`, because `--no-verify` always exists.

**Every commit builds and passes its own tests, not just the branch tip**, encri's rule for encri's reason.

**Merge with a merge commit or a rebase, never a squash** (encri D7).

**An upstream fix comes across as a signed cherry-pick, with `git cherry-pick -x -S`, never as a merge** (I1).

**Regenerating the dependency verification metadata**, after adding or bumping anything, from an empty file over
every task a workflow runs (encri D7):

```sh
rm gradle/verification-metadata.xml
./gradlew --write-verification-metadata sha256 --refresh-dependencies --rerun-tasks \
  ktlintCheck detekt allUnitTests koverVerify koverXmlReport assemble assembleAndroidTest bundleRelease
```

**Changing what a user receives** means updating the lockfile, which STRICT mode demands:
`./gradlew :app:dependencies --write-locks`.

## Local development environment

The same machine as encri-api and encri-files.

- **The phones:** LG G6 (API 28), Galaxy S9 (API 29), Galaxy A20 (API 30) and Galaxy S24 Ultra (API 37, read from the
  phone 2026-11-02). `adb` is in `~/Android/Sdk/platform-tools`, not on `PATH`.
- **A shell here can be inside the code-server container**, which has a `/dev` and a `gpg-agent` of its own. A phone
  enumerated after the container's `/dev` was made is listed in `/sys/bus/usb/devices` but has no device node, so `adb`
  does not see it, and a key unlocked outside the container is still locked inside it (seen 2026-11-02).

- **Set `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`**, and never run two Gradle invocations at once, here or in a
  sibling: the included encri-api build is one more set of outputs for them to fight over.
- **encri-api must be checked out beside this one**, at `../encri-api`, or pointed at with `-Pencri.api.dir=<path>`.
- **`uiautomator dump` hangs while a video plays**, and a window with `FLAG_SECURE` cannot be captured (`screencap`
  exits 1). A throwaway local build with `SecureWindows` emptied is how the screens were looked at. Never commit one.

---

## Decisions

### I1 — A true fork, with the inherited code left in upstream's shape and new code held to encri's gates
*2026-11-01 · accepted at the user's direction ("fork the lineageos glimpse repo and start putting together
encri-images"); **flagged for veto on three points** (below)*

**A GitHub fork of `LineageOS/android_packages_apps_Glimpse`**, as `syshlted/encri-images`, public as any fork of a
public repository must be, keeping Glimpse's 384 commits. The history is the attribution, and a real fork keeps the
way open to take fixes.

**Two source trees, and the gates follow them.** What came from Glimpse stays in `src/main/java`, in upstream's own
formatting, edited as little as a change allows. What is written here goes in `src/main/kotlin`, in the `vault`
package, and passes ktlint, detekt and a coverage floor of 80% of lines. encri's ktlint style finds 965 violations
in the inherited code and an android_studio style still finds 434 (measured 2026-11-01); reformatting would turn
every upstream fix into a conflict, for no behaviour.

**An upstream fix comes as a signed cherry-pick.** A merge would bring unsigned commits into a range the signature
checks walk. `config/upstream-fork-point` is where `commit-policy.yml` stops looking, so Glimpse's own history is
never asked for a signature it could not have.

**The toolchain is encri-files', version for version**: Gradle 9.7.1, AGP 9.4.1, Kotlin 2.4.21 on the root classpath,
JDK 21, ktlint 1.8.0, detekt 1.23.8, Kover 0.9.9, JUnit 5.14.4 and Robolectric 4.17, with the same hooks, pinned
action SHAs and encri-api pin. Dependencies are pinned twice, by sha256 and by lockfile.

**minSdk 27, down from Glimpse's 30**, because encri's floor is 27 and anyone on it should have a gallery. Lint finds
no `NewApi` in what remains, and that is all that has been checked: nothing has run below API 30. *Amended by I6:
it did not start below API 30, which lint could not see, and it now starts on API 28 and 29.*

**AndroidX stays, where encri-files has none** (encri-files F1). Its screens are the reason to fork Glimpse.

*Rejected:* a fresh repository that copies the code, which would lose the history that carries the attribution, and
the route for fixes. *Rejected:* reformatting the inherited code. *Rejected:* a coverage floor across the whole
module, which would measure Glimpse having no tests rather than this code having them.

**Flagged for veto:**

1. **The inherited screens have no coverage and no lint.** The gates hold only for `src/main/kotlin`, and encri-files'
   rule was the whole module, nothing excluded.
2. **Glimpse's alpha dependencies are kept** (`core`, `activity`, `lifecycle`, `material`, `exifinterface`), at the
   versions it was forked with. A gallery of a vault should be on stable releases before it ships (`ROADMAP.md`).
3. **Nothing in GitHub protects `master` yet.** encri-files' ruleset requires signatures; here it is not set, because
   changing a repository's rules is the user's to say.

### I2 — The names, the licence, and what is carried from Glimpse
*2026-11-01 · accepted; **flagged for veto on two points** (below)*

**`com.systemhalted.encri.images`**, following encri-files, and permanent from the first build a user installs,
because encri keys a grant on it (encri D120). **The launcher name is "Encri Images"** in every language; Glimpse's
translations of its own name were removed so none overrides it.

**Apache-2.0**, which is not a choice: the inherited code is under it. A `NOTICE` says where this came from, and
every source file that changed carries a copyright line of its own beneath The LineageOS Project's, which is section
4(b) of the licence. Nothing here claims LineageOS's endorsement.

**Versions follow encri D6's scheme**, as encri-files does: 0.1.0 is 1000.

**Flagged for veto:**

1. **Glimpse's launcher icon is still the icon.** Its licence allows that; a release should have its own, so that
   nobody takes this for LineageOS's gallery (`ROADMAP.md`).
2. **The copyright holder is written "SystemHalted".** Nothing else in these repositories names one.

**Left open:** the signing key, whether encri's registry lists this app, and whether it joins the joint release of
encri D136. encri D142 said D115's shared key does not extend here by itself, and nothing needs one until a release.

### I3 — Nothing leaves: no permission, one way in, no disk, and nothing shown while locked
*2026-11-01 · accepted; **flagged for veto on three points** (below)*

Glimpse is written for pictures that are already public to anything holding a media permission. Every outlet it has
was safe there and is a leak here, so the first version closes all of them, and each comes back only with a design of
its own.

**No permission at all.** The manifest removes `INTERNET`, `ACCESS_NETWORK_STATE` and `WAKE_LOCK`, which libraries
merge in, and the media permissions Glimpse asked for are gone. Without `INTERNET`, plaintext in this process cannot
leave by a socket, whatever a library tries, because the kernel checks it. OkHttp, Glide's integration with it, and
Media3's DASH, HLS, RTSP and SmoothStreaming modules are gone with it. `ShippedManifestTest` reads the merged manifest,
so a library that adds one fails the build.

**One way in, the launcher.** Glimpse exported `PickerActivity` for `GET_CONTENT` and `PICK`, `ViewActivity` for `VIEW`
and `REVIEW`, and `SetWallpaperActivity`. Here the first would hand vault plaintext to whichever app asked, the second
would be a way to probe it, and a wallpaper is a picture from the vault on the lock screen. Picker and wallpaper were
deleted, and the viewer is unexported and takes its request from this app's own extras.

**Nowhere to write a picture.** Glide keeps a disk cache by default, which encri D112 forbids because it would hold
plaintext outside the vault. `VaultGlide` installs its no-op `DiskCacheAdapter`, and answers `DataSource.LOCAL`, since
Glide writes what is `REMOTE` to its data cache. ExoPlayer has no cache configured. After browsing, playing and
looking at the info sheet on the A20, the app's data directory held five empty directories and no file at all.

**Nothing sent about a picture.** Glimpse's info sheet gave a photo's coordinates to `Geocoder`, which on most phones
is a network service, without asking, and offered them to a maps app. Here it shows the numbers and nothing else.
Share, edit and *use as* are gone, because they need encri's handoffs (encri D117), and the favourite and trash
buttons are gone because encri has neither.

**Nothing shown while locked.** `VaultLibrary` forgets everything when the vault locks or the session goes, and Glide's
memory is cleared. `MainActivity` hides the whole gallery behind a gate with one message and one button. **The viewer
closes itself**, because an open descriptor goes on serving across a lock (encri D103), so a video would otherwise
play on. Glimpse's `ACTION_REVIEW_SECURE`, which showed media over the keyguard, is gone.

**Asks for `READ` alone**, because this version does nothing else, and encri's screen says so to the user.

**`allowBackup` is false**, and **every window has `FLAG_SECURE`**, set in one place by `SecureWindows`.

*Rejected:* keeping these features and asking for confirmation, which is a design for later rather than a default to
inherit. *Rejected:* disabling Glide's disk cache per request, where one forgotten call is a leak; with the adapter
there is nothing to forget.

**Flagged for veto:**

1. **Screenshots are always blocked**, with no setting. encri-files has one, off by default (encri-files F3).
2. **The first version has no share, favourites, trash or delete**, all of which a gallery is expected to have.
3. **A location is shown as numbers**, which is the only way to keep it from a service without a design for asking.

### I4 — The vault as Glimpse's data source: walked, in memory, and known by URIs of its own
*2026-11-01 · accepted; **flagged for veto on two points** (below)*

**Glimpse's `MediaDataSource` is the seam.** `VaultDataSource` answers Reels, Albums and an album from the vault, and
`LocalDataSource`, `Query` and everything over MediaStore are deleted.

**The vault is walked, a folder at a time**, because encri-client has no query across folders (`walkLibrary`). It costs
one request per page of every folder. One walk per session and one on every return to the gallery, since encri's
change notices are best effort, plus one after a burst of those notices has been quiet for 500 ms. A walk that finds
the same vault publishes nothing, so a return does not redraw. **An album is a folder that directly holds a photo or a
video.** A folder deleted mid-walk is not a failure; any other failure ends the walk and says so.

**Dates are the vault's**, a document's last change, not when a photo was taken, because that is what encri knows
without opening the file. Reading EXIF for every photo is a cost a walk cannot carry.

**Inside this app a document is a `Uri`**, `encri-vault://document/<id>`, because Glimpse's screens identify everything
by one. No provider answers it and no other app could open it. It carries encri's opaque id, which says nothing about
the document (encri D15), and it is the only thing a saved playback position stores.

**A grid cell is encri's preview, and never a decode here** (`VaultGlide.PREVIEW`), so a grid of unknown images parses
nothing in this process (encri D112).

**A full image is read through a descriptor and decoded here**, as is a video's stream, by ExoPlayer over
`VaultMediaSource`, seeking by position. This is the first place an app of ours parses an original the vault holds, in
a process that holds no keys, so a malicious image reaches this app's memory and not the vault's (encri D110's isolated
decoder protects encri, not this).

**Reading is off the main thread.** Glimpse opened a file for the info sheet in the dialog's constructor; here the
viewer reads it first, because a read of a descriptor encri serves can take a while.

**Measured on the A20 at API 30, debug build, against encri 0.1.0, a vault of a few documents:** the gate through
locked, not allowed and open; encri's consent screen; Reels, Albums and Library; a photo and its info sheet; a video
playing to 00:07 of 00:08 and a seek to 00:03 showing a different frame; `force-stop` of encri during playback and
*Lock the vault* with a photo open, each closing the viewer and showing the gate; and `screencap` refused.

**And the minified release build**, signed with the debug key so it could reach the same encri: the grid, a photo, its
info sheet and a video. Its APK declares no permission but AndroidX's signature permission to itself.

**That found one bug the unit tests had not:** the viewer opened at the newest item rather than the one tapped. The
library is already in memory here, so the list beat a separate flow carrying the starting point, a race that
MediaStore's slower load had hidden. The start is now read from the request itself (`initialPositionIn`).

**Not measured, and not to be assumed:** how long a walk takes on a large vault; anything below or above API 30; pinch
zoom and whether ZoomImage's subsampling reaches a `Uri` it does not know; a motion photo; a large video over the proxy
descriptor; memory on a large album. A clip whose duration is 0 spins ExoPlayer's repeat mode, creating and destroying a
decoder several times a second (seen on the A20, inherited behaviour, `ROADMAP.md`).

*Rejected:* adding a query across folders to the contract before this app had shown it needs one. The walk gives the
measurement that would justify it. *Rejected:* a `ContentProvider` here serving vault content by `content://` URI so
Glimpse's loading would work unchanged, which would be an exported surface or a confusing unexported one, for a saving
of about 150 lines. *Rejected:* changing `Media` to carry a document id rather than a `Uri`, which touches every screen.

**Flagged for veto:**

1. **Reels sorts by last change, not by when a photo was taken**, so a photo copied in today reads as today's.
2. **A full image is decoded in this app's process**, not an isolated one. It holds no keys, but it does hold whatever
   else the user has opened.

### I5 — Nothing from encri's closed repository
*2026-11-02 · accepted at the user's direction ("encri-files and encri-images will be open-source. encri will not be.")*

**This repository is public and encri's is not, so nothing here comes from encri's**: no source, record, document,
build output, APK or test fixture, and its records are not read to settle a question here.

**What this app relies on must be said by encri-api or seen from outside**: in the contract, `PROTOCOL.md`, encri-api's
records or encri-files', or observed of an installed encri, as any app could. When none of them answers, the gap is
raised in encri-api or with the user. A citation such as *encri D112* stays as a name for a reason given elsewhere, and
is not something to go and read.

**Testing is against an encri installed on a phone.** The S24 Ultra's encri 0.1.0 is signed by the certificate
`EncriTrust.RELEASE_CERTIFICATES` pins (read from the phone 2026-11-02), so a debug build of this app trusts it with no
key in common. I4 signed the release build with the debug key to reach an encri signed by that key, and an encri from a
release does not need that. A phone without encri gets it from the user. *Amended by I9: the S9 and the G6 have
encri's release build, installed at the user's direction.*

---

### I6 — Device tests: a test vault of its own, and the app measured through its own grant
*2026-11-03 · accepted at the user's direction ("let's [go] on steps 1 and 2"); **flagged for veto on two points**
(below). **Amends I1**'s minSdk paragraph.*

**Two test APKs, because two identities.** The app's own instrumented tests, in `app/src/androidTest`, run in the
app's process, so they reach encri as the app does, through its one `VaultLink` and with its `READ` grant, and ask
for nothing more: `GateOnPhoneTest` and `WalkMeasurement`. What writes to the vault, seeding a library and removing
it, runs from `:testvault`, a library module whose test APK instruments itself as
`com.systemhalted.encri.images.testvault.test`: a uid and a grant of its own, with `READ`, `WRITE`, `CREATE` and
`DELETE`. Seeding from the app's own tests would have asked encri to widen the app's grant.

**encri's screens are pressed as encri-api's device tests press them** (encri-api L13): through the accessibility
tree, only in encri's own window, matched on the English words encri-api's tests name. A locked vault needs its
passphrase, as the argument `encriPassphrase`, which `scripts/device/instrument` takes from `ENCRI_PASSPHRASE`.

**A seed is removed by the id it wrote down, never by a name.** `SeedLibrary` makes one new top folder and writes its
id to the test package's own files before putting anything in it; `RemoveLibrary` deletes those ids and nothing
else. encri never gives an id to a second document, so a line left behind names nothing.

**`scripts/device/instrument` runs one class on one phone**: it builds, installs with `-r` and runs `am instrument`,
for encri-api L13's reason (the Gradle task uninstalls the test APK, and that drops the grant encri keyed on the
install). It wakes the phone and dismisses a keyguard that is only a swipe, because the first run on the A20 failed
on a screen that had timed out, with every activity stopped.

**CI compiles the device tests' APKs and runs none**, since CI has no encri.

**What they found at once: the app did not start below API 30.** Glimpse's theme asks for the cutout mode `always`,
which API 30 added, and API 28 and 29 refuse it when an activity starts. Lint checks an attribute against the API
level but not an enum's value, which is how I1's check missed it. `values/` now asks for `shortEdges` and
`values-v30/` keeps `always`. With that, the gate shows on the G6 (API 28) and the S9 (API 29), which have no encri,
and the gallery on the A20 (API 30).

*Rejected:* a stand-in encri in CI, as encri-api's `CrossProcessTest` has. Its way in, `ServiceBinding.connect`, is
internal to the client, so an app outside encri-api cannot bind one, and this app's unit tests already stand in at
its own seam (`VaultSession`). *Rejected:* `UiAutomator`, for L13's reason. *Rejected:* seeding by copying files in
through encri-files' screens, which would test encri-files.

**Flagged for veto:**

1. **The passphrase is an instrumentation argument**, so it is in the phone's process list while a run lasts:
   encri-api L13's flag, again, and for a test vault only.
2. **The seed's package is let into the whole vault with the rights to write**, by pressing *Allow* itself, on
   whichever phone it runs on. It writes only under its own top folder, but encri's grant does not know that.

### I7 — An answer says as much as an event: the link hears a lock, an unlock or the end in what encri answers
*2026-11-03 · accepted (step 2 of the integration plan, "harden reading")*

**encri's events can be missed.** The client drops its oldest past 64 rather than hold encri's binder thread
(encri-api L12), so a `Locked`, an `Unlocked` or an `Ended` could go unsaid, and the link believed whatever it heard
last. Two holes followed. A missed lock left the gallery showing until the next walk. A missed unlock left the gate on
*The vault is locked* until the process died, because `open()` did nothing for a session it already held, and encri's
unlock screen answers at once when the vault is already open, so pressing *Unlock* changed nothing.

**So every request through the link is heard as well.** `LOCKED` is the lock; `ENDED` or `DISCONNECTED` is the end,
which closes the session and asks for another; any other answer, a failure included, is a vault open enough to answer.
**An answer counts only if the link's state has not changed since it was asked**, so a preview asked before a lock
cannot unlock the gate by arriving after it, nor one asked before an unlock lock it again. The walk is asked through the
link too, so a walk that finds the vault locked is the gate's to show rather than the grid's error, and a walk whose
session the link has moved on from publishes nothing.

**A link that believes the vault locked asks again.** `open()` runs on every return to the gallery and after encri's
unlock screen answers; for a locked session it asks for the grant (`VaultSession.grant`, encri's `refreshGrant`, one
small read), and that answer is heard like any other. The interface still only reads.

*Rejected:* polling while locked, which would ask encri something every few seconds for as long as the gate shows.
*Rejected:* events alone, as before.

**Checked by unit tests only** (ten new, 71 in all). Not on a phone, where a real lock would need the vault's passphrase
to undo, and these runs have not been given it.

### I8 — A library of 5,000 on the A20: the walk stays, and zoom does not subsample
*2026-11-03 · accepted (step 2 of the integration plan, "measure"); **flagged for veto on one point** (below).
Answers `ROADMAP.md`'s *Measure a real library* for the A20, and *Pinch zoom and large images* as far as reading
ZoomImage's code.*

**Measured on the A20 (API 30), debug build, against its encri 0.1.0**, with libraries `SeedLibrary` made: generated
photos with a camera's grain, so a 1600x1200 is 530 KiB and a 4000x3000 3.2 MiB, dated back over three years.
`WalkMeasurement` and `scripts/device/scroll` measured them, then `RemoveLibrary` took them away.

| The vault | Walk, first and again | Folders | Heap held |
|---|---|---|---|
| Its own: 5 photos and videos | 230 ms | 4 | 62 KiB |
| 2,087, in 45 albums of up to 51 | 1,117 ms, then 648 and 672 | 54 | 660 KiB |
| 5,087: those, and 3,000 in one folder | 1,521 ms, then 1,056 and 1,164 | 56 | 1.6 MiB |

**The walk costs about 11 ms a folder and 0.12 ms an entry**: a folder of fifty lists in 11 ms, and the one of 3,000 in
362 to 442 ms over 15 pages. On those figures 20,000 photos in 200 folders would walk in four to five seconds, which is
an estimate, not a measurement. The library holds about 320 bytes a photo.

| Asked of encri | Measured |
|---|---|
| A 180x180 preview of a 1600x1200 photo, one at a time | median 62–68 ms, 90th percentile 68–74 ms |
| The same, four at a time as Glide asks | 54–56 a second |
| A 4000x3000 photo, opened, read and decoded for the screen | 16–24 ms, 188–209 ms (15–16 MiB/s), 177–183 ms |
| An 8000x6000 photo, the same | about 20 ms, 0.94–1.02 s (12–13 MiB/s), 456–464 ms |

| Twenty flings up Reels | Frames | Janky | 50th / 90th / 99th percentile | Memory (PSS, Java heap) |
|---|---|---|---|---|
| 2,087 photos | 2,046 | 9.6% | 7 / 16 / 30 ms | 124 MB, 21 MB |
| 5,087 photos | 1,450 | 7.1% | 6 / 13 / 48 ms | 131 MB, 25 MB |

With the 8000x6000 photo open in the viewer the app held 115 to 133 MB over two openings, and its data directory held
one file, AndroidX ProfileInstaller's note of when it last wrote a profile, and nothing of the vault.

**So the walk stays, and encri-api is not asked for a query across folders** (I4's rejection stands). At 5,000 photos a
first walk is a second and a half before Reels shows anything, and a return to the gallery costs encri another second
that publishes nothing new. Publishing a walk a folder at a time was not chosen either: Reels is newest first across
every folder, so a partial walk would reorder itself as it finished.

**ZoomImage never subsamples a vault photo**, read from its 1.4.0 classes. For a Glide model it knows only http(s),
`content`, `file` and assets; for anything else it asks Glide for the picture as a file with `onlyRetrieveFromCache`,
and `VaultGlide`'s disk cache is empty by design, so that fails. Zooming magnifies the bitmap Glide decoded for the
screen, and an 8000x6000 photo zoomed shows no more than its screen-sized decode. Nothing is written: the request only
reads the cache. Subsampling would need a `SubsamplingImageGenerator` of this app's own that reads through `openRead`;
that is `ROADMAP.md`'s, and not built.

**What it found besides: document ids in the log.** Glide logs a failed load at `WARN` with its model, here a
document's `encri-vault://` URI, and ZoomImage's request fails for every photo opened, so each one put its id in
logcat. `VaultGlide` sets Glide's level to `ERROR`, and a unit test fails a load and reads the log. After the fix,
opening the 8000x6000 photo and ten flings up Reels logged no vault URI and no seeded file's name.

**Not measured, and not to be assumed:** anything but the A20, because the G6 and the S9 have no encri and the S24
was not attached; a library of 20,000; video; a grant of one folder, which needs the app's grant changed on encri's
own screen. The gate test ran on all three phones (I6).

*Rejected:* a query across folders in encri-api, for now. *Rejected:* a partial walk published as it goes.

**Flagged for veto:**

1. **A first walk of a large library shows nothing until it ends**, a second and a half at 5,000 on the A20, and by
   estimate several seconds at 20,000.

### I9 — The S9 at 5,000, a grant of one folder, and a grant whose folder is gone
*2026-11-03 · accepted at the user's direction ("Let's test the other 2 phones as well"). **Amends I5** on where a test
phone's encri comes from, and **raises a question for encri-api** (below).*

**encri on the S9 and the G6 is its release build, 0.1.0**, signed by the certificate `EncriTrust.RELEASE_CERTIFICATES`
pins, which the user named and adb installed. Nothing of it is in this repository. **Their vaults are test vaults**,
made on encri's own first screen with passphrases generated for them and kept outside this repository, as the user
asked ("These are tests. You can generate and manage the passphrases."). Creating a vault asks for the phone's own
fingerprint or pattern, which only the user can give, so a new phone's vault takes the user once. The G6's waits on
that, and is not measured here.

**The S9 (API 29) against the A20 (API 30, I8)**, with the same two libraries, seeded and removed the same way:

| | A20 | S9 |
|---|---|---|
| Walk of about 2,080 photos in about 50 folders, again | 648–672 ms | 285–337 ms |
| Walk of about 5,080, with 3,000 in one folder, again | 1,056–1,164 ms | 509–515 ms |
| The folder of 3,000, listed | 362–442 ms | 173–196 ms |
| A folder of about 50, listed | 11 ms | 5 ms |
| A 180x180 preview, one at a time (median) | 62–68 ms | 46–50 ms |
| Previews, four at a time | 54–56 a second | 62–64 a second |
| A 4000x3000 photo read | 15–16 MiB/s | 23 MiB/s |
| Twenty flings up Reels: janky, 99th percentile | 7–10%, 30–48 ms | 0.6–2%, 14–24 ms |

On the S9 the app held 188 MB during the flings at 5,082 photos, 66 MB of it graphics at its 1080x2220. The walk's
shape is the A20's at about half the cost, so I8's decision stands.

**A grant of one folder reaches that folder and nothing else.** Asked on encri's consent screen for *one folder only*
and given the first library, the walk found its 2,082 photos in 43 albums across its 50 folders, and nothing of the
3,000 beside it. **Turning the app out takes the gallery away**: with the gallery in the background, encri's *Turn out
Encri Images*, and back in the same process, the gate asked for permission.

**Found: a grant whose folder is deleted.** encri lets the app in no longer: a cold start answers *not authorized*.
But a walk took the missing root for a folder deleted under it and showed an empty gallery, which says the folder is
empty. **The root not found is now the session's end**, so the link asks encri again and the gate says why there is
nothing; seen on the S9 after the fix. **And a gallery in front hears nothing at all**, because the change is to the
root's parent, which the grant does not reach, so it goes on showing the deleted folder's albums until something walks
again. `PROTOCOL.md` says a session ends when the app ends it, its process dies, the user turns it out or turns access
off, and not when its folder goes. **That is a question for encri-api, not decided here**: whether the session should
end, or the app be told. Polling for it was rejected in I7.

**`scripts/device/scroll` drew no frames on the S9 at first.** It draws at an override of 1080x2220 on a 1440x2960
panel, and input is given in the override, so every fling began below the screen. The script takes the override now.

**Not measured, and not to be assumed:** the G6, until its vault exists; API 27 and 33+; video; 20,000 photos.

