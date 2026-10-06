# Security Policy

## Supported versions

Security fixes are released for the latest version of Ansible Companion published
on JetBrains Marketplace. Update from the IDE: **Settings | Plugins | Installed**.

Security support for Ansible Companion is provided at least until
**December 2031**.

Earlier versions are not removed from JetBrains Marketplace, but they do not receive
fixes: update to the latest version.

## Reporting a vulnerability

Report suspected vulnerabilities privately, not in a public issue,
discussion, or pull request.

- **Preferred:** GitHub private vulnerability reporting, the
  **Report a vulnerability** button under this repository's **Security** tab.
- **Alternative:** email **gaphunterlabs@gmail.com** with the subject
  `SECURITY: Ansible Companion`.

Include the plugin version, the IDE name and build number, the operating
system, steps to reproduce (or a minimal sample project), and the impact as
you understand it.

If there is evidence that the issue is being exploited in the wild, say so in
the first message. Those reports are handled first.

## What to expect

- Acknowledgement within **5 business days**.
- An assessment (confirmed or not, severity, and plan) within **10 business
  days**.
- Fix targets by severity (CVSS): critical within 7 days, high within
  30 days, medium within 90 days, low in a later release.
- Coordinated disclosure: details stay private until a fix is released, for
  up to 90 days unless another date is agreed with the reporter.
- Fixes ship as a dedicated security release on JetBrains Marketplace, separate from
  new features where possible. Each fix is published as a GitHub Security
  Advisory, with a CVE when applicable, and listed under a **Security** heading
  in [CHANGELOG.md](CHANGELOG.md).
- Reporters are credited unless they ask to stay anonymous.

## Third-party components

If the issue is in a bundled third-party component, report it to that
project as well. Fixes for bundled components ship in a new release of
Ansible Companion.

## Scope

In scope: the code in this repository and the plugin as published on
JetBrains Marketplace, for example code execution triggered by opening project
files, network access not described in [PRIVACY.md](PRIVACY.md), or exposure
of file contents or credentials.

Out of scope:

- vulnerabilities in the IntelliJ Platform or JetBrains IDEs: report those to JetBrains;
- issues the plugin *detects* in a user's own code (that is its purpose);
- false positives or false negatives of inspections: use a regular
  [GitHub issue](https://github.com/GapHunterLabs/ansible-companion/issues).

## Good-faith research

Research that follows this policy, avoids privacy violations and service
disruption, and gives a reasonable time to fix before disclosure is
welcome, and will not be the subject of legal action by the maintainer.

## Release integrity

Release archives are signed with the publisher's certificate, and JetBrains
Marketplace verifies the signature when a version is uploaded.
