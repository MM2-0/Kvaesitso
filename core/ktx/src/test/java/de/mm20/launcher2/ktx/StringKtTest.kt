package de.mm20.launcher2.ktx

import org.junit.Assert
import org.junit.Test


internal class StringKtTest {

    @Test
    fun stringNormalizer_diacritics() {
        Assert.assertEquals("a b c d e f g h i j", "À b ç d Ę F Ğ h ï j".normalize())
    }

    @Test
    fun stringNormalizer_ligatures() {
        Assert.assertEquals("aeoessss", "ÆŒßẞ".normalize())
    }

    @Test
    fun stringNormalizer_search() {
        val pairs = listOf(
            "文件" to "jian",
            "文件" to "jiàn",
            "文件" to "文",
            "génération" to "Generation",
            "Kvaesitso" to "kvaes",
            "Übersetzer" to "uberset",
        )
        for ((str, q) in pairs) {
            Assert.assertTrue(str.normalize().contains(q.normalize()))
        }
    }

    /**
     * Test that A.contains(B) -> A.normalize().contains(B.normalize())
     */
    @Test
    fun stringNormalize_substring_implication() {
        val pairs = listOf(
            "文件" to "文",
            "génération" to "énér",
            "Kvaesitso" to "æ",
            "Übersetzer" to "übe",
        )
        for ((str, q) in pairs) {
            Assert.assertTrue(
                if (str.contains(q)) str.normalize().contains(q.normalize())
                else true
            )
        }
    }
}