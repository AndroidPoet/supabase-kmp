# supabase-codegen

Generates Kotlin `@Serializable` models from your **Supabase schema**. The database is the
single source of truth — you declare nothing in Kotlin. The tool reads your project's
PostgREST OpenAPI document (`GET {projectUrl}/rest/v1/`) and emits one Kotlin file of data
classes (one per table/view) and enum classes (one per Postgres enum).

It only ever **reads** the schema; it never writes anything back to the database.

## Output is multiplatform

The generated code uses only multiplatform-common types — `kotlin` stdlib scalars,
`kotlin.collections.List`, `kotlinx.serialization.@Serializable`/`@SerialName`, and
`kotlinx.serialization.json.JsonElement`. Drop the generated file straight into
`commonMain` and it compiles on every target. The generator binary itself is JVM-only and
runs at build time (it uses `java.net.http`), but its output is portable.

## Usage

```bash
./gradlew :supabase-codegen:run --args="\
  --url https://<ref>.supabase.co \
  --key <api-key> \
  --package com.example.db \
  --out shared/src/commonMain/kotlin"
```

`--url`/`--key` fall back to the `SUPABASE_URL` / `SUPABASE_KEY` environment variables.
Optional: `--file` (default `SupabaseModels`), `--package` (default `supabase.generated`),
`--out` (default `build/generated/supabase`).

> Use a key whose role can read the tables you want generated. Anon/publishable keys only
> see what RLS allows, so unprotected tables may be missing — run codegen with a
> service/secret key at build time so the full schema is visible.

## Type mapping

| Postgres | Kotlin | Notes |
|---|---|---|
| `int2`/`int4`/`serial` | `Int` | PostgREST reports these as `int32` |
| `int8`/`bigint`/`bigserial` | `Long` | PostgREST reports these as `int64` |
| `numeric`/`decimal`/`money`/`real`/`double precision` | `Double` | see precision note below |
| `boolean` | `Boolean` | |
| `json`/`jsonb` | `JsonElement` | |
| `uuid`/`text`/`varchar`/`bytea` | `String` | `bytea` is hex (`\x…`) |
| `timestamp*`/`date`/`time` | `String` | ISO-8601; keeps models dependency-free (no `kotlinx-datetime`) |
| `<type>[]` | `List<T>` | element mapped by the same rules |
| Postgres `enum` | `@Serializable enum class` | one constant per value, `@SerialName` preserves the DB spelling |

Column names are converted `snake_case` → `camelCase`, with `@SerialName("original")` added
whenever they differ. Kotlin keywords and digit-leading names are backtick-escaped.

## Nullability

A property is **non-null iff the column is `NOT NULL`** (PostgREST lists every non-null
column in each table's `required` array). Everything else is nullable.

- **Views** often expose all columns as nullable because Postgres can't prove non-nullness
  through a view — expect more `?` than you might on a base table.
- This is the deserialization-safe direction: an over-nullable field never fails to parse a
  present value, whereas a wrongly non-null field would throw on a real `null`.

## Known edge cases

- **Decimal precision.** `numeric`/`money` map to `Double`. If you need exact decimals,
  post-process those fields to `String` and parse with your own big-decimal type.
- **`bigint[]`.** Array elements carry only a coarse type in the schema, so `bigint[]`
  degrades to `List<Int>` (scalar `bigint` is still `Long`). Rare in practice.
- **Nested arrays** (`int[][]`) map to `List<JsonElement>` — the schema doesn't describe the
  inner element type, and `JsonElement` is the safe choice.
- **Name collisions.** If two tables (or two enums) normalise to the same Kotlin name, the
  generator fails fast with a message naming both — rename one so the output compiles.
