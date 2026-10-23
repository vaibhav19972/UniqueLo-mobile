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
| D-11 | Accepted | Bottom-bar tab list lives in a **top-level** `destinationTabs` val — never inside `Destination`'s companion (and `label` is abstract, not defaulted): ART class-init reentrancy poisons companion-built singleton lists with `null` | landmine 8 |
| D-12 | Accepted | Adopt backend-agent's `place_order` **v2** + `get_order_details` verbatim (SECURITY DEFINER, pinned search_path, email required, generated-uuid fallback, item fallbacks + quantity clamp) — reviewed + executed on local PG16; Android app contract unchanged; legacy fallback stays | OA-1, A12/A2, landmines 1/2 |

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

**System-back routing (phase-2 audit).** The shell calls `RootStore.onBack()`,
never `Router.back()` directly. `onBack` sends Checkout through
`closeCheckout()`, which refuses to pop while an order is submitting and
abandons the session (clearing `checkoutOpen`) on every other exit. Direct
router pops left the session flag set, making checkout unreachable until process
death (P2-1). Screens that own a guarded close (checkout back button) keep using
it; platform back and screen back now share one guarded path.

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

**Scope amendment (phase-2 audit P2-2).** The first draft covered eight
catalogue tables and matched only `roles = {public}`. The real schema has a
ninth catalogue table (`garments`, 3 write policies) and a public
`product-images` upload policy; dashboard-created policies may also name
`anon`/`authenticated` explicitly. The migration now drops write policies for
any of those three roles across all nine tables, revokes from both client roles
and drops the upload policy (guarded, because bare local PG has no `storage`
schema). Public SELECT and guest order/order-item/newsletter/review INSERT
remain. Admin writes use `service_role`.

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

## D-12 — place_order v2 + guest order lookup (Accepted, pre-Phase-3)

**Decision.** The backend agent's hardened `place_order(p_order, p_items)` and
new `get_order_details(p_order_id, p_email)`, shipped **verbatim** into
`supabase/migrations/20260101000000_place_order_fn.sql` (v1 in the same file's
history was never deployed, so the migration number stays).

**Why.** The hosted backend has no authentication (guest-only web/mobile), so
the write path must be callable by `anon` safely. v2's `SECURITY DEFINER`
+ `search_path = public, pg_temp` makes the atomic write independent of anon
INSERT policies; client-supplied UUIDs still give PK-level retry dedupe;
missing email is rejected; item fields get fallbacks and quantity ≥ 1.
`get_order_details` is the sanctioned read into `orders` (no public SELECT,
landmine 2) gated by email match — usable by a future web client for order
status; the Android app still doesn't call it (Phase 4 candidate).

**Verification (Rule 1).** Live hosted columns introspected via read-only REST
probes; SQL executed on local PG 16.15 clone `uniquelo_clone`: full battery
(documented in PROJECT_STATE verification history). Deployed-parity checks the
human runner should see: `POST /rest/v1/rpc/place_order` with `{}` →
**400 `22023`** (not `PGRST202`), matching the items-first guard order.

**Limits kept from v1 contract.** `status` remains client-suggested inside the
app's trusted shape ('reserved'); server-owned pricing/validation stays with
D-6/D-7 (payments trigger). Response body remains unused by the Android app —
extra response fields are safe. Known nuance: only `id` falls back on empty
string; other text fields treat '' as a value (harmless for our app, which
always sends catalogue-backed strings).
