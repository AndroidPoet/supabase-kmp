# Frequently Asked Questions

## Is this the official Supabase Kotlin SDK?

No. This is an independent Kotlin Multiplatform SDK.

## Does it support all Supabase products?

It covers core app-facing products: Auth, Database, Storage, Realtime, and Functions.

## How are dependencies wired?

The SDK no longer ships a DI container module. Use the provided factory functions and wire instances in your app.

## How do I publish docs updates?

Push changes under `docs/` to `main`. GitBook sync will ingest updates.
