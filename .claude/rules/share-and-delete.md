---
paths:
  - "app/src/**/vault/Handoffs*.kt"
  - "app/src/**/vault/Deletes*.kt"
  - "app/src/**/vault/Selection*.kt"
  - "app/src/main/java/**/ViewActivity.kt"
  - "app/src/main/java/**/fragments/AlbumFragment.kt"
  - "app/src/main/java/**/viewmodels/AlbumViewModel.kt"
  - "app/src/main/java/**/ThumbnailAdapter.kt"
  - "app/src/main/res/menu/**"
---

# Share and delete — component brief

What the app does to a photo or video beyond showing it: `Handoffs` (*Share* and *Open with*, through encri's
handoff), `Deletes` (*Delete*, and asking encri for the right the first time) and `Selection` (several chosen in a
grid), with the parts of Glimpse's viewer, album fragment, view model, thumbnail adapter and menus that call them.
This brief is level 1; full records are in `docs/decisions/`.

## Rules that hold here
- **Nothing is handed over before a dialog says what it costs, every time**: the app chosen gets the photo itself,
  not encrypted, and can keep it; it reads from the vault, so it finds nothing once the vault locks or this app is
  closed. *Open with* says as well that whatever that app saves, an edited copy too, it saves outside the vault.
  (I10, encri-files F7)
- **Only through encri's handoff** (encri D117): a `content://` URI on encri's provider that names neither the
  document's id nor its name, and stops working when the vault locks or the session ends. Reading is all a handoff
  needs, so the app still asks for no more than it did. Never a decrypted copy through a `FileProvider` of this
  app's own, which would write plaintext to its storage. (I10, I3)
- **The intents are encri-files'**: `ACTION_SEND` through `Intent.createChooser`, the URI in the stream extra and in a
  clip whose label is empty, with the read grant; *Open with* is `ACTION_VIEW` in a titled chooser of its own. Never
  `ACTION_VIEW` without a chooser, which would hand every photo to whichever app was once made the default. (I10)
- **encri is left out of both choosers**, by asking the package manager which of `Encri.PACKAGE`'s activities would
  take the intent. (I10)
- **Nothing is revoked when the chooser closes**: nothing reports it, and an app may read when it sends rather than
  when it is picked. (I10, encri-files F7)
- **A handoff is asked through the link**, so one that finds the vault locked is the lock. (I10, I7)
- **Several share through one handoff**: encri is asked once for every document chosen, and the share sheet gets
  `ACTION_SEND_MULTIPLE`, every URI in the stream and in the clip, with the read grant, typed as what they share,
  `image/*` or `video/*` when they are all one, or anything. A handoff that comes back short opens nothing. *Open
  with* stays one at a time, because `ACTION_VIEW` takes one. (I16)
- **No *Use as*, and no edit of its own.** A wallpaper or a contact's photo is a picture from the vault shown outside
  it for as long as it is set; *Edit* is *Open with* and its warning, since an editor given a read grant saves
  elsewhere either way. (I10, I3)
- **Delete is for a photo or a video, in the viewer or chosen in a grid, never an album**, because encri deletes a
  folder with everything in it. (I11, I16)
- **The right is asked for when it is first used.** *Delete* asks encri for the grant as it is now (`refreshGrant`,
  since the user can change it in encri) and, without `DELETE`, says so and offers *Allow in encri*: encri's consent
  screen for `READ` and `DELETE`, started for a result. A consent replaces the grant with exactly what was asked, so
  ask for every right the app means to keep. (I11, I12, encri-api L23)
- **After a consent, wait up to five seconds (`ALLOWING_MILLIS`) for a session whose grant has the right**, because
  encri ends the session whose grant it replaces and the link asks for the next at once. A `NOT_AUTHORIZED` from the
  delete itself, a grant narrowed in between, offers the consent screen again. (I11, I12)
- **A delete is said first, every time**: deleted from the vault, not put in a bin, cannot be undone, and a backup
  encri made before may still hold a copy (encri-files F5's words). The dialog names the kind, *photo* or *video*,
  never the file; several are *Delete these N photos and videos?* (I11, I16)
- **Gone is gone at once.** `VaultLibrary.deleted` takes it out of every screen and out of a walk already on its way.
  `NOT_FOUND` counts as gone. Several are deleted one after another; those that fail are counted in a toast that names
  none, and a grant narrowed part way asks encri again for what is left. (I11, I16)
- **A failure is said without naming the photo**, and every dialog goes through `SecureWindows.secure`. (I10, I11, I3)
- **The selection is this app's own**: a `Selection` of URIs in the grid's view model, so a rotation keeps it, and in
  memory alone. It holds only what the grid still shows, so a photo deleted, here or elsewhere, or a lock, leaves it at
  once. A long press in Reels or an album chooses; a tap while anything is chosen chooses or lets go; backing out of
  the action bar lets everything go. A chosen cell is drawn as Glimpse drew it. (I16)

## Traps already paid for
- Glimpse's selection used `recyclerview-selection` 1.3.0-alpha01 and reached into `DefaultSelectionTracker`'s
  private fields by reflection, which a minified build could break. (I16)
- Whether encri ended the old session or widened its grant in place was not visible, since encri logs neither; the
  app waits for either, and encri ends it. (I11, I12)
- Rejected: asking for `DELETE` at the gate, which asks every user for a right most may never use; a bin of this
  app's own, which needs `MOVE` and `CREATE` and is a folder every app with a grant sees. (I11)
- Showing the share dialog without `SecureWindows.secure` fails a test. (I10)
- The walk's filter for what was deleted: a walk that listed it first brings it back without it, which a test
  fails. (I11)

## Measured, not assumed
- *Share* and *Open with* against encri 0.1.0 on the S9, the G6, the A20 and the S24: the dialog's window secure
  (`0x1822002` on the A20 and the S24); the share sheet without encri, though encri's `ShareToEncri` takes a shared
  image on the S9, the G6 and the A20; *Open with* passed straight to the phone's one viewer, Samsung's or LG's
  gallery, which showed the photo it read through the handoff; on the S24 a chooser of nineteen viewers, where
  Samsung's Gallery was picked rather than Google Photos, which uploads. (I10)
- *Delete* on the same four phones: *Allow in encri*, encri's screen listing both rights, the question at once after
  *Allow the whole vault*, and the photo gone from a fresh walk. A second *Delete* went straight to the question. (I11)
- Several on the G6: three of six chosen, shared as far as the chooser and backed out of, then deleted after encri's
  screen, and a fresh walk at three. The release build did the same with two. (I16, I18)
- Under Robolectric: eight tests for share and open with, seventeen for delete. Not measured: deleting a video on a
  phone; the unit tests cover the library, not the viewer's playback position. (I10, I11)

## Decisions
| | Why it matters here |
|---|---|
| I10 | Share and open with through the handoff; the dialog; encri left out; two veto flags |
| I11 | Delete; the right asked for when first used; nothing to undo it; two veto flags |
| I16 | Several at once; a selection of this app's own (amends I10 and I11) |
| I12 | A consent replaces the grant and ends the session; ask for every right kept |
| I3 | What may leave, and every dialog secure; three veto flags |
| I7 | A handoff or a delete is asked through the link |
| I13 | The dialogs follow the screenshot setting |
| I18 | The release build chooses, shares and deletes |

## Open here
- **The *Send its name* tick** (encri D148, encri-api L24 and L25): unticked every time, in the dialog shown before a
  share or an *open with*, asking for the real name through `EncriSession.handOff(documentIds, withNames = true)`.
  Not built; it waits on the encri-api pin and on an encri that speaks contract version 2 (`ROADMAP.md`). Until then a
  handoff carries whatever name encri's provider gives: an encri at version 1, 0.1.0 among them, gives the real one
  (encri-api L24).
- Flagged for veto: every share and every open is confirmed, with no way to stop being asked; the share sheet is the
  platform's, records the app chosen, and on the S9 offers *Share as link* and *Quick Share*, which upload (I10); a
  delete cannot be undone, there being no bin; the gate asks for `READ` alone (I11).
- I3's second flag, *no share, favourites, trash or delete*, is overtaken for share and delete (I10, I11, I16)
  without a mark there.
- A bin and favourites are `ROADMAP.md`'s, each a design of its own.

## Read next
- Related briefs: `.claude/rules/viewer.md`, `.claude/rules/vault-link.md`, `.claude/rules/privacy-and-settings.md`
- Full records: `docs/decisions/INDEX.md`; the handoff's contract: encri-api's `PROTOCOL.md` and its L24, L25
