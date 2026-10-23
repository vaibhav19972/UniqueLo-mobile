# UniqueLo — Decision log (ADR-lite)

> Append-only. One row per decision, one section per detail. Never edit an
> accepted decision silently — add a superseding entry and link both ways.
> Statuses: **Accepted** · **Deferred** (with trigger) · **Superseded**.

| ID | Status | Decision | Related |
|---|---|---|---|
| D-1 | Accepted | Offline-first catalogue: in-memory store + okio disk cache, stale-while-revalidate, `syncError` banner | A5, phase-1-audit |
| D-2 | Accepted | Orders written atomically via `place_order` Postgres fn, legacy REST fallback kept | A1, A2 |
| D-3 | Accepted | Hand-rolled type-safe `Router` (sealed `Destination`) instead of androidx.navigation multiplatform | OA from audit #4 |
| D-4 | Accepted | Split god-Store into `CatalogStore` / `BagStore` / `CheckoutStore` / `NewsletterStore` behind a thin `RootStore` facade | — |
| D-5 | Accepted | RLS lockdown of catalogue writes ships **now** as migration SQL; deployment is a human dashboard action | A13 → OA-2 |
| D-6 | Deferred | Server-trusted pricing (recompute totals in `place_order`) — **trigger: before real payments** | A14 |
| D-7 | Deferred | Order idempotency (retry-safe order ids) — **trigger: payment integration** | A15 |
| D-8 | Accepted | Hand-rolled DI (`AppGraph`) over a framework (Koin/Hilt); revisit if modules multiply | — |
| D-9 | Accepted | CI = GitHub Actions, macOS runner, full matrix (assemble + unit + lint + iOS target) | — |
| D-10 | Accepted | Android-first; iOS KMP target must always compile, no iOS app shell yet | — |

---

## D-3 — Hand-rolled Router (Accepted, Phase 2)

**Decision.** Navigation truth lives in `navigation/Router` — a
`SnapshotStateList<Destination>` back stack over a sealed `Destination` type
(`Discover`, `Bag`, `Profile`, `Product(slug)`, `Checkout`). Screens receive
navigation callbacks, never a library type.

**Why.** The app has 5 destinations, no deep links, no multi-back-stack. The
JetBrains/androidx navigation multiplatform port would add its own
ViewModel/saveable machinery and lifecycle coupling for zero used features,
while making destination state *less* type-safe to us (route strings/bundles).
A 60-line typed router is fully unit-testable and keeps Compose out of state
logic.

**Adoption triggers** (any one ⇒ revisit, supersede this ADR):
1. Deep links / push-notification navigation (Phase 4/5).
2. Predictive-back gesture UI or multiple back stacks.
3. Destinations needing typed args bundles beyond a slug.

Parity rule: legacy behaviour preserved — back on a tab root returns to
Discover; back from PDP/Checkout pops; bottom bar visible only on tab roots.

## D-4 — Store split (Accepted, Phase 2)

**Decision.** Feature stores own their state; `RootStore` is a thin facade that
wires them, owns the shared `CoroutineScope`, and exposes cross-feature flows.

**Why.** The god-Store blocked narrow screen APIs and made every test construct
world-state. Split rules that keep audit semantics intact:

- One **shared** scope for all stores; `RootStore.close()` cancels it exactly
  once (no orphaned refreshes — audit A6/A7).
- `CatalogStore.warmFromCache()` is called **by** `RootStore` after hooking
  `onCatalogUpdated { snapshot, fromLive -> bag.validateAgainst(...) }` —
  constructor-time events must never fire before the wiring exists.
- Checkout needs bag lines: `RootStore.submitOrder(form)` passes
  `bag.takeForOrder()` + `onCommitted = bag::onOrderCommitted`. Bag is never
  cleared before the order is confirmed.
- Checkout form state remains keyed by `checkoutSession` (A8): a re-request
  while open must not bump the session; closing and reopening must.

## D-5 — RLS lockdown now (Accepted, Phase 2)

**Decision.** `supabase/migrations/20260102000000_lock_catalogue_writes.sql` —
drop public-role write policies on catalogue tables, revoke write privileges.
Writes to catalogue content go through service/admin credentials later.

**Why.** The shipped sample data created ad-hoc permissive policies for every
table; any user of the anon key can deface the store. Deployment is blocked on
human dashboard access (OA-2), so the artifact ships now and PROJECT_STATE
tracks the open action. Until deployed, audit finding A13 remains live.

## D-8 — Hand-rolled DI (Accepted, Phase 2)

**Decision.** `AppGraph.createStore()` is the single construction site:
repository, cache, bag storage, shared scope. `AppStoreViewModel` calls it and
routes `onCleared()` → `RootStore.close()`.

**Why.** One composition root is smaller than any DI framework's setup cost.
Revisit when: multiple ViewModels need scoped graphs, or tests need to swap
more than the three fakes they already can.

## D-9 — CI matrix (Accepted, Phase 2)

`macos-latest` (needs the iOS compile target), JDK 17 Temurin, Gradle caching.
Runs: `assembleDebug`, `testDebugUnitTest`, `lintDebug`,
`compileKotlinIosSimulatorArm64`. Lint's metadata noise is tracked (OA-3) —
the pipeline gates on exit codes only.
