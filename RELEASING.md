# Releasing

Releases are automated by [`.github/workflows/release.yml`](.github/workflows/release.yml):
pushing a `v*` tag runs the IntelliJ Plugin Verifier, then publishes the plugin
(unsigned) to the JetBrains Marketplace and attaches the built zip to the GitHub
Release.

## One-time setup

Add a single repository secret under **Settings → Secrets and variables → Actions**:

| Secret | How to get it |
| --- | --- |
| `PUBLISH_TOKEN` | A permanent token from <https://plugins.jetbrains.com/author/me/tokens>. |

That's the only secret required. The plugin is published unsigned; JetBrains
Marketplace handles distribution. (Author signing can be added later if desired.)

> The **first** version of a new plugin must be uploaded and pass moderation via
> the Marketplace web UI before automated `publishPlugin` updates are accepted.

## Cutting a release

1. Bump `version` in `build.gradle.kts`.
2. Move the `[Unreleased]` notes in `CHANGELOG.md` under a new version heading.
3. Tag and push:

   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```

The release workflow verifies, signs, and publishes automatically.
