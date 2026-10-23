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
| 1 | Defects & safety: rotation/process-death survival, offline-first cache, bag journal, atomic orders, saveable UI state | ✅ code complete & audited; OA-1 + OA-2 deployed and live-verified — authenticated-admin catalogue lockdown live (D-15) |
| 1-audit | Independent audit + fixes (`docs/phase-1-audit.md`) | ✅ `590cd3f` — 24 passed / 1 skipped, verified |
| **2** | **Architecture: type-safe navigation, store split (Catalog/Bag/Checkout/Newsletter), DI graph, CI pipeline** | ✅ `6840046` + `37937a0` + `76f91af` ; docs+SQL `78a5d6b` ; crash fix `ef321c6` ; audit fixes `9c5dcc4` (`docs/phase-2-audit.md`) |
| 3 | Design elevation: Uniqlo-inspired redesign (clean white, minimal type, red accent, LifeWear voice), dark theme, brand fonts, shared-element card→PDP, a11y | ⏳ next — can start now; OA-6 (admin gate on `authenticated`) is the remaining release gate, not a design dependency |
| 4 | Commerce: wishlist, order history (auth), review submission, stock validation, payments decision | ⏳ |
| 5 | Release engineering: signed builds, R8 map, icons/splash, deep links (incl. iOS shell) | ⏳ |

## Open actions (any agent may pick up; mark status when done)

| ID | Source | Action | Status |
|---|---|---|---|
| OA-1 | A12 | ~~Deploy `place_order_fn.sql` **v2**~~ **CLOSED**. Live-verified 2026-10-23 via publishable-key REST (read + rollback probes only, nothing written): named-args call → **400 `22023`** items-guard; `get_order_details` bad uuid → 400 `22P02`, stranger email → 200 `null`; junk-uuid-item probe → 400 **and** follow-up lookup → `null` ⇒ atomic rollback confirmed on the hosted project. Verify-form note: `{}` alone binds no args → `PGRST202` is *expected*, not a failure; use `{"p_order":{},"p_items":"[]"}` | **CLOSED — deployed + live-verified** |
| OA-2 | A13 | ~~Full lockdown now~~ → **shipped 2026-10-23 as the authenticated-admin variant** `20260104000000_lockdown_auth_admin.sql` (D-15) — the Admin Studio moved to Supabase Auth, so the nine-table write policies/grants fell from `anon`, writes were granted to `authenticated`, and storage upload moved public → `authenticated`. Interim `20260103` and no-auth draft `20260102` remain as replay history (order 02 → 03 → 04) | **CLOSED — live-verified**: anon reads 9/9 `200`, `orders` read → 0 rows, anon INSERT refusal battery → `42501` on all nine tables |
| OA-3 | A17 | AGP 8.13.2 lint analyser is a Kotlin 2.2 reader against 2.4.21 metadata — Kotlin static checks inside lint may be incomplete. Revisit when toolchain upgrades to AGP 9.x | OPEN — revisit later |
| OA-4 | A16 | Cache/journal are read synchronously in the Store constructor (UI thread). Acceptable at MVP scale; revisit at catalogue scale | ACCEPTED (recorded) |
| OA-5 | D-13 | ~~Deploy the full lockdown when an admin gate lands~~ **trigger fired 2026-10-23** — Admin Studio authenticated; completion shipped as `20260104` (D-15) rather than the bare-revoke `20260102`, which now stays as replay history | **CLOSED** |
| OA-6 | D-15 | `authenticated` currently holds blanket admin power with no admin predicate: catalogue policies are `using (true) with check (true)`, storage policies check only `bucket_id`. Gate both by an admin claim/allowlist (e.g. custom `user_type` JWT claim inside the policy predicates) **before public sign-up ships** | OPEN — trigger: before anyone besides staff can create accounts |

## Backend contract (shared with the web project)

The mobile app does **not** own database DDL. The authoritative backend is the
web repository:

- `/Users/apple/MySpace/UniqueLo/supabase/schema.sql` — tables, indexes, RLS,
  storage bucket
- `supabase/migrations/20261013_taxonomy_columns.sql` — taxonomy columns
- `supabase/production-seed.sql` / `supabase/seed.sql` — hosted catalogue data
- Hosted project: `https://heimnfjuwmmmejqcjbbd.supabase.co`

Any schema change lands in the web repo first, is applied to the hosted
project, and then is reflected here (DTO/mapping/docs) in the same change set.
Never add mobile-only tables or columns. The web repo may contain uncommitted
user work; mobile tasks must not rewrite it.

**Tables (13).** Nine catalogue tables — `universes`, `motifs`, `categories`,
`garments`, `collections`, `products`, `variants`, `product_images`,
`embroidery_details` — plus `reviews`, `newsletter_subscribers`, `orders`,
`order_items`.

**Mobile surface.** Reads `universes`, `categories`, `collections`, `motifs`,
`reviews`, and `products` embedded with `variants`, `product_images`,
`embroidery_details`. Writes `rpc/place_order` (preferred), the legacy
`orders` + `order_items` pair (fallback only), and `newsletter_subscribers`.
`orders` has no public SELECT; use `get_order_details` if order lookup is ever
needed.

**Live verification (2026-10-23, publishable-key REST, read-only except one
probe row that was deleted):**

| Check | Result |
|---|---|
| Public catalogue reads | `200`; 10 products, 37 variants, 58 images, 10 embroidery rows, 3 universes, 7 motifs, 5 categories, 9 garments, 3 collections, 2 reviews |
| `place_order` v2 | live — empty items → `400 22023`; header+items rollback verified |
| `get_order_details` | live — bad uuid → `400 22P02` |
| `embroidery_details` embed | returned as an **object**, not an array (see invariant below) |
| `products.intensity` | **absent** (`400 42703`) — web taxonomy migration not applied to hosted |
| Interim lockdown (D-13) | **not applied** — anon `garments` INSERT → `201`; probe row `__audit_probe__` deleted immediately, no net data change |

**Fragile invariants (break the mobile app if violated):**

1. `embroidery_details.product_slug` must stay **UNIQUE**. PostgREST then
   embeds it as a one-to-one object; `EmbroideryDto?` expects an object. If the
   constraint is dropped, the embed becomes an array and catalogue decoding
   fails. The web adapter tolerates both shapes; the mobile DTO does not
   (landmine 11).
2. The nine-table inventory feeds the lockdown array and `REVOKE` list. A new
   catalogue table without a lockdown entry reopens anonymous writes.
3. Mobile uses `select=*`; new columns are ignored safely
   (`ignoreUnknownKeys = true`), but new **non-null** columns need DTO
   defaults/mapping before they are used.

**Web-owned drift (does not block mobile):**

- `products.intensity` is in the web migration but absent from the hosted DB;
  the web adapter falls back to `expression_score`, and mobile does not read it.
- Catalogue writes are closed (2026-10-23, D-15): `anon` is read-only;
  catalogue writes and `product-images` uploads require a signed-in
  `authenticated` admin (the Admin Studio). OA-6 gates those writes by an
  admin claim before public sign-up — `authenticated` alone is not an admin
  gate yet.

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

| 2026-10-23 | docs audit | shared-backend consistency pass against `/Users/apple/MySpace/UniqueLo` (`schema.sql`, taxonomy migration, seeds, `data.ts`, `Admin.tsx`) | schema/table/column/embed contract matches mobile DTOs; **9 catalogue + 4 operational tables** confirmed; mobile owns no DDL (D-14). Web-owned drift recorded: hosted lacks `products.intensity` (migration `20261013` unapplied) |
| 2026-10-23 | docs audit | hosted backend probes (publishable key, read-only except one probe row that was deleted) | `place_order` → `22023`; `get_order_details` → `22P02`; `embroidery_details` embeds as object; anon `garments` INSERT → `201` ⇒ interim lockdown not applied; probe row deleted, no net data change |
| 2026-10-23 | `20260104` (D-15) | authenticated-admin lockdown live checks — publishable-key REST, refusal battery only (nothing written) | **PASS** — catalogue reads 9/9 `200` (storefront intact), `reviews` read `200`, `orders` anon read → `200` with **0 rows** (no leak); anon INSERT → `42501 permission denied` on **all nine** catalogue tables (5 via type-valid `{name,slug}` probes, 4 via explicit-null probes against DTO-confirmed columns; was `23502`/`201` pre-lockdown). Storage not probe-tested (stray-object risk) — authenticated path to be proven by the Admin Studio sign-in smoke |

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
11. **`embroidery_details.product_slug` must stay UNIQUE.** PostgREST uses the
    unique constraint to treat the embed as one-to-one and returns an object;
    `ProductDto.embroideryDetails` is a nullable object. Drop the constraint and
    the same query returns an array, so catalogue decoding fails at runtime.
    The web adapter accepts both shapes; mobile does not (D-14).
