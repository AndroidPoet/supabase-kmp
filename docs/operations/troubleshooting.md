# Troubleshooting

## 401 Unauthorized

- Verify project URL and API key pairing
- Ensure access token is set after sign-in
- Confirm token refresh is working

## Empty database results

- Check RLS policies
- Verify table/schema name
- Confirm filters match stored values

## Realtime not receiving events

- Verify `connect()` was called before `subscribe()`
- Check channel topic naming
- Confirm server-side replication and policies

## Storage upload failures

- Validate bucket permissions
- Check content type and path format
- Confirm file size limits and allowed mime types
