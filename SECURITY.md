# Security Policy

## Supported versions

Security fixes are released for the latest version of Ansible Companion published on
JetBrains Marketplace. Update from the IDE: **Settings | Plugins | Installed**.

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

## What to expect

- Acknowledgement within **5 business days**.
- Fixes ship as a new JetBrains Marketplace release, listed under a
  **Security** heading in [CHANGELOG.md](CHANGELOG.md). Reporters are
  credited unless they ask to stay anonymous.

## Scope

In scope: the code in this repository and the plugin as published on
JetBrains Marketplace, for example code execution triggered by opening
project files, network access not described in [PRIVACY.md](PRIVACY.md),
or exposure of file contents or credentials.

Out of scope:

- vulnerabilities in the IntelliJ Platform or JetBrains IDEs: report those
  to JetBrains;
- issues the plugin *detects* in a user's own code (that is its purpose);
- false positives or false negatives of inspections: use a regular
  [GitHub issue](https://github.com/GapHunterLabs/ansible-companion/issues).

## Release integrity

Release archives are signed with the publisher's certificate, and JetBrains
Marketplace verifies the signature when a version is uploaded.
