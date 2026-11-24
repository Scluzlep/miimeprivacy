package com.scluzlep.miimeprivacy.emoji

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BaiduEmojiFormatTest {
    @Test
    fun utf16LeX0RoundtripPreservesBomEscapesAndTripleLines() {
        val inputLines = listOf(
            "\uE057",
            "",
            "\uE056",
            "/大笑",
            "/换行\n名称",
            "/飞吻",
            "\uD83D\uDE04",
            "\u2764\uFE0F",
            "\uD83D\uDE18",
        )

        val encoded = BaiduEmojiFormat.encodeBaiduEmojiX0(inputLines)

        assertEquals(0xFF.toByte(), encoded[0])
        assertEquals(0xFE.toByte(), encoded[1])

        // Ensure no stray NUL-terminator char sequences corrupt l11.b.x0 early
        val decoded = BaiduEmojiFormat.parseBaiduEmojiX0(encoded)
        assertNotNull(decoded)
        assertArrayEquals(inputLines.toTypedArray(), decoded)
    }

    @Test
    fun sanitizeX0FieldStripsNulAndCrWhileEscapingLf() {
        assertEquals("abc\\ndef", BaiduEmojiFormat.sanitizeX0Field("abc\r\n\u0000def"))
    }

    @Test
    fun retainsUniqueStockPuaAndNamesEvenWhenStockContainsDuplicates() {
        val stockBytes = BaiduEmojiFormat.encodeBaiduEmojiX0(
            listOf(
                "\uE07C", "\uE07C", "",          // duplicate PUA char in stock
                "/第一个", "/重复", "/无PUA",
                "\uD83D\uDE00", "\uD83D\uDE01", "\uD83D\uDE02",
            ),
        )
        val stock = BaiduEmojiFormat.parseStockBaiduAssets(stockBytes)

        val source = """
            # group: Smileys & Emotion
            1F600 ; fully-qualified # 😀 E1.0 grinning face
            1F601 ; fully-qualified # 😁 E1.0 beaming face
            1F602 ; fully-qualified # 😂 E1.0 face with tears of joy
            1F979 ; fully-qualified # 🥹 E14.0 face holding back tears
        """.trimIndent()
        val entries = EmojiCatalog.parseEmojiTest(source, expectedCount = 4)
        val out = BaiduEmojiFormat.buildBaiduCatalogOutput(entries, stock)

        val parsed = BaiduEmojiFormat.parseBaiduEmojiX0(out.emojiBytes)!!
        assertEquals(12, parsed.size)
        // PUA lines 0..3: \uE07C assigned at most once
        assertEquals("\uE07C", parsed[0])
        assertEquals("", parsed[1])
        assertEquals("", parsed[2])
        assertEquals("", parsed[3])
        assertEquals(1, out.retainedPuaCount)

        // Names lines 4..7: stock retained, new entry falls back to /englishName
        assertEquals("/第一个", parsed[4])
        assertEquals("/重复", parsed[5])
        assertEquals("/无PUA", parsed[6])
        assertEquals("/face holding back tears", parsed[7])
    }

    @Test
    fun buildsNineCrLfTerminatedGroupLinesMatchingFpA() {
        val source = """
            # group: Smileys & Emotion
            1F600 ; fully-qualified # 😀 E1.0 grinning face
            # group: People & Body
            1F44B ; fully-qualified # 👋 E0.6 waving hand
            # group: Component
            1F3FB ; component # 🏻 E1.0 light skin tone
            # group: Animals & Nature
            1F43B ; fully-qualified # 🐻 E0.6 bear
            # group: Food & Drink
            1F34E ; fully-qualified # 🍎 E0.6 red apple
            # group: Travel & Places
            1F697 ; fully-qualified # 🚗 E0.6 automobile
            # group: Activities
            26BD ; fully-qualified # ⚽ E0.6 soccer ball
            # group: Objects
            1F4F1 ; fully-qualified # 📱 E0.6 mobile phone
            # group: Symbols
            2764 FE0F ; fully-qualified # ❤️ E0.6 red heart
            # group: Flags
            1F1E8 1F1F3 ; fully-qualified # 🇨🇳 E2.0 flag: China
        """.trimIndent()

        val entries = EmojiCatalog.parseEmojiTest(source, expectedCount = 10)
        val output = BaiduEmojiFormat.buildBaiduCatalogOutput(entries)

        assertTrue(output.emojiGroupText.endsWith("\r\n"))
        val lines = output.emojiGroupText.removeSuffix("\r\n").split("\r\n")
        assertEquals(9, lines.size)

        // Line 0: recent (valid 1..N IDs)
        val recentIds = lines[0].split(',').map(String::toInt)
        assertTrue(recentIds.isNotEmpty() && recentIds.size <= 20)
        assertTrue(recentIds.all { it in 1..10 })

        // Line 1: face (Smileys 1 + People 2 + Component 3)
        assertEquals("1,2,3", lines[1])
        // Line 2: bear (Animals 4)
        assertEquals("4", lines[2])
        // Line 3: food (Food 5)
        assertEquals("5", lines[3])
        // Line 4: activity (Activities 7 — note Activities before Travel in Baidu fp.a!)
        assertEquals("7", lines[4])
        // Line 5: travel (Travel 6)
        assertEquals("6", lines[5])
        // Line 6: object (Objects 8)
        assertEquals("8", lines[6])
        // Line 7: symbol (Symbols 9)
        assertEquals("9", lines[7])
        // Line 8: flag (Flags 10)
        assertEquals("10", lines[8])
    }

    @Test
    fun filtersAoBYIncompatibleSkinSequencesAndDualSkinMatrices() {
        val source = """
            # group: People & Body
            1F44B ; fully-qualified # 👋 E0.6 waving hand
            1F44B 1F3FB ; fully-qualified # 👋🏻 E1.0 waving hand: light skin tone
            1F44B 1F3FC ; fully-qualified # 👋🏼 E1.0 waving hand: medium-light skin tone
            1F44B 1F3FD ; fully-qualified # 👋🏽 E1.0 waving hand: medium skin tone
            1F44B 1F3FE ; fully-qualified # 👋🏾 E1.0 waving hand: medium-dark skin tone
            1F44B 1F3FF ; fully-qualified # 👋🏿 E1.0 waving hand: dark skin tone
            26F9 FE0F ; fully-qualified # ⛹️ E0.7 person bouncing ball
            26F9 1F3FB ; fully-qualified # ⛹🏻 E2.0 person bouncing ball: light skin tone
            26F9 1F3FC ; fully-qualified # ⛹🏼 E2.0 person bouncing ball: medium-light skin tone
            26F9 1F3FD ; fully-qualified # ⛹🏽 E2.0 person bouncing ball: medium skin tone
            26F9 1F3FE ; fully-qualified # ⛹🏾 E2.0 person bouncing ball: medium-dark skin tone
            26F9 1F3FF ; fully-qualified # ⛹🏿 E2.0 person bouncing ball: dark skin tone
            1F469 200D 2695 FE0F ; fully-qualified # 👩‍⚕️ E4.0 woman health worker
            1F469 1F3FB 200D 2695 FE0F ; fully-qualified # 👩🏻‍⚕️ E4.0 woman health worker: light
            1F469 1F3FC 200D 2695 FE0F ; fully-qualified # 👩🏼‍⚕️ E4.0 woman health worker: medium-light
            1F469 1F3FD 200D 2695 FE0F ; fully-qualified # 👩🏽‍⚕️ E4.0 woman health worker: medium
            1F469 1F3FE 200D 2695 FE0F ; fully-qualified # 👩🏾‍⚕️ E4.0 woman health worker: medium-dark
            1F469 1F3FF 200D 2695 FE0F ; fully-qualified # 👩🏿‍⚕️ E4.0 woman health worker: dark
        """.trimIndent()

        // Verify ao.b.Y mechanics directly:
        assertEquals("\uD83D\uDC4B\uD83C\uDFFB", BaiduEmojiFormat.applyBaiduAoBY("\uD83D\uDC4B", "\uD83C\uDFFB"))
        assertEquals(
            "\uD83D\uDC69\uD83C\uDFFB\u200D\u2695\uFE0F",
            BaiduEmojiFormat.applyBaiduAoBY("\uD83D\uDC69\u200D\u2695\uFE0F", "\uD83C\uDFFB"),
        )
        // Incompatible with ao.b.Y because 26F9 FE0F appends modifier AFTER FE0F
        assertFalse(
            "\u26F9\uD83C\uDFFB" == BaiduEmojiFormat.applyBaiduAoBY("\u26F9\uFE0F", "\uD83C\uDFFB"),
        )

        val entries = EmojiCatalog.parseEmojiTest(source, expectedCount = 18)
        val prepared = EmojiCatalog.prepareForSystem(
            sourceEntries = entries,
            hasGlyph = { true },
            mergeDualSkinMatrices = false,
            canMergeSkinSet = BaiduEmojiFormat::isSkinSetCompatibleWithAoBY,
        )

        // 👋 (merged into 1) + ⛹️+5 variants (kept flat = 6) + 👩‍⚕️ (merged into 1) = 8 entries
        assertEquals(8, prepared.entries.size)
        val waving = prepared.entries.first { it.unified == "1F44B" }
        val healthWorker = prepared.entries.first { it.unified == "1F469_200D_2695_FE0F" }
        val bouncingBall = prepared.entries.first { it.unified == "26F9_FE0F" }

        assertTrue(waving.supportsSkin)
        assertTrue(healthWorker.supportsSkin)
        assertFalse(bouncingBall.supportsSkin)

        val output = BaiduEmojiFormat.buildBaiduCatalogOutput(prepared.entries)
        assertEquals("1,0\n8,0\n", output.changeSkinText)
        assertEquals(2, output.skinEnabledCount)
        assertEquals("", output.errorHintText)
    }
}
