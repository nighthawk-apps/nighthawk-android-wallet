package com.nighthawkapps.lib.android.sdk.chat

/** Packaged `darkirc_exec` process posture (P2P / event graph runs inside this subprocess). */
enum class EmbeddedDarkircNodeStatus {
    /** Embedded node disabled or IRC points at a remote host. */
    NotUsed,

    /** Foreground service / process spawn in progress. */
    Starting,

    /** Subprocess alive (IRC listener may still be booting). */
    Running,

    /** Enabled but `darkirc_exec` not packaged for this ABI. */
    MissingBinary,

    /** Spawn failed or process exited. */
    Failed,
}
