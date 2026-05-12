# Production Checklist

## Security

- [ ] Use anon key only in client apps
- [ ] Keep service-role keys server-side only
- [ ] Apply RLS on all user data tables
- [ ] Prefer signed URLs for private files

## Reliability

- [ ] Handle all `SupabaseResult.Failure` branches
- [ ] Add retry strategy for transient network errors
- [ ] Observe realtime reconnect/failure states

## Performance

- [ ] Paginate large queries
- [ ] Limit selected columns where possible
- [ ] Avoid unnecessary realtime channels
