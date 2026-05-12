# GitBook Publishing

This repo is ready for GitBook free plan publishing.

## Steps

1. Create a free GitBook workspace at https://www.gitbook.com/.
2. In GitBook, create a new space and choose **Git Sync**.
3. Connect your GitHub account and select this repository.
4. Set the default branch to `main`.
5. Confirm GitBook root files:
   - `.gitbook.yaml`
   - `docs/README.md`
   - `docs/SUMMARY.md`
6. Trigger first sync.

## Ongoing workflow

- Update docs in `docs/` as part of normal PRs.
- GitBook will auto-sync on merges to `main`.
