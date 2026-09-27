package dev.gaphunter.ansiblecompanion.migration

import dev.gaphunter.ansiblecompanion.completion.JinjaExpressionDetector
import dev.gaphunter.ansiblecompanion.completion.JinjaRegion

/** One legacy fact variable in a chunk of text: `[start, end)` is the identifier's range in that text. */
data class LegacyFactHit(val start: Int, val end: Int, val variable: String) {
    /** The name under `ansible_facts`: `ansible_os_family` -> `os_family`. */
    val fact: String get() = variable.removePrefix("ansible_")

    /** Dot notation on purpose: it is valid in every quoting context (a `['os_family']` inside a single-quoted YAML string is not). */
    val replacement: String get() = "ansible_facts.$fact"
}

/**
 * ansible-core 2.20 deprecates injecting gathered facts as top-level
 * variables (`ansible_os_family`): `INJECT_FACTS_AS_VARS` still defaults to
 * True in 2.20 but is deprecated, and defaults to False in 2.24, after which
 * only `ansible_facts['os_family']` (or `ansible_facts.os_family`) works
 * (ansible-core 2.20 porting guide). This finds those uses, purely as text --
 * no Ansible runtime, no `ansible-lint` -- so playbooks can be checked and
 * migrated before the default flips.
 *
 * Only NAMES that are gathered facts are reported (an explicit list, from the
 * `setup` module's output), never anything merely starting with `ansible_`:
 * `ansible_host`, `ansible_user`, `ansible_connection`, `ansible_python_interpreter`,
 * `ansible_check_mode`, `ansible_version` and the `ansible_play_*` magic
 * variables are connection settings and magic variables, not facts, and keep
 * working. An identifier reached through an attribute (`hostvars[h].ansible_os_family`)
 * is left alone, and so is anything inside a quoted string in the expression.
 *
 * Where it looks: inside `{{ }}` / `{% %}`, and in the whole value of the keys
 * whose value is a bare expression (`when`, `failed_when`, `changed_when`, `until`, `that`).
 */
object LegacyFactVariableDetector {

    /** Keys whose value is a Jinja expression written WITHOUT braces. */
    val BARE_EXPRESSION_KEYS = setOf("when", "failed_when", "changed_when", "until", "that")

    private val FACT_NAMES = setOf(
        "all_ipv4_addresses", "all_ipv6_addresses", "apparmor", "architecture", "bios_date", "bios_vendor", "bios_version",
        "board_asset_tag", "board_name", "board_serial", "board_vendor", "board_version", "chassis_asset_tag",
        "chassis_serial", "chassis_vendor", "chassis_version", "cmdline", "date_time", "default_ipv4", "default_ipv6",
        "device_links", "devices", "distribution", "distribution_file_parsed", "distribution_file_path",
        "distribution_file_variety", "distribution_major_version", "distribution_release", "distribution_version", "dns",
        "domain", "effective_group_id", "effective_user_id", "env", "fibre_channel_wwn", "fips", "form_factor", "fqdn",
        "hostname", "hostnqn", "interfaces", "is_chroot", "iscsi_iqn", "kernel", "kernel_version", "lsb", "lvm", "machine",
        "machine_id", "memfree_mb", "memory_mb", "memtotal_mb", "mounts", "nodename", "os_family", "pkg_mgr", "proc_cmdline",
        "processor", "processor_cores", "processor_count", "processor_nproc", "processor_threads_per_core",
        "processor_vcpus", "product_name", "product_serial", "product_uuid", "product_version", "python", "python_version",
        "real_group_id", "real_user_id", "selinux", "selinux_python_present", "service_mgr", "ssh_host_key_dsa_public",
        "ssh_host_key_ecdsa_public", "ssh_host_key_ed25519_public", "ssh_host_key_rsa_public", "swapfree_mb",
        "swaptotal_mb", "system", "system_capabilities", "system_capabilities_enforced", "system_vendor",
        "uptime_seconds", "user_dir", "user_gecos", "user_gid", "user_id", "user_shell", "user_uid",
        "userspace_architecture", "userspace_bits", "virtualization_role", "virtualization_tech_guest",
        "virtualization_tech_host", "virtualization_type",
    )

    /** Every `ansible_<fact>` variable this check knows about. */
    val LEGACY_VARIABLES: Set<String> = FACT_NAMES.mapTo(HashSet()) { "ansible_$it" }

    /**
     * [text] is a YAML scalar's content with any surrounding quotes already removed.
     * [bareExpression]: the value is a whole expression (a `when:` condition).
     */
    fun find(text: String, bareExpression: Boolean): List<LegacyFactHit> {
        if (!text.contains("ansible_")) return emptyList()
        if (bareExpression) return findInExpression(text, 0)
        val hits = mutableListOf<LegacyFactHit>()
        for (region in JinjaExpressionDetector.scan(text).regions) {
            if (region.kind == JinjaRegion.Kind.COMMENT) continue
            val inner = region.range.first + 2 until region.range.last - 1
            if (inner.isEmpty()) continue
            hits += findInExpression(text.substring(inner.first, inner.last + 1), inner.first)
        }
        return hits
    }

    /** Scans one expression, skipping quoted strings; every hit's range is shifted by [offset]. */
    private fun findInExpression(expression: String, offset: Int): List<LegacyFactHit> {
        val hits = mutableListOf<LegacyFactHit>()
        var quote: Char? = null
        var i = 0
        while (i < expression.length) {
            val c = expression[i]
            if (quote != null) {
                if (c == '\\') i++ else if (c == quote) quote = null
                i++
                continue
            }
            if (c == '\'' || c == '"') {
                quote = c
                i++
                continue
            }
            if (isIdentifierStart(c) && (i == 0 || (!isIdentifierPart(expression[i - 1]) && expression[i - 1] != '.'))) {
                var end = i + 1
                while (end < expression.length && isIdentifierPart(expression[end])) end++
                val word = expression.substring(i, end)
                if (word in LEGACY_VARIABLES) hits += LegacyFactHit(offset + i, offset + end, word)
                i = end
                continue
            }
            i++
        }
        return hits
    }

    private fun isIdentifierStart(c: Char) = c == '_' || c.isLetter()
    private fun isIdentifierPart(c: Char) = c == '_' || c.isLetterOrDigit()
}
