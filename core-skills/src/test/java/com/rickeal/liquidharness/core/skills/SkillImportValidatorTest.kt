package com.rickeal.liquidharness.core.skills

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [SkillImportValidator] 的判定顺序与边界。 */
class SkillImportValidatorTest {

    private val validator = SkillImportValidator()

    @Test
    fun acceptsPlainSkillArchive() {
        val verdict = validator.validate(
            listOf("pdf-toolkit/SKILL.md" to 900L, "pdf-toolkit/notes.txt" to 100L),
        )

        assertTrue(verdict is ImportVerdict.Ok, "expected Ok but was $verdict")
        val ok = verdict as ImportVerdict.Ok
        assertEquals(2, ok.entryCount)
        assertEquals(1000L, ok.bytes)
    }

    @Test
    fun rejectsOversizedArchive() {
        val verdict = validator.validate(listOf("SKILL.md" to 1L), totalBytes = 3L * 1024L * 1024L)

        assertEquals(SkillImportValidator.REASON_TOO_LARGE, reason(verdict))
    }

    @Test
    fun rejectsTooManyEntries() {
        val entries = (1..60).map { "f$it.txt" to 1L } + ("SKILL.md" to 1L)

        assertEquals(SkillImportValidator.REASON_TOO_MANY_FILES, reason(validator.validate(entries)))
    }

    @Test
    fun rejectsExecutablePayload() {
        val verdict = validator.validate(listOf("SKILL.md" to 10L, "payload.dex" to 10L))

        assertEquals(SkillImportValidator.REASON_EXECUTABLE_PAYLOAD, reason(verdict))
    }

    @Test
    fun rejectsParentDirectoryEscape() {
        val verdict = validator.validate(listOf("SKILL.md" to 10L, "../evil" to 10L))

        assertEquals(SkillImportValidator.REASON_PATH_ESCAPE, reason(verdict))
    }

    @Test
    fun rejectsAbsolutePath() {
        val verdict = validator.validate(listOf("SKILL.md" to 10L, "/etc/passwd" to 10L))

        assertEquals(SkillImportValidator.REASON_PATH_ESCAPE, reason(verdict))
    }

    @Test
    fun rejectsArchiveWithoutSkillMd() {
        val verdict = validator.validate(listOf("readme.md" to 10L, "notes/todo.txt" to 10L))

        assertEquals(SkillImportValidator.REASON_MISSING_SKILL_MD, reason(verdict))
    }

    @Test
    fun reportsMostSevereReasonFirst() {
        // 同时超体积、超条目、含可执行载荷 ⇒ 只报最严重的体积问题。
        val entries = (1..60).map { "f$it.dex" to 100_000L }

        assertEquals(SkillImportValidator.REASON_TOO_LARGE, reason(validator.validate(entries)))
    }

    private fun reason(verdict: ImportVerdict): String {
        assertTrue(verdict is ImportVerdict.Rejected, "expected Rejected but was $verdict")
        return (verdict as ImportVerdict.Rejected).rejection.reason
    }
}
