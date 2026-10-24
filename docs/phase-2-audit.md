# Phase 2 audit — UniqueLo

**Date:** 2026-10-23
**Scope:** commits `78a5d6b`..`de6b1f8` (typed navigation, store split, DI,
tests split, CI, catalogue lockdown SQL, tab-init crash fix), reviewed against
the Phase 1 audit and the live hosted project. **Fixes:** `9c5dcc4`.
**Verdict:** Phase 2 architecture is sound and the database lockdown is safe to
apply, after the two scope/behaviour fixes below. Phase 3 can start once OA-1
and OA-2 are applied by someone with Supabase dashboard access.

> **Post-audit addendum (2026-10-23, after `fcd8c99`).** Historical snapshot.
> OA-1 is **closed** (v2 live); D-12 replaced the v1 `SECURITY INVOKER` function
> with a `SECURITY DEFINER` one. D-13 **pauses** the full lockdown in favour of
> the interim six-table revoke (OA-2, still not applied; OA-5 completes it) so
> the web Admin Studio keeps working. Statements below that `place_order` is
> undeployed / `SECURITY INVOKER`, or that the full lockdown is the next deploy,
> are superseded. Current state: `PROJECT_STATE.md` § Backend contract.
> Phase 3 design work can start now; OA-2 is a release/security gate, not a
> design dependency.

## Method

- Read every Phase 2 code, test, SQL and doc change against `590cd3f`.
- Exercised the lockdown on a **fresh local PostgreSQL 16.15 database built from
  the real `../backend/schema.sql` DDL**, with Supabase-style `anon` /
  `authenticated` roles and default table grants (not a reduced fixture).
- Verified both before/after states as `anon`: catalogue reads, catalogue
  writes, guest order/order-item/newsletter inserts, and `place_order`.
- Re-ran the whole Android/iOS matrix with `--rerun-tasks` and parsed the JUnit
  XML for exact counts.
- Re-probed the hosted project safely (read + intentionally invalid insert; no
  real rows written).
- Cross-checked README/doc claims against code, tests and the real schema.

## Findings

| ID | Severity | Area | Finding | Status |
|---|---|---|---|---|
| P2-1 | High | Navigation | System back called `Router.back()` directly. Backing out of Checkout popped the overlay but left `checkoutOpen = true`, so `requestCheckout()` then refused to open again until process death; it could also pop a **submitting** order (the on-screen back button was guarded, system back was not). Phase 1 parity was lost. | **Fixed** — `RootStore.onBack()` routes Checkout through `closeCheckout()`; 3 regression tests |
| P2-2 | High (security) | RLS | The lockdown listed eight catalogue tables and omitted **`garments`**, whose 3 public insert/update/delete policies therefore stayed live. It also missed the public **`product-images` upload policy**, and matched only `roles = {public}` (explicit `anon`/`authenticated` policies would survive the leak). | **Fixed** — 9 tables, role-overlap match, upload policy (guarded); verified on PG16 |
| P2-3 | Medium | Docs | README still described `state/Store.kt`, `StoreTest`, 24 tests, an open "Phase 2 gate", and an unmitigated RLS warning. PROJECT_STATE was current; README was not, violating the doc/code consistency contract. | **Fixed** — README rewritten around Phase 2 + audit |
| P2-4 | Low | CI | The "Accept Android licences" step was `yes | command -v sdkmanager … || true`: a no-op that could never fail and never accepted a licence. Misleading, and it would hide a missing SDK. | **Fixed** — removed; added `permissions: contents: read` and `timeout-minutes: 45` |
| P2-5 | Low | Docs | The retention matrix did not declare the router back stack, which Phase 2 newly retains across rotation. | **Fixed** — matrix row + landmine 9 |
| P2-6 | Info | Orders | `place_order` still trusts client prices/stock (D-6) and has no retry idempotency (D-7). Both were already deferred in Phase 2 docs; not regressions. | Tracked — triggers unchanged |
| P2-7 | Info | Abuse | Guest `INSERT` policies remain on `orders`, `order_items`, `newsletter_subscribers` and `reviews` (with `WITH CHECK (true)`). This is by design for the guest-first MVP, but there is no rate limiting/validation of review content, and anyone can spam rows. | Deferred to launch hardening |
| P2-8 | Info | Deploy | Hosted database DDL/policies cannot be inspected without DB credentials. Everything here is verified against `schema.sql`; if the hosted project drifted, OA-2's post-apply queries are the gate. `place_order` is still `PGRST202` and the catalogue write hole is still live. | OA-1 / OA-2 open |

## P2-1 — system back and checkout sessions

The old shell routed platform back through `Store.closeCheckout()`, which
returns early while `OrderState.Submitting`. The Phase 2 shell replaced that
with `store.onBack()` → `router.back()`:

```kotlin
// before
PlatformBackHandler(enabled = store.checkoutOpen || product != null || ...) {
    when { store.checkoutOpen -> store.closeCheckout(); ... }
}

// after (pre-fix)
PlatformBackHandler(enabled = destination != Destination.Discover) {
    store.onBack()          // == router.back()
}
```

Consequences: pressing Android back while an order is in flight pops the
checkout overlay; pressing it on the confirmation screen pops it but leaves
`checkoutOpen` set. The next `requestCheckout()` sees an already-open session
and returns without navigating, so checkout is unreachable until the process is
killed. The on-screen back button still worked, which made this easy to miss in
manual smoke tests.

Fix: `RootStore.onBack()` now intercepts `Destination.Checkout` and delegates to
`closeCheckout()` (submitting guard + `abandon()` + `popIfTop`). The three new
tests cover submitting, confirmed, and abandoned exits. All platform-back
callbacks go through this one method.

## P2-2 — lockdown scope

The first SQL draft was tested against a reduced eight-table fixture, so its
"8/8 policies dropped" claim did not cover the real schema. A full run against
`schema.sql` found:

- **`garments`** is a ninth catalogue table with three public write policies
  (`Allow admin insert/update/delete garments`). It is not read by the current
  app, so omitting it was silent.
- **`storage.objects`** has `Public Upload Product Images` for the public role;
  anyone with the publishable key can upload to the public `product-images`
  bucket. The app only reads images; admin uploads should use `service_role`.
- Matching `'public' = any (roles)` misses policies created as `to anon` or
  `to authenticated` (common in dashboard-created policies). The privilege
  `REVOKE` still blocks writes for those roles, but the policies themselves
  would survive, and the sanity query would not be a complete check.

The migration now drops write policies for any overlap with
`{public, anon, authenticated}` across all nine tables, revokes
`INSERT/UPDATE/DELETE` from `anon` and `authenticated`, and drops the upload
policy inside a `to_regclass('storage.objects')` guard (bare local PG has no
`storage` schema). It deliberately leaves public `SELECT`, guest
order/order-item/newsletter/review `INSERT`, and the `SECURITY INVOKER`
`place_order` path untouched.

### Verified matrix (local PG 16.15, real DDL + Supabase-style grants)

| Step | Result |
|---|---|
| Before lockdown: `anon` insert into `products` and `garments` | succeeded (hole reproduced) |
| Apply migration | no errors |
| Write policies left on the nine catalogue tables (public/anon/authenticated) | 0 |
| `anon` SELECT `products` | allowed |
| `anon` INSERT / UPDATE / DELETE `products` | denied (`42501 permission denied`) |
| `anon` INSERT `garments` | denied after fix |
| `anon` INSERT `orders` / `order_items` / `newsletter_subscribers` | allowed |
| `anon` `select place_order(...)` | succeeded; header + item committed |
| Re-run migration | idempotent (no errors) |
| Public `product-images` upload policy | dropped when a storage schema exists; guard no-ops otherwise |

**Safety assessment:** the script changes only policies and grants, no data. It
is idempotent, the catalogue stays readable, and all guest write paths the app
uses remain available. Apply it in one SQL-editor paste; if a statement fails,
nothing outside that statement is changed and a re-run completes the rest.
After applying, run both queries at the bottom of the file — do not rely on the
old `8/8` reduced-fixture claim.

## P2-3 — documentation consistency

README now matches the Phase 2 code: `RootStore`/feature stores, `AppGraph`,
typed `Router`, the exact test counts, the open OA-1/OA-2 actions and the new
lockdown scope. `PROJECT_STATE.md` gained the router retention row, the
corrected full-schema SQL evidence and landmines 9–10; `DECISIONS.md` records
the back-routing rule (D-3) and the lockdown scope amendment (D-5).

## Verification evidence

| Check | Result |
|---|---|
| `./gradlew :composeApp:testDebugUnitTest --rerun-tasks` | **40 passed / 1 skipped / 0 failures** (41 tests: Router 10, RootStore 14, BagStore 7, CatalogStore 3, LocalPersistence 6, live skipped) |
| `./gradlew :composeApp:assembleDebug` | pass |
| `./gradlew :composeApp:compileKotlinIosSimulatorArm64` | pass |
| `./gradlew :composeApp:lintDebug` | 0 errors / 12 warnings (unchanged OA-3 metadata noise) |
| Lockdown on full `schema.sql`, PG 16.15 | 23 catalogue write policies + 1 storage upload policy dropped; matrix above |
| Hosted `POST /rest/v1/rpc/place_order {}` | `404 PGRST202` — still not deployed (OA-1) |
| Hosted invalid `products` insert | `400 23502` (NOT NULL) — RLS still lets the attempt through (OA-2) |
| Hosted invalid `garments` insert | `400 23502` — confirms the omitted ninth table's public write policy is live on the hosted project (P2-2) |
| Hosted `GET /rest/v1/products` | `200` — public catalogue reads work |

## Phase 3 readiness

Architecture, tests, CI and the DB migration are ready. Nothing in the Phase 2
refactor blocks a design pass. Before Phase 3 starts, the two human deploy
actions (OA-1, OA-2) should be done, and the post-apply verification queries run
against the hosted project.

### Ambiguities / future risks to decide

1. **Hosted schema drift.** We can only verify `schema.sql`. If the hosted
   project has extra catalogue tables or different policy names, the migration
   will not cover them. The verification queries are the only reliable check
   without DB credentials.
2. **Catalogue table inventory is manual.** Any new catalogue table must be
   added to both the array and the `REVOKE` list (landmine 10). Consider moving
   the list to a schema-level naming convention or a generated migration.
3. **Admin path.** The lockdown removes `authenticated` writes too. The future
   Admin Studio must use `service_role` (server-side), never the app key; if it
   instead uses an authenticated admin claim, the migration needs a deliberate
   follow-up policy.
4. **Guest write abuse.** Orders, newsletter and reviews are unthrottled
   `WITH CHECK (true)` inserts. Review content/`verified` are client-supplied.
   Add validation/rate limiting before public launch.
5. **Server-trusted pricing (D-6).** If `place_order` becomes
   `SECURITY DEFINER`, it must pin `search_path` and own explicit grants; today
   it is `SECURITY INVOKER` and depends on anon's retained `INSERT` on
   `orders`/`order_items`.
6. **Idempotency (D-7).** A timeout after commit can duplicate an order on
   retry; decide the request-id contract with payments.
7. **Storage policy naming.** The migration targets the shipped policy name.
   If the dashboard has additional upload policies/buckets, audit them
   separately; the removal is intentionally narrow.

## Files changed by this audit

- `composeApp/src/commonMain/kotlin/com/uniquelo/app/state/RootStore.kt` —
  guarded `onBack()`.
- `composeApp/src/commonTest/kotlin/com/uniquelo/app/RootStoreTest.kt`,
  `TestSupport.kt` — gated order fake + 3 system-back tests.
- `../backend/migrations/20260102000000_lock_catalogue_writes.sql` — garments,
  role-overlap matching, storage upload policy, corrected evidence.
- `.github/workflows/ci.yml` — remove no-op licence step; timeout/permissions.
- `README.md`, `docs/PROJECT_STATE.md`, `docs/DECISIONS.md` — Phase 2 truth.
- `composeApp/src/androidMain/kotlin/com/uniquelo/app/MainActivity.kt` — stale
  comment (`Store` → `AppGraph`).
