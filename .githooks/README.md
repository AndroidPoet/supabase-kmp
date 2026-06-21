# Git hooks

Version-controlled hooks that run the same quality gates as CI, so you catch
problems before pushing instead of after.

## Install (once per clone)

```bash
./gradlew installGitHooks
```

That points git at this directory (`git config core.hooksPath .githooks`). Run it
once after cloning; new hooks added here are picked up automatically afterwards.

## What runs

| Hook | When | Does |
| --- | --- | --- |
| `pre-commit` | every `git commit` | `spotlessApply` on **staged** Kotlin, then re-stages it — commits are always formatted |
| `pre-push` | every `git push` | `detekt` + `apiCheck` (the CI gates); add `RUN_TESTS=1` to also run `jvmTest` |

`pre-commit` only does work when Kotlin/`.kts` files are staged, so non-code commits
stay instant.

## Skipping

```bash
git commit --no-verify          # skip hooks for one commit
git push   --no-verify          # skip hooks for one push
SKIP_HOOKS=1 git commit ...     # skip via env var
RUN_TESTS=1  git push ...       # include jvmTest in pre-push
```

## Uninstall

```bash
git config --unset core.hooksPath
```
