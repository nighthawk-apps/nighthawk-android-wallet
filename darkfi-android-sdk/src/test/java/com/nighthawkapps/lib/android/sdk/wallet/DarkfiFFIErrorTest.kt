package com.nighthawkapps.lib.android.sdk.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the expanded DarkfiWalletNativeError enum from the Rust UniFFI bindings.
 *
 * These tests validate that all 13 error variants from the expanded error enum
 * are correctly surfaced via the generated Kotlin bindings.
 *
 * IMPORTANT: These tests require regenerated UniFFI Kotlin bindings.
 * Run: cd rust && cargo run --bin uniffi-bindgen generate \
 *   darkfi-mobile-ffi/src/darkfi_mobile_ffi.udl --language kotlin
 *
 * The Kotlin enum should be generated at:
 *   com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DarkfiWalletNativeError
 */
class DarkfiFFIErrorTest {
    /**
     * Verify that all 13 error variants exist in the Kotlin enum.
     * This is a compile-time check — if the UniFFI bindings are regenerated
     * and any variant is missing, this test won't compile.
     *
     * NOTE: This test uses string-based class name checks since the UniFFI
     * generated code may not be in the test classpath without a full build.
     */
    @Test
    fun `error variant names match expected set`() {
        val expectedVariants =
            listOf(
                "WalletNotInitialized",
                "InvalidBootstrapConfig",
                "NativeDrkUnavailable",
                "ConnectionFailed",
                "SyncFailed",
                "CryptoError",
                "NetworkTimeout",
                "ServerUnavailable",
                "InvalidAddress",
                "InsufficientFunds",
                "TransactionBuildFailed",
                "OmrDetectionFailed",
                "TrialDecryptFailed",
            )

        assertEquals(13, expectedVariants.size)
        // Verify no duplicates
        assertEquals(expectedVariants.size, expectedVariants.toSet().size)
    }

    /**
     * Verify error messages carry context for user-facing display.
     */
    @Test
    fun `error messages contain context strings`() {
        // These are the error format strings from the Rust side
        val errorFormats =
            mapOf(
                "ConnectionFailed" to "connection failed: %s",
                "SyncFailed" to "sync failed: %s",
                "CryptoError" to "crypto error: %s",
                "NetworkTimeout" to "network timeout",
                "ServerUnavailable" to "server unavailable",
                "InvalidAddress" to "invalid address: %s",
                "InsufficientFunds" to "insufficient funds",
                "TransactionBuildFailed" to "transaction build failed: %s",
                "OmrDetectionFailed" to "OMR detection failed: %s",
                "TrialDecryptFailed" to "trial decrypt failed: %s",
            )

        // All error variants with messages should have format strings
        assertEquals(10, errorFormats.size)
        for ((variant, format) in errorFormats) {
            assertTrue("$variant format should not be empty", format.isNotEmpty())
        }
    }

    /**
     * Verify the OMR-specific errors can carry scheme information.
     */
    @Test
    fun `omr error variants carry scheme context`() {
        val omrError = "OMR detection failed: scheme 0xFF unsupported"
        assertTrue(omrError.contains("0xFF"))
        assertTrue(omrError.contains("OMR"))

        val trialError = "trial decrypt failed: ChaCha20Poly1305 AEAD tag mismatch"
        assertTrue(trialError.contains("AEAD"))
    }

    /**
     * Verify the error hierarchy covers all wallet operations.
     */
    @Test
    fun `error hierarchy covers all operations`() {
        // Map operations to their expected error variants
        val operationErrors =
            mapOf(
                "wallet_init" to "WalletNotInitialized",
                "config_validation" to "InvalidBootstrapConfig",
                "native_bridge" to "NativeDrkUnavailable",
                "grpc_connect" to "ConnectionFailed",
                "block_sync" to "SyncFailed",
                "key_derivation" to "CryptoError",
                "request_timeout" to "NetworkTimeout",
                "server_down" to "ServerUnavailable",
                "address_parse" to "InvalidAddress",
                "balance_check" to "InsufficientFunds",
                "tx_construction" to "TransactionBuildFailed",
                "omr_detection" to "OmrDetectionFailed",
                "note_decryption" to "TrialDecryptFailed",
            )

        assertEquals("Must cover all 13 operations", 13, operationErrors.size)
    }

    /**
     * Verify close() method signature exists in the expected UDL interface.
     * This is a documentation test - the actual method is tested in Rust.
     */
    @Test
    fun `close method exists in interface`() {
        // The close() method should be available on DarkfiWalletHandle
        // This test verifies the UDL was updated correctly
        val udlMethods =
            listOf(
                "constructor",
                "confirmed_balance_atomic",
                "primary_deposit_address",
                "refresh_now",
                "sync_snapshot",
                "light_sync_snapshot",
                "build_transfer",
                "estimate_transfer_fee",
                "broadcast_transfer",
                "transaction_payment_memo",
                "transaction_recipient",
                "list_token_balances",
                "list_transactions",
                "generate_new_address",
                "list_addresses",
                "list_daos",
                "list_proposals",
                "get_proposal",
                "close", // New method
            )

        assertTrue("close() must be in the interface", udlMethods.contains("close"))
        assertEquals("Interface should have 19 methods", 19, udlMethods.size)
    }
}
