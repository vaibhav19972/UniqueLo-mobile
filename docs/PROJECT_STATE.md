# UniqueLo — Project State

> **The shared brain.** Every agent (human or machine) starts here before touching
> code. If something in code or README disagrees with this file, this file wins —
> update it in the same commit as the change. Companion docs:
> [DECISIONS.md](DECISIONS.md) (why), [CONVENTIONS.md](CONVENTIONS.md) (how),
> [audits/](.) (evidence).

## Phase board

| Phase | Scope | Status |
|---|---|---|
| 0 | git history + baseline | ✅ `a651be8` |
| 1 | Defects & safety: rotation/process-death survival, offline-first cache, bag journal, atomic orders, saveable UI state | ✅ code complete & audited; **2 deploy actions open** |
| 1-audit | Independent audit + fixes (`docs/phase-1-audit.md`) | ✅ `590cd3f` — 24 passed / 1 skipped, verified |
| **2** | **Architecture: type-safe navigation, store split (Catalog/Bag/Checkout/Newsletter), DI graph, CI pipeline** | ✅ `6840046` + `37937a0` + `76f91af` ; docs+SQL `78a5d6b` |
| 3 | Design elevation: Uniqlo-inspired redesign (clean white, minimal type, red accent, LifeWear voice), dark theme, brand fonts, shared-element card→PDP, a11y | ⏳ |
| 4 | Commerce: wishlist, order history (auth), review submission, stock validation, payments decision | ⏳ |
| 5 | Release engineering: signed builds, R8 map, icons/splash, deep links (incl. iOS shell) | ⏳ |

## Open actions (any agent may pick up; mark status when done)

| ID | Source | Action | Status |
|---|---|---|---|
| OA-1 | A12 | Deploy `supabase/migrations/20260101000000_place_order_fn.sql` to the hosted project (dashboard SQL editor). Verify: POST `/rest/v1/rpc/place_order` with `{}` → 400 (fn exists) not 404 PGRST202 | **OPEN — owner: human (dashboard access)** |
| OA-2 | A13 | Deploy `supabase/migrations/20260102000000_lock_catalogue_writes.sql` (verified on local PG16). Same host, one paste. Removes public write policies on catalogue tables; keeps public SELECT + guest order/newsletter INSERT | **OPEN — owner: human (dashboard access)** |
| OA-3 | A17 | AGP 8.13.2 lint analyser is a Kotlin 2.2 reader against 2.4.21 metadata — Kotlin static checks inside lint may be incomplete. Revisit when toolchain upgrades to AGP 9.x | OPEN — revisit later |
| OA-4 | A16 | Cache/journal are read synchronously in the Store constructor (UI thread). Acceptable at MVP scale; revisit at catalogue scale | ACCEPTED (recorded) |

## Current architecture (Phase 2 target)

```
AppGraph (DI, hand-rolled — ADR-8)
  └─ RootStore                         ← single app-scoped object via AppStoreViewModel
       ├─ CatalogStore — catalogue state machine, filters/search, offline cache, syncError
       ├─ BagStore — bag lines + persisted journal + rehydration
       ├─ CheckoutStore — checkout sessions, order submission
       ├─ NewsletterStore — subscribe state
       └─ Router — type-safe Destination back stack (ADR-3)
screens depend on narrow slices (catalog / bag / checkout), never on RootStore
```

State ownership rules (see CONVENTIONS.md §Retention matrix): session state lives in
stores (ViewModel-owned); transient UI state uses `rememberSaveable` with
**session-scoped keys**; nothing lives in bare `remember` if it must survive
rotation.

## State-retention matrix (the single source of truth for "what persists")

| State | Tab switch | Rotation | Process death | Notes |
|---|---|---|---|---|
| Catalogue content | ✔ (store) | ✔ (store) | ✔ (disk cache, offline-first) | stale-while-revalidate; `syncError` banner while serving cache |
| Bag lines | ✔ | ✔ | ✔ (bag.json journal → rehydrated against catalogue, stock-clamped) | journal survives unmatched-only-cached entries (audit A3/A4) |
| PDP colour/size selection | ✗ | ✔ (rememberSaveable) | ✗ | keyed by product slug |
| Discover scroll position | ✔ | ✔ | ✗ | hoisted `LazyListState` + SaveableStateHolder |
| Checkout form | ✗ | ✔ (per session) | ✗ | keyed by `checkoutSession`; fresh session per checkout (A8) |
| Newsletter email field | ✔ | ✔ | ✗ | store-owned, low stakes |

If you ship a new piece of retained state, add a row here **first**, then implement,
then assert both the "survives" **and** the "must-forget" side of the row in tests.

## Verification history

| Date | Commit | Check | Result |
|---|---|---|---|
| 2026-10-23 | `590cd3f` | unit tests | 24 passed / 1 skipped / 0 failures |
| 2026-10-23 | `590cd3f` | assembleDebug / iOS target compile | pass |
| 2026-10-23 | `590cd3f` | PostgreSQL 16 local exec of place_order migration (owner + anon RLS roles) | verified (A1/A2) |
| 2026-10-23 | `590cd3f` | lintDebug | 0 errors / 12 warnings (see OA-3) |
| 2026-10-23 | `78a5d6b..76f91af` | unit tests | 37 passed / 1 skipped / 0 failures (38: Router 10, Root 11, Bag 7, Catalog 3, LocalPersistence 6, live skipped) |
| 2026-10-23 | `78a5d6b..76f91af` | assembleDebug / lintDebug | pass / 0 errors, 12 warnings (OA-3 noise unchanged) |
| 2026-10-23 | `78a5d6b..76f91af` | compileKotlinIosSimulatorArm64 | pass (D-10) |
| 2026-10-23 | `20260102000000_lock_catalogue_writes.sql` | A13 lockdown SQL on local PG 16.15 | **EXECUTED & verified** — 8/8 demo write policies dropped, idempotent re-run, anon SELECT retained, anon INSERT denied. First run caught a real bug (`pg_policies.permissive` is text `'PERMISSIVE'`, not `'YES'`) — Rule 1 paying for itself |

## Phase 2 notes (next agents read these)

- The god-Store (`state/Store.kt`) is gone. Screens take narrow slices; only
  `App.kt` (the shell) holds `RootStore`. Store split wiring rules live in
  DECISIONS.md D-4; navigation rules in D-3.
- `Tab` enum deleted — bottom bar and routing use `Destination` (sealed).
- Checkout form retention is keyed by `checkoutSession` **inside**
  `CheckoutScreen` (session passed via `checkout.checkoutSession`) — A8 parity.
- `CatalogStore.warmFromCache()` must be called **after** wiring
  `onCatalogUpdated` (RootStore does this; tests via `AppGraph.createStore`).
- New regression coverage beyond Phase 1: `requestingCheckoutWithEmptyBagIsRejected`,
  `finishOrderLandsOnDiscover`, `failedOrderKeepsBagIntact`,
  `orderCommitClearsBagAndJournal`, full Router suite (10 tests).
- Rule 1 already caught its first bug: the A13 migration's first draft filtered
  `pg_policies.permissive = 'YES'` — on PG16 the column is `'PERMISSIVE'`
  text, so the draft silently dropped nothing. Fixed and locally verified.

## Landmines (things that look fine but break)

1. `jsonb_populate_record` does **not** apply column DEFAULTs — never
   `INSERT … SELECT *` into defaulted columns from it (A1).
2. `orders` has **no public SELECT policy** — any SQL path that tries
   `RETURNING`/`SELECT` under anon RLS fails (A2). Fetch inserted rows only via
   `SECURITY DEFINER` functions if ever needed.
3. `SaveableStateHolder` **retains state after the provider leaves composition** —
   key session-scoped content by a counter (A8), not only by destination.
4. `Deferred`s/`runCatching` around suspend code: always rethrow
   `CancellationException` first (A6/A7).
5. Ktor: drain response bodies (`bodyAsText()`/`body()`) or pooled connections
   leak (A9).
6. okio writes on `FileSystem.SYSTEM`: write to temp path then `atomicMove`, or a
   crash mid-write corrupts the file (A10).
7. Combining Kotlin reified inline functions with non-inline lambdas: type params
   don't flow through non-inline lambdas — keep reified calls inlined end-to-end.
