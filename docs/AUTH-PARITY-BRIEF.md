# Mobile auth parity — implementation brief

**For:** Anti-Gravity (or any executing agent)
**Owner decision:** build auth parity on mobile. Accounts are no longer web-only.
**Status:** ready to execute, one blocking prerequisite (§2)
**Date:** 2026-10-31
**Repo:** `mobile/` (independent git repo — see §7.3)

---

## 0. Read this first — the scope is smaller than it looks, and one thing you were probably told is false

The task arrived as "mobile is behind: no auth, no orders, no coupons." Verified against the code, that is
**one-third right**:

| Claimed missing | Reality |
|---|---|
| Auth | ✅ **Genuinely missing.** No session, no token, no GoTrue call. `SupabaseHttpClient.kt` hardcodes `Authorization: Bearer <publishable key>`. |
| Orders + coupon flow | ❌ **Already built.** `QuoteStore` quotes on every bag change; `CheckoutScreen` has a coupon field, inline errors, and an `availableOffers` rail with the `free_shipping` special case. `OrdersScreen` (502 lines) + `OrdersStore` + guest lookup via `get_order_details` all work. |
| Backend support for accounts | ❌ **Already shipped and live.** No migration is needed. See §1.1. |

**A prior analysis claimed mobile leaks shipping revenue** (`shippingCents = 0`, `totalCents = subtotal` in
`createOrder`). **That claim is false.** `20261101_checkout_pricing.sql` rebuilt `place_order` to ignore the
client's money fields entirely and write `shipping_cents` / `discount_cents` / `cod_fee_cents` /
`total_cents` from a server-side `price_cart()` result. Live proof on a ₹1,199 SKU: `shipping_cents: 9900`,
`total_cents: 129800` prepaid, `135700` with COD. Mobile's zeros are inert.

> **Do not "fix" mobile's totals.** Any change that makes client arithmetic authoritative inverts decision
> **D-21** ("server is the only money authority"). Leave `OrderInsert`'s money fields alone except as noted
> in §4.5.

The real work is: **auth, plus the four surfaces auth unlocks.** Do not rebuild orders or coupons.

---

## 1. Verified backend facts — do not re-derive, and do not write migrations

All of the following was checked against the code and the **live hosted project** on 2026-10-31.

### 1.1 `place_order` already attributes orders to the signed-in user

- `create or replace function public.place_order` sets `v_user_id := auth.uid();` at
  `migrations/20261031_customer_accounts.sql:166` and `migrations/20261101_checkout_pricing.sql:729`.
- **No later migration replaces the function.** `20261102` is lifecycle DDL; `20261103` is admin policies.
- Ownership is derived, never supplied — a `p_order.user_id` in the payload is ignored on purpose.

**Consequence:** the moment mobile sends a real user JWT, orders attribute themselves. Order history becomes
a plain PostgREST read. **No RPC change, no schema change, no contract change.**

### 1.2 The account tables and RLS are already deployed

Live probes (HTTP 200, `[]` — a missing column would be `42703`, as `coupons.value_cents` demonstrates):

```
GET /rest/v1/orders?select=id,user_id,coupon_code,discount_cents,shipping_cents,total_cents  → 200 []
GET /rest/v1/orders?select=cod_fee_cents                                                     → 200 []
GET /rest/v1/customer_addresses?select=id                                                    → 200 []
GET /rest/v1/customer_wishlists?select=product_slug                                          → 200 []
```

So `20261031` and `20261101` **are applied to hosted**. Policies in play:

| Table | Policy | Scope |
|---|---|---|
| `orders` | `Users view own orders` | select, `to authenticated`, `user_id = auth.uid()` |
| `order_items` | `Users view own order items` | select, `to authenticated`, via subquery on `orders.user_id` (`20261032`) |
| `customer_addresses` | `Users manage own addresses` | `for all to authenticated`, `auth.uid() = user_id` |
| `customer_wishlists` | `Users manage own wishlist` | `for all to authenticated`, `auth.uid() = user_id` |

`customer_addresses` has a **partial unique index** `uq_customer_addresses_one_default` on
`(user_id) where is_default` — see §5.4.

### 1.3 Signing in will not break catalogue browsing

Storefront SELECT policies in `schema.sql:472–481` are `for select using (true)` with **no `to` clause**, so
they apply to `public` (anon *and* authenticated). `shipping_rules` and `coupons` reads are explicitly
`to anon, authenticated` (`20261101:148,226`). A grep audit for `to anon` found **no** anon-only SELECT
policy on any catalogue table.

⚠️ Still run the definitive check in §9.1 before shipping — grep is not proof.

### 1.4 Live pricing fixtures for tests

`POST /rpc/quote_order` with `variant_sku: "UL-T485-IVORY-M"` (₹1,199):

| payment | subtotal | shipping | cod_fee | total |
|---|---|---|---|---|
| prepaid | 119900 | 9900 | 0 | **129800** |
| cod | 119900 | 9900 | 5900 | **135700** |

`free_shipping_threshold_cents: 149900` · `shipping_rules`: `flat_rate_cents 9900`, `free_above_cents 149900`,
`cod_fee_cents 5900`, `cod_max_units 10`.
Offers returned: `ATELIER10` (percent, 11990), `GEM10` (percent, 11990), `FREESHIP` (free_shipping, 9900).

### 1.5 Already-handled server quirks — do not re-solve

- **KD-1** (`best_coupon.value_cents = 0` for free_shipping): mobile **already** works around it by reading
  `available_offers` and special-casing `discountType == "free_shipping"` in `CheckoutScreen:356–361`.
- **COD fee is already rendered** on mobile: `BagScreen.kt:299` and `CheckoutScreen.kt:576` both emit a
  "COD fee" row when `q.paymentMethod == "cod" && q.codFeeCents > 0`. (A prior analysis claimed this was
  missing — it is not. Do not add a second row.)
- **`status` is safe today**: `confirmed-cod` **is** in the `20261102` CHECK vocabulary. Sending it is legal,
  just redundant (§4.5).

---

## 2. Blocking prerequisite — the email problem (resolve before P1 ships)

Live `GET /auth/v1/settings`:

```json
{ "mailer_autoconfirm": false, "disable_signup": false, "external": { "email": true } }
```

The project has **no custom SMTP**. Supabase's built-in mailer only delivers to organisation team
addresses. Therefore **sign-up cannot complete for a real customer**: they get "check your email," the email
never arrives, and they can never sign in.

Web already handles this honestly (`web/src/stores/auth.ts`): `AuthStatus = 'awaiting_confirmation'`,
`AuthError.isMailerLimit`, and the comment *"HONEST BEHAVIOUR: surface it, never pretend success."*

**Mobile must replicate that honesty — but the decision is not yours to make in code.** Pick one with the
owner before building sign-up:

| Option | Effect | Cost |
|---|---|---|
| **A. Enable `mailer_autoconfirm`** (dashboard toggle) | Password signup works instantly, no email needed | Zero code; disables email verification — acceptable pre-launch |
| **B. Custom SMTP** (Resend/Postmark) | Full confirmation flow, real launch-ready | Ops work, out of scope for this brief |
| **C. Ship sign-in only** | Existing/web-created accounts can sign in on mobile; signup stays web-only | Smallest scope, honest |

**Recommendation: A**, because it is a dashboard toggle and unblocks everything. Onboarding friction
(email verification) can be added later without touching client code.

Whatever is chosen: **sign-in must work end to end regardless.** Only sign-up is gated.

---

## 3. Auth architecture (decided — do not re-litigate)

**Hand-rolled GoTrue over the existing Ktor client. Do not add `supabase-kt`.**

Rationale: `libs.versions.toml` has no Supabase SDK; the data layer is deliberately a thin Ktor + PostgREST
client (`SupabaseRepository`, the comment at line 103 says so explicitly). Pulling in `supabase-kt` brings a
second HTTP stack, its own Postgrest implementation that would invite replacing the existing repository, and
version friction with ktor 3 / kotlinx-serialization. The whole surface is ~150 lines against
`/auth/v1/token?grant_type=password`, `?grant_type=refresh_token`, `/auth/v1/signup`, `/auth/v1/logout`,
`/auth/v1/user`.

### 3.1 The seam: one function, dynamic header

`SupabaseHttpClient.kt` currently does:

```kotlin
defaultRequest {
    header("apikey", SupabaseConfig.PUBLISHABLE_KEY)
    header(HttpHeaders.Authorization, "Bearer ${SupabaseConfig.PUBLISHABLE_KEY}")
}
```

Change the second line to read a **token provider** evaluated per request:

```kotlin
defaultRequest {
    header("apikey", SupabaseConfig.PUBLISHABLE_KEY)          // never changes
    header(HttpHeaders.Authorization, "Bearer ${tokenProvider.accessToken()}")
}
```

Because Ktor evaluates `defaultRequest` on every request, this single edit authenticates **every existing
call** — catalogue reads, `quote_order`, `place_order`, `get_order_details` — with zero call-site changes.

⚠️ `accessToken()` must return the **current** value at call time. A token captured at client-construction
time is stale for the life of the app. This is the #1 way this integration fails.

### 3.2 Token handling — non-negotiable

1. **Store both tokens** (`access_token` **and** `refresh_token`). GoTrue rotates refresh tokens; an
   access-token-only store breaks after ~1 hour.
2. **Single-flight refresh.** On a 401, exactly **one** refresh runs; concurrent callers await the same
   `Mutex`/`Deferred`. Without this, parallel catalogue + quote calls each refresh and **invalidate each
   other's rotated tokens** — a bug that only appears in production.
3. **Refresh path must not use the user token.** The `?grant_type=refresh_token` call carries the
   publishable key as bearer. Exclude auth paths from the dynamic-header rule (or override per request), or
   refresh will 401 forever.
4. **`expectSuccess = false`** (already set) means 401s arrive as *responses*, not exceptions. Parse bodies
   like `{"error":"invalid_grant","error_description":"..."}` and map to the error type — do not assume a
   thrown exception.
5. **Secure storage, expect/actual.** Android: EncryptedSharedPreferences (Keystore-backed). iOS: Keychain.
   Follow the existing `expect/actual` pattern used by `LocalFiles` — but **do not** reuse okio plain-text
   files the way `BagPersistence` does. A refresh token in plaintext on disk is a security defect.

### 3.3 Models to mirror from web

Copy the shape, not the code: `AuthError { code, message, isMailerLimit }` where `isMailerLimit` is true when
the code is `email_address_not_authorized` or the message matches `/only send|not authorized/i`. Statuses:
`idle | loading | awaiting_confirmation | error`.

---

## 4. Work items

Ordered by dependency. Each is independently reviewable.

### 4.1 — Session layer
**New:** `data/AuthApi.kt` (GoTrue calls), `data/SessionStore.kt` (user/session/status/error, restore on
launch), `data/SecureStore.kt` + `SecureStore.android.kt` / `SecureStore.ios.kt`, `data/TokenProvider.kt`.
**Edit:** `SupabaseHttpClient.kt` (the §3.1 seam), `AppGraph.kt` (compose the session store; note its
parameters are consumed by `TestSupport` fakes — every test constructor ripples).

**Acceptance:** sign in → `GET /auth/v1/user` succeeds with the user's token; cold launch restores the
session; access token expiry triggers one refresh and retries once; sign-out clears storage.

### 4.2 — Auth UI + route
`Destination.kt` has **no** auth destination today; `Router` is a simple stack. Add `Destination.Auth`
(or a sheet — decide and state it; web uses a modal + account drawer).
`ProfileScreen.kt:87` already renders the dead string **"Sign in to sync saved pieces"** — that is the entry
point. Add email+password form, sign-in/sign-up toggle, `awaiting_confirmation` state, `isMailerLimit`
message, error display, sign-out affordance.

**Acceptance:** all four states render truthfully; no fake success on signup when `mailer_autoconfirm` is
false; 48dp touch targets and semantics per **D-16**.

### 4.3 — Cloud order history (the reason auth exists)
When signed in, read:

```
GET /orders?select=id,status,total_cents,created_at,order_items(id,product_name,variant_sku,quantity,price_cents)
    &order=created_at.desc
```

RLS scopes it to the caller — mirror `web/src/pages/Orders.tsx:33–38`, and add `.eq('user_id', ...)` there
for index use (see §8). Keep the existing guest lookup (`get_order_details(id, email)`, granted to `anon`)
working for signed-out users, matching web's `/orders` vs `/track-order` split.

**Data-loss risk:** do **not** drop the device-local `LocalOrderSummary` list on sign-in. Mobile orders are
written with a client-generated UUID, so dedupe by `id` is exact. Show guest-local and cloud orders
distinctly (or union-and-dedupe) rather than replacing.

**Acceptance:** sign in on device B → A's signed-in orders appear; guest history survives sign-in; sign-out
hides account orders without deleting guest ones.

### 4.4 — Cloud wishlist with union-merge
Mirror `web/src/stores/wishlist.ts` **exactly** — the semantics are deliberate and load-bearing:

- on sign-in: fetch remote, **union** with local, push the union up (never "server wins" or "local wins";
  either destroys data),
- while signed in: toggle writes through fire-and-forget (local list stays the UI source of truth),
- on sign-out: `resetLocal()` — clear local, cloud copy retained.

`customer_wishlists` is deliberately **not** FK'd to `products`; hide slugs that no longer resolve.

This supersedes **D-18** ("local-only wishlist, no auth").

### 4.5 — Payload hygiene (small, in the same pass)
- **Stop sending `status`** in `OrderInsert`. `20261101` derives `v_payment` from
  `shipping_address.payment_method` (which mobile already sends) and coalesces status from it, so removal is
  safe and closes **KD-6** for this client. Two lines.
- **Leave the money fields** (`subtotalCents`, `discountCents`, `shippingCents`, `totalCents`) as they are —
  they are ignored server-side (§0). Do not add arithmetic.
- **Pass `p_customer_email` when signed in.** `QuoteOrderPayload.customerEmail` already exists and is
  unused. `price_cart` counts per-user coupon limits against both `auth.uid()` and the typed email
  (`20261101:406–437`), so this closes the residual of **parity-gap #9**.
- **Prefill and lock the checkout email** to the account email when signed in — `place_order` binds
  ownership to `auth.uid()` but coupons count against the typed email, so a mismatched email splits identity.

### 4.6 — Saved addresses
`customer_addresses` CRUD (`for all to authenticated`, directly reachable). Surface as a picker in
`CheckoutScreen` that fills the existing free-text fields.

**Trap:** to save a new default you must first
`update customer_addresses set is_default = false where user_id = ... and is_default` — otherwise the partial
unique index throws **23505**. Web does exactly this at `auth.ts:217–225`. Copy the sequence.

**Acceptance:** save two addresses, mark the second default, no error; delete; checkout prefills.

### 4.7 — Optional, cheap, only if time allows
Parity-gaps #3/#4/#5 (`characterIds`, `artStyleSlug`, `editorialImages`) — mobile DTOs are already open
(`ignoreUnknownKeys = true`), so these are additive mapper fields. Not required for auth parity; explicitly
out of the critical path.

---

## 5. Traps — read before writing code

1. **Stale/hardcoded bearer** (§3.1). The single highest-risk mistake.
2. **Refresh stampede** (§3.2#2). Causes intermittent, production-only sign-outs.
3. **Role switch.** A user token moves PostgREST from `anon` to `authenticated`. Read §1.3 and run §9.1 —
   if any catalogue SELECT is anon-only, signed-in users lose the storefront.
4. **23505 on second default address** (§4.6).
5. **Wishlist "server wins"** silently deletes a guest's saved pieces on their first sign-in.
6. **Dropping local orders on sign-in** destroys guest history.
7. **Don't gate `get_order_details` on auth** — it matches on email and must keep serving signed-in customers
   tracking old guest orders.
8. **`AppGraph.createStore` default params** are consumed by `TestSupport` fakes; new stores ripple into
   `RootStore`, `App.kt`, `ProfileScreen`, `CheckoutScreen`, `OrdersScreen`, `WishlistStore` and every test.
9. **iOS.** Keychain needs an `actual`; verify the framework link task compiles
   (`assembleDebugAppleFrameworkForXcode`), not just Android.
10. **`coupons.value_cents` does not exist** (live `42703`). The table uses `discount_type` /
    `discount_value`. Never select the former.

---

## 6. Non-goals — do not touch

- ❌ Client-side price/total arithmetic (violates **D-21**).
- ❌ Any backend migration, RPC, or `CONTRACT.md` change (§1.1).
- ❌ Rebuilding coupons, offers UI, or `QuoteStore` — they exist and work.
- ❌ Adding a "COD fee" row — already rendered (§1.5).
- ❌ Admin Studio / catalogue writes.
- ❌ OTP/SMS auth (deferred by design).
- ❌ Payment gateway integration.
- ❌ SMTP setup (§2 — ops task, separate owner).

---

## 7. Verification gates

Match the bar the quote work was held to: **86 tests / 0 failures, `assembleDebug` + `lintDebug` green on
forced reruns.**

### 7.1 Test harness
Extend `commonTest/TestSupport.kt` (already has `FakeRepository`, `FakeCache`, `FakeBagJournal`,
`FakeWishlistPersistence`, `FakeOrdersPersistence`, `FakeThemePersistence`). Add `FakeAuthApi`,
`FakeTokenStore`. Cover:
sign-in state machine (all 4 statuses) · refresh single-flight (two concurrent 401s → one refresh) ·
`isMailerLimit` mapping · order dedupe by id · wishlist union-not-replace · default-address swap ·
sign-out clears session but keeps guest-local orders.

### 7.2 Commands
```bash
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:testDebugUnitTest
./gradlew :composeApp:lintDebug          # 0 errors (metrics noise OK, OA-3)
./gradlew :composeApp:allTests           # common tests incl. iOS targets
```
Plus iOS framework link check.

### 7.3 Three independent repos — split your commits
`UniqueLo/` root has **no** `.git` (`git log` → exit 128). `backend/.git` and `mobile/.git` are separate
remotes. All work in this brief lands in **`mobile/`** only. Never push unless asked.

### 7.4 Live smoke (manual, on the hosted project)
1. Sign up (or sign in if option C) → session persists across app restart.
2. Add ₹1,199 piece → bag shows 1199 + 99 = **1298**. Toggle COD → **1357** with a COD fee row.
3. Place order → confirmation; row appears in `GET /orders` **with `user_id` set**.
4. Sign in on a second install → same order visible.
5. Sign out → history empty, guest lookup still works, catalogue still loads (**role-switch check**).
6. Save two addresses, second as default → no 23505.
7. Signed-in wishlist toggle → row in `customer_wishlists`; sign in on device B → union appears.

---

## 8. "What's missing on the web?" — the honest answer

**Nothing material.** Directionally the gap runs **web → mobile**; every open row in
`backend/contracts/parity-gaps.md` is mobile-side. Do not invent web work.

Genuine, small, unrelated web items found during this scan (none block mobile auth):

| Item | Location | Severity |
|---|---|---|
| **"Save ₹0" render.** Renders `bestCoupon.value_cents` directly; when the cart is under ₹999 `best_coupon` resolves to `FREESHIP` with `value_cents: 0`. Mobile already works around this; web does not. | `web/src/pages/Checkout.tsx:615` | low — copy the mobile fix |
| Dead `shipping_cents` in the `place_order` payload (ignored since `20261101`). | `web/src/lib/data.ts:1243` | hygiene |
| Order history relies on RLS with no explicit `.eq('user_id', ...)`. | `web/src/pages/Orders.tsx:33` | clarity/index |
| `/track-order` route registered; guest lookup implementation unverified. | `web/src/app/routes.tsx:178` | verify |

Deferred by design, already logged: **KD-3** (`coupon_redemptions` has RLS + zero policies — no admin audit
path), **KD-5** (hardcoded "Tax — included in the atelier price", no `tax_cents` column), **KD-7** (no
status-transition guard; deliberately deferred until the admin UI exists).

---

## 9. Must close before ship

### 9.1 Role-switch proof (highest consequence, cheapest to close)
Grep found no anon-only SELECT policy, but grep is not proof. With service-role access run:

```sql
select tablename, policyname, roles
from pg_policies
where schemaname = 'public' and cmd = 'SELECT'
  and 'anon' = any(roles) and not ('authenticated' = any(roles));
```

Expect **zero rows** on catalogue tables (`products`, `variants`, `product_images`, `universes`,
`categories`, `collections`, `motifs`, `garments`, `embroidery_details`, `reviews`). Any row = a signed-in
customer loses that read.

### 9.2 `order_items` read path
§1.2 lists the policy, but the nested select used in §4.3 was not executed. Smoke-test explicitly: sign in as
a user with one order, run the §4.3 query, expect **rows, not `42501`**. A broken policy here means order
history lists orders and shows zero line items — the exact bug `20261032` was written to fix.

### 9.3 Docs (part of the deliverable, not an afterthought)
This repo treats docs as the contract. In the same change set as the code:

- `backend/contracts/parity-gaps.md` — flip row **#10** from *"open — decision needed"* to
  **closed 2026-10-31**, decision: build auth parity (not web-only). Its own rule: *do not delete rows — the
  log is the value.* Add a row for the client-supplied `status` cleanup.
- `mobile/docs/DECISIONS.md` — new `D-22`: GoTrue transport, hand-rolled over Ktor; supersedes **D-18**
  (local-only wishlist). Note that **D-13/D-15**'s premise ("the hosted backend has no authentication") is
  now stale — web auth exists and `20261031` is deployed.
- `mobile/docs/PROJECT_STATE.md` — phase entry for auth parity.

---

## 10. One-paragraph summary for the executing agent

Mobile auth is real work, but it is **client-only** work: `place_order` already sets `user_id = auth.uid()`
and every account table plus its RLS is live, so no backend change is needed and none should be made.
Coupons and orders **already exist** on mobile — do not rebuild them; the coupon rail even already carries
the `free_shipping` workaround for KD-1. What's missing is a session: scrape the hardcoded bearer out of
`SupabaseHttpClient.defaultRequest`, replace it with a per-request token provider, hand-roll GoTrue against
`/auth/v1`, store both tokens in Keystore/Keychain, and refresh with single-flight. Then thread that session
into the four surfaces it unlocks: account order history (`orders` + nested `order_items`, RLS-scoped),
cloud wishlist (**union**, never replace), saved addresses (clear the old default first or hit 23505), and
an identity-aware quote (pass `p_customer_email`). Leave the money fields alone — the server ignores them,
and "fixing" them inverts D-21. The one thing no mobile code can fix is `mailer_autoconfirm: false` with no
SMTP: resolve §2 with the owner first, and make sign-up honest (`awaiting_confirmation`, `isMailerLimit`)
exactly like web does.
