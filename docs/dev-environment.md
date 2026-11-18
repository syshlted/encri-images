# encri-images — development environment

> Moved out of `AGENTS.md` on 2026-11-11 (I21). The text below is unchanged; it was the
> *Local development environment* section of that file. What has changed since is struck through or dated in place.

## Local development environment

The same machine as encri-api and encri-files.

- **The phones:** LG G6 (API 28), Galaxy S9 (API 29), Galaxy A20 (API 30) and Galaxy S24 Ultra (API 37, read from the
  phone 2026-11-02). `adb` is in `~/Android/Sdk/platform-tools`, not on `PATH`.
- **An API 27 emulator runs here, headless, only with `-gpu swangle_indirect`**: with `swiftshader_indirect` or `guest`
  it dies in its render threads (exit 139, seen 2026-11-04). Unset `DISPLAY`, set `QT_QPA_PLATFORM=offscreen`, and never
  pipe its output through `head`, whose closing pipe kills it. It has no encri.
- **Waydroid on bigtab01 is API 33** (Android 13, read from it 2026-11-04, though bigtab01's own notes speak of Android
  16), ~~with no encri~~ with encri since 2026-11-11 and a test vault (below). It is LineageOS
  `20.0-20260403-GAPPS-waydroid_x86_64`, x86_64, a `userdebug` build with `ro.debuggable=0`, so there is no `adb root`.
  Reach it by `ssh 10.42.0.137`, where `sshd` is started by hand after a reboot; its container is
  `192.168.240.112:5555` on bigtab01's own bridge, so from here through a tunnel,
  `ssh -N -L 127.0.0.1:5562:192.168.240.112:5555 10.42.0.137`, then `adb connect 127.0.0.1:5562`. A new adb key is
  allowed on the tablet's screen; this machine's was, 2026-11-04, and again on 2026-11-11, because the container had
  made its `/data/misc/adb` again, empty, when it started. A restart of the container may lose the key again.
- **The tunnel to Waydroid drops.** Three times in the round of 2026-11-11 and 2026-11-12, ssh's keep-alive found
  bigtab01 not answering for two minutes and more while bigtab01 stayed up, and once the tunnel stalled with ssh still
  running and adb not answering. Restart it, then `adb disconnect 127.0.0.1:5562` and `adb connect 127.0.0.1:5562`.
- **encri on Waydroid was installed on 2026-11-11**, the first time, with Encri Files, Encri Images and their test
  APKs. bigtab01 has a pattern screen lock, so encri's create asked for the device's credential, which only its owner
  can give, and cancelling would have abandoned the create (encri D35, D74); the owner drew it. The test vault there
  has a device slot as well as a passphrase. **`run-as` is refused there** (*run-as: setegid(AID_PACKAGE_INFO) failed:
  Operation not permitted*), so an app's own files cannot be read or removed over adb; `pm clear` removes them.
- **The A20 is reached by wireless adb, and its vault was made again on 2026-11-12.** The old vault's passphrase was
  on no file, so at the user's word `pm clear com.systemhalted.encri` destroyed it, and a test vault with a passphrase
  alone was made in its place, since the A20 has no screen lock. Encri Images was let in again on encri's screen. Its
  encri, Encri Files and Encri Images are debug builds signed with this machine's debug key, so `adb install -r` keeps
  their data.
- **The test vaults' passphrases are on file**, the A20's and Waydroid's beside the S9's and the G6's, in the file
  `PASSPHRASES` names, kept outside the repository (I17).
- **A shell here can be inside the code-server container**, which has a `/dev` and a `gpg-agent` of its own. A phone
  enumerated after the container's `/dev` was made is listed in `/sys/bus/usb/devices` but has no device node, so `adb`
  does not see it, and a key unlocked outside the container is still locked inside it (seen 2026-11-02).

- **Set `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`**, and never run two Gradle invocations at once, here or in a
  sibling: the included encri-api build is one more set of outputs for them to fight over.
- **encri-api must be checked out beside this one**, at `../encri-api`, or pointed at with `-Pencri.api.dir=<path>`.
- **Play Protect on the S9 asks to send the test vault's APK to Google** at every `adb install` of it, and the install
  waits on the question, so `scripts/device/instrument` sits silent until *Don't send* is pressed on the phone (seen
  2026-11-03; *Don't send* was pressed both times).
- **Measure the G6 with an opaque screen in front**, such as Settings. Its wallpaper is a live one, which takes about a
  fifth of a core whenever the home screen shows, and `WalkMeasurement` has no screen of its own. Its CPU governor is
  `interactive`, so anything else running raises the clock and speeds a walk up: a shell loop reading the clock every
  200 ms cut one to a third (I9).
- **The S24 is a phone in daily use ~~with a test vault on it~~** (the user's word, 2026-11-03). Read only encri's and
  this app's text from its screen, and capture nothing: other apps' windows hold what is not a test's. ~~Its vault's
  passphrase is the user's and is not on file; the phone's fingerprint unlocks it.~~ Its vault was destroyed on
  2026-11-17 (below), ~~and none has been made in its place yet~~ and its owner has made one since, which crop and
  rotate ran over by 2026-11-18 (`CHANGELOG.md`). encri 0.1.0 can neither change nor remove a passphrase:
  *Ways into this vault* only renames one.
- **The S24's encri, Encri Files and this app are debug builds since 2026-11-17**, at the user's word, all signed with
  this machine's debug key. Its encri had been the release build, which a debug build cannot be installed over, so it
  was uninstalled and its vault went with it: the 5,080 photos I9 was measured over, and this app's grant and the
  seed's. This app, already a debug build, was updated at `d116f22` with `adb install -r` and kept its own data. It
  trusts that encri as one signed by its own key (encri-api L12), not by the certificate I5 names (I5, amended). A new
  vault is its owner's to make, and its create will ask for the phone's screen lock as well (encri D35, D74).
- **Norton Mobile Security on the S24 opened a *malware found* screen** during the first install of the test vault's
  APK (2026-11-04). What it named was not read, because a run's own UI automation keeps `uiautomator dump` out; it had
  closed by the time the run ended, and nothing was removed.
- **`uiautomator dump` hangs while a video plays**, and a window with `FLAG_SECURE` cannot be captured (`screencap`
  exits 1). A throwaway local build with `SecureWindows` emptied is how the screens were looked at. Never commit one.
