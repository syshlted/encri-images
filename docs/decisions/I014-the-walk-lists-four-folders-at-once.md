# I14 — The walk lists four folders at once
*2026-11-04 · accepted (`ROADMAP.md`'s *Measure a real library*, the G6's part). **Amends I4** on how the vault is
walked; I8's decision, to walk rather than ask encri-api for a query across folders, stands.*

**A level of the tree at a time, four folders of it at once** (`walkLibrary`, `FOLDERS_AT_ONCE`), as many as Glide asks
previews of at once, which encri serves already. A walk of small folders was a string of short requests with a wait
between each, and on the G6 that wait kept the CPU's clock low (I9). What a walk finds does not depend on the order the
answers come in, because the library is sorted once it is whole, and a failure anywhere still ends it.

**Measured on the G6 (API 28)**, with Settings in front, over 2,000 photos of 160x120 in 50 folders that `SeedLibrary`
made and `RemoveLibrary` took away, by `WalkMeasurement`'s new `atOnce`:

| Folders at once | Walk, first and again | A folder listed, median |
|---|---|---|
| 1, as before | 1,354 ms, then 903–1,214 ms | 10–18 ms |
| 4 | 440 ms, then 274–299 ms | 7–8 ms |
| 8 | 535 ms, then 226–343 ms | 13–19 ms |

Four is a third of one, and eight no better than four, with each listing slower for the company.

*Rejected:* a walk with no limit, which would put every folder of a level to encri at once. *Rejected:* eight, which
measured no better.

**Verified.** Under unit tests: never more folders under way than asked, one at a time when asked for one, and the same
library from one and from four or eight at once. **Not measured:** the other phones, whose walks were not bound by the
clock (I9); a library of 20,000.
