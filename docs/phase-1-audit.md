# Phase 1 audit — UniqueLo

**Date:** 2026-10-23
**Scope:** commits `df5459f`, `9cf22b7`, `dad79e5`, `1972e3c` (offline-first
store, atomic orders, UI state preservation, docs) plus a doc/code consistency
pass.
**Verdict:** Phase 1 is functionally complete after the fixes below. One
deployment action and one security decision remain before Phase 2 is declared
green.

## Method

- Read every Phase 1 code, SQL, test and doc change against the baseline commit
  `a651be8`.
- Ran the Android unit suite, Android build, iOS compiler target and lint.
- Ran the ignored live catalogue test against the hosted project.
- Executed `place_order` against a local PostgreSQL 16 cluster using the real
  `orders` / `order_items` DDL, then again as a non-owner `anon` role with the
  project's insert-only RLS policies.
- Probed the hosted PostgREST endpoints safely (no writes to real rows).

## Findings

| ID | Severity | Area | Finding | Status |
|---|---|---|---|---|
| A1 | Critical | SQL | `jsonb_populate_record` does not apply column defaults; `INSERT … SELECT *` wrote `NULL` into `orders.created_at` (NOT NULL), so the RPC would fail on every order | Fixed, verified |
| A2 | Critical | SQL | `INSERT … RETURNING *` requires SELECT visibility; `orders` intentionally has no public SELECT policy, so the RPC failed under RLS | Fixed, verified |
| A3 | High | Offline | Cache-stage bag rehydration cleared the whole journal whenever *any* line matched, losing unmatched lines before the live refresh | Fixed, tested |
| A4 | Medium | Offline | A live refresh cleared unmatched journal entries in memory but did not rewrite the file when the visible bag was unchanged, so they could resurrect next launch | Fixed, tested |
| A5 | Medium | Orders | Legacy compensation only ran for network/5xx failures; a 4xx item failure left an orphaned header | Fixed |
| A6 | Medium | Lifecycle | `Store` coroutine scope was never cancelled when the ViewModel cleared | Fixed |
| A7 | Medium | Lifecycle | `runCatching` in order/newsletter submission and refresh swallowed `CancellationException` | Fixed |
| A8 | Medium | UI state | Checkout form state was retained by the stable `SaveableStateHolder` key, so a completed order's customer details could prefill the next checkout | Fixed, tested |
| A9 | Low | Networking | RPC and write responses were not consumed, so Ktor could not promptly return connections to the pool | Fixed |
| A10 | Low | Persistence | Cache/journal writes truncated in place; a crash mid-write could lose the bag | Fixed (temp file + atomic rename) |
| A11 | Docs | Docs | Duplicate README sections; stale "two REST calls, not a transaction" claim; truncated sentence; wrong pass/warning counts | Fixed |
| A12 | Blocker | Deploy | `place_order` is **not deployed** to the hosted project (`PGRST202`); checkout currently uses the legacy path | Open — action needed |
| A13 | High (security) | RLS | Catalogue admin write policies are still public; a probe confirmed anon reaches table constraints instead of being blocked | Open — Phase 2 decision |
| A14 | Phase 2 | Trust | Unit price and quantity are client-supplied; the server does not recompute price or check stock | Open — scope decision |
| A15 | Phase 2 | Reliability | A timeout after a committed order leaves the client unsure; a retry generates a new UUID and can duplicate the order | Open — policy decision |
| A16 | Low | Perf | Cache and journal are read synchronously in the `Store` constructor on the UI thread | Accepted for MVP; revisit at catalogue scale |
| A17 | Low | Tooling | AGP 8.13.2's lint runs a Kotlin 2.2 analyser against Kotlin 2.4.21 metadata and logs metadata errors; lint still reports, but Kotlin checks may be incomplete | Open — revisit with an AGP upgrade |
| A18 | Low | Android | `allowBackup="true"` includes app-private files; today they contain public catalogue data and slug/SKU/quantity only, no PII | Open — revisit with accounts/order history |
| A19 | Info | Orders | Legacy compensation is best-effort: `orders` has no public DELETE policy, so the delete may affect zero rows | Documented |
| A20 | Info | Orders | Order ids use Kotlin `Random`, which is fine for uniqueness/opacity but not a crypto source; no security decision depends on it | Accepted |

### A1/A2 — why the RPC was broken

`jsonb_populate_record(null::public.orders, p_order)` builds a composite with
`NULL` for every key the payload omits. The old `insert … select *` therefore
wrote an explicit `NULL` into `created_at` instead of letting the
`DEFAULT timezone('utc', now())` fire:

```
ERROR: null value in column "created_at" of relation "orders"
       violates not-null constraint
```

Even after that was fixed, `RETURNING *` failed under the project's real RLS
shape because `orders` has only an INSERT policy:

```
ERROR: new row violates row-level security policy for table "orders"
```

The migration now:

- inserts only the columns the client owns, so `created_at` keeps its default;
- takes the order id from `p_order`, not from a returned row;
- forces `order_items.order_id` to the header just written;
- rejects an empty item array and a missing order id;
- returns only `{"id": …}`, which the app ignores.

Verified as a non-owner `anon` role with insert-only policies: header and items
were written, `created_at` was populated, a deliberately mismatched client
`order_id` was overridden, and empty/missing-id payloads rolled back without a
partial row.

### A3/A4 — bag journal semantics

The README promises that journal entries are kept until either the cached
catalogue validates them or a live refresh confirms removal. The old code
cleared the entire pending list as soon as the cache matched anything, and it
did not persist the cleanup if the visible bag happened not to change. The
store now:

- removes only entries that matched;
- carries the rest into the live pass;
- rewrites `bag.json` with visible lines plus unmatched entries on every pass;
- drops unmatched entries for good only after a live refresh;
- removes a line from the journal when the user explicitly removes it.

Two regression tests cover partial matches and the live-drop persistence.

### A8 — checkout session isolation

The form's `rememberSaveable` values were keyed only by the stable
`"checkout"` provider. `SaveableStateHolder` retains removed state, so the next
checkout could restore the previous name/email/address. `Store.checkoutSession`
now increments for each new checkout and is passed as a `rememberSaveable`
input: rotation keeps the session, finishing or backing out starts blank.

## Verification evidence

| Check | Result |
|---|---|
| `./gradlew :composeApp:testDebugUnitTest` | 24 passed, 1 skipped (ignored live test), 0 failures |
| `./gradlew :composeApp:assembleDebug` | Build successful |
| `./gradlew :composeApp:compileKotlinIosSimulatorArm64` | Build successful |
| `./gradlew :composeApp:lintDebug` | 0 errors, 12 warnings (version updates, v26 icon folder) |
| Live catalogue test (un-ignored, then restored) | 10 products, 3 universes; variants, images, embroidery and reviews decoded |
| `place_order` on local PostgreSQL 16, owner role | Header + item committed, `created_at` default applied |
| `place_order` as `anon` + insert-only RLS | Committed; mismatched item `order_id` overridden; empty/missing id rejected |
| Hosted `/rest/v1/rpc/place_order` probe | `404 PGRST202` — function not deployed |
| Hosted `products` invalid-insert probe | `400 23502` (NOT NULL) — RLS allowed the attempt, confirming public write policy |
| Hosted `orders` delete probe (non-existent id) | `204` — no rows affected, no real data touched |
| Secret scan of tracked files | Only the publishable key and its README placeholder; no `service_role`, secret key or `.env` tracked; `local.properties` ignored |

Note on A17: the lint report is valid for resources/manifest/Gradle checks, but
the analyser logged `Module was compiled with an incompatible version of
Kotlin` for Kotlin 2.4.21 artifacts. Do not treat `0 errors` as proof that all
Kotlin static checks ran until the toolchain is aligned.

## Phase 2 gate

**Must do before calling Phase 1 deployed:**

1. Apply `supabase/migrations/20260101000000_place_order_fn.sql` to the hosted
   project, then verify the RPC exists. A safe probe is a POST with `{}`:
   it should return a 400 validation error (function runs) rather than
   `404 PGRST202` (function missing). Run one real checkout and confirm both
   rows land with a `created_at`.
2. Decide who owns the migration deployment (dashboard SQL editor vs CLI) and
   record the applied version.

**Decisions needed for Phase 2:**

3. **Catalogue RLS** — remove the public admin write policies now, or make them
   part of the Phase 2 auth/admin work? Recommended: now, with writes restricted
   to a service role or an authenticated admin claim; keep public SELECT and
   guest order/newsletter INSERT. Because `place_order` is `SECURITY INVOKER`,
   `anon` still needs INSERT on both `orders` and `order_items`; if direct
   `order_items` inserts are revoked later, convert the function to
   `SECURITY DEFINER` with a fixed `search_path` in the same migration.
4. **Server-side pricing/stock** — should `place_order` validate
   `variant_sku`/`price_cents` against `variants` and decrement stock, or stay
   client-trusted for the demo?
5. **Retry/idempotency** — on a timeout after commit, do we accept a possible
   duplicate order, or add a client request id and make `place_order` return the
   existing row for a repeated id?
6. **Phase 2 scope** — auth, order history, payments, admin/CMS, iOS shell?
   The KMP iOS target compiles, but there is no Xcode app shell yet.

## Files changed in this audit

- `supabase/migrations/20260101000000_place_order_fn.sql` — defaults, RLS-safe
  inserts, order-id enforcement, validation
- `composeApp/src/commonMain/kotlin/com/uniquelo/app/state/Store.kt` — journal
  retention, persistence union, cancellation, `close()`, checkout sessions
- `composeApp/src/commonMain/kotlin/com/uniquelo/app/state/AppStoreViewModel.kt`
  — cancel the store scope on clear
- `composeApp/src/commonMain/kotlin/com/uniquelo/app/data/SupabaseRepository.kt`
  — always compensate on legacy item failure, drain responses
- `composeApp/src/commonMain/kotlin/com/uniquelo/app/data/SnapshotCache.kt`,
  `BagPersistence.kt` — atomic temp-file writes
- `composeApp/src/commonMain/kotlin/com/uniquelo/app/ui/screens/CheckoutScreen.kt`,
  `App.kt` — session-keyed form state
- `composeApp/src/androidMain/kotlin/com/uniquelo/app/data/LocalFiles.android.kt`
  — document/suppress the application-context holder warning
- `composeApp/src/commonMain/kotlin/com/uniquelo/app/data/SupabaseHttpClient.kt`
  — comment now matches the CIO engine
- `composeApp/src/commonTest/kotlin/com/uniquelo/app/StoreTest.kt` — journal and
  checkout-session regression tests
- `README.md` — deduplicated and corrected
