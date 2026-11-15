# I22 — Crop and rotate, into a copy beside the original
*2026-11-15 · accepted at the user's direction ("I want to also add basic image/video crop … and rotation (0, 90,
180, 270 degrees)"); **flagged for veto on six points** (below). **Amends I3, I10 and I11.***

**The viewer's bottom bar has *Edit***, between *Share* and *Delete*, for a photo or a video, but not a GIF, whose copy
would keep one frame of it. It opens *Crop and rotate*, a screen of this app's own (`EditActivity`). Like the viewer it
is not exported, takes the photo or video from this app's own extras, and closes when the vault locks or the session
goes (I3).

**The crop is a dotted frame over the picture.** A finger on a corner or a side drags it, inside it moves it, and
outside it moves the picture. Two fingers zoom the picture under the frame, up to eight times fitted, so the frame can
be put on a detail too small to reach unzoomed; a double tap fits the picture again. What the frame leaves out is
dimmed, and its thirds show while a finger is on it. The frame is drawn as a dotted white line over a darker one, so it
shows on white as well as on black.

**The frame can be held to a shape**: *Free*, each side on its own; *Original*, the picture's own, as it is turned;
*Square*, *4:3*, *3:4*, *16:9* and *9:16*. A shape chosen is the largest box of it, centred where the crop was as
nearly as it fits. Held, a corner drags against the corner opposite, and a side against the side opposite, keeping its
middle. *Free* lets go without moving the frame.

**Rotate turns the picture a quarter clockwise**, the crop and its shape with it (4:3 becomes 3:4), so 0, 90, 180 and
270 degrees are four presses. The crop is kept in the turned picture's pixels, so a turn moves it with the picture
rather than leaving it where it was. *Reset* asks for nothing again. *Save a copy* is off until something is asked.

**An edit is saved as a new document beside the original, which is never changed.** It is named after the original and
marked, `IMG_1 (edited).jpg`, and where that is taken encri picks the first free name (`FLAG_FREE_NAME`,
`IMG_1 (edited) (1).jpg`), since the name is this app's and not the user's; a copy of a copy is not marked twice. Once
saved, the viewer moves to the copy when the walk finds it. *Rejected:* replacing the original, which is a delete of
what was there with nothing to undo it (I11). *Rejected:* asking for a name each time; encri-files F5 refuses a name
taken, but there the user typed it.

**Making a document and writing it are `CREATE` and `WRITE`, asked for the first time a copy is saved**, as I11 asks for
`DELETE`: *Allow saving copies, in encri?* offers encri's consent screen, started for a result, and the app waits up to
five seconds for a session whose grant holds both (`Rights.awaitHeld`). The rights are asked before the copy is made,
so nobody waits through a video only to be asked.

**A consent asks for every right the grant already holds as well** (`Rights.toAsk`), because what the user allows
replaces the grant with exactly what was asked (encri-api L23): asking for `CREATE` and `WRITE` alone would take
`DELETE` away, and I11's `READ` and `DELETE` would take these. *This amends I11*, whose consent asked for `READ` and
`DELETE` alone, and holds the invariant that a right is asked for with every right the app means to keep.

**What is written is made first**, so a copy that cannot be made leaves nothing in the vault. A document encri made and
this app could not fill, or was stopped filling, is deleted again where the grant lets it delete. Where it does not,
it stays, empty, and the walk shows it as a photo or video that will not open.

**A photo is decoded here**, from a descriptor through the link, as the viewer decodes it (I4). Its own EXIF
orientation is applied as it is shown, so the crop chosen on the photo the user saw is the crop kept. **Only the part
kept is decoded**: 1,024 rows at a time, through `BitmapRegionDecoder`, each band drawn straight into the copy, turned,
so one bitmap the size of the copy is all that is held. Above about 50 megapixels it is sampled down by a power of
two, so a 50 MP camera's photo is kept whole and a 200 MP one halved, and again where the phone has no memory for it. A
format Android cannot decode a part of is decoded whole. The copy is encoded as it is written to encri's pipe, so no
encoded copy is held either: JPEG at quality 95, or PNG or WebP as the original was, and JPEG for HEIC, AVIF and
anything else Android does not write. A photo's colour space, Display P3 among them, is the copy's.

**A video is made by Media3's Transformer** (`media3-transformer`, `-effect` and `-muxer` 1.10.0, the line the viewer's
player is on). It reads the video through `VaultMediaSource`, as the player does, and decodes and encodes it on this
phone. **A turn alone is not decoded at all**: Transformer writes it as the track's orientation, so the copy holds the
original's samples. A crop is encoded again, on whole even pixels as an encoder asks, with what the phone's encoders
can do.

**The MP4 is muxed into memory, never a file** (`MemoryOutput`, `MemoryMuxer`). A muxer goes back over what it wrote,
encri's pipe does not seek, and a file would put the video on this phone's storage in plaintext (I3). So the whole video
is held, in direct buffers outside the Java heap, and then written to encri in order. One video may take an eighth of
the phone's memory, and no more than 1 GiB; past that, *This is too large to edit on this phone.* A turn alone of a
video larger than that is refused before it starts. *Rejected:* Media3's fragmented MP4 muxer, which writes in order
and would need no memory, because its files carry no index and ExoPlayer cannot seek in one. *Rejected:* a file in
this app's cache, deleted afterwards, which is a picture on disk (I3).

**A copy carries nothing of the original but its picture and its sound.** A photo's copy has no EXIF, so no location,
camera or time it was taken; a video's carries its orientation and nothing else of the original's metadata. A copy
reads as newest in Reels, which sorts by last change (I4).

**Nothing is reported to the platform.** Transformer and ExoPlayer each report to `MediaMetricsManager` by default,
from API 31, and their own documentation says Google may collect it where the user shares usage data. The editor's
player and its Transformer have it off. *The viewer's player had it on, and is fixed alongside (`CHANGELOG.md`,
2026-11-15).*

**Every dialog is secure** (`SecureWindows`), and a toast names nothing: *A copy was saved beside the original.*, *The
copy was not saved.* (I3).

**Guava's annotations stay out of the APK.** Media3 leaves them out of the Guava it brings; `kotlinx-coroutines-guava`,
which `media3-effect` brings, did not, and would have shipped `jsr305`, `checker-qual` and `j2objc-annotations` and
moved `error_prone_annotations`. They are excluded on the two declarations that bring them, so what ships changes by
the three Media3 modules, `kotlinx-coroutines-guava` and `concurrent-futures` 1.2.0, each in the notices (I19).

**Verified.** Under Robolectric and the JVM, 88 tests of the unit suite's 233. They cover the geometry: each of
EXIF's eight orientations with each of four turns; the frame fitted, dragged by each corner and side, free and held to
a shape; and which handle a finger takes. They cover the zoom, held between fitted and eight times and about the
fingers, and the view taking a corner, a move, a pan, a pinch and two fingers moving together. Under Robolectric's
native graphics, a photo's quarters come through a crop, a turn and both; EXIF orientation 6 on a JPEG is applied; 2,500
rows in three bands lose nothing where the bands meet; a BMP is decoded whole; and each format's copy is written. Memory
is written over, truncated and refused past its limit. `MemoryMuxer`'s MP4 holds each of four turns as the track's
matrix, and no location, where Media3's muxer given the same writes it. Transformer's effects are checked for a turn
alone, a crop and both. The copy is kept beside the original, refused, deleted when it could not be filled or was
stopped while filling, and named. The consent asks for every right held, for a save and for a delete. The editor
itself runs over a test application that is a `VaultHost`: a photo shown, turned, held to a shape and saved, a lock,
and what it cannot open. **Five guarantees were broken one at a time and watched fail their tests**: the rights held
asked for again, a second finger ending the first's hold, a copy not filled deleted, a video's metadata not carried,
and a photo's EXIF orientation applied. The second at first did not fail, because its test spread the fingers, which a
pinch takes at once; it was rewritten for two fingers moving together, the gesture the fix is for, and then failed.
89.5% of the lines Kover measures, the `edit` package 87.3%. Everything CI runs passed with the verification metadata
regenerated from an empty file, the release bundle and the device tests' APKs included.

**Not measured: anything on a phone.** No build of this was installed on one: the installs were not made in the session
that built it. So unseen are the editor's screens and gestures, encri's consent screen for `CREATE` and `WRITE` and
the five-second wait after it, encri's free name, encri's preview of a copy, Transformer on any phone's encoders,
memory with a large photo or a long video, an HDR video, EXIF orientation from a real camera, and whether the
platform reports stop on API 31 and later.

**Flagged for veto:**

1. **A copy, never the original**: there is no *Save* over it.
2. **A copy keeps none of the original's metadata**: no location, camera or time taken, so it reads as today's.
3. **A video is held whole in memory while it is made**, up to an eighth of the phone's memory or 1 GiB, and a larger
   one is not edited here.
4. **The shapes offered**: *Free*, *Original*, *Square*, *4:3*, *3:4*, *16:9* and *9:16*; and JPEG at 95.
5. **A consent asks for every right the grant holds as well as the new one** (amends I11).
6. **The copy's mark, "(edited)", is English in every language**, as a document's name rather than a string.
