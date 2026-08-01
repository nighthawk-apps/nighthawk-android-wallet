# Nym Mixnet / NymVPN Integration Plan

## 1. Executive Summary

With Tor increasingly viewed as compromised for high-level threat models, transitioning Nighthawk's network layer to the Nym Mixnet offers a robust alternative. Nym provides both a decentralized VPN (dVPN) and a full mixnet (with dummy traffic and timing obfuscation).

This proposal outlines a phased implementation plan for integrating the Nym SDK/NymVPN stack into both the iOS and Android Nighthawk wallets, focusing on app-level networking, mobile architecture, and user experience.

## 2. Architectural Baseline & Integration Strategy

Nym's developer documentation specifies that integration depends heavily on the host architecture. Since Nighthawk already has a robust pattern for embedding proxy daemons (currently Guardian's `tor-android` and embedded `arti` on iOS) and routing traffic via SOCKS5, the most viable path is **integrating Nym at the app networking layer via a local SOCKS5 proxy**, mirroring the existing Tor integration pattern.

### Option A: Embedded Nym Client SDK (Recommended)
Instead of forcing users to install a separate NymVPN app, we will embed the Nym Rust client (`nym-client` / Nym mobile SDK) directly into the app using UniFFI bindings.
*   **Android:** Compile the Nym Rust client to an `.aar` using `uniffi-rs`. Expose a local SOCKS5 listener.
*   **iOS:** Compile the Nym Rust client to an `XCFramework`. Expose a local SOCKS5 listener.
*   **Routing:** The existing `AppTorCoordinator` and `DarkfiChatPreferences` logic will be extended to route traffic (Wallet RPC and DarkIRC P2P) through this new Nym SOCKS5 listener.

### Option B: External NymVPN App (Fallback)
If embedded compilation proves unstable on mobile, Nighthawk will instruct users to install the official NymVPN app and configure Nighthawk's "External SOCKS" settings to point to the local proxy port exposed by the NymVPN app.

## 3. Mixnet vs. dVPN Mode

NymVPN supports two modes:
1.  **Fast Mode (dVPN):** 2-hop route. Faster, suitable for high-bandwidth tasks (like downloading chain blocks or syncing wallet state).
2.  **Anonymous Mode (Mixnet):** 5-hop route with Sphinx packet formatting and timing obfuscation. Slower, but provides metadata anonymity.

**Recommendation:** 
*   Use **Fast Mode (dVPN)** for standard Wallet RPC synchronization where latency is noticeable.
*   Use **Mixnet Mode** for DarkIRC P2P messages and transaction broadcasting, where latency (a few seconds) is acceptable in exchange for maximum metadata protection.

## 4. zk-nym Credentials

Nym network usage requires bandwidth credentials (zk-nyms). 
*   **Initial Phase:** Nighthawk apps can utilize a shared pool of zk-nym credentials provided by the Nighthawk project (funded via the dev fund) to abstract this complexity away from the user.
*   **Future Phase:** Users can import their own zk-nyms generated from the Nym desktop wallet, or Nighthawk could implement an in-app purchase/crypto swap mechanism to purchase bandwidth.

## 5. Settings UX Changes

The Tor Settings UX will be rebranded and expanded to a general "Network Privacy" screen.

**New UX Flow:**
*   **Privacy Routing Toggle:** "Route wallet and chat traffic through privacy networks."
*   **Network Selection (Radio Buttons):**
    *   ( ) **Built-in Tor (Arti)** (Legacy fallback)
    *   (x) **Nym Mixnet** (Recommended)
    *   ( ) **External SOCKS Proxy**
*   **Nym Configuration (when selected):**
    *   Toggle: "Max Anonymity Mode (Slower) vs Fast dVPN Mode"
    *   Text field: "Custom zk-nym credential (Optional)"
*   **Status Indicator:** "Nym Client: Connected / Bootstrapping / Offline"

## 6. Implementation Phases

*   **Phase 1: Research & Rust Bindings (1-2 weeks):** Compile the `nym-client` for iOS/Android using UniFFI and ensure it can expose a local SOCKS5 port.
*   **Phase 2: Android Integration (2 weeks):** Replace/Augment the `tor-android` coordinator with the Nym client coordinator. Update network interceptors to use the Nym SOCKS port.
*   **Phase 3: iOS Integration (2 weeks):** Wrap the Nym client in the `DarkfiFfiSafe` layer alongside Arti.
*   **Phase 4: UX & Credential Management (1 week):** Build the new Settings UI and implement the zk-nym credential injection mechanism.
