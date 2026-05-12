# Functions Guide

`FunctionsClient` invokes Supabase Edge Functions.

## Invocation options

- JSON payload with `invoke`
- raw body with `invokeWithBody`
- typed decode with `invokeTyped<T>`
- optional region routing using `FunctionRegion`

## JSON invocation example

```kotlin
val result = functions.invoke(
    function = "send-welcome-email",
    body = """{"user_id":"u1"}""",
)
```

## Typed invocation example

```kotlin
@Serializable
data class Health(val ok: Boolean)

val health = functions.invokeTyped<Health>(
    function = "health",
)
```

## Raw body example

```kotlin
val binary = functions.invokeWithBody(
    function = "process-image",
    body = pngBytes,
    contentType = "image/png",
    region = FunctionRegion.AWS_AP_SOUTH_1,
)
```
