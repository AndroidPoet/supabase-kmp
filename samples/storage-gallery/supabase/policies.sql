-- Storage Gallery sample — bucket + permissive demo policies.
--
-- This makes the 'gallery' bucket readable/writable with just the anon key so the
-- sample is plug-and-play. DO NOT ship these policies as-is: a real app should
-- scope writes to an authenticated user and remove public delete.

-- 1) Create a public bucket named 'gallery' (id == name).
insert into storage.buckets (id, name, public)
values ('gallery', 'gallery', true)
on conflict (id) do update set public = excluded.public;

-- 2) Allow the anon role to do everything on objects in this bucket (demo only).
create policy "gallery anon read"
  on storage.objects for select to anon
  using (bucket_id = 'gallery');

create policy "gallery anon insert"
  on storage.objects for insert to anon
  with check (bucket_id = 'gallery');

create policy "gallery anon update"
  on storage.objects for update to anon
  using (bucket_id = 'gallery');

create policy "gallery anon delete"
  on storage.objects for delete to anon
  using (bucket_id = 'gallery');
