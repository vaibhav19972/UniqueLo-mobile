-- ─────────────────────────────────────────────────────────────────────────────
-- place_order: atomic order creation for UniqueLo.
--
-- The app writes an order header (`orders`) plus one row per bag line
-- (`order_items`). Doing that with two separate PostgREST INSERTs is not
-- transactional: a crash or network failure between the calls can leave an
-- order header without items. This function writes both in one statement
-- sequence inside a single transaction, and the app calls it first, falling
-- back to plain REST only when the function is not deployed (status 404).
--
-- Apply it in the Supabase dashboard (SQL editor) or with the CLI:
--   supabase db push   # or paste this file into SQL editor and run
--
-- Uses jsonb_populate_record so column types are resolved from the table
-- definitions themselves — no hardcoded casts to maintain.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function public.place_order(p_order jsonb, p_items jsonb)
returns jsonb
language plpgsql
as $$
declare
    created public.orders;
begin
    -- `id` is supplied by the client so order_items rows can reference it and
    -- a retried write for the same id stays idempotent (PK conflict instead of
    -- a duplicate order).
    insert into public.orders
    select * from jsonb_populate_record(null::public.orders, p_order)
    returning * into created;

    insert into public.order_items
    select * from jsonb_populate_recordset(null::public.order_items, p_items);

    return to_jsonb(created);
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
