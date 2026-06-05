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
}
