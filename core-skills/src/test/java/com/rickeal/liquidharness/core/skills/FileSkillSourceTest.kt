package com.rickeal.liquidharness.core.skills

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [FileSkillSource] 的扫描语义（用临时目录，不依赖 Android）。 */
class FileSkillSourceTest {

    @Test
    fun scansNestedAndFlatMarkdownFiles() {
        val root = tempDir()
        try {
            write(root, "alpha/SKILL.md", skill("alpha", "first"))
            write(root, "beta.md", skill("beta", "second"))

            val bodies = runBlocking { FileSkillSource(root).scan() }

            assertEquals(listOf("alpha", "beta"), bodies.map { it.meta.name }.sorted())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun skipsFilesWithoutFrontmatter() {
        val root = tempDir()
        try {
            write(root, "alpha/SKILL.md", skill("alpha", "first"))
            write(root, "broken/SKILL.md", "# no frontmatter at all")
            write(root, "notes.md", "just a plain note")

            val bodies = runBlocking { FileSkillSource(root).scan() }

            assertEquals(listOf("alpha"), bodies.map { it.meta.name })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun doesNotRecurseDeeperThanOneLevel() {
        val root = tempDir()
        try {
            write(root, "outer/inner/SKILL.md", skill("inner", "too deep"))

            val bodies = runBlocking { FileSkillSource(root).scan() }

            assertTrue(bodies.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun returnsEmptyWhenRootMissing() {
        val parent = tempDir()
        try {
            val bodies = runBlocking { FileSkillSource(File(parent, "does-not-exist")).scan() }

            assertTrue(bodies.isEmpty())
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun stampsConfiguredSourceId() {
        val root = tempDir()
        try {
            write(root, "alpha/SKILL.md", skill("alpha", "first"))

            val bodies = runBlocking { FileSkillSource(root, "user").scan() }

            assertEquals("user", bodies.single().meta.source)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun tempDir(): File = Files.createTempDirectory("core-skills-test").toFile()

    private fun write(root: File, path: String, text: String) {
        val file = File(root, path)
        file.parentFile?.mkdirs()
        file.writeText(text, Charsets.UTF_8)
    }

    private fun skill(name: String, description: String): String =
        "---\nname: $name\ndescription: $description\n---\n# $name\n\nDo the thing.\n"
}
