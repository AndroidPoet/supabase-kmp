# Functions Guide

`FunctionsClient` invokes Supabase Edge Functions.

## Invocation options

- JSON payload with `invoke`
- raw body with `invokeWithBody`
- typed decode with `invokeTyped<T>`
- optional region routing using `FunctionRegion`

## Typical patterns

- auth-required callable endpoints
- lightweight server-side validation
- orchestration over multiple Supabase services
