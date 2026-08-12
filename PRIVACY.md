# Privacy Policy — Ansible Companion

**Effective date:** 2026-08-04

Ansible Companion is a Gap Hunter Labs plugin for IntelliJ Platform IDEs.
This policy is short because the plugin's design makes it short: there
is nothing to disclose beyond what's below.

## What this plugin collects

**Nothing.** Ansible Companion does not collect, store, transmit, or sell
any data — no source code, no file contents, no usage analytics, no
telemetry, no crash reports, no personally identifiable information.

## Network access

**None.** Ansible Companion makes zero network calls during normal
operation. Every check, detection, and analysis it performs runs
entirely in-process, inside your IDE, against the code already open in
your editor. Nothing you write, open, or edit is ever sent anywhere.

The one narrow exception: this plugin includes an optional paid Pro
tier. The IDE's built-in `LicensingFacade` may validate a purchased
license locally against JetBrains' own licensing infrastructure — the
same mechanism every commercial JetBrains-ecosystem plugin uses,
entirely separate from and unrelated to the plugin's actual
detection/analysis logic, which still never leaves your machine.

## Third parties

None. Ansible Companion has no third-party SDKs, no analytics libraries,
no ad networks, no external dependencies that phone home.

## Changes to this policy

If this ever changes, this file will be updated and the change will be
noted in the plugin's `CHANGELOG.md`.

## Contact

Questions about this policy: **gaphunterlabs@gmail.com**
