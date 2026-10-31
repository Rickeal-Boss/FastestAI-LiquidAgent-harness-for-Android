package com.rickeal.liquidharness.core.skills

import com.rickeal.liquidharness.core.agent.skill.SkillBody
import com.rickeal.liquidharness.core.agent.skill.SkillMeta
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [SkillCatalogImpl] 的合并/排序/搜索语义。 */
class SkillCatalogImplTest {

    @Test
    fun duplicateNameKeepsFirstSource() {
        runBlocking {
            val first = FakeSource("file", listOf(body("shared", "from first")))
            val second = FakeSource("asset", listOf(body("shared", "from second")))
            val catalog = SkillCatalogImpl(listOf(first, second))

            val meta = catalog.list().single()
            assertEquals("from first", meta.description)
            assertEquals("file", meta.source)
        }
    }

    @Test
    fun listIsSortedByName() {
        runBlocking {
            val source = FakeSource("file", listOf(body("zeta", "z"), body("alpha", "a"), body("mid", "m")))
            val catalog = SkillCatalogImpl(listOf(source))

            assertEquals(listOf("alpha", "mid", "zeta"), catalog.list().map { it.name })
        }
    }

    @Test
    fun listOrderIsStableAcrossCalls() {
        runBlocking {
            val source = FakeSource("file", listOf(body("b", "b"), body("a", "a"), body("c", "c")))
            val catalog = SkillCatalogImpl(listOf(source))

            assertEquals(catalog.list(), catalog.list())
        }
    }

    @Test
    fun loadReturnsExactMatch() {
        runBlocking {
            val catalog = SkillCatalogImpl(listOf(FakeSource("file", listOf(body("alpha", "a")))))

            val loaded = catalog.load("alpha")
            assertNotNull(loaded)
            assertEquals("body-alpha", loaded.body)
        }
    }

    @Test
    fun loadReturnsNullWhenMissing() {
        runBlocking {
            val catalog = SkillCatalogImpl(listOf(FakeSource("file", listOf(body("alpha", "a")))))

            assertNull(catalog.load("Alpha"))
        }
    }

    @Test
    fun searchMatchesNameAndDescription() {
        runBlocking {
            val source = FakeSource(
                "file",
                listOf(body("pdf-toolkit", "Extract text"), body("notes", "Organize PDF scans")),
            )
            val catalog = SkillCatalogImpl(listOf(source))

            assertEquals(listOf("notes", "pdf-toolkit"), catalog.search("PDF").map { it.name })
        }
    }

    @Test
    fun searchReturnsEmptyWhenNothingMatches() {
        runBlocking {
            val catalog = SkillCatalogImpl(listOf(FakeSource("file", listOf(body("alpha", "a")))))

            assertTrue(catalog.search("zzz").isEmpty())
        }
    }

    @Test
    fun blankQueryReturnsAll() {
        runBlocking {
            val catalog = SkillCatalogImpl(listOf(FakeSource("file", listOf(body("b", "b"), body("a", "a")))))

            assertEquals(listOf("a", "b"), catalog.search("   ").map { it.name })
        }
    }

    @Test
    fun refreshDropsCacheAndRescans() {
        runBlocking {
            val source = FakeSource("file", listOf(body("a", "a")))
            val catalog = SkillCatalogImpl(listOf(source))

            catalog.list()
            catalog.list()
            assertEquals(1, source.scans)

            catalog.refresh()
            catalog.list()
            assertEquals(2, source.scans)
        }
    }

    private fun body(name: String, description: String): SkillBody =
        SkillBody(SkillMeta(name, description, emptyList(), "test"), "body-$name")

    private class FakeSource(
        override val id: String,
        private val bodies: List<SkillBody>,
    ) : SkillSource {
        var scans: Int = 0
            private set

        override suspend fun scan(): List<SkillBody> {
            scans += 1
            return bodies
        }
    }
}
