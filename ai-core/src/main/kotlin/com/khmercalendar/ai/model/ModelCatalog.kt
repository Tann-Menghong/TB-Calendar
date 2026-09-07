package com.khmercalendar.ai.model

/**
 * A model bundle the app knows how to install and run.
 *
 * @property sizeBytes Exact published size. A download that ends at a different length is
 *   truncated, and a truncated bundle fails deep inside the native runtime with an error no
 *   user could act on, so the size is checked before the file is ever offered to the engine.
 * @property requiredRamBytes What the device needs in total, not what the file weighs. A
 *   bundle needs its weights resident plus a KV cache plus the app's own heap.
 */
data class ModelSpec(
    val id: String,
    val displayName: String,
    val fileName: String,
    /** Null for entries that can only be sideloaded from local storage. */
    val downloadUrl: String?,
    val sizeBytes: Long,
    val requiredRamBytes: Long,
    val khmerQuality: KhmerQuality,
    val noteKm: String,
) {
    val sizeLabel: String get() = "%.1f GB".format(sizeBytes / 1_000_000_000.0)
    val ramLabel: String get() = "%.0f GB".format(requiredRamBytes / 1_073_741_824.0)

    /**
     * How well the bundle handles Khmer.
     *
     * Khmer is a long-tail language in every model small enough to run on a phone, and
     * quality tracks both parameter count and how multilingual the base model is. Saying so
     * plainly in the picker is more useful than letting someone install three gigabytes and
     * discover it for themselves.
     */
    enum class KhmerQuality { BASIC, GOOD, BEST }
}

/**
 * Every bundle here downloads without a Hugging Face account or token.
 *
 * Gemma 3 bundles are free as well but sit behind a licence click that returns 401 to an app
 * until the user creates an access token, so they are deliberately absent.
 */
object ModelCatalog {

    private const val HF = "https://huggingface.co"
    private const val GB = 1_073_741_824L

    /**
     * The one that runs on an ordinary phone. Half a gigabyte, and the only entry a 4 GB
     * device should be offered.
     */
    val QWEN_0_5B = ModelSpec(
        id = "qwen2.5-0.5b-instruct-q8",
        displayName = "Qwen 2.5 0.5B",
        fileName = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        downloadUrl = "$HF/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/" +
            "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        sizeBytes = 546_660_344L,
        requiredRamBytes = 3 * GB,
        khmerQuality = ModelSpec.KhmerQuality.BASIC,
        noteKm = "តូចបំផុត ដំណើរការលឿន សម្រាប់ទូរស័ព្ទធម្មតា។ ភាសាខ្មែរមូលដ្ឋាន។",
    )

    val QWEN_1_5B = ModelSpec(
        id = "qwen2.5-1.5b-instruct-q8",
        displayName = "Qwen 2.5 1.5B",
        fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        downloadUrl = "$HF/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/" +
            "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        sizeBytes = 1_597_913_616L,
        requiredRamBytes = 6 * GB,
        khmerQuality = ModelSpec.KhmerQuality.GOOD,
        noteKm = "តុល្យភាពរវាងទំហំ និងគុណភាព។",
    )

    /** Best Khmer of the bundles that fit on a phone, and the heaviest to load. */
    val GEMMA_4_E2B = ModelSpec(
        id = "gemma4-e2b-it",
        displayName = "Gemma 4 E2B",
        fileName = "gemma-4-E2B-it-web.task",
        downloadUrl = "$HF/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-web.task",
        sizeBytes = 2_003_697_664L,
        requiredRamBytes = 8 * GB,
        khmerQuality = ModelSpec.KhmerQuality.BEST,
        noteKm = "ភាសាខ្មែរល្អបំផុត។ ត្រូវការឧបករណ៍ថ្មី និង RAM ច្រើន។",
    )

    val all: List<ModelSpec> = listOf(QWEN_0_5B, QWEN_1_5B, GEMMA_4_E2B)

    fun byId(id: String?): ModelSpec? = all.firstOrNull { it.id == id }

    /** The largest entry the device can host, or the smallest if none of them fit. */
    fun recommendedFor(totalRamBytes: Long): ModelSpec =
        all.lastOrNull { totalRamBytes >= it.requiredRamBytes } ?: QWEN_0_5B
}
