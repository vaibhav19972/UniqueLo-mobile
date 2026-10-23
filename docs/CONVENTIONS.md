# UniqueLo — Working conventions

> The work contract. Phase 1's audit (A1–A17) happened because several of these
> rules were unwritten. Each rule cites the miss it exists to prevent.

## Handoff contract (multi-agent)

1. **Read `PROJECT_STATE.md` first**, every session, before any code.
2. Make no silent design changes: record the decision in `DECISIONS.md`
   (append D-N with rationale) in the same commit.
3. Finish every unit of work with: code + tests + this `gradlew` matrix run +
   `PROJECT_STATE.md` updated (phase board + verification row) + commit.
4. Audits append `docs/phase-N-audit.md` using the phase-1 table format
   (ID / Severity / Area / Finding / Status) and convert every "must fix" into
   an OA row in `PROJECT_STATE.md`.
5. Never leave a stale claim in PROJECT_STATE — it outranks README and code
   comments as the source of truth. Update it in the same commit.

## Definition of done (per feature)

- [ ] Code matches existing structure/naming/style.
- [ ] Both sides of every retention rule asserted in tests: **survives** X
      **and is forgotten** when it should be (checkout A8 slipped because only
      the first half had a test).
- [ ] Verification matrix below run locally at least once.
- [ ] PROJECT_STATE + (if applicable) DECISIONS updated in the same commit.
- [ ] Commit subject one line, imperative, references ID(s), e.g.
      `fix(bag): keep pending journal entries on partial cache match (A3)`.

## Verification matrix

| Check | Command | Gate |
|---|---|---|
| Unit tests | `./gradlew :composeApp:testDebugUnitTest` | 0 failures |
| Android build | `./gradlew :composeApp:assembleDebug` | success |
| iOS target | `./gradlew :composeApp:compileKotlinIosSimulatorArm64` | success (D-10) |
| Lint | `./gradlew :composeApp:lintDebug` | 0 errors (metrics noise OK, OA-3) |

## Rule 1: SQL counts only when executed (A1/A2)

`jsonb_populate_record` DEFAULTs, RLS-shadowed `RETURNING`, privilege edges —
static review misses all three. **Any migration must be executed against real
DDL (local Postgres or Supabase) before its commit lands**, even when the
hosted deployment stays a human action (OA-2/OA-5). If no engine was available,
the commit is *not done*; record that in PROJECT_STATE instead of claiming it.

## Rule 2: Enumerate cross-snapshot edge cases (A3/A4)

State machine tests must cover the **partial** middle ground, not just clean
endpoints. Template for snapshot-driven state (cache vs live):

- snapshot matches all lines
- snapshot matches **some** lines
- snapshot matches **none**
- same, with journal entries pending for those lines
- write-to-disk always persists the **visible + pending union**

## Rule 3: Cancellation hygiene (A6/A7)

- `runCatching`/`try` around suspend code must rethrow
  `kotlinx.coroutines.CancellationException` first.
- Never `catch (Exception)` in a `launch` without rethrowing Cancellation.
- Every owned scope is cancelled exactly once by its owner
  (ViewModel `onCleared` → `RootStore.close()`).
- Don't create a scope you can't reach from the code that must cancel it.

## Rule 4: Compose state & SaveableStateHolder (A8)

- Session state → store (ViewModel-owned). Transient UI → `remember<state>`.
  Rotation-surviving transient UI → `rememberSaveable`.
- `SaveableStateHolder` retains state after its provider leaves composition —
  **key every session-scoped screen by a store counter** (e.g.
  `checkoutSession`), never by destination alone.
- Declare new retained state in the `PROJECT_STATE.md` retention matrix before
  implementing.

## Rule 5: IO hardening (A9/A10)

- Drain Ktor response bodies, even ones you don't parse.
- File writes on `okio` go temp-path → `atomicMove`, never direct.
- Persistence may return `null` when the platform dir is unavailable — treat
  null persistence as "disabled", not as an error.

## Rule 6: Dependency & toolchain changes

- Update `gradle/libs.versions.toml` only via PR-style isolated commit.
- Kotlin/AGP/lint version notes that affect checking go into PROJECT_STATE
  landmines (see OA-3 before trusting lint's Kotlin static analysis).

## Rule 7: The web repo owns the backend (D-14)

- **No mobile DDL.** Read `/Users/apple/MySpace/UniqueLo/supabase/schema.sql`
  and its migrations before changing any DTO, query or embedding. Schema
  changes are web-first, hosted second, mobile third.
- **Mirror the table inventory.** The nine-table lockdown array/`REVOKE` list is
  a claim about the web schema; a new catalogue table is a two-repo change.
- **Probe hosted state honestly.** Record the date and result in
  `PROJECT_STATE.md`. If a probe writes, delete the probe row and verify it is
  gone; never leave test data in the shared database.
- **Preserve web work.** The web repo may have uncommitted user changes. Read
  it; do not rewrite, revert or commit it from a mobile task.

## Rule 8: Theme tokens and accessibility (D-16)

- **`Palette` is theme-aware.** `Ink`/`Paper` invert in dark mode; never treat
  them as fixed black/white. Over photography use `ImageScrim` + `OnImage`.
  For a panel that must stay brand-black in both themes use `InverseSurface` +
  `OnInverse` + `InverseAccent`.
- **Accent contrast.** `Rust` is the red; pair it with `OnAccent` for fills.
  Red text on paper/bone is `#D5001C` in light mode (≥4.5:1) and `#FF4D57` in
  dark. Do not put `Rust` text on an inverted panel; use `InverseAccent`.
- **Touch targets ≥48dp** for every interactive control. Plain `clickable`
  does not enforce this; size the hit area explicitly.
- **Semantics are part of the control.** Use `selectable` with
  `Role.Tab`/`Role.RadioButton` for selectors; `liveRegion` for async
  success/error; `heading()` for screen titles; `contentDescription` for
  icon-only controls and images. A button that has no action must not claim
  `Role.Button`.
- **New retained state goes in the retention matrix first.** Appearance is the
  current example: `ThemeStore` + `theme.txt`, with both a restore test and a
  corrupt-file fallback test.
- **Fonts are licensed assets.** Satoshi is self-hosted under the ITF Free Font
  License (`docs/fonts/SATOSHI-FFL.txt`); a font swap is a licence review, not
  a silent asset replacement.
