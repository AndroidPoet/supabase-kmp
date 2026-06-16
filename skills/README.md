# Agent Skills

AI coding agents have barely seen `supabase-kmp` in training data, so they tend to
hallucinate **supabase-js** APIs (`.from().select()` chains, thrown errors) that
don't exist here. The [`supabase-kmp`](./supabase-kmp/SKILL.md) skill teaches an
agent this SDK's actual surface — `SupabaseResult`, the per-feature clients, the
typed PostgREST DSL — plus the security rules (anon vs service-role, RLS, session
storage).

It follows the open [Agent Skills](https://www.anthropic.com/news/skills) format
(a `SKILL.md` with `name` + `description` frontmatter), so it works with any tool
that supports the standard.

## Install

**Claude Code** — copy the skill folder into your skills directory:

```bash
# project-scoped (this repo / a consuming project)
mkdir -p .claude/skills && cp -r skills/supabase-kmp .claude/skills/

# or user-scoped (all your projects)
mkdir -p ~/.claude/skills && cp -r skills/supabase-kmp ~/.claude/skills/
```

The agent loads it automatically when a task matches the `description`.

**Cursor / GitHub Copilot / other agents** — point your rules/instructions at
[`supabase-kmp/SKILL.md`](./supabase-kmp/SKILL.md) (e.g. reference it from a
`.cursorrules` / `copilot-instructions.md`), or paste its contents into your
project's agent-instructions file.

## Feedback

See [`supabase-kmp/skill-feedback.md`](./supabase-kmp/skill-feedback.md).
