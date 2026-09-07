# The on-device assistant, and how to replace its model

Everything here runs on the phone. There is no cloud API, no API key anywhere in the source,
and no calendar data is sent off the device. The only network request the assistant makes is
the one-time download of a model file from Hugging Face, and only when the user taps download.

---

## 1. The two answers the assistant can give

This distinction is the design, not a detail, and the UI labels every reply with which one it
is:

**`AnswerSource.RULES` — computed.** Summaries of what is coming up, conflict detection, free
slots, and the date arithmetic behind a parsed sentence all come from `CalendarTools`, which
reads the database and does arithmetic. These are exact. They work with **no model installed
at all**, which is why the app is fully usable with the assistant switched off — the label
reads *គណនាពិតប្រាកដពីប្រតិទិន*.

**`AnswerSource.LOCAL_MODEL` — generated.** Free-form wording, and parsing a sentence too
irregular for the rule-based Khmer parser. Labelled *បង្កើតដោយម៉ូដែលក្នុងឧបករណ៍*.

`AiAssistant` always tries the rules first. The model is a fallback, not the front door.

**Nothing the assistant produces is saved without confirmation.** A parsed sentence becomes a
*draft* shown in a card with its date, time, title and reminders; the user taps save.

---

## 2. Architecture

```
:app  ──▶  AiAssistant  ──▶  CalendarTools   (exact, always available)
                        └─▶  AiEngine        (interface)
                                 ├── MediaPipeEngine   (LLM Inference API)
                                 └── NoOpAiEngine      (no model / unsupported device)
```

`AiEngine` is the whole seam. Nothing in `:app` refers to a concrete runtime, and
`AiEngineFactory.create()` is the single place that decides which one to build. The factory
probes for the MediaPipe classes by reflection, so a stripped build or an incompatible ABI
gives you "unavailable on this device" instead of a crash on first use.

| File | |
|---|---|
| `ai/AiEngine.kt` | The interface, `AiRuntimeConfig`, and `NoOpAiEngine`. |
| `ai/engine/AiEngineFactory.kt` | Backend selection. |
| `ai/engine/MediaPipeEngine.kt` | The MediaPipe LLM Inference implementation. |
| `ai/model/ModelCatalog.kt` | The bundles the app knows how to install. |
| `ai/model/ModelStore.kt` | Where files live on disk; install, delete, adopt. |
| `ai/model/ModelDownloadWorker.kt` | Resumable download as a WorkManager foreground job. |
| `ai/DeviceCapability.kt` | RAM, ABI and free-storage checks. |
| `ai/tools/CalendarTools.kt` | The exact, non-generated answers. |

---

## 3. The bundled catalogue

All three download without a Hugging Face account or token. Sizes are the exact published
content lengths; a download that ends at a different length is treated as truncated and
rejected, because a truncated bundle fails deep inside the native runtime with an error no
user could act on.

| Model | File | Size | RAM needed | Khmer |
|---|---|---|---|---|
| Qwen 2.5 0.5B | `Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task` | 547 MB | 3 GB | BASIC |
| Qwen 2.5 1.5B | `Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task` | 1.60 GB | 6 GB | GOOD |
| Gemma 4 E2B | `gemma-4-E2B-it-web.task` | 2.00 GB | 8 GB | BEST |

All are hosted under `huggingface.co/litert-community/`.

`requiredRamBytes` is **total device RAM**, not the file size — a bundle needs its weights
resident, plus a KV cache, plus the app's own heap. `DeviceCapability.fit()` turns that into a
`ModelFit`: `Good`, `Tight`, `TooLarge`, `NoStorage` or `Unsupported`, each with a reason
string the AI screen shows verbatim. A model that will not fit is displayed with its reason
rather than hidden, so the user knows *why*.

Gemma 3 bundles are deliberately absent: they are free but sit behind a licence click that
returns 401 to an app until the user creates an access token.

---

## 4. Replacing or adding a model

### 4a. Add another `.task` bundle (no code beyond the catalogue)

Any LiteRT `.task` bundle the MediaPipe LLM Inference API accepts will work. Add it to
`ModelCatalog`:

```kotlin
val MY_MODEL = ModelSpec(
    id = "my-model-q8",                       // stable; it is what gets persisted
    displayName = "My Model 1B",
    fileName = "my-model-1b-q8.task",
    downloadUrl = "https://…/my-model-1b-q8.task",   // or null for sideload-only
    sizeBytes = 1_234_567_890L,               // exact Content-Length, verified on download
    requiredRamBytes = 4 * GB,
    khmerQuality = ModelSpec.KhmerQuality.GOOD,
    noteKm = "…",                             // shown in the picker, in Khmer
)

val all: List<ModelSpec> = listOf(QWEN_0_5B, MY_MODEL, QWEN_1_5B, GEMMA_4_E2B)
```

`all` is ordered smallest to largest — `recommendedFor()` picks the last entry the device can
host, so keep it sorted by `requiredRamBytes`.

Get the exact size with:

```bash
curl -sIL "https://…/my-model-1b-q8.task" | grep -i content-length
```

Nothing else changes. The download worker, the fit check, the picker and the load path all
read the spec.

### 4b. Sideload a file the user already has

Two gigabytes over a mobile connection is expensive and slow in Cambodia, and fetching the
file once on a computer and copying it across is often the only practical route.
`ModelStore.adopt(spec, file)` copies a local file into the store and verifies its length
against the spec. Give the spec a `downloadUrl` of `null` for entries that can only arrive
this way.

Files live in the app's private `models/` directory, which is excluded from Android backup
(`backup_rules.xml`, `data_extraction_rules.xml`) — a two-gigabyte file has no business in a
cloud backup, and it is re-downloadable.

### 4c. Swap the runtime entirely (llama.cpp, LiteRT-LM, an NPU backend)

1. Implement `AiEngine`. Five members: `id`, `isReady`, `load`, `unload`, `generate`,
   `generateStream`. Contract: **never throw** — return `Result.failure` with a message fit to
   show a user, and report `isReady == false` when nothing is loaded.
2. Add a branch to `AiEngineFactory.create()`.
3. Update `ModelCatalog` with the file format your runtime wants (`.gguf` instead of
   `.task`, say).

No calendar code changes. That separation is why `:ai-core` is its own module: `:app` depends
on it, it does not depend on `:app`, and the compiler enforces that the calendar never reaches
into the assistant.

### 4d. Ship without any AI

Remove `implementation(project(":ai-core"))` from `app/build.gradle.kts` — or just leave the
feature off. `AiEngineFactory` returns `NoOpAiEngine` when the runtime classes are absent,
`aiEnabled` defaults to `false`, and every scheduling answer is still computed from
`CalendarTools`. The calendar does not degrade.

---

## 4e. What "verified" means for a downloaded bundle

The check is **exact length against the published `Content-Length`**, and that is the whole
check. It catches the failure that actually happens: a transfer cut short by a dropped
connection, a killed process or a full disk.

There is deliberately **no content sniffing**. A `.task` bundle has no common signature — the
Qwen bundles are a length-prefixed zip (`PK` at offset 4), the Gemma one a length-prefixed
TFLite flatbuffer (`TFL3` at offset 4). An earlier version of this code checked for zip magic
and rejected both real catalogue files as damaged. A check that fails valid input is worse
than no check.

Catching subtler corruption — a flipped bit somewhere inside two gigabytes — needs a published
digest, which these bundles do not have. If you host your own, add a `sha256` alongside
`sizeBytes` and check it in `ModelStore.inspect`; the update manifest already does exactly
this for APKs.

## 4f. Why downloading can no longer take the app down

`ModelDownloadWorker` promotes itself to a foreground service so a multi-gigabyte transfer
survives the user switching apps. From Android 14, `startForeground()` with a type the
`<service>` element does not declare throws — **inside `Service.onStartCommand`, on the main
thread**, where no `try` in the worker can reach it. WorkManager's own manifest entry declares
no type, so this app re-declares it:

```xml
<service
    android:name="androidx.work.impl.foreground.SystemForegroundService"
    android:foregroundServiceType="dataSync"
    tools:node="merge" />
```

This was a real crash, not a hypothetical one. Beyond the manifest, the worker now treats
foreground promotion as a nicety that may be refused, bounds its retries, and reports typed
failures rather than retrying forever.

## 5. Memory safety

- **Before download:** free storage is checked against the file size, and total RAM against
  `requiredRamBytes`. `Tight` still allows the install but says so.
- **Before load:** the file must exist and match its expected length.
- **On load failure:** the engine returns a failure, the screen shows the message, and the app
  keeps working. Nothing is left half-initialised.
- **On unload / disabling AI / deleting the selected model:** the engine is unloaded first, so
  the native session is released rather than orphaned.
- The download runs as a WorkManager foreground job with a progress notification, is resumable,
  and can be cancelled.

## 6. Tuning

AI settings exposes `maxTokens` (128–2048) and `temperature` (0–1), persisted in DataStore and
applied on the next load. Structured extraction uses `AiRuntimeConfig.EXTRACTION`
(temperature 0, topK 1) regardless — parsing a sentence into a date wants determinism, not
creativity.
