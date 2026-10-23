# UniqueLo

**Wearable worlds. Stitched stories.** UniqueLo is an Android-first Kotlin
Multiplatform storefront for limited-run embroidered apparel, built with Compose
Multiplatform and backed by a live Supabase project.

The project started as a structural rebuild of a decompiled Nike Android app
(feed → PDP → bag → profile). No Nike code, assets, copy or data are included.
The catalogue, imagery, craft stories and orders now come from the Supabase
schema described below.

## What the MVP does

| Screen | Behaviour |
|---|---|
| **Discover** | Three universe cards with live theme colours, category chips, search across pieces/motifs/crafts, featured edit, two-column grid, newsletter signup |
| **Product** | Photo pager, universe + category, rating/review count, INR price, colour and size selection with live stock, craft story (technique, region, artisan hours, thread), materials/care/fit, customer reviews |
| **Bag** | Variant-aware lines (colour + size + SKU), quantity stepper with stock cap, remove, INR subtotal/total |
| **Checkout** | Name/email/address form, order summary, writes a real `orders` row plus `order_items`, shows the generated order code |
| **Profile** | Guest profile, order/saved/alerts placeholders, maker call-to-action |

## Offline-first behaviour (Phase 1)

The store survives what users actually do to phones, not just what demos do:

- **Rotation / window recreation** — the `Store` is owned by the platform
  ViewModel store (`androidx.lifecycle.viewmodel.compose.viewModel` on every
  KMP target), so the catalogue, bag and open product page live through
  configuration changes.
- **Offline start** — the last successfully loaded catalogue is cached as JSON
  (`catalog-cache.json`) and painted instantly on launch; the network refresh
  then runs in the background (stale-while-revalidate). A failed refresh over
  cached content does not show a dead error screen — the feed stays usable and
  a `SyncBanner` offers a retry.
- **Bag journal** — the bag persists as `slug + sku + quantity`
  (`bag.json`); on start, lines rehydrate against the catalogue with
  quantities clamped to live stock and vanished sizes/products dropped. The
  journal is kept until either the cached catalogue can validate the lines or
  a live refresh confirms removal.
- **Reads retry** — catalogue GETs retry twice with exponential backoff on
  transient failures; 4xx responses fail fast.
- Files live in the app-private directory (`filesDir` on Android,
  Application Support on iOS); persistence is silently disabled when no
  directory is available, which is what unit tests use.

## Atomic orders

`orders` + `order_items` used to be two independent REST calls — a failure in
between left a header row with no items. `SupabaseRepository.createOrder` now:

1. Calls the Postgres function `place_order(p_order jsonb, p_items jsonb)`
   first, which writes both tables in one transaction
   (`supabase/migrations/20260101000000_place_order_fn.sql` — run it once in
   the dashboard/CLI).
2. Falls back to the legacy two-call flow only when the function is not
   deployed (HTTP 404/PGRST202).
3. On the legacy path, if the items write fails, it compensates by deleting
   the orphaned header row before rethrowing.

## What the MVP does (screens)

| Screen | Behaviour |
|---|---|
| **Discover** | Three universe cards with live theme colours, category chips, search across pieces/motifs/crafts, featured edit, two-column grid, newsletter signup |
| **Product** | Photo pager, universe + category, rating/review count, INR price, colour and size selection with live stock, craft story (technique, region, artisan hours, thread), materials/care/fit, customer reviews |
| **Bag** | Variant-aware lines (colour + size + SKU), quantity stepper with stock cap, remove, INR subtotal/total — persisted across launches |
| **Checkout** | Name/email/address form, order summary, writes a real `orders` row plus `order_items` atomically, shows the generated order code |
| **Profile** | Guest profile, order/saved/alerts placeholders, maker call-to-action |

### UI state preservation

- The Discover scroll position survives tab switches and device rotation.
- PDP colour/size selections and the checkout form survive rotation.
- These come from a `SaveableStateHolder` keyed by destination plus
  `rememberSaveable` — no app-level hacks.

Data is loaded from Supabase on launch (or from the on-disk cache when the
network is down). The app shows a branded loading state only when there is no
cache at all, and a retry card when the project is unreachable and nothing is

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

`orders.id` is generated in the app so the same id can be used for `order_items`
without a `SELECT` policy on `orders` (PostgREST cannot return inserted rows
without read access). The order and its items are two REST calls, not a database
transaction.

### Currency

`price_cents` is interpreted as **paise**: `149900` renders as `₹1,499`, using
Indian digit grouping (`₹12,34,567`).

### RLS warning

The supplied schema marks the admin write policies as role `public` with
`USING (true)` / `WITH CHECK (true)` on catalogue tables such as `products`,
`variants`, `universes` and `collections`. Anyone with the publishable key can
therefore modify the catalogue. Before production, restrict those policies to an
authenticated admin role and keep only the intended public `SELECT` and
order/newsletter `INSERT` policies.

## Stack

- Kotlin **2.4.21**, Compose Multiplatform **1.9.3**, AGP **8.13.2**, Gradle **8.14.3**
- Targets: `androidTarget`, `iosX64`, `iosArm64`, `iosSimulatorArm64`
- UI is 100% `commonMain`; Android supplies the activity, manifest, resources and back-handler actual
- Ktor **3.6.0** + kotlinx.serialization for PostgREST; CIO on Android, Darwin on iOS
- JetBrains **lifecycle-viewmodel-compose 2.9.6** (KMP) owns the `Store` across rotations
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
    data/SnapshotCache.kt          disk cache of the catalogue (offline-first)
    data/BagPersistence.kt         persisted bag journal (slug/sku/quantity)
    data/LocalFiles.kt             expect/actual app-private directory (+ android/ios actuals)
    data/Mappers.kt                DTO → domain mapping
    data/SupabaseHttpClient.kt     Ktor client, RLS-safe headers, timeouts
    state/Store.kt                 catalogue, filters, bag, checkout, newsletter
    ui/components/                 artwork, remote image, icons, card, bottom bar
    ui/screens/                    Discover, Product, Bag, Checkout, Profile
  androidMain/                     MainActivity, CIO engine, manifest, theme
  iosMain/                         MainViewController, Darwin engine
  commonTest/                      StoreTest, LocalPersistenceTest, ignored live test
supabase/migrations/
  20260101000000_place_order_fn.sql  atomic orders SQL (apply once)
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

## Structure

```
composeApp/src/
  commonMain/kotlin/com/uniquelo/app/
    App.kt                         root navigation, checkout overlay, image loader
    model/Product.kt               domain models, cart line, INR formatting
    data/SupabaseConfig.kt         project URL + publishable key
    data/SupabaseDtos.kt           PostgREST DTOs and insert payloads
    data/SupabaseRepository.kt     reads, order/newsletter writes, UUIDs
    data/Mappers.kt                DTO → domain mapping
    data/SupabaseHttpClient.kt     Ktor client, RLS-safe headers, timeouts
    state/Store.kt                 catalogue, filters, bag, checkout, newsletter
    ui/components/                 artwork, remote image, icons, card, bottom bar
    ui/screens/                    Discover, Product, Bag, Checkout, Profile
  androidMain/                     MainActivity, CIO engine, manifest, theme
  iosMain/                         MainViewController, Darwin engine
  commonTest/                      StoreTest + ignored live integration test
```

## Design direction

Bone paper, ink, acid lime and rust; serif display type against wide-tracked
uppercase labels; square-ish 4dp corners. Universe cards borrow their colours
from `universes.theme_tokens`, so each world changes the palette of the feed
without hardcoded theme objects. Batch numbers (`Batch 01 / 50`) are treated as
part of the product identity.

## Verification

- `:composeApp:assembleDebug` — passes
- `:composeApp:testDebugUnitTest` — 21 passed, 1 skipped (the ignored live test);
  includes persistence round-trips (`LocalPersistenceTest`) and offline/bag-journal behaviour (StoreTest)
- `:composeApp:lintDebug` — 0 errors, 8 warnings (dependency/version updates and
  the v26 adaptive-icon folder)
- Live catalogue decode verified against the project: products, variants, remote
  images, embroidery details, universes and reviews all map correctly
- `:composeApp:compileKotlinIosSimulatorArm64` — passes with the Darwin engine
- Android emulator smoke test on API 36: live feed, product grid, PDP photo
  pager, size selection, add to bag, checkout form, real `orders` + `order_items`
  inserts, confirmation code, empty bag, newsletter insert, search and profile
- The smoke test created a reservation for `qa@uniquelo.app` (code `UL-9FB62F47`)
  and one newsletter row in the live project. Remove them from the dashboard if
  you want a pristine dataset.

## Deliberately out of scope

- Authentication, accounts and persisted order history
- Payments, tax calculation, shipping rates and inventory decrement
- Review submission (reads are wired; the public insert policy exists)
- Admin/CMS UI, real newsletter double opt-in
- Image upload and relative-path rewriting for web-hosted seed images
- iOS Xcode app shell (the KMP target and entry point are ready)

## Reference / legal

The reference APK was analysed statically and is not executed or redistributed
here. No Nike source, assets, strings or trademarks are included. UniqueLo is an
independent demo; use the reference only for analysis you are authorised to do.
