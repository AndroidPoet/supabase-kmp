-- Reset test data
truncate table public.e2e_messages;
truncate table public.chat_messages;
truncate table public.chat_rooms cascade;

-- Re-seed rooms
insert into public.chat_rooms (name)
select 'general'
where not exists (select 1 from public.chat_rooms where name = 'general');

insert into public.chat_rooms (name)
select 'random'
where not exists (select 1 from public.chat_rooms where name = 'random');
