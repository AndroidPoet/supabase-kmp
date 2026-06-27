-- ════════════════════════════════════════════════════════════════════════════
-- supabase-e2ee — opt-in end-to-end encryption tables
-- ════════════════════════════════════════════════════════════════════════════
-- Apply this if you use `supabase-e2ee` (KeyDirectory + EncryptedRoom). It adds:
--   1. device_keys     — each user's PUBLIC E2EE key (base64), and
--   2. e2ee_messages   — per-room ciphertext rows (the server never sees plaintext).
-- Public keys are not proof of identity: clients must compare the safetyNumber()
-- out of band before trusting a key (see EncryptedRoom verification).

-- ──────────────────────────── public key directory ─────────────────────────
create table if not exists public.device_keys (
    user_id    uuid primary key references auth.users (id) on delete cascade,
    public_key text not null,                       -- base64 ECDH P-256 public key
    updated_at timestamptz not null default now()
);

alter table public.device_keys enable row level security;

-- Public key material: any authenticated user may read it to start a session.
drop policy if exists "device_keys read" on public.device_keys;
create policy "device_keys read"
    on public.device_keys for select to authenticated using (true);

-- A user may only publish / replace their OWN key.
drop policy if exists "device_keys write own" on public.device_keys;
create policy "device_keys write own"
    on public.device_keys for all to authenticated
    using (user_id = auth.uid()) with check (user_id = auth.uid());

-- ──────────────────────────── encrypted messages ───────────────────────────
create table if not exists public.e2ee_messages (
    id         uuid primary key default gen_random_uuid(),
    room_id    text not null,
    sender_id  uuid references auth.users (id),
    ciphertext text not null,                        -- base64 AES-256-GCM ciphertext
    created_at timestamptz not null default now()
);

create index if not exists e2ee_messages_room_created_at_idx
    on public.e2ee_messages (room_id, created_at desc);

alter table public.e2ee_messages enable row level security;

-- Tighten this to your room-membership model in production. The default allows
-- authenticated users to read/write; the bodies are ciphertext regardless.
drop policy if exists "e2ee_messages auth all" on public.e2ee_messages;
create policy "e2ee_messages auth all"
    on public.e2ee_messages for all to authenticated
    using (true) with check (true);

-- Realtime delivery of new ciphertext rows.
alter publication supabase_realtime add table public.e2ee_messages;
