# Glossary

Use these words, in these meanings, in code, docs and conversation.

## Domain (taken from the app's own screens)

| Term | Meaning |
|---|---|
| **Deck** | The whole workspace: every saved session plus which one is open. Also the main screen. |
| **Session** | One saved brainstorm: a title, a seed prompt, its idea cards and its graveyard. Stored in `neural_deck_sessions_v1.json`. Class: `BrainstormSession`. |
| **Seed prompt** | The idea or question typed into the terminal box at the bottom; what a spark starts from. |
| **Idea Card** | One node of the tree: title, summary, rationale ("// WHY"), tags, skill badge, accent colour, starred, collapsed. Class: `IdeaCard`. |
| **Root card** | A card with no parent. |
| **Skill** (cartridge) | A thinking lens the model applies, such as SCAMPER, 10X_DIVERGE or PRE_MORTEM: a badge, a colour, an instruction and an output count. Eight are built in; users can forge custom ones. Class: `CreativeSkill`. |
| **Active skill** | The skill shown as `SKILL: <badge>` in the header, used by Spark and quick Branch. |
| **Spark** | Ask the model for new root cards from the seed prompt (`SPARK_IDEAS`). |
| **Branch** | Ask the model for child cards of one card, through a skill. |
| **Splice** | Select two or more cards (`[+SPLICE]`) and ask the model for hybrids of them (`SPLICE_X<n>`). |
| **Banish** | Remove a card and everything under it, optionally leaving a graveyard rule behind. |
| **Graveyard** | Banished cards plus their anti-pattern rules. Active rules are added to every prompt so the model stops suggesting those directions. |
| **Graveyard rule** | One "don't suggest anything like this" line, with a reason tag (TOO_CLICHE, OUT_OF_SCOPE, WRONG_VIBE, DEAD_END). Can be muted. |
| **Revive** | Bring a banished card back from the graveyard. |
| **Purge** | Delete a graveyard entry for good. |
| **Prompt Lab** | The sheet that rewrites a rough seed prompt with a framework (CO-STAR, 10X_DIVERGENT, SOCRATIC, CONSTRAINT_BOX); falls back to an offline heuristic without a key. |
| **BYOK vault** | Bring-your-own-key settings: provider, base URL, model, API key, temperature, global context. |
| **Provider** | An OpenAI-compatible endpoint preset: OpenRouter, OpenAI, Groq, DeepSeek or a custom URL. |
| **Rails** | The pixel "circuit" lines drawn left of nested cards to show the tree. |

## Planned modules

Final names and interfaces: [docs/architecture/FINAL-DESIGN.md](docs/architecture/FINAL-DESIGN.md), which wins over
the summaries below. **Phase A** is the refactor that keeps the look, layout and every existing string; it changes
behaviour only for the approved defect fixes and the two manifest changes, plus one approved screen (D14).
**Phase B** is every other visible change, each previewed to the user first.

| Term | Meaning |
|---|---|
| **Sessions store** | Will read and write the sessions file safely: temp file then rename, a backup, corrupt files set aside instead of overwritten. |
| **Deck module** (C1) | Will own the sessions, the open session, requests in flight and saving. The main screen will only send what the user did and draw what the Deck reports. |
| **IdeaTree** (C2) | Will be the single owner of tree shape: visible rows, depth, rails, subtree size, ancestors and siblings. |
| **Idea Engine** (C3) | Will turn a spark / branch / splice / optimise request into cards or an error, with prompt building and reply parsing behind it and a swappable source of model text. |
| **Provider profile** (C4) | Will hold one provider's settings as a value; the BYOK sheet edits a draft that only SAVE commits. |

## Architecture vocabulary

These words have exact meanings in this project's design documents. Avoid "component", "service", "API", "boundary".

| Term | Meaning |
|---|---|
| **Module** | Anything with an interface and an implementation: a function, a class, a package. |
| **Interface** | Everything a caller must know to use a module correctly: signatures, plus invariants, ordering, error modes and required configuration. |
| **Implementation** | The code inside a module. |
| **Depth** | How much behaviour a caller gets per unit of interface they must learn. **Deep**: a lot behind a small interface. **Shallow**: the interface is nearly as complex as the implementation. |
| **Seam** | A place where behaviour can be swapped without editing that place; where a module's interface sits. |
| **Adapter** | A concrete thing plugged in at a seam (for example the real HTTP client, or a fake for tests). One adapter is a hypothetical seam; two make a real one. |
| **Leverage** | What callers gain from depth: more capability per thing learned. |
| **Locality** | What maintainers gain from depth: changes, bugs and tests concentrate in one place. |
