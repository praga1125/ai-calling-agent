# AI Calling Agent

Spring Boot prototype of an AI sales calling agent for a CRM product.

It simulates an outbound qualification call — no real telephony. You create a lead, start a voice
conversation, hear the agent speak (**Text-to-Speech**), answer with your microphone or an audio
file (**Speech-to-Text**), and the app stores every turn, then produces a summary and a final call
outcome.

**All speech runs on OpenAI**: `/v1/audio/speech` for the agent's voice, `/v1/audio/transcriptions`
for turning the customer's recording into text.

## Features

- Lead CRUD backed by H2 (create, list, detail with call history, update, delete)
- Rule-based CRM qualification dialog: interest → team size → lead handling → demo
- OpenAI Text-to-Speech for every AI turn, OpenAI Speech-to-Text for every customer turn
- Full conversation history, generated summary, and final outcome stored per call
- Pipeline dashboard: leads counted per stage (qualified, converted and the rest) plus call totals
- Every turn reports the qualification captured so far, so answers are visible during the call
- Selecting a lead reopens its latest conversation at the step it stopped at, ready to continue
- Browser simulator at `/` with a leads table, mic recording, audio upload, and a typed-reply fallback
- Swagger UI at `/swagger-ui.html`, structured error responses, unit and integration tests

## Tech stack

Java 17, Spring Boot 3.4 (Web, Validation, Data JPA), H2, springdoc-openapi, Maven.

## Quick start

### 1. Get an OpenAI API key

Create one at [platform.openai.com/api-keys](https://platform.openai.com/api-keys). The audio
endpoints are pay-as-you-go, so the account needs credit; a full demo call costs a fraction of a
cent. Keys from a free trial without billing return `insufficient_quota`.

Check which models the key may actually use, because projects are often limited to a subset:

```bash
curl https://api.openai.com/v1/models -H "Authorization: Bearer $OPENAI_API_KEY"
```

Choosing a model the project lacks returns HTTP 403 `model_not_found`, which the app reports with
the exact command above rather than as a key problem.

### 2. Run the app with the key exported

The key is read at startup, so it must be exported in the **same terminal** that starts the app.

```bash
cd ~/ai-calling-agent
export OPENAI_API_KEY=sk-your_key_here
./mvnw spring-boot:run
```

Or for a single run: `OPENAI_API_KEY=sk-your_key_here ./mvnw spring-boot:run`

### 3. Confirm the key was picked up

```bash
curl -s http://localhost:8080/api/speech/status
```

```json
{
  "provider": "openai",
  "apiKeyConfigured": true,
  "sttModel": "gpt-audio-mini",
  "sttEndpoint": "/chat/completions",
  "ttsModel": "gpt-4o-mini-tts",
  "ttsVoice": "alloy",
  "language": "en",
  "note": "OpenAI speech is configured. Calls use OpenAI for both transcription and voice."
}
```

### 4. Prove speech works in both directions

This speaks a phrase with OpenAI TTS and transcribes that same audio back with OpenAI STT:

```bash
curl -s -X POST "http://localhost:8080/api/speech/test?text=Yes,%20I%20am%20interested%20in%20a%20CRM"
```

```json
{
  "spokenText": "Yes, I am interested in a CRM",
  "audioUrl": "/api/audio/speech-test-1f0c....wav",
  "audioBytes": 96044,
  "transcript": "Yes, I am interested in a CRM",
  "transcriptSimilarity": 1.0,
  "roundTripSucceeded": true
}
```

`transcriptSimilarity` near `1.0` means both directions work.

### 5. Open the simulator

<http://localhost:8080/> — the pipeline tiles and leads table sit at the top; click a tile to filter
the table by stage, click a row to select that lead. Then press **Start AI call**, listen to the
greeting (it plays by itself, there is no player to press), and answer with **Record mic**,
**Stop**, **Send recorded reply**.

Browsers only grant microphone access on `localhost` or HTTPS, and will ask for permission the
first time. If a microphone is awkward, **Upload audio** accepts any recorded file, and
**Send typed reply** answers as the customer in text.

## APIs

### Required by the assignment

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/leads` | Create a lead |
| `GET` | `/api/leads` | List leads |
| `POST` | `/api/calls/start/{leadId}` | Start the AI conversation; returns the greeting text and its audio URL |
| `POST` | `/api/calls/{callId}/response` | Multipart `audio`; transcribes it and returns the next AI message with audio |
| `GET` | `/api/calls/{callId}/conversation` | Full conversation history |
| `GET` | `/api/calls/{callId}/summary` | Summary and final outcome |

### Lead management

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/leads/{id}` | Lead detail with its call history and outcomes |
| `PUT` | `/api/leads/{id}` | Update name, phone, company or status (partial body) |
| `DELETE` | `/api/leads/{id}` | Delete a lead with its calls, messages and audio files |

Lead status also moves automatically as a call progresses: `NEW` → `CONTACTED` when the call
starts, then `QUALIFIED` after a demo request or `DISQUALIFIED` when the customer is not interested
(the agent acknowledges the refusal — "Okay, thank you for letting me know…" — and closes the call).
An answer that is neither yes nor no is confirmed once; if the second answer is still unclear the
call closes the same way, because asking a third time would loop forever.
`CONVERTED` is set by hand through `PUT /api/leads/{id}`, since closing a deal is a human decision.

Selecting a lead in the browser page reopens its most recent call: the stored turns, what has been
captured, and the step it stopped at. An unfinished call is simply continued — the composer stays
enabled and the next answer goes to the same `callId`.

### Supporting

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/calls/{callId}/response/text` | JSON `{"text": "..."}`; the same turn typed instead of spoken |
| `GET` | `/api/dashboard` | Leads per stage and calls per outcome, counted by the database |
| `GET` | `/api/audio/{filename}` | Stream a stored AI or customer audio file |
| `GET` | `/api/speech/status` | OpenAI models in use and whether the API key is present |
| `POST` | `/api/speech/test` | TTS → STT round trip that validates the key |

The text endpoint exists for demos where a microphone is unavailable or the free-tier quota is
exhausted. It runs the identical conversation logic and still speaks the agent's reply; only
speech-to-text is skipped, so the stored customer message has no audio file and the response
carries `"inputMode": "TEXT"` — a typed answer is never presented as a transcription. Add
`?speak=false` to skip speech generation as well, which keeps a call moving while a rate limit
resets. The mandatory `POST /api/calls/{callId}/response` remains audio-only.

### Example curl flow

```bash
# 1. Create a lead
curl -s -X POST http://localhost:8080/api/leads \
  -H 'Content-Type: application/json' \
  -d '{"name":"Priya Sharma","phoneNumber":"+919876543210","companyName":"Northwind Sales"}'

# 2. Start the call (use the returned lead id)
curl -s -X POST http://localhost:8080/api/calls/start/1

# 3. Send the customer's recorded reply; OpenAI transcribes the audio
curl -s -X POST http://localhost:8080/api/calls/1/response -F 'audio=@./my-reply.wav'

# 3b. Or type the reply when no microphone is available (speech-to-text is skipped)
curl -s -X POST http://localhost:8080/api/calls/1/response/text \
  -H 'Content-Type: application/json' \
  -d '{"text":"Yes, I am interested in a CRM"}'

# 4. History and summary
curl -s http://localhost:8080/api/calls/1/conversation
curl -s http://localhost:8080/api/calls/1/summary
```

The voice path is the real one: audio in, transcript out, exactly as the assignment requires. The
text path is a labelled demo aid, not a fallback the voice path silently drops into.
`docs/SAMPLE_CONVERSATION.md` shows full responses for each step.

## Conversation flow

The agent greets the lead, confirms interest, then collects three details: sales team size, how
leads are handled today, and whether they want a demo. Three rules keep the dialog usable with
real, unscripted replies:

- Details the customer offers before being asked are recorded, and that question is then skipped.
- A question the reply does not answer is repeated **once**; after that the agent moves on and the
  summary notes the detail was not shared, so the call can never loop on one question.
- "Not interested" and callback requests end the call immediately, from any step.

Outcomes: `INTERESTED`, `DEMO_REQUESTED`, `NOT_INTERESTED`, `CALLBACK_REQUESTED`, `COMPLETED`
(`IN_PROGRESS` while the call is active).

## How speech works

| Direction | OpenAI call | Handling |
|-----------|-------------|----------|
| TTS | `POST /v1/audio/speech` with the model, voice and `response_format: wav` | The reply is a complete WAV file, so the bytes are written straight to disk and served; the service rejects anything that does not start with `RIFF` rather than storing a file that will not play |
| STT — transcription model | `POST /v1/audio/transcriptions` as multipart form data with the recording and `response_format: json` | The reply is `{"text": "..."}`; an empty transcript becomes an HTTP 400 instead of an invented one |
| STT — chat audio model | `POST /v1/chat/completions` with the recording base64-encoded as an `input_audio` part and `modalities: ["text"]` | The model is told to answer with the spoken words only, or `NO_SPEECH`; that sentinel and any quotes it wraps around the text are stripped before the transcript is stored |

A chat model that cannot make out the recording sometimes answers the instruction instead of the
audio — "Sure, please go ahead and provide the audio recording, and I'll transcribe it for you."
That is detected, retried once with the audio first and the framing removed, and if the model
refuses again the turn is rejected as an unreadable recording (HTTP 400, nothing stored) and the
excuse goes to the log, where the model name and the setting to change are useful. It is never
shown as the customer's words and never quoted back in the browser. Storing such a reply derails
the qualification script: "Sure…" reads as interest, and the agent moves on having learned nothing.

The instructions sent with the audio stay deliberately plain for the same reason. Framing the job
as analysing a *voice* or a *speaker* invites a safety refusal ("I can't analyze or transcribe the
audio based on a voice sample"), so the model is only ever asked what is said in the clip — as a
question, because a model that heard nothing echoes an imperative straight back. Any reply that
turns out to be the instruction itself is discarded rather than stored as the customer's turn.

The check is deliberately not a list of phrases, since the wording varies every time. A reply
counts as chatter only when it mentions the transcription task (`transcribe`, `audio`, `recording`)
**and** speaks as an assistant about it (`I can't`, `I'm sorry`, `please provide`, `let me know`).
A customer saying "we keep a recording of every sales call" trips one list, not both.

Notes:

- Which of the two STT rows applies is decided by `app.openai.stt-model`: a name containing
  `audio` goes to the chat endpoint, anything else to the transcription endpoint. Nothing else in
  the app changes — both implement the same `SpeechToTextService`.
- The chat audio path accepts WAV and MP3 only; the transcription path accepts far more. The
  browser converts recordings to WAV either way, so both work from the page.
- The multipart upload keeps its original filename, because OpenAI detects the audio container from
  the extension rather than the content type.
- The browser still converts mic recordings to **16 kHz mono WAV** before upload. OpenAI accepts
  webm too, but decoding in the page lets it measure the level and warn about a silent recording
  before spending an API call on it.
- `app.openai.tts-instructions` steers tone and pace. Only `gpt-4o-mini-tts` accepts it — blank it
  out when switching to `tts-1` or `tts-1-hd`.
- The agent asks the same scripted questions on every call, so generated audio is cached in memory
  by model, voice and message text. Repeat demos reuse it instead of paying per character again,
  and each message still gets its own file so deleting one call cannot break another's history.
- HTTP 429 is surfaced as a 429 with the wait time, and is never retried automatically — retrying a
  rate limit only makes it worse. An `insufficient_quota` 429 says so explicitly, because that one
  is an empty balance rather than a wait.
- Transcription is not asked to guess: unlike a prompt-driven model there is no sentinel for
  silence, so an empty `text` is what marks a recording as unusable.

## Configuration

| Property | Env var | Default | Purpose |
|----------|---------|---------|---------|
| `app.openai.api-key` | `OPENAI_API_KEY` | empty | OpenAI platform key |
| `app.openai.base-url` | | `https://api.openai.com/v1` | OpenAI REST endpoint |
| `app.openai.stt-model` | `OPENAI_STT_MODEL` | `gpt-audio-mini` | Listening model. A name containing `audio` uses `/chat/completions`; `gpt-4o-mini-transcribe`, `gpt-4o-transcribe` and `whisper-1` use `/audio/transcriptions` |
| `app.openai.tts-model` | `OPENAI_TTS_MODEL` | `gpt-4o-mini-tts` | Speech model; `tts-1` and `tts-1-hd` also work |
| `app.openai.tts-voice` | | `alloy` | One of alloy, ash, ballad, coral, echo, fable, onyx, nova, sage, shimmer, verse |
| `app.openai.tts-instructions` | | warm support agent | Tone and pace guidance; `gpt-4o-mini-tts` only |
| `app.openai.language` | | `en` | ISO-639-1 transcription hint; blank lets the model detect it |
| `app.storage.audio-dir` | | `./storage/audio` | Where audio files are written |
| `app.public-base-url` | `PUBLIC_BASE_URL` | _(blank)_ | Prefix for returned audio URLs. Blank gives relative URLs that work on any host or port; set an absolute URL only for clients on another origin |

## Project layout

```
src/main/java/com/flowzo/callingagent/
├── controller/      # Leads, calls, audio streaming, speech diagnostics
├── entity/          # Lead, CallRecord, ConversationMessage
├── repository/      # Spring Data JPA
├── dto/             # API request/response contracts
├── enums/           # Call status, outcome, conversation step, speaker, lead status
├── exception/       # Typed errors + @RestControllerAdvice
└── service/
    ├── conversation/  # Rule-based sales dialog state machine
    ├── openai/        # Shared audio API client: auth + error handling
    ├── stt/           # OpenAI speech-to-text
    ├── tts/           # OpenAI text-to-speech
    └── audio/         # Mime type helpers
```

## Tests

```bash
./mvnw test
```

61 tests, no network access and no API key required. Integration tests replace the two OpenAI
services with fakes (`src/test/java/.../support/FakeSpeechConfig.java`) so the real controllers,
conversation engine, repositories and database are all exercised, while the OpenAI services
themselves are unit tested against a mocked HTTP server (`MockRestServiceServer`).

Coverage: the full call flow, every outcome branch, the re-prompt rule, typed turns mixed with
spoken ones, lead CRUD and cascade delete, dashboard totals, audio streaming, error handling, the multipart upload
and JSON bodies sent to OpenAI, both transcription endpoints, playable-WAV validation, rate-limit
and empty-balance handling, missing-model-entitlement errors, and the audio cache.

## Troubleshooting

| Symptom | Cause and fix |
|---------|---------------|
| A red "Speech is not configured" strip appears, or starting a call returns HTTP 502 | The key was not visible at startup. Export `OPENAI_API_KEY` in the same shell that runs `./mvnw spring-boot:run`, then restart. |
| `OpenAI rejected the API key (HTTP 401)` | The key is revoked, mistyped, or belongs to another organisation. Create a fresh one at [platform.openai.com/api-keys](https://platform.openai.com/api-keys). |
| `The OpenAI account is out of credit (insufficient_quota)` | Billing, not a bug: the audio endpoints are pay-as-you-go. Add credit at [platform.openai.com billing](https://platform.openai.com/settings/organization/billing). Until then, **Send typed reply** with `speak=false` still exercises the whole conversation flow. |
| `OpenAI rate limit reached ... Wait about N seconds` (HTTP 429) | Too many requests per minute for the account tier. Wait the stated time and resend the same answer — nothing is lost, because the turn is rolled back. Repeated agent lines come from the audio cache, so a replayed demo costs far less than the first run. |
| `The OpenAI project cannot use the configured model` (HTTP 403 `model_not_found`, or 404) | The key is fine; the project is not entitled to that model. List what it can use with `curl https://api.openai.com/v1/models -H "Authorization: Bearer $OPENAI_API_KEY"`, then set `OPENAI_STT_MODEL` / `OPENAI_TTS_MODEL` to models on that list. A project with `gpt-audio-mini` but no `*-transcribe` model should use `OPENAI_STT_MODEL=gpt-audio-mini`, which is the default. |
| `OpenAI TTS returned ... not WAV audio` | The configured speech model ignored `response_format: wav`. Switch `app.openai.tts-model` to `gpt-4o-mini-tts` or `tts-1`. |
| "no speech stood out from the background" after Stop | The take is kept and **Send** still works — this is advice, not a block. Check the browser's site settings to confirm the right microphone is selected and unmuted, then record again. |
| Quiet speech warning after Stop | A voice was found but it is soft. Send it, or move closer and record again. |
| The page looks unchanged after a rebuild | Static resources are served `no-cache`, so a plain reload is enough. An older browser session may still need Ctrl+Shift+R once. |

Recording captures raw PCM from the microphone graph and normalises the gain before anything is
judged, so a weak input still produces a usable WAV. The voice check has its own harness, which
runs the page's real functions over synthetic and recorded audio:

```bash
node tools/voice-check.test.js
```
| "That recording is very short" | Speak for a second or two before pressing Stop. |
| "That recording could not be read" (HTTP 400) | OpenAI answered but produced no words. Move closer to the mic and record again, or type the reply — the call is still waiting on the same question, so nothing is lost. If the log shows `refused twice`, the model would not transcribe rather than mishear: confirm with `curl -X POST "localhost:8080/api/speech/test?text=Hello"`, which transcribes audio the app generated itself, then switch `OPENAI_STT_MODEL` to `whisper-1` or `gpt-4o-mini-transcribe` if the project is entitled to one. |
| Mic button does nothing | Browsers only allow microphone access on `localhost` or HTTPS. |

## Requirement coverage

| Requirement | Where it is implemented | Proof |
|-------------|------------------------|-------|
| Create and manage a lead | `LeadController`, `LeadService` — create, list, detail, update, delete | `LeadManagementIntegrationTest` |
| Start an AI conversation for the lead | `POST /api/calls/start/{leadId}` → `CallService.startCall` | `CallFlowIntegrationTest` |
| Generate the AI response and convert it to speech | `RuleBasedConversationEngine` + `OpenAiTextToSpeechService` | every turn returns an `audioUrl` |
| Accept customer voice and convert it to text | `POST /api/calls/{callId}/response` → `OpenAiSpeechToTextService` | transcript stored as a `CUSTOMER` message |
| Continue the conversation based on the response | `ConversationStep` state machine: captures out-of-order answers, re-asks once, never loops | outcome and re-prompt tests |
| Store the complete conversation history | `conversation_messages` (call, speaker, text, sequence, audio path, timestamp) | `GET /api/calls/{id}/conversation` |
| Generate a summary and final outcome | `RuleBasedConversationEngine.buildSummary`, `CallOutcome` | `GET /api/calls/{id}/summary` |

The typed-reply endpoint is an extra on top of this table, not a substitute for speech-to-text: it
is a separate path, marked `TEXT` in every response, so the voice requirement is still met by real
OpenAI transcription.

## Deliverables map

| Deliverable | Location |
|-------------|----------|
| Java Spring Boot source code | `src/main/java` |
| Setup instructions | this README |
| Database structure | `docs/DATABASE.md` |
| Postman collection / Swagger | `postman/`, `/swagger-ui.html` |
| Sample conversation output | `docs/SAMPLE_CONVERSATION.md` |
| Unit and integration tests | `src/test/java` |
