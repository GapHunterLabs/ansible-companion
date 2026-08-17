<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Ansible Companion Changelog

## [Unreleased]

## [2026.2.0]

### Added

- Ctrl+Click / Ctrl+B navigation (Ansible Companion Pro) from a role
  reference straight to that role's `tasks/main.yml` — works from a
  `roles:` list entry and from `include_role`/`import_role`'s `name:`
  key. The first concrete piece of "role support", listed as pending
  since the very first release.
- FQCN-aware completion (Ansible Companion Pro) now also covers
  `community.general` (566 modules, 11 deprecated modules excluded at
  fetch time) and `ansible.posix` (14 modules), on top of the existing
  69 `ansible.builtin` modules — 649 total, up from 69. Each module's
  description is its real `short_description` from the module's own
  `DOCUMENTATION` docstring in the upstream
  `ansible-collections/community.general` and
  `ansible-collections/ansible.posix` repositories (`main` branch),
  fetched the same way the original `ansible.builtin` set was.

### Changed

- `AnsibleModule` now carries an explicit `namespace` field instead of
  hardcoding `ansible.builtin.` as the FQCN prefix — internal-only
  change, no user-facing behavior difference for existing
  `ansible.builtin.*` completions.

## [2026.1.1]

### Fixed

- Removed internal Marketplace account/support details that had been
  mistakenly documented in this changelog and in `plugin.xml` — no
  user-facing change.

## [2026.1.0]

### Changed

- **Version scheme**: this plugin now versions as `YYYY.MINOR.PATCH`
  (JetBrains's own convention for Paid/Freemium plugins) instead of
  semver (`0.1.x`) — required by a hard Marketplace validation rule:
  `<product-descriptor>`'s `release-version` must share its leading
  digits with the plugin's own version (`release-version=20261` only
  validates against a plugin version starting `2026.1`), confirmed by
  a real local `verifyPlugin` failure, not assumed from documentation
  alone. This is the only plugin in the Gap Hunter Labs catalog on
  this scheme — the other 19 aren't enrolled in Paid/Freemium and keep
  semver unchanged.

### Added

- `<product-descriptor>` in `plugin.xml`, required to complete the
  Freemium pricing model JetBrains approved for this plugin —
  `optional="true"` is the attribute that keeps this Freemium rather
  than fully Paid: the plugin still installs and runs with no license
  at all, only the Pro-tier features gate on
  `CheckLicense.isLicensed()`, unchanged.

### Fixed

- FQCN completion no longer fires inside a nested module-parameter key
  (e.g. `copy:\n  <caret>`) -- it's now scoped to a task's own key only
  (`- ansible.builtin.<caret>` or a fresh `- name: x\n  <caret>` line),
  via a new `YamlKeyPositionDetector` that walks every key-value between
  the cursor and the enclosing task, verified against the real YAML PSI
  parser (not just hand-reasoned) with tests covering the exact
  `copy:`/nested-parameter repro from `KNOWN_ISSUES.md`.

## [0.1.5]

### Added

- FQCN-aware completion for `ansible.builtin.*` modules (69 bundled
  modules with real descriptions), scoped to YAML task-key positions —
  the paid Ansible Companion Pro tier. Unlicensed users see a single
  upsell item instead of the real completions.
- Jinja2 (`{{ }}`/`{% %}`/`{# #}`) syntax highlighting inside Ansible
  YAML scalars, also part of the Pro tier — pure text-scan detection, no
  real Jinja2 engine or Python dependency.

### Fixed

Seven real bugs found and fixed during integration — five caught live in
`runIde`, two caught only by the full `verifyPlugin` (6 target IDEs) —
see `KNOWN_ISSUES.md` for full root causes:

- Infinite recursion (`StackOverflowError`) in the Ansible file
  detector: it read file content via `VirtualFile.contentsToByteArray()`,
  which internally re-invokes every registered `FileTypeOverrider`
  (including itself) — switched to `getInputStream()`, which doesn't
  touch file-type resolution at all.
- FQCN completion never fired for the single most common case (a
  brand-new task, no `:` typed yet) — the YAML-key-position guard only
  checked for an already-parsed `YAMLKeyValue`, which doesn't exist
  until the colon is typed.
- Completion/highlighting guards trusted `PsiFile.fileType == AnsibleYamlFileType`,
  which can silently disagree with `FileTypeOverrider`'s own result for
  the lifetime of an already-open file — both now re-run the same
  detection heuristic directly instead.
- The unlicensed upsell completion item was being added to the result
  set but never rendered: its lookup string didn't satisfy the
  platform's prefix matching against what was already typed. Fixed by
  building the lookup string from the real typed prefix instead of a
  fixed guess.
- `CheckLicense.showRegisterDialog()` (ported from JetBrains's own
  reference license-verification plugin) called an `ActionUtil`
  overload only available from IDE 244 onward, breaking compatibility
  with this plugin's own `sinceBuild=243` — replaced with
  `ActionUtil.invokeAction(AnAction, DataContext, ...)` after two other
  candidates were each confirmed wrong by a real `verifyPlugin` run
  (one didn't exist pre-244, the other is `@ApiStatus.OverrideOnly` and
  can't be invoked by client code).
- `FileTypeOverrider` (the interface `AnsibleFileTypeOverrider`
  implements) is itself `@ApiStatus.Experimental` with no
  non-experimental alternative for this use case — `EXPERIMENTAL_API_USAGES`
  removed from this plugin's `verifyPlugin` failure gate as a
  documented, deliberate exception.

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

[Unreleased]: https://github.com/GapHunterLabs/ansible-companion/compare/2026.2.0...HEAD
[2026.2.0]: https://github.com/GapHunterLabs/ansible-companion/compare/2026.1.1...2026.2.0
[2026.1.1]: https://github.com/GapHunterLabs/ansible-companion/compare/2026.1.0...2026.1.1
[2026.1.0]: https://github.com/GapHunterLabs/ansible-companion/compare/0.1.5...2026.1.0
[0.1.5]: https://github.com/GapHunterLabs/ansible-companion/compare/0.1.4...0.1.5
[0.1.4]: https://github.com/GapHunterLabs/ansible-companion/compare/0.1.3...0.1.4
[0.1.3]: https://github.com/GapHunterLabs/ansible-companion/compare/0.1.2...0.1.3
[0.1.2]: https://github.com/GapHunterLabs/ansible-companion/compare/0.1.1...0.1.2
[0.1.1]: https://github.com/GapHunterLabs/ansible-companion/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/GapHunterLabs/ansible-companion/commits/0.1.0
