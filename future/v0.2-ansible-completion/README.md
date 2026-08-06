# v0.2.0 — FQCN completion + Jinja2 highlighting (on hold, paid tier)

**Business model (decided 2026-07-23):** v0.1.x (vault encrypt/decrypt)
stays free forever — it's the acquisition hook and the thing driving
Marketplace reviews. v0.2.0 (this folder) is the paid tier: "Ansible
Companion Pro", priced well under the paid incumbent, with a 30-day
free trial handled automatically by the IDE once the plugin is
enrolled for Marketplace monetization (see "What you still need to
do" below). Internal pricing/account details live outside this public
repo, not in this file.

## Why this isn't wrapping `ansible-language-server` anymore

The original plan (see git history: `future/v0.2-ansible-lsp/`, abandoned
2026-07-23) was to wrap Red Hat's `@ansible/ansible-language-server` via
LSP4IJ. That work — a full Node-runtime provisioner that downloads and
caches a checksum-verified Node binary per user, since bundling Node
itself for all 6 OS/arch combos would have meant a 500MB+ plugin — is
real and it worked (verified end-to-end against the real nodejs.org).

But tracing the language server's own source turned up a hard blocker:
`utils/misc.js`'s `getUnsupportedError()` does a bare
`process.platform === "win32"` check and tells the client "Ansible
Language Server can only run inside WSL on Windows." This isn't cosmetic
— confirmed by actually launching the bundled server with a real Node
binary and completing a real LSP `initialize` handshake: it responds fine
to the handshake, but its core value (FQCN completion, `ansible-lint`
diagnostics, config discovery) all shell out to real `ansible`,
`ansible-lint`, `ansible-config` binaries and even use the POSIX-only
`command -v` for tool detection (`services/ansibleConfig.js`,
`utils/getAnsibleMetaData.js`). None of that runs in native Windows
cmd/PowerShell, consistent with `ansible-core` itself failing to import
`fcntl` there (see `AnsibleVaultCipher`'s own doc comment).

Since this plugin's whole reason for existing is Windows users who need
IntelliJ/PyCharm specifically because Ansible doesn't run natively for
them, shipping a paid feature that silently doesn't work for most of that
audience was a non-starter. Pivoted to the same "reimplement, don't wrap
a tool that doesn't run where our users are" call already made for the
vault cipher: **static, bundled data + client-side logic, zero external
dependency.** The old `lsp/` and `runtime/` code was removed (still in
git history) rather than kept around unused.

## What's built here

| File | What it does |
|---|---|
| `completion/AnsibleModuleIndex.kt` | Loads a bundled JSON index of `ansible.builtin.*` module names + short descriptions. Data fetched once from the real `ansible/ansible` GitHub repo (the modules directory under `lib/ansible`, stable-2.17 branch, 69 modules with real descriptions after dropping 2 internal/deprecated ones) — see `resources/ansible_builtin_modules.json`. Hand-rolled minimal JSON parser (flat string map only) instead of a new dependency, same call as `AnsibleVaultCipher`/`ArchiveExtractor`. |
| `completion/AnsibleModuleCompletionContributor.kt` | A `CompletionContributor` scoped to `AnsibleYamlFileType` (from `detection/`, unchanged from the LSP attempt — still needed to avoid hijacking Kubernetes/Helm/Docker-compose YAML) **and** to YAML mapping-key positions specifically (`isCompletingYamlKey`, via `PsiTreeUtil`/`YAMLKeyValue`) so module names don't clutter completion while typing a value or comment. Not yet narrowed further to "top-level task key only" (see the class's own doc comment) — that needs live `runIde` verification to get right, not more guessing. |
| `completion/CheckLicense.kt` | JetBrains's own reference license-verification code (ported from `github.com/JetBrains/marketplace-makemecoffee-plugin`, `CheckLicense.java`, fetched and diffed byte-for-byte for the two embedded root certificates), gating the completion contributor. `PRODUCT_CODE` is set to the real Marketplace-assigned value in `plugin.xml`/`CheckLicense.kt` directly, not repeated here. |
| `completion/JinjaExpressionDetector.kt` | Pure text scan for `{{ }}`/`{% %}`/`{# #}` Jinja2 regions inside a string, plus malformed-block detection (unterminated openers, stray closers) — addresses complaint #3. No real Jinja2 engine, no Python. Explicitly verified NOT to false-positive on YAML's own `{key: value}` flow-mapping syntax. |
| `completion/JinjaHighlightingAnnotator.kt` | A generic-`PsiElement`-based `Annotator` (deliberately avoids depending on YAML-specific PSI types — see its doc comment) that highlights `JinjaExpressionDetector`'s regions using `DefaultLanguageHighlighterColors.TEMPLATE_LANGUAGE_COLOR`, gated behind the same license check with a 60s cache (`LicensingFacade` calls aren't free to make on every keystroke). |
| `completion-tests/AnsibleModuleIndexTest.kt` | 6 JUnit tests: JSON parsing (escapes, empty object, sorting), FQCN formatting, and a real load of the bundled resource asserting sane content (50-200 modules, `copy`/`debug`/`command` present, every entry non-blank). |
| `completion-tests/JinjaExpressionDetectorTest.kt` | 13 JUnit tests: all 3 region kinds, mixed/adjacent regions, empty expressions, unterminated/stray-closer malformed cases, and — the important one — confirming YAML's own `{...}`/`[...]` flow syntax is never mistaken for Jinja2 (that exact false positive is the incumbent's bug this feature is meant to avoid repeating). |

**Verified for real, not just written:** temporarily staged every file in
`completion/` and `detection/` into the live `src/main` (which already
has the real IntelliJ Platform SDK cached from earlier `verifyPlugin`
runs) — including a temporary `bundledPlugin("org.jetbrains.plugins.yaml")`
line in `build.gradle.kts`, since `AnsibleModuleCompletionContributor`
and `AnsibleYamlFileType` need it — and ran `./gradlew compileKotlin`.
Confirms `LicensingFacade`, `ActionUtil.performAction`,
`AnActionEvent.createEvent`, `Annotator`/`AnnotationHolder`/
`TextAttributesKey`/`DefaultLanguageHighlighterColors`, and
`YAMLKeyValue`/`PsiTreeUtil` are all called correctly against the actual
2025.2.6.2 + bundled-YAML-plugin classpath, not just plausible-looking
Kotlin. Also ran the real test suite (19/19 green across
`AnsibleModuleIndexTest` + `JinjaExpressionDetectorTest`). Reverted
`build.gradle.kts` and removed every temporary copy afterward — `git
diff build.gradle.kts` and `git status` both confirmed nothing leaked
into the live v0.1.x source set or build config.

**Bug caught during this verification, worth remembering:** Kotlin block
comments *nest* (unlike Java/C). A KDoc originally described the data
source as fetched from `` `lib/ansible/modules/*.py` `` — the `/*` inside
that path opened a *nested* comment that the KDoc's own closing `*/` never
reached, so the outer comment stayed open all the way to true EOF and
`compileKotlin` failed with "Unclosed comment" pointing at the last line
of the file, nowhere near the actual cause. This is the mirror image of
the previously-documented bug (a literal `*/` inside a comment closing it
early, e.g. `roles/*/tasks`) — same root cause (a path-like string
containing `/*` or `*/`), opposite mechanism (nesting-overflow instead of
early-close). Found it by bisecting the file with real `compileKotlin`
runs rather than eyeballing, since character-by-character reasoning about
where `/*`/`*/` pairs "should" match didn't account for nesting depth.
**Rule of thumb: never let a literal `/*` or `*/` substring — from a path,
a glob pattern, a code snippet — appear inside a Kotlin comment.**

## Marketplace monetization enrollment — applied, not yet approved (2026-07-23)

Gap Hunter Labs applied on the Monetization tab for the **FREEMIUM**
pricing model (the plugin itself stays free to install; only the v0.2
features go behind a license — as opposed to the plain "PAID" model,
which would paywall everything from install). JetBrains assigned a
real product code immediately, now set in `CheckLicense.PRODUCT_CODE`
(was a placeholder before this) — but per JetBrains's own text on that
page, **"After applying for the Freemium or Fully Paid pricing model,
you will be contacted by our Support team"**, so this isn't fully
approved yet, just applied for. Account/vendor-specific identifiers
(the real product code, organization slug, etc.) live in the actual
`plugin.xml`/Marketplace vendor console, not documented here.

JetBrains's own checklist for what's left (shown on that same page,
reproduced here so it doesn't get lost):

1. Inform users about the upcoming changes. (marketing, not code)
2. Obfuscate the plugin (optional — skip).
3. ~~Implement license verification calls.~~ **Done** — `CheckLicense.kt`.
4. ~~Create an organization and add banking information in the 'Vendor
   Information' tab.~~ **Done (2026-07-23).** Still need to confirm
   plugin.xml's `<vendor>` ends up matching the vendor's
   Marketplace-assigned `organization_id` (step 8 below) once that's
   visible on the Marketplace side.
5. Prepare the plugin on the Marketplace demo to test changes (optional).
6. Make changes in `plugin.xml` (see below — do this only when v0.2 ships).
7. Set the product descriptor's `code`, `release-date`, `release-version`,
   **and set `optional="true"`** — this is the attribute that actually
   marks the paid features as an add-on rather than making the whole
   plugin require a license, i.e. it's what makes FREEMIUM actually mean
   freemium instead of fully paid. Did not know about this attribute
   before reading JetBrains's own checklist; it changes the
   `<product-descriptor>` tag below.
8. ~~Check that plugin.xml's `<vendor>` matches the `organization_id`.~~
   **Checked (2026-07-23): they don't match yet.** The organization's
   Marketplace-assigned URL slug is lowercase and hyphenated (the
   actual value lives in the vendor console, not here). Live
   `plugin.xml` currently has `<vendor>Gap Hunter Labs</vendor>` —
   display-case, with a space. Do **not** change this in the live
   v0.1.x `plugin.xml` right now (same reasoning as the
   `<product-descriptor>` tag: changing vendor metadata while
   v0.1.0/0.1.1/0.1.2 are mid-moderation is an unnecessary risk to an
   unrelated, already-submitted change). Change it to match the real
   slug in the same batch of edits as adding `<product-descriptor>`,
   when v0.2 actually ships.
9. Set up pricing, offers, and coupons. **Confirmed 2026-07-23: not
   available yet because both the plugin and the vendor account are
   still under review** (per JetBrains's own
   [Preparing your plugin for publication](https://plugins.jetbrains.com/docs/marketplace/prepare-your-plugin-for-publication.html)
   docs) — nothing to do here but wait.
10. Release the plugin.

## `<product-descriptor>` exact rules (learned 2026-07-23, corrects an earlier guess)

Read JetBrains's actual
[required-parameters](https://plugins.jetbrains.com/docs/marketplace/add-required-parameters.html)
and
[versioning](https://plugins.jetbrains.com/docs/marketplace/versioning-of-paid-plugins.html)
docs rather than guessing further:

- `code`: max 15 chars, must start with `P`, ALL CAPS, no digits/symbols.
  This plugin's assigned code satisfies this — confirmed correct, not
  just assigned-and-hoped.
- `release-date`: `YYYYMMDD`.
- `release-version`: **not just an incrementing integer** — my earlier
  placeholder (`release-version="1"`) is invalid. Rules: at least 2
  digits; must strictly increase across releases (never descend); its
  first digits must align with the plugin's own `<version>` (JetBrains's
  own example: `<version>2021.1.1</version>` pairs with
  `release-version="20211"` — year+major concatenated, dots stripped).
  Our `<version>` is plain semver (`0.2.0`, not calendar-based), so the
  exact mapping isn't obvious from their examples — **decide this
  deliberately at reactivation time** (possibly by asking
  marketplace@jetbrains.com directly, since Support is already going to
  be in touch about the Freemium application) rather than guessing a
  value that could get the release rejected.
- `optional="true"`: required for FREEMIUM (see checklist item 7 above).

## Reactivation status (updated 2026-07-30): code is fully integrated into `src/main`

Everything in this folder except this README has been moved into
`src/main/kotlin/...` / `src/test/kotlin/...`, and `future/v0.2-ansible-completion/`
now only holds this document as a historical record of the design
decisions and the bugs found while integrating. What actually happened,
in order:

1. Moved `completion/`, `detection/`, and their test folders into
   `src/main`/`src/test` proper.
2. Restored `bundledPlugin("org.jetbrains.plugins.yaml")` for real in
   `build.gradle.kts` (no longer temporary).
3. Registered `AnsibleFileTypeOverrider`, `AnsibleModuleCompletionContributor`,
   and `JinjaHighlightingAnnotator` in `plugin.xml`'s `<extensions>`.
4. Found and fixed **five real bugs**, only two of which unit tests
   caught — the rest needed a real `runIde` sandbox with manual
   completion/highlighting testing to surface at all. Full root-cause
   writeups live in `../../KNOWN_ISSUES.md` (Rounds 1-5, all dated
   2026-07-30):
   - Round 1: infinite recursion in `AnsibleFileTypeOverrider` from
     calling `VirtualFile.contentsToByteArray()`.
   - Round 2: FQCN completion never fired for a brand-new task (no
     `YAMLKeyValue` parsed yet before the `:` is typed).
   - Round 3: `PsiFile.fileType == AnsibleYamlFileType` guard never
     matched, because `FileTypeOverrider`'s result and the PSI-visible
     `FileType` can disagree for the lifetime of an already-open file —
     both the completion contributor and the annotator now re-run
     `AnsibleFileDetector` directly instead of trusting FileType
     identity.
   - Round 4 + 5: the unlicensed upsell completion item was being added
     to the `CompletionResultSet` but silently filtered before
     rendering, because its lookup string didn't satisfy the platform's
     prefix match against what was already typed — fixed by building
     the lookup string from `result.prefixMatcher.prefix` itself.
5. Verified manually in a real `runIde` sandbox (not just unit tests):
   FQCN completion's upsell item renders correctly for an unlicensed
   user; Jinja2 highlighting stays correctly gated off (no highlight,
   no exception) without a license. Neither had ever been exercised in
   an actual running editor before this session.
6. `./gradlew test` green (36/36) after every fix; `verifyPlugin` (6
   IDEs) run as the final gate before this can ship — see
   `../../CHANGELOG.md` `[0.1.5]` for the confirmed result.
7. Item 2 from the original pending list (narrowing the completion
   trigger to top-level task keys only, not nested module parameters
   like `copy:\n  <caret>`) is **still open** — confirmed still firing
   for nested keys during this session's `runIde` testing. Left as a
   known follow-up (`KNOWN_ISSUES.md` Round 2's note), not blocking this
   release.

## Still pending — Marketplace/licensing side only, not code

The remaining checklist items are entirely about the paid-tier rollout
mechanics, not this folder's code:

1. Add `<product-descriptor code="..." release-date="YYYYMMDD" release-version="TBD" optional="true"/>`
   to `plugin.xml` with the real assigned code (see exact rules above
   for `release-version` — don't reuse a placeholder literally),
   **and** change `<vendor>` from `Gap Hunter Labs` to the real
   Marketplace organization slug in the same edit (checklist item 8,
   confirmed above). Deliberately **not** done in this session's
   `0.1.5` — this is a separate, explicit step for whenever the paid
   tier actually goes live on Marketplace, not bundled into "ship the
   free-tier-visible feature code."
2. Everything else already listed above under "Marketplace monetization
   enrollment" (steps 1, 2, 5, 9, 10 of JetBrains's own checklist).
