create table if not exists public.e2e_messages (
    id uuid primary key default gen_random_uuid(),
    body text not null,
    created_at timestamptz not null default now()
);

alter table public.e2e_messages enable row level security;

-- RLS policies gate which rows are visible, but the anon role still needs
-- table-level privileges to touch the table at all.
grant select, insert on public.e2e_messages to anon;

drop policy if exists "anon can read e2e messages" on public.e2e_messages;
create policy "anon can read e2e messages"
    on public.e2e_messages
    for select
    to anon
    using (true);

drop policy if exists "anon can insert e2e messages" on public.e2e_messages;
create policy "anon can insert e2e messages"
    on public.e2e_messages
    for insert
    to anon
    with check (true);
