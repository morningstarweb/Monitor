## Description

Please provide a brief summary of the changes made and the motivation behind them.

Fixes #(issue number if applicable)

## Type of Change

- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Refactor or performance enhancement
- [ ] Documentation update
- [ ] CI/CD or build tooling update

## Component(s) Affected

- [ ] Windows Desktop App (`desktop/crates/monitor-*`)
- [ ] Android Companion App (`android/`)
- [ ] Cloudflare Edge Broker (`cloud/`)
- [ ] Windows Installer (`installer/`)
- [ ] Documentation (`docs/`)

## Verification Checklist

- [ ] My code strictly adheres to the project's coding standards and architectural invariants.
- [ ] Rust code contains zero `unsafe` and compiles cleanly with `cargo clippy -- -D warnings`.
- [ ] Android code conforms to Dark Obsidian tokens and does not exceed 200 SLOC per file.
- [ ] I have added tests that prove my fix is effective or that my feature works.
- [ ] All new and existing tests pass (`scripts/verify_all.ps1`).
- [ ] I have updated relevant documentation in `docs/` where applicable.
- [ ] No secrets, keys, credentials, or personal tokens are committed.
