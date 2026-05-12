# Contributing

## Local quality checks

```bash
./gradlew jvmTest
./gradlew build --no-configuration-cache
```

## Contribution standards

- Document all public APIs with KDoc
- Add tests for behavior changes
- Keep module boundaries clean
- Update `docs/` when user-facing behavior changes

## Pull request checklist

- [ ] API behavior explained
- [ ] tests added/updated
- [ ] docs updated
- [ ] changelog/release notes prepared if needed
