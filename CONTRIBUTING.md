# Contributing

Aggressive Pomodoro is a Windows-first Kotlin desktop app. Before changing behavior, read the active [OpenSpec change](openspec/changes/build-aggressive-pomodoro-desktop/) and [ARCHITECTURE.md](docs/ARCHITECTURE.md). Keep assistant-specific instructions and generated agent workflows local; `.gitignore` excludes them from the repository.

## Propose and implement

1. Open an [issue](https://github.com/BharathKmalviya/Aggressive-Pomodoro/issues/new) for a bug or substantial behavior change, with steps to reproduce and the expected result. Do not include private data or security vulnerabilities in a public issue.
2. Keep changes focused. Put timer and task rules in the pure domain module, desktop I/O in adapters, and rendering in presentation. Avoid extra abstraction where a direct implementation is clear.
3. Update the relevant OpenSpec artifacts and docs when behavior, commands, or release steps change. Include tests for deterministic domain and storage behavior, and provide manual Windows steps for UI, sound, sleep, and packaging changes.
4. Run `./kotlin.bat build`, `./kotlin.bat test`, and `./scripts/package-windows.ps1` in PowerShell on Windows. Describe results and any checks you could not run in the pull request.

See [TESTING.md](docs/TESTING.md) for the hands-on scenarios. Security reports go through the private route in [SECURITY.md](SECURITY.md).

## Rights and license

You retain copyright in your original contribution. By submitting a contribution for inclusion in this repository, you agree that it can be distributed under the repository's [MIT License](LICENSE). Submit only work you have the right to provide; identify third-party code or assets and their licenses in the pull request. No copyright assignment or contributor license agreement is required.
