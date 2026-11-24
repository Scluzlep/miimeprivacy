package com.scluzlep.miimeprivacy.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EmojiPatchConfigTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun assignsLargerOrderValueToEarlierGroupIncludingCombinationPackage() {
        val reversed = EmojiCatalog.orderGroups.map(EmojiGroup::packageId).reversed()
        val configuration = EmojiPatchConfig(groupOrder = reversed)

        assertTrue(2 in reversed)
        assertEquals(11_000L, configuration.orderValue(reversed.first()))
        assertEquals(1_000L, configuration.orderValue(reversed.last()))
        assertTrue(configuration.orderValue(reversed[2]) > configuration.orderValue(reversed[3]))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsIncompleteGroupOrder() {
        EmojiPatchConfig(groupOrder = listOf(3, 4))
    }

    @Test
    fun countsInfoXmlEntriesInsteadOfSkinVariantImages() {
        val packageRoot = temporaryFolder.newFolder("emojipackage")
        val people = File(packageRoot, "4_9000").apply { mkdirs() }
        File(people, "info.xml").writeText(
            """
                <expList>
                  <exppkg>
                    <exp name="20000"/>
                    <exp name="20001" supportSkin="1"/>
                  </exppkg>
                </expList>
            """.trimIndent(),
        )
        File(people, "emoji").mkdirs()
        repeat(7) { File(people, "emoji/2000$it.png").writeText("not relevant") }

        val counts = InstalledEmojiGroupCounts.read(packageRoot)
        val peopleIndex = EmojiCatalog.groups.indexOfFirst { it.packageId == 4 }

        assertEquals(2, counts[peopleIndex])
    }

    @Test
    fun reportsMissingGroupsAndSupportsMinifiedXml() {
        val packageRoot = temporaryFolder.newFolder("emojipackage")
        val flags = File(packageRoot, "12_-1000").apply { mkdirs() }
        File(flags, "info.xml").writeText(
            "<expList><exppkg><exp name=\"1\"/><exp name=\"2\"/></exppkg></expList>",
        )

        val counts = InstalledEmojiGroupCounts.read(packageRoot)
        val flagsIndex = EmojiCatalog.groups.indexOfFirst { it.packageId == 12 }
        val smileysIndex = EmojiCatalog.groups.indexOfFirst { it.packageId == 3 }

        assertEquals(2, counts[flagsIndex])
        assertEquals(InstalledEmojiGroupCounts.MISSING_GROUP, counts[smileysIndex])
    }
}
