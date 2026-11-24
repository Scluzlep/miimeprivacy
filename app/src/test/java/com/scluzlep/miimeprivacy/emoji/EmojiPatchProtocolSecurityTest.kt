package com.scluzlep.miimeprivacy.emoji

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiPatchProtocolSecurityTest {
    @Test
    fun trustedArchiveUriRequiresContentSchemeAndExactProviderAuthority() {
        assertTrue(
            EmojiPatchProtocol.isTrustedArchiveUri(
                scheme = "content",
                authority = "${EmojiPatchProtocol.MODULE_PACKAGE}.archiveprovider",
            ),
        )
        assertFalse(
            EmojiPatchProtocol.isTrustedArchiveUri(
                scheme = "file",
                authority = "${EmojiPatchProtocol.MODULE_PACKAGE}.archiveprovider",
            ),
        )
        assertFalse(
            EmojiPatchProtocol.isTrustedArchiveUri(
                scheme = "content",
                authority = "com.evil.fake.archiveprovider",
            ),
        )
        assertFalse(
            EmojiPatchProtocol.isTrustedArchiveUri(
                scheme = null,
                authority = "${EmojiPatchProtocol.MODULE_PACKAGE}.archiveprovider",
            ),
        )
        assertFalse(
            EmojiPatchProtocol.isTrustedArchiveUri(
                scheme = "content",
                authority = null,
            ),
        )
    }

    @Test
    fun trustedModuleCallerEnforcesCreatorProofAndApi34SentFromPackage() {
        val mod = EmojiPatchProtocol.MODULE_PACKAGE

        // API < 34: relies strictly on unforgeable PendingIntent creatorPackage
        assertTrue(EmojiPatchProtocol.isTrustedModuleCaller(33, sentFromPackage = null, proofCreatorPackage = mod))
        assertFalse(EmojiPatchProtocol.isTrustedModuleCaller(33, sentFromPackage = null, proofCreatorPackage = null))
        assertFalse(EmojiPatchProtocol.isTrustedModuleCaller(33, sentFromPackage = null, proofCreatorPackage = "com.evil.app"))

        // API 34+: BOTH sentFromPackage AND proofCreatorPackage must match MODULE_PACKAGE (null rejected)
        assertTrue(EmojiPatchProtocol.isTrustedModuleCaller(34, sentFromPackage = mod, proofCreatorPackage = mod))
        assertFalse(EmojiPatchProtocol.isTrustedModuleCaller(34, sentFromPackage = null, proofCreatorPackage = mod))
        assertFalse(EmojiPatchProtocol.isTrustedModuleCaller(34, sentFromPackage = "com.evil.app", proofCreatorPackage = mod))
        assertFalse(EmojiPatchProtocol.isTrustedModuleCaller(34, sentFromPackage = mod, proofCreatorPackage = "com.evil.app"))
    }

    @Test
    fun trustedTargetSenderEnforcesImeiPackageMatchAcrossApiLevels() {
        val sogou = EmojiPatchProtocol.TARGET_SOGOU
        val baidu = EmojiPatchProtocol.TARGET_BAIDU

        assertTrue(EmojiPatchProtocol.isTrustedTargetSender(33, null, sogou, sogou))
        assertTrue(EmojiPatchProtocol.isTrustedTargetSender(34, baidu, baidu, baidu))
        assertFalse(EmojiPatchProtocol.isTrustedTargetSender(34, null, sogou, sogou))
        assertFalse(EmojiPatchProtocol.isTrustedTargetSender(34, baidu, sogou, baidu))
        assertFalse(EmojiPatchProtocol.isTrustedTargetSender(33, null, "com.evil.app", "com.evil.app"))
        assertFalse(EmojiPatchProtocol.isTrustedTargetSender(33, null, sogou, "com.evil.app"))
    }
}
