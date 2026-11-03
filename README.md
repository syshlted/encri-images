# Encri Images

A gallery for an encri vault. It shows the photos and videos in the vault, reaching them through encri's companion
service with the [encri-api](https://github.com/syshlted/encri-api) client library, and never through Android's media
store or file picker.

It is a fork of [LineageOS Glimpse](https://github.com/LineageOS/android_packages_apps_Glimpse), and keeps its screens.
What changed is everything a gallery does with a picture once it has one: this app holds no permissions, not even for
the network, writes no picture to disk, and shows nothing while the vault is locked.

**Pre-release. Nothing is published.** Today it shows the vault and changes nothing: there is no share, delete or
favourite yet. See [`ROADMAP.md`](ROADMAP.md) for what comes next and [`AGENTS.md`](AGENTS.md) for why it is built the
way it is.

Encri Images is not affiliated with or endorsed by The LineageOS Project. See [`NOTICE`](NOTICE).

## Building

JDK 21, the Android SDK (compileSdk 36), and encri-api checked out beside this repository at `../encri-api`, at the
commit [`config/encri-api.pin`](config/encri-api.pin) names, or named with `-Pencri.api.dir=<path>`. Then:

```sh
scripts/ci/encri-api fetch ../encri-api   # encri-api at the pinned commit
lefthook install                          # once per clone: signed, Conventional commits and the local gates
./gradlew ktlintCheck detekt allUnitTests koverVerify assemble
```

The app talks only to an encri signed by encri's release key or by the same key as itself, so a debug build of this
app finds the encri on Google Play, and a debug build of encri made on the same machine.

## Licence

Apache-2.0, as Glimpse is. See [`LICENSE`](LICENSE).
