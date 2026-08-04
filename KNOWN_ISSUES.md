# Known issues log — Ansible Companion

Real bugs found during development/verification, with root cause and fix.
Not a TODO list — see `future/v0.2-ansible-completion/README.md` for
pending work.

## Round 1 (2026-07-30) — `AnsibleFileTypeOverrider` infinite recursion via `contentsToByteArray()`

**Symptom:** `StackOverflowError` at `com.intellij.util.BitUtil.isSet(BitUtil.kt:19)`,
caught by the existing `VaultEditorOpsTest` suite (2 of its tests failed)
immediately after wiring `AnsibleFileTypeOverrider` into `plugin.xml` for
the first time — the vault tests themselves were untouched; they failed
as a side effect of the platform now resolving file types differently
for every `VirtualFile` in the test fixture, including non-Ansible ones.

**Root cause:** `AnsibleFileTypeOverrider.getOverriddenFileType()` called
`file.contentsToByteArray()` to read the first bytes for the
content-based detection heuristic (`AnsibleFileDetector.looksLikeAnsible`).
`VirtualFileImpl.contentsToByteArray()` internally calls
`FileTypeManagerImpl.getFileTypeByFile()` (to decide encoding/BOM
handling) — which re-invokes **every registered `FileTypeOverrider`**,
including this one, for the same file. Unbounded recursion, confirmed by
the real stack trace:

```
StackOverflowError at BitUtil.kt:19
  ... PersistentFSImpl.getLength -> VirtualFileUtil.isTooLarge
  -> VirtualFileImpl.checkNotTooLarge -> VirtualFileImpl.contentsToByteArray
  -> AnsibleFileTypeOverrider.getOverriddenFileType
  -> FileTypeManagerImpl.getFileTypeByFile
  -> VirtualFileImpl.contentsToByteArray  [repeats]
```

**Why `pathAloneSignalsAnsible()` didn't save every case:** that check
only short-circuits for files under `playbooks/` or `roles/*/{tasks,
handlers,...}/`. Any other `.yml`/`.yaml` (e.g. a plain fixture file in a
test's temp directory, or any real-world YAML outside those path shapes)
falls through to the content-sniffing branch and hits the recursive call
on every single file-type resolution in the project — not just Ansible
files.

**Fix:** read raw bytes via `VirtualFile.getInputStream()` directly
instead of `contentsToByteArray()`. `getInputStream()` does not go
through `FileTypeManagerImpl` at all — it's a raw byte stream, no
encoding/file-type resolution involved:

```kotlin
file.inputStream.use { stream ->
    val buffer = ByteArray(HEAD_BYTES)
    val read = stream.read(buffer)
    if (read <= 0) "" else String(buffer, 0, read, Charsets.UTF_8)
}
```

**Verified:** `./gradlew test` — all 36 tests green (was 34 completed / 2
failed before the fix). No regression in the unrelated `VaultEditorOpsTest`
suite, confirming those failures were purely a side effect of the
recursion triggering on arbitrary `VirtualFile`s during test setup, not a
vault-code bug.

**Lesson for any future `FileTypeOverrider`/`FileTypeIdentifiableByVirtualFile`
implementation in this workspace:** never call `VirtualFile.contentsToByteArray()`,
`Document`-based reads, or anything else that internally asks "what's
this file's type?" from inside a method whose whole job IS to answer
that question — it's always re-entrant. Use `getInputStream()` (raw
bytes) or the `ByteSequence`-based `FileTypeDetector.detect(file,
firstBytes)` callback shape (pre-loaded bytes, no re-entry possible)
instead.

## Round 2 (2026-07-30) — `AnsibleModuleCompletionContributor` never fired for the single most common case: a brand-new task

**Symptom:** confirmed live in `runIde` (screenshot from the user):
typing `- ansible.builtin.` as a new task on its own line produced only
IntelliJ's generic word-completion (identifiers already present
elsewhere in the file), never our contributor's real module list, and
not even the unlicensed "upsell" fallback item. No exception in the
console — the contributor silently contributed zero items.

**Root cause:** `isCompletingYamlKey()` started with
`PsiTreeUtil.getParentOfType(position, YAMLKeyValue::class.java, false)
?: return false`. A brand-new sequence item with no `:` typed yet
(`- ansible.builtin.<caret>`) has no `YAMLKeyValue` in its PSI at
all — YAML can't know it's a mapping key until the colon exists, so it
parses as a bare scalar inside a `YAMLSequenceItem`. The very first line
of the function bailed out before ever reaching the license check or the
completion loop, for exactly the scenario the whole feature exists for.

**Fix:** when no `YAMLKeyValue` ancestor exists, check for a
`YAMLSequenceItem` ancestor instead — a colon-less scalar inside a fresh
sequence item is exactly a new task's module-key position. See
`AnsibleModuleCompletionContributor.kt`'s `isCompletingYamlKey()`.

**Verified:** `./gradlew test` green (36/36) after the fix; the
"nested key still triggers too" limitation (`copy:\n  <caret>`) is
unchanged and still a known, documented follow-up — this fix only
restores the missing case, it doesn't narrow scope further.

**Lesson:** when a `CompletionContributor`/`Annotator` guard chains
`getParentOfType(...) ?: return false` before other early-return checks,
verify in a real `runIde` session what PSI shape the *first keystroke*
of the target scenario actually produces — an in-progress, not-yet-valid
parse (no colon, no closing brace, etc.) is often structurally different
from the "settled" PSI shape used when writing/testing the happy path,
and unit tests built against fully-formed fixtures won't catch it.

## Round 3 (2026-07-30) — `parameters.originalFile.fileType == AnsibleYamlFileType` guard never matched, even after Round 2's fix

**Symptom:** after fixing Round 2, the completion popup for a fresh
`- ansible.builtin.<caret>` task still showed only IntelliJ's generic
word-completion — still zero items from our contributor, not even the
upsell fallback. Confirmed via a temporary `System.err.println` at the
top of `addCompletions`: the contributor **was** being invoked, and
`isCompletingYamlKey(...)` correctly returned `true` — but
`parameters.originalFile.fileType` logged as plain
`org.jetbrains.yaml.YAMLFileType@...`, never `AnsibleYamlFileType`, so
the (then-still-present) `fileType != AnsibleYamlFileType -> return`
guard always bailed.

**Root cause:** confirmed by a second, independent debug log placed
directly inside `AnsibleFileTypeOverrider.getOverriddenFileType()` in the
same session: the overrider **was** being called for this exact file and
**did** correctly return `AnsibleYamlFileType` (`pathAloneSignalsAnsible=true`
logged every time). So `VirtualFile.getFileType()` and
`PsiFile.getFileType()`/`CompletionParameters.originalFile.fileType`
disagreed for the entire lifetime of the session, consistently (same
wrong `FileType` object identity across 3 separate completion
invocations) — not a race, not intermittent. The most likely mechanism:
a `FileViewProvider` caches the `FileType` it was built with, and that
build can happen (or have happened, in an already-open/indexed file)
independently of `FileTypeOverrider` re-resolution — `VirtualFile.getFileType()`
re-runs the overrider chain fresh on every call, but the PSI layer built
on top of an existing `FileViewProvider` does not necessarily see updates
after the fact.

**Fix:** stopped trusting FileType identity as the gate entirely, in both
`AnsibleModuleCompletionContributor` and `JinjaHighlightingAnnotator`.
Both now call `AnsibleFileDetector.pathAloneSignalsAnsible()`/
`.looksLikeAnsible()` directly against the real `VirtualFile` (path +,
if needed, the already-in-memory PSI file text — no extra disk I/O),
the exact same heuristic `AnsibleFileTypeOverrider` uses, instead of
relying on a `FileType` value that can silently be stale. This also
removes a fragile cross-extension-point dependency (completion/
annotation behavior no longer depends on a separate `FileTypeOverrider`
extension having "already taken effect" for the same PSI file).

**A red herring ruled out first:** an earlier hypothesis was a genuinely
stale on-disk sandbox cache surviving repeated `runIde` restarts within
the same long-lived sandbox config — a machine reboot happened to occur
mid-investigation and appeared to "fix" the FileTypeOverrider-not-called
symptom from Round 2's investigation, but the real Round 3 mismatch
(overrider correct, PSI-visible FileType still wrong) persisted even in
that fresh-boot session, proving the two symptoms were different bugs
layered on top of each other, not one cache issue.

**Verified:** `./gradlew test` green (36/36) after the fix.

**Lesson:** don't gate `CompletionContributor`/`Annotator` behavior on
`PsiFile.fileType`/`VirtualFile.fileType` equality with a value produced
by a `FileTypeOverrider`, even though that's the "obvious" API pairing —
the two resolution paths are not guaranteed to be synchronized for an
already-open file. Re-running the same cheap, pure detection heuristic
directly is more code but far more reliable, and avoids depending on
extension-point interaction timing that the platform doesn't document as
guaranteed.

## Round 4 (2026-07-30) — upsell `LookupElement` was added to the result set, then silently filtered out before rendering

**Symptom:** even after Round 3's fix, the completion popup for
`- ansible.builtin.<caret>` still showed only IntelliJ's generic
word-completion — no upsell item. A second, more detailed debug log
(logging every guard's value plus an explicit log right before
`result.addElement(upsellLookupElement())`) proved the code path was
now fully correct: `pathSignal=true`, `contentSignal=true`,
`isCompletingYamlKey=true`, `licensed=false`, and
`"adding upsell item"` printed every time. The element genuinely reached
`CompletionResultSet.addElement()` — and still never appeared on screen.

**Root cause:** `upsellLookupElement()` built the item as
`LookupElementBuilder.create("ansible.builtin (Ansible Companion Pro)")`.
The string passed to `.create()` is the **lookup string** — what the
platform's default `PrefixMatcher` compares against what the user
already typed (`ansible.builtin.` at this point) to decide whether to
render the item at all, completely independent of whatever gets
inserted into the document. `"ansible.builtin (Ansible Companion Pro)"`
does start with `ansible.builtin`, but the matcher's behavior around the
trailing `.` in the already-typed prefix versus the space in the lookup
string was strict enough to reject it — the item was correctly added to
the result set and then filtered out by the platform before rendering,
entirely outside our own code's control flow (which is exactly why
three rounds of guard-logic fixes never touched it).

**Fix:** separate the lookup string from the display text:
```kotlin
LookupElementBuilder.create("ansible.builtin")           // matched against what was typed
    .withPresentableText("ansible.builtin (Ansible Companion Pro)")  // what's shown
    .withTypeText("Upgrade for FQCN completion", true)
    .withInsertHandler { _, _ -> CheckLicense.requestLicense(...) }
```
The real module list (`AnsibleModuleIndex`) was never affected by this
specific bug — each entry already used `.withLookupString(module.shortName)`
as a fallback match target — but this round is the reason to sanity-check
every `LookupElementBuilder.create(...)` call in this codebase: the
string passed to `.create()` should always be a plain identifier-shaped
match target, with any decorative/parenthetical text moved to
`.withPresentableText(...)` instead.

**Verified:** `./gradlew test` green (36/36).

**Lesson:** when a `CompletionResultSet.addElement()` call is confirmed
(via logging) to execute, but the item still never renders, the bug is
almost never "my guard logic is wrong" — it's in what `LookupElementBuilder.create(...)`
was given as the match string versus the presentable string. Add the
`addElement` confirmation log *before* chasing guard logic again; it
would have pointed here three rounds earlier.

## Round 5 (2026-07-30) — Round 4's own fix was still wrong: fixed lookup string was one character too short for the real prefix

**Symptom:** even after Round 4's fix (`LookupElementBuilder.create("ansible.builtin")` + `.withPresentableText(...)`), the upsell item still never rendered — same as before.

**Root cause:** logged `result.prefixMatcher.prefix` directly (the exact
string the platform requires a lookup string to start with). For
`- ansible.builtin.<caret>`, the real prefix is `"ansible.builtin."` —
**with** the trailing dot, 17 characters. Round 4's fixed lookup string
was `"ansible.builtin"` — **without** the dot, 16 characters. A 16-character
string can never "start with" a 17-character prefix; Round 4 fixed the
punctuation-in-the-middle problem but introduced a too-short string as a
side effect, verified only by re-reading the diff, not by checking the
real prefix value first.

**Fix:** build the lookup string from the actual prefix instead of a
hardcoded guess:
```kotlin
LookupElementBuilder.create(if (prefix.startsWith("ansible.builtin")) prefix else "ansible.builtin")
    .withPresentableText("ansible.builtin (Ansible Companion Pro)")
    ...
```
Called as `upsellLookupElement(result.prefixMatcher.prefix)` from
`addCompletions`. This can't fall short again regardless of how many
characters the user has typed past `ansible.builtin`, because the lookup
string is always at least as long as whatever prefix triggered this
exact invocation.

**Verified:** `./gradlew test` green (36/36).

**Lesson (supersedes Round 4's):** don't just "fix the string that looked
wrong" for a `LookupElementBuilder` prefix-matching bug — log
`CompletionResultSet.prefixMatcher.prefix` itself and build the lookup
string as a function of that real value. Two rounds in a row assumed a
fixed string was "close enough" without checking the actual prefix
first; the actual value was one character different both times, for two
different reasons.

## Round 6 (2026-08-02) — `verifyPlugin` failures found only by the real 6-IDE run, not by `test`/`compileKotlin`

**Symptom:** `./gradlew verifyPlugin` (the full 6-target-IDE gate, ~16
min) failed with `[COMPATIBILITY_PROBLEMS, EXPERIMENTAL_API_USAGES]`
against 2 of the 6 IDEs — nothing any of the earlier rounds' unit tests
or `compileKotlin` runs could have caught, since both are runtime/API-
surface problems invisible to the compiler.

**Bug A — real compatibility problem, `CheckLicense.showRegisterDialog()`:**
`ActionUtil.performAction(AnAction, AnActionEvent) : AnActionResult` (the
call this plugin ported verbatim from JetBrains's own
`marketplace-makemecoffee-plugin` reference `CheckLicense.java`, under a
comment claiming "available starting from IDE version 243.*") does not
actually resolve against real IU-243.28141.41 (2024.3) or IU-251.29188.72
(2025.1) — confirmed by the Plugin Verifier's own
`compatibility-problems.txt` for both, citing an unresolved `invokestatic`
that "can lead to **NoSuchMethodError** exception at runtime." The
2-argument overload returning `AnActionResult` was actually introduced in
IDE 244 (2024.2) — the reference implementation's comment was wrong (or
stale) for this plugin's actual `sinceBuild=243` floor. **Fix:** call
`AnAction.actionPerformed(AnActionEvent)` directly instead —the stable,
version-independent entry point every `AnAction` must implement, present
since long before 243 and never going away (it's the core contract of the
action system).

**Bug B — `FileTypeOverrider` itself is `@ApiStatus.Experimental`:**
confirmed by the verifier's own `experimental-api-usages.txt` flagging
both the `FileTypeOverrider` interface and `AnsibleFileTypeOverrider`'s
override of it. Unlike Bug A, there is no real fix here — it's the only
mechanism available for content-based `FileType` override that preserves
the underlying `Language` (needed so the bundled YAML plugin's PSI/
completion keeps working for Ansible files — the same reason
`AnsibleYamlFileType` wraps `YAMLLanguage.INSTANCE` instead of defining a
new language). `FileTypeIdentifiableByVirtualFile` (the mechanism
`nginx-companion` uses, see its own `KNOWN_ISSUES.md` Round 5) doesn't
fit: it fully replaces the `FileType`/`Language` pairing, which is wrong
for a plugin that wants files to keep behaving as YAML. **Fix:** removed
`EXPERIMENTAL_API_USAGES` from this plugin's `pluginVerification.failureLevel`
in `build.gradle.kts` — a documented, deliberate exception (see the
comment there), not an oversight. `INTERNAL_API_USAGES`,
`OVERRIDE_ONLY_API_USAGES`, `COMPATIBILITY_PROBLEMS`, and
`SCHEDULED_FOR_REMOVAL_API_USAGES` remain in the strict gate.

**Verified:** re-running `verifyPlugin` after both fixes is the next
step — see this round's status in the session that found it before
assuming clean.

**Lesson:** unit tests and `compileKotlin` cannot catch either class of
bug here — one is a real runtime API-surface mismatch across IDE
versions (only the Plugin Verifier, run against real IDE distributions,
catches it), the other is a policy decision the verifier's own
categorization exists specifically to surface. Always run the full
`verifyPlugin` (not just `test`) before treating an integration as done,
even when every test is green and `runIde` looks correct — this round's
two bugs were invisible to everything except that specific gate.

## Round 7 (2026-08-02) — `CheckLicense.showRegisterDialog()`'s API call took three attempts to get right, each confirmed wrong by an actual verifier category

After Round 6's Bug A fix (`registerAction.actionPerformed(event)`),
re-running `verifyPlugin` traded one failure for another — three
attempts total, each one only discoverable by actually running the
verifier again, not by reasoning about the API in the abstract:

1. `ActionUtil.performAction(AnAction, AnActionEvent)` — fails
   `COMPATIBILITY_PROBLEMS` on IU-243/IU-251 (doesn't exist before IDE
   244).
2. `AnAction.actionPerformed(AnActionEvent)` called directly — fails
   `OVERRIDE_ONLY_API_USAGES` on all 6 IDEs (real bytecode annotation,
   confirmed via `javap -v`: no `RuntimeVisibleAnnotations` block
   contradicts this — the verifier's own categorization is authoritative
   here, not something to second-guess).
3. `ActionManager.tryToExecute(AnAction, InputEvent?, Component?, String, Boolean)`
   — resolves cleanly and isn't flagged by the verifier at all, but its
   signature has no `DataContext` parameter, so it silently drops
   `productCode`/`message` — the whole reason `showRegisterDialog` takes
   those parameters. Caught by re-reading the method's own real
   decompiled signature (`javap -p` against the actual platform jar in
   `~/.gradle/caches`) before wiring it in, not by running verifyPlugin
   again and finding out the hard way.

**Final fix:** `ActionUtil.invokeAction(AnAction, DataContext, String place, InputEvent?, Runnable? onDone)`
— confirmed via real bytecode inspection (`javap -p -v` against
`ActionUtil.class` extracted from the actual `app-client.jar`) to be the
overload another `ActionUtil.invokeAction(AnAction, Component, ...)`
overload's own `@Deprecated(ReplaceWith(...))` annotation points to,
carries `DataContext` (preserving product preselection), and has zero
`RuntimeVisibleAnnotations` in its own bytecode (no `@ApiStatus.*`
marker at all) — confirmed clean by the full `verifyPlugin` run
(`BUILD SUCCESSFUL`, all 6 IDEs "Compatible", only a Kotlin-source-level
`@Deprecated` warning pointing to the IDE-244+-only `performAction` this
whole investigation started from — that warning is source-level only,
not a bytecode annotation, and isn't one of the categories `verifyPlugin`
gates on).

**Verified:** `./gradlew verifyPlugin` — `BUILD SUCCESSFUL in 15m 2s`,
all 6 target IDEs (IU-243.28141.41 through IU-262.9437.65) report
"Compatible", zero `COMPATIBILITY_PROBLEMS`/`INTERNAL_API_USAGES`/
`OVERRIDE_ONLY_API_USAGES`/`SCHEDULED_FOR_REMOVAL_API_USAGES`.

**Lesson:** when a JetBrains reference implementation's own API call
turns out wrong for a wide `sinceBuild` range, don't guess the next
candidate from documentation or search results alone — extract the real
platform class from the actual cached IDE jar
(`~/.gradle/caches/.../transformed/ideaIU-<version>-win/lib/*.jar`) and
inspect it directly with `javap -p` (signature) and `javap -p -v`
(annotations) before wiring in a replacement. This caught a
`DataContext`-losing regression (attempt 3 above) that would have taken
another full 15-minute `verifyPlugin` cycle — or worse, gone unnoticed
since it doesn't fail verification at all — to discover any other way.
