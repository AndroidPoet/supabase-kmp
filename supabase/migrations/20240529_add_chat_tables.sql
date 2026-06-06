-- Chat rooms
create table if not exists public.chat_rooms (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    created_at timestamptz not null default now()
);

-- Chat messages
create table if not exists public.chat_messages (
    id uuid primary key default gen_random_uuid(),
    room_id uuid references public.chat_rooms(id) not null,
    sender_id uuid references auth.users(id),
    sender_name text not null default 'anonymous',
    body text not null,
    created_at timestamptz not null default now()
);

create index if not exists chat_messages_room_created_at_idx
    on public.chat_messages (room_id, created_at desc);

-- Enable RLS
alter table public.chat_rooms enable row level security;
alter table public.chat_messages enable row level security;

-- Allow anon read/write on chat rooms
drop policy if exists "anon all chat_rooms" on public.chat_rooms;
create policy "anon all chat_rooms"
    on public.chat_rooms
    for all
    to anon
    using (true)
    with check (true);

-- Allow authenticated read/write on chat rooms
drop policy if exists "auth all chat_rooms" on public.chat_rooms;
create policy "auth all chat_rooms"
    on public.chat_rooms
    for all
    to authenticated
    using (true)
    with check (true);

-- Allow anon read/write on chat messages
drop policy if exists "anon all chat_messages" on public.chat_messages;
create policy "anon all chat_messages"
    on public.chat_messages
    for all
    to anon
    using (true)
    with check (true);

-- Allow authenticated read/write on chat messages
drop policy if exists "auth all chat_messages" on public.chat_messages;
create policy "auth all chat_messages"
    on public.chat_messages
    for all
    to authenticated
    using (true)
    with check (true);

-- Insert default rooms when they do not already exist.
insert into public.chat_rooms (name)
select 'general'
where not exists (select 1 from public.chat_rooms where name = 'general');

insert into public.chat_rooms (name)
select 'random'
where not exists (select 1 from public.chat_rooms where name = 'random');

do $$
begin
    if exists (select 1 from pg_publication where pubname = 'supabase_realtime')
        and not exists (
            select 1
            from pg_publication_tables
            where pubname = 'supabase_realtime'
                and schemaname = 'public'
                and tablename = 'chat_messages'
        ) then
        alter publication supabase_realtime add table public.chat_messages;
    end if;
end $$;

-- Default public bucket used by the Compose sample.
insert into storage.buckets (id, name, public)
values ('public', 'public', true)
on conflict (id) do update set public = excluded.public;

drop policy if exists "anon read public sample bucket" on storage.objects;
create policy "anon read public sample bucket"
    on storage.objects
    for select
    to anon, authenticated
    using (bucket_id = 'public');

drop policy if exists "anon insert public sample bucket" on storage.objects;
create policy "anon insert public sample bucket"
    on storage.objects
    for insert
    to anon, authenticated
    with check (bucket_id = 'public');

drop policy if exists "anon update public sample bucket" on storage.objects;
create policy "anon update public sample bucket"
    on storage.objects
    for update
    to anon, authenticated
    using (bucket_id = 'public')
    with check (bucket_id = 'public');

drop policy if exists "anon delete public sample bucket" on storage.objects;
create policy "anon delete public sample bucket"
    on storage.objects
    for delete
    to anon, authenticated
    using (bucket_id = 'public');

create or replace function public.chat_room_message_count(room uuid)
returns integer
language sql
stable
as $$
    select count(*)::integer
    from public.chat_messages
    where room_id = room
$$;

grant execute on function public.chat_room_message_count(uuid) to anon, authenticated;
