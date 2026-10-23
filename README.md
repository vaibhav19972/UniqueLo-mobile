# UniqueLo

**Wearable worlds. Stitched stories.** UniqueLo is an Android-first Kotlin
Multiplatform storefront for limited-run embroidered apparel, built with Compose
Multiplatform and backed by a live Supabase project.

The project started as a structural rebuild of a decompiled Nike Android app
(feed → PDP → bag → profile). No Nike code, assets, copy or data are included.
The catalogue, imagery, craft stories and orders now come from the Supabase
schema described below.

## Project status (audited 2026-10-23)

Phase 1 (offline-first state, atomic order SQL) and Phase 2 (typed navigation,
store split, DI, CI, catalogue lockdown SQL) are code-complete and audited. Two
deployment actions remain, both requiring Supabase dashboard access:

- The `place_order` migration is committed but **not applied** to the hosted
  project. Until it is applied, checkout uses the legacy two-call fallback.
- The catalogue-write lockdown is committed and verified on local PostgreSQL,
  but **not applied**. Until it is applied the public write policies remain
  live. See [RLS warning](#rls-warning).

Evidence: [`phase-1-audit.md`](docs/phase-1-audit.md),
[`phase-2-audit.md`](docs/phase-2-audit.md). The shared source of truth is
[`PROJECT_STATE.md`](docs/PROJECT_STATE.md).

## What the MVP does

| Screen | Behaviour |
|---|---|
| **Discover** | Three universe cards with live theme colours, category chips, search across pieces/motifs/crafts, featured edit, two-column grid, newsletter signup |
| **Product** | Photo pager, universe + category, rating/review count, INR price, colour and size selection with live stock, craft story (technique, region, artisan hours, thread), materials/care/fit, customer reviews |
| **Bag** | Variant-aware lines (colour + size + SKU), quantity stepper with stock cap, remove, INR subtotal/total — persisted across launches |
| **Checkout** | Name/email/address form, order summary, writes a real `orders` row plus `order_items` atomically when the RPC is deployed, shows the generated order code |
| **Profile** | Guest profile, order/saved/alerts placeholders, maker call-to-action |

Data is loaded from Supabase on launch (or from the on-disk cache when the
network is down). The app shows a branded loading state only when there is no
cache at all, and a retry card when the project is unreachable and nothing is
available to paint. There is no payment step.

## Architecture (Phase 2)

`AppGraph` is the single composition root. It creates one app-scoped
`RootStore`, owned by `AppStoreViewModel`, and injects the repository, cache,
bag journal and one shared coroutine scope. Screens receive narrow slices
(`CatalogStore`, `BagStore`, `CheckoutStore`, `NewsletterStore`) and navigation
callbacks, never the root.

```
AppGraph (DI)
  └─ RootStore                    ← survives configuration changes
       ├─ CatalogStore            catalogue, filters/search, offline cache
       ├─ BagStore                bag lines + persisted journal
       ├─ CheckoutStore           checkout sessions + order submission
       ├─ NewsletterStore         subscribe state
       └─ Router                  typed back stack over sealed Destination
```

Navigation is a hand-rolled typed `Router` over a sealed `Destination` type
(`Discover`, `Bag`, `Profile`, `Product(slug)`, `Checkout`) instead of route
strings or a navigation library (ADR-3). System back is routed through
`RootStore.onBack()`, which guards checkout: a submitting order cannot be popped,
and backing out of confirmation abandons the session and returns to the tab.
Decision records and invariants live in [`docs/DECISIONS.md`](docs/DECISIONS.md)
and [`docs/CONVENTIONS.md`](docs/CONVENTIONS.md).

## Offline-first behaviour (Phase 1)

The store survives what users actually do to phones, not just what demos do:

- **Rotation / window recreation** — `RootStore` (catalogue, bag, checkout,
  newsletter and router) is owned by the platform ViewModel store
  (`androidx.lifecycle.viewmodel.compose.viewModel` on every KMP target), so the
  current screen and all feature state live through configuration changes and
  window recreation. Process death is covered by the on-disk cache and bag
  journal, not by the ViewModel.
- **Offline start** — the last successfully loaded catalogue is cached as JSON
  (`catalog-cache.json`) and painted instantly on launch; the network refresh
  then runs in the background (stale-while-revalidate). A failed refresh over
  cached content does not show a dead error screen — the feed stays usable and
  a `SyncBanner` offers a retry.
- **Bag journal** — the bag persists as `slug + sku + quantity` (`bag.json`).
  On start, lines rehydrate against the catalogue with quantities clamped to
  live stock. Journal entries the cached catalogue cannot match yet are kept
  and retried against the live catalogue; only a live refresh makes removal
  final.
- **Reads retry** — catalogue GETs retry twice with exponential backoff on
  transient failures; 4xx responses fail fast.
- **Durable writes** — the cache and journal are written to a temporary file
  and atomically renamed, so a crash mid-write leaves the previous file intact.
- Files live in the app-private directory (`filesDir` on Android,
  Application Support on iOS); persistence is silently disabled when no
  directory is available, which is what unit tests use.

## Atomic orders

`orders` + `order_items` used to be two independent REST calls — a failure in
between left a header row with no items. `SupabaseRepository.createOrder` now:

1. Calls the Postgres function `place_order(p_order jsonb, p_items jsonb)`
   first, which writes both tables in one transaction
   (`supabase/migrations/20260101000000_place_order_fn.sql`). The function
   ignores the client-supplied item `order_id` and always attaches items to the
   header it just wrote.
2. Falls back to the legacy two-call flow only when the function is not
   deployed (PostgREST `PGRST202`, HTTP 404/501).
3. On the legacy path, if the items write fails, it makes a best-effort
   compensating delete of the orphaned header row before rethrowing. The delete
   can be a no-op because `orders` has no public `DELETE` policy.

The function deliberately does **not** use `INSERT … RETURNING`: `orders` has
no public `SELECT` policy, and `RETURNING` would fail under RLS for the `anon`
role. It also omits `created_at` from the column list so the table default
applies — `jsonb_populate_record` fills missing keys with `NULL`, it does not
apply defaults.

**Deployment status:** the hosted project returns `PGRST202` for
`/rpc/place_order` (probed 2026-10-23), so checkout currently uses the fallback.
Apply the migration once in the Supabase SQL editor or with `supabase db push`
before treating the atomic path as live.

## UI state preservation

- The Discover scroll position survives tab switches and device rotation.
- PDP colour/size selections survive rotation and tab switches.
- The router back stack (tab, open PDP, open checkout) is store-owned and
  survives rotation.
- The checkout form survives rotation within a checkout session. Finishing,
  backing out or abandoning checkout starts a clean session, so one customer's
  name, email and address do not reappear in the next order.
- These come from a `SaveableStateHolder` keyed by destination plus
  `rememberSaveable` keyed by checkout session — no app-level hacks.

## Screens

<p>
<img src="screenshots/discover.png" width="220" alt="Discover feed" />
<img src="screenshots/detail.png" width="220" alt="Product detail" />
<img src="screenshots/bag.png" width="220" alt="Bag" />
<img src="screenshots/checkout.png" width="220" alt="Checkout" />
</p>

<p>
<img src="screenshots/grid.png" width="220" alt="Product grid" />
<img src="screenshots/profile.png" width="220" alt="Profile" />
</p>

<p>
<img src="screenshots/confirmation.png" width="220" alt="Order confirmation" />
<img src="screenshots/newsletter.png" width="220" alt="Newsletter signup" />
</p>

## Supabase integration

Credentials live in
`composeApp/src/commonMain/kotlin/com/uniquelo/app/data/SupabaseConfig.kt`:

```kotlin
const val URL = "https://heimnfjuwmmmejqcjbbd.supabase.co"
const val PUBLISHABLE_KEY = "sb_publishable_…"
```

This is a **publishable** client key, not a secret or a `service_role` key. It is
safe to ship in an app as long as row level security (RLS) is correct. The
`VITE_` prefixes from the dashboard are web-only; a native client reads the same
URL and key from this config object.

### Reads

| Table | Used for |
|---|---|
| `products` | Catalogue rows; embedded `variants`, `product_images` and `embroidery_details` in one PostgREST query |
| `universes` | Three world cards, taglines, lore and `theme_tokens` colours |
| `categories` | Filter chips |
| `collections` | Collection name on the PDP |
| `motifs` | Motif name/meaning fallback for the craft story |
| `reviews` | Review list and star breakdown on the PDP |

Products without remote image URLs (for example seeded rows that point at
`/images/...` on a separate web host) automatically fall back to the deterministic
generated artwork, so a broken path never leaves a blank card.

### Writes

| Table | Columns written |
|---|---|
| `orders` | Client-generated UUID, customer name/email, `status = "reserved"`, subtotal/shipping/total in paise, `shipping_address` JSON |
| `order_items` | One row per bag line: order id, product slug, variant SKU, name, colour, size, quantity, unit price |
| `newsletter_subscribers` | Email from the Discover footer |

`orders.id` is generated in the app so the same id can be used for
`order_items` without a `SELECT` policy on `orders` (PostgREST cannot return
inserted rows without read access). The preferred write path is the
`place_order` RPC; the legacy fallback is two REST calls, not a database
transaction.

### Currency

`price_cents` is interpreted as **paise**: `149900` renders as `₹1,499`, using
Indian digit grouping (`₹12,34,567`).

### RLS warning

The supplied schema marks the admin write policies as role `public` with
`USING (true)` / `WITH CHECK (true)` on nine catalogue tables (`products`,
`variants`, `product_images`, `embroidery_details`, `universes`, `categories`,
`collections`, `motifs`, `garments`). Anyone with the publishable key can
therefore modify the catalogue. A safe probe (an intentionally invalid
`products` insert) reaches the table's NOT NULL constraint instead of being
rejected by RLS, which confirms the policy is open.

[`supabase/migrations/20260102000000_lock_catalogue_writes.sql`](supabase/migrations/20260102000000_lock_catalogue_writes.sql)
drops every public/anon/authenticated write policy on those tables, revokes
`INSERT`/`UPDATE`/`DELETE` from `anon` and `authenticated`, and removes the demo
policy that allows public uploads to the `product-images` bucket. It keeps
public `SELECT` and the guest order / order-item / newsletter / review `INSERT`
policies, so checkout keeps working (including the `SECURITY INVOKER`
`place_order` function). Admin edits will use `service_role`.

Executed against the real DDL on local PostgreSQL 16, but **still not applied to
the hosted project**, so the hole is live until OA-2 is done. After applying it,
run the verification queries at the bottom of the migration file (expected: no
client write policies on the nine catalogue tables).

## Stack

- Kotlin **2.4.21**, Compose Multiplatform **1.9.3**, AGP **8.13.2**, Gradle **8.14.3**
- Targets: `androidTarget`, `iosX64`, `iosArm64`, `iosSimulatorArm64`
- UI is 100% `commonMain`; Android supplies the activity, manifest, resources and back-handler actual
- Ktor **3.6.0** + kotlinx.serialization for PostgREST; CIO on Android, Darwin on iOS
- JetBrains **lifecycle-viewmodel-compose 2.9.6** (KMP) owns `RootStore` across rotations
- **okio** for the on-disk catalogue cache and bag journal (deterministic, unit-testable with `FakeFileSystem`)
- Coil **3.4.0** + `coil-network-ktor3` for remote product photography
- No Supabase SDK: the MVP needs six catalogue reads and three writes, so the REST surface is explicit and small
- Generated `ProductArtwork` fallback keeps the app presentable without image assets
- Hand-drawn `UiIcon` set; no icon dependency

## Structure

```
composeApp/src/
  commonMain/kotlin/com/uniquelo/app/
    App.kt                         navigation, saveable state holder, image loader
    model/Product.kt               domain models (serializable), cart line, INR formatting
    data/SupabaseConfig.kt         project URL + publishable key
    data/SupabaseDtos.kt           PostgREST DTOs and insert payloads
    data/SupabaseRepository.kt     reads with retry, atomic order write, newsletter
    data/SnapshotCache.kt          atomic disk cache of the catalogue (offline-first)
    data/BagPersistence.kt         atomic persisted bag journal (slug/sku/quantity)
    data/LocalFiles.kt             expect/actual app-private directory (+ android/ios actuals)
    data/Mappers.kt                DTO → domain mapping
    data/SupabaseHttpClient.kt     Ktor client, RLS-safe headers, timeouts
    navigation/Destination.kt      sealed destinations + bottom-bar tab list
    navigation/Router.kt           typed back stack
    state/AppGraph.kt              composition root (hand-rolled DI)
    state/RootStore.kt             app-scoped facade + cross-store flows
    state/CatalogStore.kt          catalogue, filters/search, offline cache
    state/BagStore.kt              bag lines + persisted journal
    state/CheckoutStore.kt         checkout sessions + order submission
    state/NewsletterStore.kt       subscribe state
    state/AppStoreViewModel.kt     owns RootStore and cancels its scope
    ui/components/                 artwork, remote image, icons, card, bottom bar
    ui/screens/                    Discover, Product, Bag, Checkout, Profile
  androidMain/                     MainActivity, CIO engine, manifest, theme
  iosMain/                         MainViewController, Darwin engine
  commonTest/                      Router/RootStore/BagStore/CatalogStore/
                                   LocalPersistence tests + ignored live test
supabase/migrations/
  20260101000000_place_order_fn.sql       atomic orders SQL
  20260102000000_lock_catalogue_writes.sql  catalogue write lockdown
docs/
  PROJECT_STATE.md, DECISIONS.md, CONVENTIONS.md
  phase-1-audit.md, phase-2-audit.md      findings + evidence
```

## Run it

Requirements: JDK 17+, Android SDK with API 36 and build-tools 36.

```bash
./gradlew :composeApp:assembleDebug
# APK: composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

Or open the `UniqueLo` folder in Android Studio and run the `composeApp` configuration.

Unit tests (offline; a fake repository drives the store):

```bash
./gradlew :composeApp:testDebugUnitTest
```

Live Supabase smoke test (ignored by default, requires network):

```bash
# remove @Ignore from SupabaseLiveTest, then:
./gradlew :composeApp:testDebugUnitTest --tests 'com.uniquelo.app.SupabaseLiveTest'
```

iOS framework (optional, no Xcode shell included):

```bash
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

## Design direction

Bone paper, ink, acid lime and rust; serif display type against wide-tracked
uppercase labels; square-ish 4dp corners. Universe cards borrow their colours
from `universes.theme_tokens`, so each world changes the palette of the feed
without hardcoded theme objects. Batch numbers (`Batch 01 / 50`) are treated as
part of the product identity.

## Verification

- `:composeApp:assembleDebug` — passes
- `:composeApp:testDebugUnitTest` — 40 passed, 1 skipped, 0 failures (41 tests:
  Router 10, RootStore 14, BagStore 7, CatalogStore 3, LocalPersistence 6, live
  test skipped)
- `:composeApp:lintDebug` — 0 errors, 12 warnings (dependency/version updates
  and the v26 adaptive-icon folder; see OA-3)
- Live catalogue decode verified against the project: 10 products, 3 universes,
  variants, remote images, embroidery details and reviews map correctly
- `:composeApp:compileKotlinIosSimulatorArm64` — passes with the Darwin engine
- `place_order` executed against a local PostgreSQL 16 instance with the real
  `orders` / `order_items` schema and anon-style RLS: `created_at` default is
  applied, items attach to the header, empty payloads roll back, and the
  function still works after the catalogue lockdown
- Catalogue lockdown executed against a local PostgreSQL 16 database built from
  the real `schema.sql` DDL plus Supabase-style grants: 23 write policies on
  nine catalogue tables and the public image-upload policy dropped; re-run is
  idempotent; anon SELECT retained; anon catalogue writes denied; guest order,
  order-item and newsletter inserts still allowed
- Android emulator smoke test on API 36: live feed, product grid, PDP photo
  pager, size selection, add to bag, checkout form, `orders` + `order_items`
  inserts, confirmation code, empty bag, newsletter insert, search and profile
- The earlier smoke test created a reservation for `qa@uniquelo.app` (code
  `UL-9FB62F47`) and one newsletter row in the live project. Remove them from
  the dashboard if you want a pristine dataset.

## Open actions / next gates

- [ ] **OA-1** — apply
      `supabase/migrations/20260101000000_place_order_fn.sql` and verify
      `/rpc/place_order` no longer returns `PGRST202`.
- [ ] **OA-2** — apply
      `supabase/migrations/20260102000000_lock_catalogue_writes.sql`, then run
      its verification queries in the dashboard.
- [ ] **OA-3** — revisit lint's Kotlin analyser when the toolchain moves to
      AGP 9.x (currently a Kotlin 2.2 reader against 2.4.21 metadata).
- [ ] **D-6** — add server-trusted pricing/stock validation before real
      payments (the client currently supplies the unit price).
- [ ] **D-7** — add retry-safe order ids at payment integration (a timeout
      after commit can currently duplicate an order if the client retries).
- [ ] **Phase 3** — design elevation: Uniqlo-inspired redesign, dark theme,
      brand fonts, shared-element card → PDP transition, accessibility.

See [`docs/PROJECT_STATE.md`](docs/PROJECT_STATE.md) for status and
[`docs/phase-2-audit.md`](docs/phase-2-audit.md) for this audit's findings and
evidence.

## Deliberately out of scope

- Authentication, accounts and persisted order history
- Payments, tax calculation, shipping rates and inventory decrement
- Server-side price recomputation and coupon validation
- Review submission (reads are wired; the public insert policy exists)
- Admin/CMS UI, real newsletter double opt-in
- Image upload and relative-path rewriting for web-hosted seed images
- iOS Xcode app shell (the KMP target and entry point are ready)

## Reference / legal

The reference APK was analysed statically and is not executed or redistributed
here. No Nike source, assets, strings or trademarks are included. UniqueLo is an
independent demo; use the reference only for analysis you are authorised to do.
