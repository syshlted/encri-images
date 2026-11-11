# encri-images — development environment

> Moved out of `AGENTS.md` on 2026-11-11 (I21). The text below is unchanged; it was the
> *Local development environment* section of that file.

## Local development environment

The same machine as encri-api and encri-files.

- **The phones:** LG G6 (API 28), Galaxy S9 (API 29), Galaxy A20 (API 30) and Galaxy S24 Ultra (API 37, read from the
  phone 2026-11-02). `adb` is in `~/Android/Sdk/platform-tools`, not on `PATH`.
- **An API 27 emulator runs here, headless, only with `-gpu swangle_indirect`**: with `swiftshader_indirect` or `guest`
  it dies in its render threads (exit 139, seen 2026-11-04). Unset `DISPLAY`, set `QT_QPA_PLATFORM=offscreen`, and never
  pipe its output through `head`, whose closing pipe kills it. It has no encri.
- **Waydroid on bigtab01 is API 33** (Android 13, read from it 2026-11-04, though bigtab01's own notes speak of Android
  16), with no encri. Reach it by `ssh 10.42.0.137`, where `sshd` is started by hand after a reboot; its container is
  `192.168.240.112:5555` on bigtab01's own bridge, so from here through a tunnel,
  `ssh -N -L 127.0.0.1:5562:192.168.240.112:5555 10.42.0.137`, then `adb connect 127.0.0.1:5562`. A new adb key is
  allowed on the tablet's screen; this machine's was, 2026-11-04.
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
- **The S24 is a phone in daily use with a test vault on it** (the user's word, 2026-11-03). Read only encri's and this
  app's text from its screen, and capture nothing: other apps' windows hold what is not a test's. Its vault's
  passphrase is the user's and is not on file; the phone's fingerprint unlocks it. encri 0.1.0 can neither change nor
  remove a passphrase: *Ways into this vault* only renames one.
- **Norton Mobile Security on the S24 opened a *malware found* screen** during the first install of the test vault's
  APK (2026-11-04). What it named was not read, because a run's own UI automation keeps `uiautomator dump` out; it had
  closed by the time the run ended, and nothing was removed.
- **`uiautomator dump` hangs while a video plays**, and a window with `FLAG_SECURE` cannot be captured (`screencap`
  exits 1). A throwaway local build with `SecureWindows` emptied is how the screens were looked at. Never commit one.
