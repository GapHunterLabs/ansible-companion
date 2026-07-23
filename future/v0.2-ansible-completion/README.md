# v0.2.0 — FQCN completion + Jinja2 highlighting (on hold, paid tier)

**Business model (decided 2026-07-23):** v0.1.x (vault encrypt/decrypt)
stays free forever — it's the acquisition hook and the thing driving
Marketplace reviews. v0.2.0 (this folder) is the paid tier: "Ansible
Companion Pro". Suggested price $15-19 USD/year (well under the paid
incumbent's $59+/year), with a 30-day free trial handled automatically by
the IDE once the plugin is enrolled for Marketplace monetization (see
"What you still need to do" below).

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
| `completion/AnsibleModuleCompletionContributor.kt` | A `CompletionContributor` scoped to `AnsibleYamlFileType` (from `detection/`, unchanged from the LSP attempt — still needed to avoid hijacking Kubernetes/Helm/Docker-compose YAML) that offers `ansible.builtin.*` completions. |
| `completion/CheckLicense.kt` | JetBrains's own reference license-verification code (ported from `github.com/JetBrains/marketplace-makemecoffee-plugin`, `CheckLicense.java`, fetched and diffed byte-for-byte for the two embedded root certificates), gating the completion contributor. `PRODUCT_CODE` is a placeholder — see below. |
| `completion-tests/AnsibleModuleIndexTest.kt` | 6 JUnit tests: JSON parsing (escapes, empty object, sorting), FQCN formatting, and a real load of the bundled resource asserting sane content (50-200 modules, `copy`/`debug`/`command` present, every entry non-blank). |

**Verified for real, not just written:** temporarily staged
`AnsibleModuleIndex.kt` and `CheckLicense.kt` into the live `src/main`
(which already has the real IntelliJ Platform SDK cached from earlier
`verifyPlugin` runs) and ran `./gradlew compileKotlin` — confirms
`LicensingFacade`, `ActionUtil.performAction`, `AnActionEvent.createEvent`
and the rest of the licensing API calls are correct against the actual
2025.2.6.2 platform classpath, not just plausible-looking Kotlin. Also ran
the real test suite (6/6 green, including a load of the actual bundled
JSON through the real classpath). Removed the temporary copies afterward
— `git status` confirmed nothing leaked into the live v0.1.x source set.

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

"Gap Hunter Labs" applied on the Monetization tab for the **FREEMIUM**
pricing model (the plugin itself stays free to install; only the v0.2
features go behind a license — as opposed to the plain "PAID" model,
which would paywall everything from install). JetBrains assigned the real
product code **`[REDACTED-PRODUCT-CODE]`** immediately, now in
`CheckLicense.PRODUCT_CODE` (was a placeholder before this) — but per
JetBrains's own text on that page, **"After applying for the Freemium or
Fully Paid pricing model, you will be contacted by our Support team"**,
so this isn't fully approved yet, just applied for.

JetBrains's own checklist for what's left (shown on that same page,
reproduced here so it doesn't get lost):

1. Inform users about the upcoming changes. (marketing, not code)
2. Obfuscate the plugin (optional — skip).
3. ~~Implement license verification calls.~~ **Done** — `CheckLicense.kt`.
4. **Create an organization and add banking information** in the
   'Vendor Information' tab. This is the real financial/legal/tax step —
   account holder's own action, not scriptable, same as flagged before.
5. Prepare the plugin on the Marketplace demo to test changes (optional).
6. Make changes in `plugin.xml` (see below — do this only when v0.2 ships).
7. Set the product descriptor's `code`, `release-date`, `release-version`,
   **and set `optional="true"`** — this is the attribute that actually
   marks the paid features as an add-on rather than making the whole
   plugin require a license, i.e. it's what makes FREEMIUM actually mean
   freemium instead of fully paid. Did not know about this attribute
   before reading JetBrains's own checklist; it changes the
   `<product-descriptor>` tag below.
8. Check that plugin.xml's `<vendor>` matches the `organization_id` created
   in step 4 — may need to change from the display name "Gap Hunter Labs"
   to whatever ID the organization gets.
9. Set up pricing, offers, and coupons (this is where the actual
   $15-19/year price and 30-day trial get configured — apparently not
   available until the steps above are further along, not simply a field
   on the current page).
10. Release the plugin.

## What's still pending before reactivating this in `src/main`

1. Add `<product-descriptor code="[REDACTED-PRODUCT-CODE]" release-date="YYYYMMDD" release-version="1" optional="true"/>`
   to `plugin.xml` — **do not** add this to the
   live v0.1.x `plugin.xml` before v0.2 actually ships in the same
   release; it would misrepresent the currently-free vault plugin as paid.
2. Decide the exact completion trigger (right now the contributor fires
   on any completion inside an Ansible-detected file; consider scoping it
   to task-list module-key position specifically, to avoid noisy
   suggestions everywhere).
3. Jinja2-in-YAML highlighting (complaint #3) — not started yet. Likely
   an `Annotator` that tags `{{ }}`/`{% %}` regions inside YAML scalars
   with distinct text attributes, not a full grammar/parser.
4. Move `detection/`, `completion/` back to `src/main/kotlin/...`, and
   `detection-tests/`, `completion-tests/` to `src/test/kotlin/...`.
5. Run the full `verifyPlugin` (6 IDEs) before publishing.

## To reactivate

1. Add `<product-descriptor>` to `plugin.xml` (step 1 above).
2. Move the folders per step 4 above.
3. `./gradlew test buildPlugin verifyPlugin`.
