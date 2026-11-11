# I19 — The third-party notices: written for this app, opened from the settings, and held to the build
*2026-11-05 · accepted; owed before any APK leaves this machine, the internal testing track included, for encri-files
F10's reason, which is encri D138's. Each choice is F10's, for this app's 117 modules where Encri Files ships six.*

**What is owed.** 117 modules ship, the same in both variants (`checkDebugThirdPartyNotices` and
`checkReleaseThirdPartyNotices` list them), and most of their artifacts carry no licence file. The Apache License asks
for its text to go with the app and for any NOTICE file to be reproduced; Glide's own code is BSD 2-clause and its GIF
decoder MIT, Adobe XMP Core is BSD 3-clause, and the Kotlin standard library carries ThreeTen-Backport's BSD code and
Boost's, as Encri Files' notices already say.

**Written for this app, from each library's own files at the version that ships**: its POM, what its artifact bundles,
and its repository at the tag, read for licence, NOTICE and copyright lines, and for code it carries from elsewhere
(FlatBuffers in emoji2, Sonic in Media3, Android's and Compose's code in ZoomImage, Harmony-style ASF code in Glide and
Okio, JSR 166 and the Public Suffix List's data in Guava). One entry per project, AndroidX by library family; Encri
Files' entries for the Kotlin standard library, kotlinx.coroutines and JetBrains' annotations word for word, being
the same versions; the Apache License once, at the end, and every other licence in full in its entry. kotlinx.coroutines'
and kotlinx.serialization's NOTICE files are reproduced; no other artifact or repository at its tag has one. Glide's
and Okio's ASF-headed code points to a NOTICE neither project ships, so the ASF's own notice line is given once.

**Held to the build by `:app:checkThirdPartyNotices`**, Encri Files' gate as it is: every module a variant's runtime
classpath resolves to is named by a line holding only `group:artifact:version`, and nothing else is, so a version bump
fails until the new release has been read. It runs in CI, beside the unit tests in the pre-push hook, and in
`reproduce.sh`'s refusal of a commit without notices (I20).

**Opened from the settings**, a row of its own under *About*, below the switches, because a licence's terms do not
depend on there being a vault. `NoticesActivity` draws the asset as Encri Files' `noticeText` does, selectable, under
this app's toolbar, and its window follows the screenshot setting as every window here does (I13). Not exported.

*Known and left:* Adobe's licence page for XMP Core, which its POM names, is gone; its text is from an archived copy of
that page, Adobe's BSD 3-clause. Guava's public-suffix data is MPL-2.0 and is credited with its source; R8 strips that
package from the release build. Okio's copyright line is its README's, its LICENSE having none.

**Verified.** Under Robolectric: the screen shows the whole asset to the end of the Apache License, headings without
their marks, selectable; kept out of screenshots; up leaves it; and the settings row opens it. The gate passes on both
variants and fails with a module's line removed and with a version moved, naming both. **On the S9 (API 29):** the
settings row opened it, from its introduction to the end of the Apache License, in a `SECURE` window.
