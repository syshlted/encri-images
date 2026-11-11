# encri-images — conventions

> Moved out of `AGENTS.md` on 2026-11-11 (I21). The text below is unchanged but for two strike-throughs I21 made; it
> was the *Relationship to the others* and *Repo conventions* sections of that file.

## Relationship to the others

- **Decisions here are numbered I1, I2, …**, and the others' are cited as *encri D120*, *encri-api L12* and
  *encri-files F2*. Four logs each numbered from 1 would make "D3" mean four things.
- **About the app, ~~this file wins~~ this repository's decision records win (amended by I21).** About the
  contract, encri-api's does; about encri's internals, encri's does.
- **Upstream is Glimpse**, as the `upstream` remote. `config/upstream-fork-point` is the commit forked from.
- **Nothing here comes from encri's own repository**, which is closed (I5). encri-api and encri-files are the open
  sources to work from.

## Repo conventions

**Three files, and they do not overlap.** ~~This one is the decision log~~ The decision log is
`docs/decisions/`, one record to a file (amended by I21), `ROADMAP.md` is what is next and in what
order, and `CHANGELOG.md` is what happened. When they disagree, ~~**this file wins**~~ **the decision
records win** (amended by I21).

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
  ktlintCheck detekt :app:lintDebug allUnitTests koverVerify koverXmlReport assemble assembleAndroidTest bundleRelease
```

**Changing what a user receives** means updating the lockfile, which STRICT mode demands:
`./gradlew :app:dependencies --write-locks`.
