package com.nighthawkapps.lib.android.sdk.wallet.darkfid

import com.nighthawkapps.lib.android.sdk.wallet.DarkfiNetwork
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class DarkfidEmbeddedConfigGeneratorTest {
    @Test
    fun testnetToml_includesLoopbackRpc() {
        val root =
            File.createTempFile("darkfid-root", null).apply {
                delete()
                mkdirs()
            }
        val toml =
            DarkfidEmbeddedConfigGenerator.buildToml(
                root,
                DarkfiNetwork.Testnet,
                DarkfidP2pTransport.Clearnet,
            )
        assertTrue(toml.contains("network = \"testnet\""))
        assertTrue(toml.contains("rpc_listen = \"tcp://127.0.0.1:18345\""))
        assertTrue(toml.contains("tcp+tls://lilith0.dark.fi:18340"))
    }

    @Test
    fun torProfileUsesSocks5ForP2p() {
        val root =
            File.createTempFile("darkfid-root2", null).apply {
                delete()
                mkdirs()
            }
        val toml =
            DarkfidEmbeddedConfigGenerator.buildToml(
                root,
                DarkfiNetwork.Testnet,
                DarkfidP2pTransport.TorViaSocks5("127.0.0.1", 9050),
            )
        assertTrue(toml.contains("active_profiles = [\"socks5\"]"))
        assertTrue(toml.contains("socks5://127.0.0.1:9050/wgxxaifz"))
    }

    @Test
    fun mainnetToml_usesElevenBlockThreshold() {
        val root =
            File.createTempFile("darkfid-mainnet", null).apply {
                delete()
                mkdirs()
            }
        val toml =
            DarkfidEmbeddedConfigGenerator.buildToml(
                root,
                DarkfiNetwork.Mainnet,
                DarkfidP2pTransport.Clearnet,
            )
        assertTrue(toml.contains("threshold = 11"))
        assertTrue(toml.contains("rpc_listen = \"tcp://127.0.0.1:8345\""))
    }
}
