# Functions Guide

`FunctionsClient` invokes Supabase Edge Functions.

## Invocation modes

- JSON payload invocation
- Raw body invocation with explicit content type
- Optional region routing via `FunctionRegion`
- Typed deserialization via `invokeTyped<T>(...)`
