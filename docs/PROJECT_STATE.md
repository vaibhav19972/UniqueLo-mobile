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
| 1 | Defects & safety: rotation/process-death survival, offline-first cache, bag journal, atomic orders, saveable UI state | ✅ code complete & audited; OA-1 deployed + live-verified; OA-2 **interim** (D-13); completion = OA-5 |
| 1-audit | Independent audit + fixes (`docs/phase-1-audit.md`) | ✅ `590cd3f` — 24 passed / 1 skipped, verified |
| **2** | **Architecture: type-safe navigation, store split (Catalog/Bag/Checkout/Newsletter), DI graph, CI pipeline** | ✅ `6840046` + `37937a0` + `76f91af` ; docs+SQL `78a5d6b` ; crash fix `ef321c6` ; audit fixes `9c5dcc4` (`docs/phase-2-audit.md`) |
| 3 | Design elevation: Uniqlo-inspired redesign (clean white, minimal type, red accent, LifeWear voice), dark theme, brand fonts, shared-element card→PDP, a11y | ⏳ |
| 4 | Commerce: wishlist, order history (auth), review submission, stock validation, payments decision | ⏳ |
| 5 | Release engineering: signed builds, R8 map, icons/splash, deep links (incl. iOS shell) | ⏳ |

## Open actions (any agent may pick up; mark status when done)

| ID | Source | Action | Status |
|---|---|---|---|
| OA-1 | A12 | ~~Deploy `place_order_fn.sql` **v2**~~ **CLOSED**. Live-verified 2026-10-23 via publishable-key REST (read + rollback probes only, nothing written): named-args call → **400 `22023`** items-guard; `get_order_details` bad uuid → 400 `22P02`, stranger email → 200 `null`; junk-uuid-item probe → 400 **and** follow-up lookup → `null` ⇒ atomic rollback confirmed on the hosted project. Verify-form note: `{}` alone binds no args → `PGRST202` is *expected*, not a failure; use `{"p_order":{},"p_items":"[]"}` | **CLOSED — deployed + live-verified** |
| OA-2 | A13 | ~~Full lockdown now~~ **PAUSED by D-13**: the web Admin Studio (`web repo src/pages/Admin.tsx`) writes via the publishable key — product-images storage uploads + `categories`/`garments`/`collections` upserts — and the full file breaks both. Interim instead: revoke the 6 core tables (products, variants, product_images, embroidery_details, universes, motifs) via `20260103000000_lockdown_interim.sql`; the full file stays in the repo as the OA-5 completion artifact | **PARTIAL — interim paste-run pending by owner; completion = OA-5** |
| OA-3 | A17 | AGP 8.13.2 lint analyser is a Kotlin 2.2 reader against 2.4.21 metadata — Kotlin static checks inside lint may be incomplete. Revisit when toolchain upgrades to AGP 9.x | OPEN — revisit later |
| OA-4 | A16 | Cache/journal are read synchronously in the Store constructor (UI thread). Acceptable at MVP scale; revisit at catalogue scale | ACCEPTED (recorded) |
| OA-5 | D-13 | When an authenticated admin gate lands on web `/admin` (or Admin Studio moves to service-role credentials): deploy the **full** `20260102000000_lock_catalogue_writes.sql` — drops taxonomy grants (categories/collections/garments) + the product-images upload policy + all permissive demo policies on the 9 tables (supersedes the interim revoke; Admin-Studio anon writes break **by design**). Verify with the file's 2 sanity queries (both → 0 rows) | OPEN — trigger: Supabase Auth / admin gate wired |

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
| Navigation / back stack | ✔ | ✔ | ✗ | `Router` is store-owned; process death lands on Discover |
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
| 2026-10-23 | `20260102000000_lock_catalogue_writes.sql` | A13 lockdown SQL on local PG 16.15 (reduced eight-table fixture) | **EXECUTED** — 8/8 fixture write policies dropped, idempotent re-run, anon SELECT retained, anon INSERT denied. First run caught a real bug (`pg_policies.permissive` is text `'PERMISSIVE'`, not `'YES'`) — Rule 1 paying for itself. **Superseded by the full-schema run below** |
| 2026-10-23 | `ef321c6` | reproduce user crash on emulator-5554 pre-fix | **6/6 cold launches crashed** (`NoWhenBranchMatchedException`, `BottomBarKt.iconFor` line 59) — deterministic, not stale artifacts; runtime probe + javap proved `Destination.tabs[0]` was `null` (landmine 8) |
| 2026-10-23 | `ef321c6` | emulator-5554 post-fix: 8/8 cold launches | 0 crashes; then interaction smoke: Discover→Bag (journal rehydrated)→Profile→PDP→checkout overlay→back — all render, single PID, 0 exceptions |
| 2026-10-23 | `ef321c6` | full matrix (assembleDebug / unit / lintDebug / iOS target) | green — 4 suites incl. Router 10 pass; lint 0 errors / 12 warnings (OA-3) |
| 2026-10-23 | this commit (D-12) | **live hosted-schema introspection** via publishable-key REST probes (read-only) | orders 12 cols (incl. coupon_code/gift_note/shipping_address/created_at) + items 10 cols all exist; **no `created_at` on order_items**; hosted still lacks `place_order` (`PGRST202`) → both functions reviewed pre-deploy |
| 2026-10-23 | this commit (D-12) | other-agent `place_order` v2 + `get_order_details` executed on local PG 16.15 clone (`uniquelo_clone`) | **battery green** — app-shaped payload round-trip (status `reserved`, email lowercased, `created_at` default ✓, items bound to fn header id, client order_id ignored, absent customization NULL), same-id retry → 23505, empty items → 22023, missing email → 22023, generated-id + qty-clamp path, `get_order_details` match/wrong-email/unknown-id → null, legacy anon-INSERT parity |
| 2026-10-23 | dashboard deploy (human) | OA-1 live checks, publishable-key REST — read + rollback probes only, **no rows written** | **PASS** — `place_order` (named args, items `[]`) → 400 `22023` items-guard; `get_order_details` bad uuid → 400 `22P02`, stranger email → 200 `null`; junk-uuid-item order probe → 400 **and** follow-up `get_order_details` → `null` ⇒ header+items rolled back together: **atomic write confirmed on the hosted project** |
| 2026-10-23 | dashboard deploy (human) | OA-2 live checks, publishable-key REST (refusal probes with valid columns; non-matching predicates ⇒ nothing deletable) | **LOCKDOWN NOT IN EFFECT** — `products` / `garments` INSERT passed privilege+RLS and reached table constraints (`23502` not-null) — impossible if the policy-drop block had run; 0-row DELETEs inconclusive (no-policy ⇒ silent 0 rows); embedded catalog reads fine. Only `place_order` v2 is live. Lockdown must be re-pasted + re-run |
| 2026-10-23 | `20260103` interim (D-13) | scope decision — web-agent audit of Admin.tsx write surface + live baseline probes | Admin-Studio anon writes = storage upload + 3 taxonomy upserts (`categories`/`garments`/`collections`); baseline: anon INSERT on `products`/`garments` → 23502 (grants live). Interim file `20260103000000_lockdown_interim.sql` scoped to revoke exactly the six non-Admin tables; post-deploy flip probe (expect 42501/403) pending the owner's paste-run |
| 2026-10-23 | `9c5dcc4` | unit tests (fresh `--rerun-tasks`) | **40 passed / 1 skipped / 0 failures** (41: Router 10, RootStore 14, Bag 7, Catalog 3, LocalPersistence 6, live skipped) |
| 2026-10-23 | `9c5dcc4` | assembleDebug / lintDebug / iOS target | pass / 0 errors, 12 warnings (OA-3 unchanged) / pass |
| 2026-10-23 | `9c5dcc4` | lockdown re-run on **full** `schema.sql` + Supabase-style grants (PG 16.15) | 23 catalogue write policies on nine tables **+ public `product-images` upload policy** dropped; idempotent; anon SELECT retained; anon catalogue writes denied; guest orders/order_items/newsletter and `place_order` still work |
| 2026-10-23 | `9c5dcc4` | system back during/after checkout | regression fixed + 3 tests: submitting cannot pop; back abandons the session; back after success closes it |

> The earlier `8/8` lockdown row was a reduced eight-table fixture. The
> authoritative full-schema result is the `9c5dcc4` row above: the real
> `schema.sql` has **23** write policies across **nine** catalogue tables,
> plus the public storage upload policy.

## Phase 2 notes (next agents read these)

- The god-Store (`state/Store.kt`) is gone. Screens take narrow slices; only
  `App.kt` (the shell) holds `RootStore`. Store split wiring rules live in
  DECISIONS.md D-4; navigation rules in D-3.
- `Tab` enum deleted — bottom bar and routing use `Destination` (sealed). The
  bar iterates the **top-level** `destinationTabs` val — never a companion
  member (landmine 8 / D-11).
- Checkout form retention is keyed by `checkoutSession` **inside**
  `CheckoutScreen` (session passed via `checkout.checkoutSession`) — A8 parity.
- `CatalogStore.warmFromCache()` must be called **after** wiring
  `onCatalogUpdated` (RootStore does this; tests via `AppGraph.createStore`).
- New regression coverage beyond Phase 1: `requestingCheckoutWithEmptyBagIsRejected`,
  `finishOrderLandsOnDiscover`, `failedOrderKeepsBagIntact`,
  `orderCommitClearsBagAndJournal`, full Router suite (10 tests), plus
  system-back checkout guards (`systemBackOnCheckoutAbandonsSessionAndReturnsToTab`,
  `systemBackCannotPopSubmittingCheckout`, `systemBackAfterSuccessClosesCheckoutSession`).
- **System back must go through `RootStore.onBack()`**, never `router.back()`
  directly from the shell: `onBack` routes Checkout through `closeCheckout`,
  which refuses to pop a submitting order and abandons the session on exit.
  `router.back()` alone leaves `checkoutOpen = true`, making checkout
  unreachable until process death (phase-2 audit P2-1).
- Rule 1 already caught its first bug: the A13 migration's first draft filtered
  `pg_policies.permissive = 'YES'` — on PG16 the column is `'PERMISSIVE'`
  text, so the draft silently dropped nothing. The full-schema audit then found
  two scope holes: `garments` (3 write policies) and the public
  `product-images` upload policy; both are now covered.

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
8. **Never build eager lists of sealed `data object`s inside an interface
   companion.** ART/JVM class-init reentrancy: while the companion's clinit
   builds the list, reading `Sub.OBJECT.INSTANCE` re-enters the subclass's own
   (in-progress) init *on the same thread*, which returns the field's current
   value — `null`. The list stays poisoned (`[null, …]`) for the whole process
   and compile-time-exhaustive `when`s crash with
   `NoWhenBranchMatchedException` (A-shape: `BottomBarKt.iconFor` line 59,
   reproduced 6/6 cold launches; fixed via `fix(nav)`, D-11). Keep such lists
   top-level, or wrap in `lazy` — first touch must come *after* the hierarchy
   is fully initialized. Related: `Destination$DefaultImpls` disappears when
   the interface property is abstract instead of defaulted.
9. **The shell must not call `Router.back()` directly for system back.** It
   bypasses `RootStore.onBack()`'s checkout guard: a submitting order can be
   popped, and backing out of confirmation leaves `checkoutOpen = true`, so
   `requestCheckout()` refuses to open again forever. Route every platform-back
   callback through `RootStore.onBack()` (P2-1).
10. **The lockdown migration's table list is a scope claim.** The shipped schema
    has nine catalogue tables; omitting `garments` left its public
    insert/update/delete policies live even though the eight listed tables were
    locked. When the schema gains a catalogue table, add it to the array and
    the `REVOKE` list, then re-run the verification queries (P2-2).
