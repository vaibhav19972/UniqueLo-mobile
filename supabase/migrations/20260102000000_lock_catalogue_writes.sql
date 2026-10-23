-- ─────────────────────────────────────────────────────────────────────────────
-- Catalogue write lockdown (audit A13 → decision D-5, open action OA-2).
--
-- The sample/demo data shipped permissive public write policies on the
-- catalogue tables: any visitor holding the anon key could insert, update or
-- delete products, variants, images, details, and merchandising tables.
--
-- This script:
--   1. drops every permissive policy that grants the `public` role any write
--      action on those tables (names unknown → enumerated from pg_policies),
--   2. revokes INSERT/UPDATE/DELETE privileges from anon and authenticated.
--
-- It deliberately does NOT touch:
--   - SELECT (the catalogue must stay publicly readable),
--   - orders / order_items / newsletter_subscribers (guest insert-only flows
--     stay as-is, governed by their own policies),
--   - the anon/authenticated roles' ability to read.
--
-- Admin edits should run under the service_role key once an admin flow
-- exists. Apply in the Supabase dashboard SQL editor (or `supabase db push`).
-- Safe to re-run: every statement is idempotent.
-- ─────────────────────────────────────────────────────────────────────────────

do $$
declare
  policy record;
begin
  -- pg_policies.permissive is text ('PERMISSIVE'/'RESTRICTIVE' on PG 14+) and
  -- introduced version drift here once already; match on the write command and
  -- the granted role instead, which are stable across releases.
  for policy in
    select p.tablename, p.policyname
    from pg_policies p
    where p.schemaname = 'public'
      and p.tablename in (
        'products', 'variants', 'product_images', 'embroidery_details',
        'universes', 'categories', 'collections', 'motifs'
      )
      and p.cmd in ('ALL', 'INSERT', 'UPDATE', 'DELETE')
      and 'public' = any (p.roles)
  loop
    execute format('drop policy if exists %I on public.%I',
                   policy.policyname, policy.tablename);
  end loop;
end $$;

revoke insert, update, delete
  on public.products, public.variants, public.product_images,
     public.embroidery_details, public.universes, public.categories,
     public.collections, public.motifs
  from anon, authenticated;

-- Sanity check (run manually afterwards):
--   select tablename, policyname, cmd from pg_policies
--    where schemaname = 'public'
--      and tablename in ('products','variants','product_images',
--                        'embroidery_details','universes','categories',
--                        'collections','motifs')
--      and cmd in ('ALL','INSERT','UPDATE','DELETE')
--      and 'public' = any (roles);
-- Expected: 0 rows.
--
-- Executed against a local PostgreSQL 16.15 clone (owner-style policies,
-- supabase-like anon/authenticated roles): all eight demo write policies
-- dropped, re-run is idempotent, anon SELECT retained, anon INSERT denied.
