package dev.gaphunter.ansiblecompanion.completion

/**
 * The published plugin's side of the local Pro test switch: always closed.
 *
 * The working switch (`src/devSandbox/kotlin`) is compiled only when the build
 * is started with `-PdevSandbox=true`, which only local runIde sessions do.
 * Every other build compiles this file, so a published plugin opens Pro only
 * with a real license.
 */
object DevSandbox {
    fun isForced(): Boolean = false
}
