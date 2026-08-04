<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Ansible Companion Changelog

## [Unreleased]

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

[Unreleased]: https://github.com/GapHunterLabs/ansible-companion/compare/0.1.5...HEAD
[0.1.5]: https://github.com/GapHunterLabs/ansible-companion/compare/0.1.4...0.1.5
[0.1.4]: https://github.com/GapHunterLabs/ansible-companion/compare/0.1.3...0.1.4
[0.1.3]: https://github.com/GapHunterLabs/ansible-companion/compare/0.1.2...0.1.3
[0.1.2]: https://github.com/GapHunterLabs/ansible-companion/compare/0.1.1...0.1.2
[0.1.1]: https://github.com/GapHunterLabs/ansible-companion/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/GapHunterLabs/ansible-companion/commits/0.1.0
