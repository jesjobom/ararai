package com.jesjobom.ararai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationResourcesTest {
    @Test
    fun `Brazilian Portuguese defines every default string resource`() {
        val defaults = stringNames(resourceFile("values/strings.xml"))
        val portuguese = stringNames(resourceFile("values-pt-rBR/strings.xml"))

        assertEquals("Missing or extra pt-BR string resources", defaults, portuguese)
    }

    @Test
    fun `Compose screens do not introduce direct literal copy`() {
        val uiDirectory = sourceDirectory("java/com/jesjobom/ararai/ui")
        val forbidden =
            listOf(
                Regex("""\bText\(\s*\"[A-Za-z]"""),
                Regex("""\btext\s*=\s*\"[A-Za-z]"""),
                Regex("""\bcontentDescription\s*=\s*\"[A-Za-z]"""),
                Regex("""\btitle\s*=\s*\"[A-Za-z]"""),
            )
        val violations =
            uiDirectory
                .walkTopDown()
                .filter {
                    it.isFile &&
                        it.extension == "kt" &&
                        it.name != "ModelStatusUiState.kt" &&
                        "@Composable" in it.readText()
                }
                .flatMap { file ->
                    file.readLines().mapIndexedNotNull { index, line ->
                        line.takeIf { candidate -> forbidden.any { it.containsMatchIn(candidate) } }
                            ?.let { "${file.name}:${index + 1}: ${it.trim()}" }
                    }
                }.toList()

        assertTrue("Direct user-visible Compose strings:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    private fun stringNames(file: File): Set<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val strings = document.getElementsByTagName("string")
        return (0 until strings.length)
            .map { strings.item(it) as Element }
            .mapTo(sortedSetOf()) { it.getAttribute("name") }
    }

    private fun resourceFile(path: String): File = sourceFile("res/$path")

    private fun sourceDirectory(path: String): File = sourceFile(path).also {
        assertTrue("Unable to locate source directory $path", it.isDirectory)
    }

    private fun sourceFile(path: String): File {
        val candidates = listOf(File("app/src/main/$path"), File("src/main/$path"))
        return candidates.firstOrNull(File::exists)
            ?: error("Unable to locate $path from ${File(".").absolutePath}")
    }
}
