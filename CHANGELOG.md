<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Ansible Companion Changelog

## [Unreleased]

## [0.1.4]

### Changed

- Added a strict local `verifyPlugin` gate (catches
  `@ApiStatus.OverrideOnly`/`Internal`/`Experimental` API usage and
  compatibility problems before Marketplace's own verifier would) — no
  user-visible change, confirmed passing clean against all 6 target IDEs.

## [0.1.3]

### Added

- Gap Hunter Labs brand icon (`pluginIcon.svg` / `pluginIcon_dark.svg`).

## [0.1.2]

### Fixed

- The plugin description and both action tooltips (in `plugin.xml`) were
  still hardcoded in Spanish, the same issue fixed in 0.1.1 for the
  dialogs — now in English, consistent with the rest of the listing.

## [0.1.1]

### Fixed

- The encrypt/decrypt dialogs and messages had hardcoded Spanish text,
  inconsistent with the rest of the plugin's UI (English). All
  user-visible text is now in English.

## [0.1.0]

### Added

- Encrypt/decrypt Ansible Vault (1.1/AES256) on the editor selection, via
  the context menu. Own implementation with `javax.crypto` (no
  `ansible-vault` installed, no new dependencies) — verified against the
  real test vector from `ansible/ansible`
  (`test/units/parsing/vault/test_vault.py`).
- `sinceBuild=243`, open `untilBuild` — avoids dying from a narrow
  `untilBuild`.

### On hold for 0.2.0

- FQCN-aware completion (`ansible.builtin.*`).
- Correct Jinja2 parsing inside YAML.
- File-type detection that doesn't hijack Kubernetes/Helm/Docker-compose
  YAML.
- Role support, multi-environment variable preview.

[Unreleased]: https://github.com/GapHunterLabs/ansible-companion/compare/0.1.4...HEAD
[0.1.4]: https://github.com/GapHunterLabs/ansible-companion/compare/0.1.3...0.1.4
[0.1.3]: https://github.com/GapHunterLabs/ansible-companion/compare/0.1.2...0.1.3
[0.1.2]: https://github.com/GapHunterLabs/ansible-companion/compare/0.1.1...0.1.2
[0.1.1]: https://github.com/GapHunterLabs/ansible-companion/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/GapHunterLabs/ansible-companion/commits/0.1.0
