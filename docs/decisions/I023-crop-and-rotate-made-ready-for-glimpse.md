# I23 — Crop and rotate made ready for Glimpse: a store the editor reads and keeps through
*2026-11-18 · accepted at the user's direction ("Let's do all of the "In the fork, so it pays off later" items in this
session", and "Defer everything else"); **flagged for veto on four points, all confirmed by the user the same day**
("I23 looks good to me"; below). **Amends I22.***

**Crop and rotate are to be offered to Glimpse, photos first and video later.** Glimpse's newest branch,
`lineage-24.0`, has no editor of its own: its *Edit* sends `ACTION_EDIT` to another app. About 970 lines of I22, the
frame, its shapes, the zoom and the geometry of a turn, have nothing of the vault in them, and a photo's copy, decoded
a band at a time, needs no library Glimpse does not already ship. A video's needs Media3 Transformer, Effect and Muxer,
which Glimpse would have to check in as prebuilts for LineageOS's own build, so it waits. Whether Glimpse wants an
editor at all is for its maintainers, and the user is to ask them first (`ROADMAP.md`, *Later*).

**What upstreaming pays back is only what this app can take back.** Once Glimpse has the editor, this app would
cherry-pick it (I1) and keep only what is the vault's. That needs an editor that reads and keeps through something the
vault can stand in for, which is what this record makes.

**The editor reads and keeps through an `EditStore`**: where a video is read (`videos`, a Media3
`DataSource.Factory`, for the player under the frame and for Transformer), how a photo is opened (`open`, a new
descriptor each time), and where a copy is kept (`keep`, which answers `Saved`). The vault's is `VaultEditStore`: the
link to read, as the viewer reads (I4), and `Copies` to keep (I22). A gallery over MediaStore would have one of its
own. `Made` and `Saved` moved with it, out of the vault's package, and a copy's name, `IMG_1 (edited).jpg`, is
`Made.nameOf`, the same for every store.

**Leave to keep copies is the vault's, not the store's.** `SaveFlow`, which asks encri for `CREATE` and `WRITE` with
every right the grant holds (I22), moved to the vault's package. What every store shares is `Saving`, in the editor's:
the dialog that says how far along a copy is, *Cancel*, keeping it through the store, and the toasts. A store that
refuses tells whoever made the `Saving`, which in the vault offers encri's consent again, as before. *Rejected:* a step
in `EditStore` that asks leave, which a MediaStore store would never use, since an app may always add its own picture
there.

**Only `EditActivity` knows the vault**: it chooses the store, starts encri's consent screen for a result, and closes
at a lock (I3). `EditorSeamTest` reads the editor's sources and fails when any other file names the vault's package or
encri's client. `LoopWhatLasts` is let through: a player's listener that loops a clip that lasts, kept in the vault's
package and knowing nothing of it. *Rejected:* moving the editor to a module of its own, which would hold the line in
the build, for a change to Gradle, the gates and Kover's packages that the cherry-pick back does not need.

**What a copy carries of the original's metadata is its store's to say** (`Carried`), as the user asked it be: none
in the vault, all but the orientation for Glimpse. `NOTHING` is I22's. `ALL_BUT_ORIENTATION` is all that still
describes the copy: when and where it was taken, what took it and how, and its words. Not its orientation, size or
thumbnail, which are the copy's own; not where a subject was in it, which a crop moves; not the original's unique id,
the software that wrote it, or XMP. A photo's tags are named one by one, 84 of them, since `ExifInterface` lists none.
A video keeps what Media3's MP4 muxer takes, location, times, Android's `mdta` keys and XMP, but for an orientation
offered with them: the copy's track has its own.

**A photo's EXIF is written by its store, a video's by its muxer.** `ExifInterface` writes only onto a file that
seeks, and copies the whole picture to a temporary file of its own as it does (`saveAttributes`, 1.4.2, read). So
`Making` reads the tags the store's `Carried` names from the original, `Made.exif` takes them to the store, and a store
whose copy is a file writes them there once it is whole, as a MediaStore store would. **The vault's store carries
nothing, and can do no other**: encri's pipe does not seek, and a temporary file is a picture on this phone's storage
(I3). It refuses a copy offered tags, rather than drop them unsaid. A video's tags are the muxer's to write, which holds
the whole video anyway (`MemoryMuxer`). *Rejected:* writing a JPEG's EXIF segment into the stream here, which would be
an EXIF writer of this app's own, for JPEG alone, for a store that keeps nothing.

**Deferred, at the user's word**, everything that is Glimpse's own: the code in Glimpse's package, `src/main/java`
and formatting; a store over MediaStore; its strings; how the built-in editor and `ACTION_EDIT` share *Edit*; whether
Glimpse takes the tests; a chain of small changes on Gerrit; and a video's crop and turn, after the photo's, **looking
and working almost exactly as the photo's do** (the user: "implement it so the look and feel is almost exactly like the
image rotate and crop"). Here they already share one screen: the same frame, shapes, turn and *Save a copy*, the video
playing under the frame (`VideoStage`). In Glimpse the video's is to be built on the photo's screen in the same way,
not as a screen of its own. `ROADMAP.md`, *Later*, holds the steps.

**The first change offered to Glimpse is a line of this app's own**: the viewer's player reporting nothing to the
platform (`CHANGELOG.md`, 2026-11-15), which Glimpse's player does as well. It is ready as a branch of one signed
commit in the user's Glimpse checkout, beside three earlier ones (`docs/dev-environment.md`). The setter is Media3's
unstable API, and lint does not carry an opt-in on a property into its initializer, so the change opts in on the view
model, in place of the opt-in on `playMotionPhoto`. Lint reports nothing new, and Glimpse's debug build builds. Not
uploaded: there is no Gerrit account on this machine.

**Verified.** Under Robolectric, the editor over a store that is not the vault, `FolderStore`, files in a folder: a
photo read from it, turned and kept there, red on top where it was on the left; and `Saving` keeping a copy and saying
so, telling whoever asked of a refusal, and saying a failure. `VaultEditStore` reads a photo through the link, a new
descriptor each time, reads a video from `VaultMediaSource` alone, and keeps through `Copies`. `SaveFlow`'s seven tests
pass unchanged but for how it is built, and so do the editor's own. `EditorSeamTest` was watched fail, naming
`Making.kt`, with one import of `VaultUris` put back into it. Glimpse's change: `lintDebug` and `assembleDebug`.

**What a copy carries, verified** under Robolectric. Of a JPEG with EXIF of each kind, `ALL_BUT_ORIENTATION` names the
time and its offset, the place, the maker, the aperture and the artist, and not the orientation, the size, the
subject's area, the unique id or the software; `NOTHING` names nothing. Over `FolderStore`, a JPEG taken turned a
quarter, with a time and a place, is copied upright under both: with `ALL_BUT_ORIENTATION` its copy has the time and
the place and is not to be turned again, and with `NOTHING` it has neither. `MemoryMuxer` writes a place it is offered
under `ALL_BUT_ORIENTATION`, and keeps the track's own turn over an orientation offered with it. `VaultEditStore`
carries nothing and refuses a copy offered tags. **Two guarantees were broken and watched fail**: the photo's
orientation carried, which failed three tests, among them a copy turned 90 degrees again that was already upright;
and a video's offered orientation taken, which failed two. The unit suite: 248 tests, 90.3% of the lines Kover
measures, the `edit` package 88.7%, and every gate CI runs passed, the release build among them.

**Not measured.** The editor was not run on a phone after this change, which moves what it is built from and not what
it does; the S24's run (I22, amended) was of the build before. Glimpse's change was not run on a phone at API 31 or
later, where the reports begin: Waydroid's tablet could not be reached from this session.

**Flagged for veto, and confirmed:**

1. **The store is three things**: where a video is read, how a photo is opened, and where a copy is kept. Leave to
   keep a copy is the vault's, outside it, in `SaveFlow`.
2. **`EditorSeamTest` holds the line by reading the sources**, a test of the tree rather than of what the app does,
   with `LoopWhatLasts` let through by name.
3. **The first change for Glimpse opts in to Media3's unstable API on the whole of `LocalPlayerViewModel`**, rather
   than moving the player's builder into a function of its own.
4. **What `ALL_BUT_ORIENTATION` carries**: the 84 EXIF tags named, and all a video's muxer takes but its orientation.
   Its serial numbers and its owner's name are carried with the rest, as the original's.
