package com.mitra.inference

/** Where the on-device model lives and comes from. One place to change the source. */
object ModelRegistry {
    // Brain: Gemma 4 E2B (~2.59 GB). Switched from Qwen3-0.6B, which was too weak — it reasoned about
    // tools but only narrated, never emitting parseable tool calls. E2B does RELIABLE native tool
    // calling. Downloads anonymously from the HF resolve URL. Use the GENERIC CPU file (Dimensity 1300
    // has no usable NPU — never the *.mediatek.* variant).
    const val MODEL_FILE = "gemma-4-E2B-it.litertlm"
    const val MODEL_URL =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/$MODEL_FILE"

    // Integrity pins for the artifact at MODEL_URL. Obtained from the HF response headers via
    // `curl -sIL <MODEL_URL>` on 2026-06-16 (X-Linked-ETag / X-Linked-Size). HF stores xet blobs
    // by their SHA-256, so X-Linked-ETag IS the SHA-256.
    //
    // Why pin: a corrupt or wrong-version download otherwise reaches LoadingModel → silent crash
    // with no actionable error. Pin + verify catches it at the boundary and surfaces a clean
    // failure so the user can retry. Bump these two constants in lock-step when MODEL_URL moves.
    const val EXPECTED_SIZE_BYTES = 2_588_147_712L
    const val EXPECTED_SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
}
