# I16 — Several at once: chosen in the grid, shared through one handoff, deleted after one question
*2026-11-04 · accepted (`ROADMAP.md`'s *Several at once*). **Amends I10 and I11**, which were one photo at a time.*

**A long press in Reels or an album chooses a photo or video, and a tap while anything is chosen chooses or lets go.**
The toolbar becomes an action bar with the count, *Share* and *Delete*. A chosen cell is drawn as Glimpse drew it:
blurred from API 31, behind a scrim before that, and checked. What is chosen is a `Selection` in the grid's view model,
so a rotation keeps it, and in memory alone; it holds only what the grid still shows, so a photo deleted, here or
elsewhere, or a lock, leaves it at once. Backing out of the action bar lets everything go.

**A selection of this app's own, not Glimpse's.** Glimpse used `recyclerview-selection` 1.3.0-alpha01 and reached into
`DefaultSelectionTracker`'s private fields by reflection, which a minified build could break; a set of URIs is what is
needed, and it is ten lines.

**Share several is one handoff** (I10): encri is asked once for every document chosen, and the share sheet is given them
as `ACTION_SEND_MULTIPLE`, every URI in the stream and in the clip, with the read grant, as the type they have in common,
`image/*` or `video/*` when they are all one, or anything. A handoff that comes back short opens nothing. The dialog says
the same as for one, of *them*. *Open with* stays one at a time, because `ACTION_VIEW` takes one.

**Delete several is one question** (I11): *Delete these N photos and videos?*, with I11's warning in the plural, after
the same right asked for the first time. They are deleted one after another; those encri deletes leave the library at
once, those that fail are counted in a toast that names none, and a grant narrowed part way asks encri again for what is
left.

*Rejected:* Glimpse's `recyclerview-selection`, for its alpha and its reflection. *Rejected:* *Open with* for several.

**Verified.** Under Robolectric: a selection chosen and let go, in the grid's order, and what the grid no longer shows
left out; several shared through one handoff as `ACTION_SEND_MULTIPLE`, in the stream and the clip, the type they share,
and nothing opened for a short handoff; several deleted after one question, those that fail counted, and a grant narrowed
part way asking again for what is left. **On the G6 (API 28)**, over six photos `SeedLibrary` made: a long press and two
taps showed *3 selected*; *Share* said *what is in them* and opened the platform's chooser, backed out of with nothing
sent; *Delete* asked to allow it in encri, whose screen listed both rights, then *Delete these 3 photos and videos?*, and
the grid was three, as was a fresh walk.
