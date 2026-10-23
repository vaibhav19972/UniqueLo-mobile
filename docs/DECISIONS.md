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
| D-13 | Accepted | **Interim partial lockdown**: revoke the 6 core catalogue tables now; PRESERVE storage `product-images` uploads + `categories`/`garments`/`collections` writes — the web Admin Studio still runs on the publishable key; full lockdown (D-5/OA-2) completes when Auth is wired | OA-5, P2-2, A13 → superseded same-day by D-15 |
| D-14 | Accepted | **The web repo is the backend source of truth**: mobile owns no DDL. Nine catalogue + four operational tables; reads via PostgREST embeds; writes via `place_order`/newsletter; `embroidery_details` stays one-to-one (UNIQUE). Schema changes land web-first, then mobile. | D-12, D-13, PROJECT_STATE § Backend contract |
| D-15 | Accepted | **Authenticated-admin catalogue lockdown** (closes D-5/D-13/OA-2/OA-5): with the Admin Studio on Supabase Auth, catalogue writes are `authenticated`-only — nine-table policy drop + anon revoke + grants/policies + storage upload moved public → `authenticated`; anon keeps read-only storefront access. Opens OA-6 (`authenticated` is not yet an admin gate) | OA-6; OA-2/OA-5 closed |

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

**Deployment timing superseded by D-13.** The scope above still stands, but the
full file must not be deployed while the web Admin Studio writes with the
publishable key. The interim revoke runs first (OA-2); the full file becomes
the OA-5 completion artifact.

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
(documented in PROJECT_STATE verification history). **Deployed and
live-verified on the hosted project, 2026-10-23** — items-first guard
confirmed via named-args call (`{"p_order":{},"p_items":"[]"}` → 400
`22023`); correspondence probe (junk item uuid inside a valid order + items
array → 400 `22P02` with the header lookup afterwards returning `null`) proved
the header+items transaction rolls back atomically on the live deployment.
Note for future verifiers: a bare `{}` body binds **no** arguments and yields
`PGRST202` — that is expected, not a deploy failure.

**Limits kept from v1 contract.** `status` remains client-suggested inside the
app's trusted shape ('reserved'); server-owned pricing/validation stays with
D-6/D-7 (payments trigger). Response body remains unused by the Android app —
extra response fields are safe. Known nuance: only `id` falls back on empty
string; other text fields treat '' as a value (harmless for our app, which
always sends catalogue-backed strings).

## D-13 — Interim partial lockdown (Accepted, pre-Phase-3)

**Decision.** The full catalogue lockdown (D-5/OA-2, `20260102000000_`) is
**PAUSED** — do not deploy until Supabase Auth / an admin gate exists. The
hosted project runs the interim revoke in
`supabase/migrations/20260103000000_lockdown_interim.sql` instead: revoke
INSERT/UPDATE/DELETE on the six core tables (`products`, `variants`,
`product_images`, `embroidery_details`, `universes`, `motifs`) from
`anon, authenticated`.

**Why.** The web repo's Admin Studio (`src/pages/Admin.tsx`) performs
product-photography uploads into the `product-images` bucket and taxonomy
upserts (`categories`, `garments`, `collections`) under the **public
publishable key**, because Supabase Auth does not exist yet. The full lockdown
drops the upload policy and revokes taxonomy grants, breaking both live
business flows. The interim protects the commercially sensitive core (product
content, prices, stock, embroidery) while preserving exactly the paths Admin
Studio writes. Accepted residual risks until OA-5 completes the lockdown:
anonymous uploads into the bucket (file-hosting abuse on project quota) and
taxonomy defacement (labels/imagery via `categories.image`,
`collections.cover_image`) remain possible — cosmetic/abuse-level, not
buyer-facing transactional data. Baseline probes (2026-10-23) confirmed grants
were live pre-run (anon INSERT on `products`/`garments` → `23502`). A later
re-probe the same day returned `201` for a type-valid anon `garments` insert
(the probe row was deleted immediately), confirming the interim had **not** been
applied yet.

**Completion trigger (OA-5).** The moment `/admin` authenticates (Supabase
Auth or moved to service-role credentials), deploy the full `20260102` file in
full/idempotent — it supersedes the interim's revokes, drops all permissive
demo policies and the upload policy; Admin-Studio anon writes then fail **by
design** and the flow must move to the authenticated role. The full file stays
in the repo as the completion artifact with a DO-NOT-DEPLOY-until-then banner.

> **Completed 2026-10-23.** The trigger fired the same day: the Admin Studio
> moved to Supabase Auth and the lockdown shipped as the authenticated-admin
> variant (D-15, `20260104000000_lockdown_auth_admin.sql`). OA-2/OA-5 closed;
> both D-13 residual risks (anonymous uploads, taxonomy defacement) are shut.

## D-14 — The web repo owns the backend schema (Accepted, pre-Phase-3)

**Decision.** `/Users/apple/MySpace/UniqueLo` is the single source of truth for
the Supabase schema, migrations, seeds, RLS and admin write paths. The mobile
repo consumes that backend through PostgREST and does **not** create tables,
columns, policies or functions. Backend changes are web-first: edit and execute
them in the web repo, apply them to the hosted project, then update the mobile
DTOs/mappers/docs in the same change set.

**Contract.**

- **13 tables.** Nine catalogue (`universes`, `motifs`, `categories`,
  `garments`, `collections`, `products`, `variants`, `product_images`,
  `embroidery_details`) plus four operational (`reviews`,
  `newsletter_subscribers`, `orders`, `order_items`).
- **Reads.** `universes`, `categories`, `collections`, `motifs`, `reviews`, and
  `products` with `variants`, `product_images`, `embroidery_details` embedded.
- **Writes.** `rpc/place_order` (preferred), legacy `orders` + `order_items`
  (fallback only), `newsletter_subscribers`.
- **Cardinality invariant.** `embroidery_details.product_slug` stays UNIQUE so
  PostgREST embeds an object; the mobile DTO depends on it (landmine 11).

**Why.** Two clients share one database. A mobile-side migration would fork the
schema, make RLS/lockdown scope unverifiable, and let the two storefronts drift.
The web repo already holds the DDL, production seed and the Admin Studio code
whose write paths determine what the lockdown may revoke.

**Consequences.**

1. The mobile lockdown table list and `REVOKE` list mirror the web schema; a new
   catalogue table is a two-repo change.
2. The hosted project can lag the web repo. Record live drift in
   `PROJECT_STATE.md` § Backend contract instead of assuming `schema.sql` is
   deployed (current example: `products.intensity`).
3. The web repo may contain uncommitted user work. Mobile tasks read it but
   never overwrite it.
4. If a backend change requires a mobile code change, the mobile commit cites
   the web-side change and does not claim to have applied it.

## D-15 — Authenticated-admin catalogue lockdown (Accepted, 2026-10-23)

**Decision.** The OA-5 trigger fired: the web Admin Studio now authenticates
its staff (Supabase Auth) instead of writing under the bare publishable key.
The lockdown completion shipped as
`supabase/migrations/20260104000000_lockdown_auth_admin.sql`: drop the
remaining public/anon write policies on all nine catalogue tables, revoke
write grants from `anon`, grant them to `authenticated`, recreate per-table
authenticated write policies, and move storage from the public upload policy
to authenticated `Admin Upload` / `Admin Modify Product Images` policies. The
D-13 interim (`20260103`) and the no-auth draft (`20260102`) remain as replay
history — on a fresh environment the order `02 → 03 → 04` composes to the
current hosted state.

**Why.** Completes D-5 as designed and shuts both residual risks D-13 had
accepted: anonymous uploads into `product-images` and anonymous taxonomy
defacement are impossible while anon keeps read-only storefront access.
Live verification (publishable-key REST, nothing written): catalogue reads
9/9 `200`; anon `orders` read → 0 rows; anon INSERT refusal battery →
`42501 permission denied` on all nine catalogue tables.

**Known gap (OA-6).** `authenticated` means *any signed-in user*, not a
verified admin: catalogue policies are `using (true) with check (true)` and
the storage policies check only `bucket_id` — an ordinary customer account
would get full catalogue write power. Gate these policies by an admin claim
or allowlist (custom JWT claim / user-type check inside the policy
predicates) before public sign-up ships.
