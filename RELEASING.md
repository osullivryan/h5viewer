# Releasing

Releases are automated by [`.github/workflows/release.yml`](.github/workflows/release.yml):
pushing a `v*` tag runs the IntelliJ Plugin Verifier, then signs and publishes the
plugin to the JetBrains Marketplace and attaches the built zip to the GitHub Release.

## One-time setup

Add these repository secrets (**Settings → Secrets and variables → Actions**):

| Secret | How to get it |
| --- | --- |
| `PUBLISH_TOKEN` | A permanent token from <https://plugins.jetbrains.com/author/me/tokens>. |
| `CERTIFICATE_CHAIN` | Contents of `chain.crt` (see below). |
| `PRIVATE_KEY` | Contents of `private.pem` (see below). |
| `PRIVATE_KEY_PASSWORD` | The password you chose for the private key. |

Generate the signing material once (keep `private.pem` safe and out of git):

```bash
openssl genpkey -aes-256-cbc -algorithm RSA -out private.pem -pkeyopt rsa_keygen_bits:4096
openssl req -key private.pem -new -x509 -days 3650 -out chain.crt
```

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
