-- ─────────────────────────────────────────────────────────────────────────────
-- place_order: atomic order creation for UniqueLo.
--
-- The app writes an order header (`orders`) plus one row per bag line
-- (`order_items`). Doing that with two separate PostgREST INSERTs is not
-- transactional: a crash or network failure between the calls can leave an
-- order header without items. This function writes both in one statement
-- sequence inside a single transaction, and the app calls it first, falling
-- back to plain REST only when the function is not deployed (PostgREST
-- PGRST202, HTTP 404/501).
--
-- Apply it in the Supabase dashboard (SQL editor) or with the CLI:
--   supabase db push   # or paste this file into SQL editor and run
--
-- Uses jsonb_populate_record so column types are resolved from the table
-- definitions themselves — no hardcoded casts to maintain.
--
-- Important: jsonb_populate_record builds a record with NULLs for keys that
-- are absent; it does not apply column DEFAULTs. Only the columns the client
-- actually owns are listed in the INSERT, so `created_at` (and any future
-- defaulted column we do not name) still gets its table default. Naming every
-- column here would insert an explicit NULL into `created_at` and fail its
-- NOT NULL constraint.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function public.place_order(p_order jsonb, p_items jsonb)
returns jsonb
language plpgsql
as $$
declare
    created_id uuid;
begin
    if jsonb_typeof(p_items) is distinct from 'array'
        or jsonb_array_length(p_items) = 0 then
        raise exception 'place_order requires at least one order item'
            using errcode = '22023';
    end if;

    -- `id` is supplied by the client so order_items rows can reference it.
    -- A retry with the same id fails on the primary key instead of creating a
    -- duplicate header (the app does not retry with the same id today).
    created_id := (p_order->>'id')::uuid;
    if created_id is null then
        raise exception 'place_order requires p_order.id'
            using errcode = '22023';
    end if;

    -- No RETURNING here: `orders` intentionally has no public SELECT policy,
    -- and INSERT ... RETURNING would therefore fail under RLS for anon.
    insert into public.orders (
        id,
        customer_name,
        customer_email,
        status,
        subtotal_cents,
        discount_cents,
        shipping_cents,
        total_cents,
        coupon_code,
        gift_note,
        shipping_address
    )
    select
        r.id,
        r.customer_name,
        r.customer_email,
        coalesce(r.status, 'confirmed'),
        r.subtotal_cents,
        coalesce(r.discount_cents, 0),
        coalesce(r.shipping_cents, 0),
        r.total_cents,
        r.coupon_code,
        r.gift_note,
        r.shipping_address
    from jsonb_populate_record(null::public.orders, p_order) as r;

    -- order_id is taken from the header this transaction just wrote, never
    -- from the client payload, so items cannot be attached to another order.
    insert into public.order_items (
        id,
        order_id,
        product_slug,
        variant_sku,
        product_name,
        color,
        size,
        quantity,
        price_cents,
        customization
    )
    select
        i.id,
        created_id,
        i.product_slug,
        i.variant_sku,
        i.product_name,
        i.color,
        i.size,
        i.quantity,
        i.price_cents,
        i.customization
    from jsonb_populate_recordset(null::public.order_items, p_items) as i;

    return jsonb_build_object('id', created_id);
end;
$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- Security checklist (required before production, see README "RLS warning"):
--   * Catalogue tables (products, variants, universes, collections, motifs…)
--     must have admin-only write policies, not public USING(true).
--   * orders / order_items / newsletter_subscribers: public INSERT.
--   * An authenticated user read path for order history is planned in the
--     roadmap (Supabase auth); checkout stays guest-first for now.
-- ─────────────────────────────────────────────────────────────────────────────
