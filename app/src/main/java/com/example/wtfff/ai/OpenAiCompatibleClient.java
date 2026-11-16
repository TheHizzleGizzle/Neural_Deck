package com.example.wtfff.ai;

import android.os.Handler;
import android.os.Looper;

import com.example.wtfff.model.CreativeSkill;
import com.example.wtfff.model.GraveyardEntry;
import com.example.wtfff.model.IdeaCard;
import com.example.wtfff.model.PromptOptimizationResult;
import com.example.wtfff.storage.AiConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OpenAiCompatibleClient {

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    private final AiConfig aiConfig;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public OpenAiCompatibleClient(AiConfig aiConfig) {
        this.aiConfig = aiConfig;
    }

    public void testConnection(String baseUrl, String apiKey, String model, Callback<String> callback) {
        executor.execute(() -> {
            try {
                String reply = executeChatCompletion(
                        baseUrl,
                        apiKey,
                        model,
                        0.2f,
                        "You are a concise diagnostic probe. Reply with exactly: LINK_ESTABLISHED",
                        "Ping neural link."
                );
                String trimmed = reply.trim();
                if (trimmed.length() > 90) trimmed = trimmed.substring(0, 90) + "...";
                final String msg = "ONLINE // " + model + " -> " + trimmed;
                mainHandler.post(() -> callback.onSuccess(msg));
            } catch (Exception e) {
                final String err = e.getMessage() != null ? e.getMessage() : "Connection failed";
                mainHandler.post(() -> callback.onError(err));
            }
        });
    }

    public void generateSeedIdeas(String seedPrompt,
                                  CreativeSkill initialSkill,
                                  List<GraveyardEntry> graveyard,
                                  Callback<List<IdeaCard>> callback) {
        executor.execute(() -> {
            try {
                String badge = initialSkill != null ? initialSkill.badge : "SEED";
                int accent = initialSkill != null ? initialSkill.accentColorHex : 0xFF00F0FF;
                int count = initialSkill != null ? initialSkill.outputCount : 4;
                String lens = initialSkill != null
                        ? initialSkill.systemInstruction
                        : "Generate wildly distinct, high-leverage creative angles that avoid generic advice.";

                String sys = buildBrainstormSystemPrompt(lens, graveyard, count);
                String user = "Brainstorm Topic / Seed:\n" + seedPrompt +
                        "\n\nReturn JSON object with key \"ideas\" containing " + count + " distinct cards.";

                String raw = executeChatCompletion(
                        aiConfig.getBaseUrl(),
                        aiConfig.getActiveApiKey(),
                        aiConfig.getModel(),
                        aiConfig.getTemperature(),
                        sys,
                        user
                );
                List<IdeaCard> cards = parseIdeaCards(raw, null, 0, badge, accent);
                mainHandler.post(() -> callback.onSuccess(cards));
            } catch (Exception e) {
                postError(callback, e);
            }
        });
    }

    public void branchFromCard(IdeaCard parent,
                               String sessionSeed,
                               CreativeSkill skill,
                               List<GraveyardEntry> graveyard,
                               Callback<List<IdeaCard>> callback) {
        executor.execute(() -> {
            try {
                String sys = buildBrainstormSystemPrompt(skill.systemInstruction, graveyard, skill.outputCount);
                StringBuilder user = new StringBuilder();
                if (sessionSeed != null && !sessionSeed.isEmpty()) {
                    user.append("Session Root Context: ").append(sessionSeed).append("\n\n");
                }
                user.append("Target Parent Card to Branch From:\n")
                        .append("- Title: ").append(parent.title).append("\n")
                        .append("- Summary: ").append(parent.summary).append("\n");
                if (parent.rationale != null && !parent.rationale.isEmpty()) {
                    user.append("- Rationale: ").append(parent.rationale).append("\n");
                }
                user.append("\nApply the skill [").append(skill.name).append("] and return JSON with ")
                        .append(skill.outputCount).append(" child branch cards.");

                String raw = executeChatCompletion(
                        aiConfig.getBaseUrl(),
                        aiConfig.getActiveApiKey(),
                        aiConfig.getModel(),
                        aiConfig.getTemperature(),
                        sys,
                        user.toString()
                );
                List<IdeaCard> children = parseIdeaCards(
                        raw,
                        parent.id,
                        parent.depth + 1,
                        skill.badge,
                        skill.accentColorHex
                );
                mainHandler.post(() -> callback.onSuccess(children));
            } catch (Exception e) {
                postError(callback, e);
            }
        });
    }

    public void mashupCards(List<IdeaCard> selectedCards,
                            String sessionSeed,
                            List<GraveyardEntry> graveyard,
                            Callback<List<IdeaCard>> callback) {
        executor.execute(() -> {
            try {
                String lens = "Synthesize unexpected hybrid concepts by fusing the strongest mechanics of ALL selected cards into cohesive, non-obvious mashups. Do not merely list features side-by-side; create emergent synergy.";
                String sys = buildBrainstormSystemPrompt(lens, graveyard, 3);

                StringBuilder user = new StringBuilder();
                if (sessionSeed != null && !sessionSeed.isEmpty()) {
                    user.append("Session Context: ").append(sessionSeed).append("\n\n");
                }
                user.append("Fuse these ").append(selectedCards.size()).append(" Idea Nodes:\n");
                for (int i = 0; i < selectedCards.size(); i++) {
                    IdeaCard c = selectedCards.get(i);
                    user.append(i + 1).append(". [").append(c.title).append("]: ").append(c.summary).append("\n");
                }
                user.append("\nReturn JSON with 3 emergent hybrid mashup cards.");

                String raw = executeChatCompletion(
                        aiConfig.getBaseUrl(),
                        aiConfig.getActiveApiKey(),
                        aiConfig.getModel(),
                        Math.min(1.2f, aiConfig.getTemperature() + 0.1f),
                        sys,
                        user.toString()
                );

                IdeaCard anchor = selectedCards.get(0);
                List<IdeaCard> hybrids = parseIdeaCards(
                        raw,
                        anchor.id,
                        anchor.depth + 1,
                        "SPLICE_X" + selectedCards.size(),
                        0xFFFF2E93
                );
                mainHandler.post(() -> callback.onSuccess(hybrids));
            } catch (Exception e) {
                postError(callback, e);
            }
        });
    }

    public void optimizePrompt(String rawPrompt,
                               String frameworkName,
                               List<GraveyardEntry> graveyard,
                               Callback<PromptOptimizationResult> callback) {
        executor.execute(() -> {
            try {
                String frameworkGuide = getFrameworkInstruction(frameworkName);
                StringBuilder sys = new StringBuilder();
                sys.append("You are an elite AI Prompt Engineer specialized in divergent creative brainstorming.\n")
                        .append("Rewrite the user's rough brainstorm seed into a razor-sharp, high-leverage prompt using the ")
                        .append(frameworkName).append(" methodology.\n")
                        .append(frameworkGuide).append("\n");

                appendGlobalAndGraveyardContext(sys, graveyard);

                sys.append("\nRespond ONLY with valid JSON matching this exact schema:\n")
                        .append("{\n")
                        .append("  \"optimized_prompt\": \"The upgraded prompt ready to run...\",\n")
                        .append("  \"clarity_score\": 94,\n")
                        .append("  \"upgrades\": [\"Added concrete target constraint\", \"Defined divergent output axes\", \"Filtered generic clichés\"]\n")
                        .append("}");

                String raw = executeChatCompletion(
                        aiConfig.getBaseUrl(),
                        aiConfig.getActiveApiKey(),
                        aiConfig.getModel(),
                        0.65f,
                        sys.toString(),
                        "Rough Input Prompt:\n" + rawPrompt
                );

                PromptOptimizationResult result = parseOptimizationResult(rawPrompt, frameworkName, raw);
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                postError(callback, e);
            }
        });
    }

    private String getFrameworkInstruction(String framework) {
        if ("CO-STAR".equalsIgnoreCase(framework)) {
            return "Structure around Context, Objective, Style, Tone, Audience, and Response format—kept punchy for rapid ideation.";
        }
        if ("10X_DIVERGENT".equalsIgnoreCase(framework)) {
            return "Force radical orthogonality: demand ideas across opposing paradigms (hardware/software, zero-UI, inversion, asymmetric wedge).";
        }
        if ("SOCRATIC".equalsIgnoreCase(framework)) {
            return "Frame the prompt around exposing hidden assumptions, bottlenecks, and counter-intuitive first-principles questions.";
        }
        if ("CONSTRAINT_BOX".equalsIgnoreCase(framework)) {
            return "Inject productive creative constraints (zero budget, 48-hour build, single-screen UX, no notifications) to force ingenuity.";
        }
        return "Clarify objective, target persona, sharp constraints, and anti-cliché guardrails.";
    }

    private String buildBrainstormSystemPrompt(String skillInstruction,
                                               List<GraveyardEntry> graveyard,
                                               int count) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are NEURAL//DECK, an AI-first creative brainstorming engine.\n")
                .append("Core Directive: ").append(skillInstruction).append("\n")
                .append("Be concrete, vivid, and opinionated. Avoid vague corporate buzzwords.\n");

        appendGlobalAndGraveyardContext(sb, graveyard);

        sb.append("\nRespond ONLY with valid JSON in this exact structure (no markdown commentary outside JSON):\n")
                .append("{\n")
                .append("  \"ideas\": [\n")
                .append("    {\n")
                .append("      \"title\": \"Punchy 2-5 Word Concept Name\",\n")
                .append("      \"summary\": \"2-3 crisp sentences explaining the exact mechanic and user experience.\",\n")
                .append("      \"rationale\": \"1 sharp sentence on why this angle unlocks asymmetric leverage.\",\n")
                .append("      \"tags\": [\"TAG_ONE\", \"TAG_TWO\"]\n")
                .append("    }\n")
                .append("  ]\n")
                .append("}\n")
                .append("Generate exactly ").append(count).append(" distinct items in \"ideas\".");
        return sb.toString();
    }

    private void appendGlobalAndGraveyardContext(StringBuilder sb, List<GraveyardEntry> graveyard) {
        String globalCtx = aiConfig.getGlobalContext();
        if (globalCtx != null && !globalCtx.isEmpty()) {
            sb.append("\n[USER PERSONA / GLOBAL CONTEXT]\n").append(globalCtx).append("\n");
        }
        if (graveyard != null && !graveyard.isEmpty()) {
            List<GraveyardEntry> active = new ArrayList<>();
            for (GraveyardEntry g : graveyard) {
                if (g.activeInPrompts) active.add(g);
            }
            if (!active.isEmpty()) {
                sb.append("\n[FORBIDDEN ANTI-PATTERNS // IDEA GRAVEYARD]\n")
                        .append("The user explicitly banished these directions. DO NOT propose anything similar to:\n");
                for (GraveyardEntry g : active) {
                    sb.append("- [").append(g.reasonTag).append("] ").append(g.antiPatternRule).append("\n");
                }
            }
        }
    }

    private String executeChatCompletion(String baseUrl,
                                         String apiKey,
                                         String model,
                                         float temperature,
                                         String systemPrompt,
                                         String userPrompt) throws Exception {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalStateException("Missing Base URL. Configure BYOK endpoint first.");
        }
        String endpoint = normalizeChatEndpoint(baseUrl.trim());
        boolean isLocal = endpoint.contains("127.0.0.1") || endpoint.contains("localhost") || endpoint.contains("10.0.2.2");
        if ((apiKey == null || apiKey.trim().isEmpty()) && !isLocal) {
            throw new IllegalStateException("Missing API Key. Tap [BYOK_KEY] in the top bar to add your key.");
        }

        JSONObject body = new JSONObject();
        body.put("model", (model == null || model.trim().isEmpty()) ? "gpt-4o-mini" : model.trim());
        body.put("temperature", temperature);

        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.put(sysMsg);

        JSONObject usrMsg = new JSONObject();
        usrMsg.put("role", "user");
        usrMsg.put("content", userPrompt);
        messages.put(usrMsg);

        body.put("messages", messages);

        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(45000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
            }
            if (endpoint.contains("openrouter.ai")) {
                conn.setRequestProperty("HTTP-Referer", "https://neuraldeck.local");
                conn.setRequestProperty("X-Title", "NEURAL//DECK Brainstormer");
            }

            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload);
                os.flush();
            }

            int status = conn.getResponseCode();
            InputStream stream = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
            String responseText = readStream(stream);

            if (status < 200 || status >= 300) {
                String apiErr = extractApiError(responseText, status);
                throw new RuntimeException(apiErr);
            }

            JSONObject respJson = new JSONObject(responseText);
            JSONArray choices = respJson.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                throw new RuntimeException("Empty choices from provider response.");
            }
            JSONObject first = choices.getJSONObject(0);
            JSONObject message = first.optJSONObject("message");
            if (message != null) {
                return message.optString("content", "");
            }
            return first.optString("text", "");
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String normalizeChatEndpoint(String baseUrl) {
        String clean = baseUrl;
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        if (clean.endsWith("/chat/completions")) {
            return clean;
        }
        return clean + "/chat/completions";
    }

    private String extractApiError(String responseText, int status) {
        try {
            JSONObject obj = new JSONObject(responseText);
            JSONObject err = obj.optJSONObject("error");
            if (err != null) {
                String msg = err.optString("message", "");
                if (!msg.isEmpty()) {
                    return "HTTP " + status + ": " + msg;
                }
            }
        } catch (Exception ignored) {
        }
        String snippet = responseText != null ? responseText.trim() : "";
        if (snippet.length() > 160) snippet = snippet.substring(0, 160) + "...";
        return "HTTP " + status + (snippet.isEmpty() ? "" : " — " + snippet);
    }

    private List<IdeaCard> parseIdeaCards(String rawContent,
                                          String parentId,
                                          int depth,
                                          String badge,
                                          int accentHex) throws Exception {
        String jsonSlice = extractJsonBlock(rawContent);
        List<IdeaCard> out = new ArrayList<>();

        JSONArray arr = null;
        if (jsonSlice.startsWith("[")) {
            arr = new JSONArray(jsonSlice);
        } else {
            JSONObject root = new JSONObject(jsonSlice);
            arr = root.optJSONArray("ideas");
            if (arr == null) arr = root.optJSONArray("cards");
            if (arr == null) arr = root.optJSONArray("branches");
        }

        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) continue;
                String title = item.optString("title", "NODE_" + (i + 1));
                String summary = item.optString("summary", item.optString("description", ""));
                String rationale = item.optString("rationale", item.optString("why", ""));
                List<String> tags = new ArrayList<>();
                JSONArray tArr = item.optJSONArray("tags");
                if (tArr != null) {
                    for (int t = 0; t < tArr.length(); t++) {
                        String tag = tArr.optString(t, "").trim().toUpperCase().replace(' ', '_');
                        if (!tag.isEmpty()) tags.add(tag);
                    }
                }
                out.add(new IdeaCard(parentId, depth, title, summary, rationale, badge, accentHex, tags));
            }
        }

        if (out.isEmpty()) {
            out.add(new IdeaCard(
                    parentId,
                    depth,
                    "AI Synthesis Output",
                    rawContent.trim(),
                    "Parsed from freeform model output.",
                    badge,
                    accentHex,
                    new ArrayList<>()
            ));
        }
        return out;
    }

    private PromptOptimizationResult parseOptimizationResult(String original,
                                                             String framework,
                                                             String rawContent) {
        try {
            String jsonSlice = extractJsonBlock(rawContent);
            JSONObject obj = new JSONObject(jsonSlice);
            String optimized = obj.optString("optimized_prompt", obj.optString("prompt", "")).trim();
            int score = obj.optInt("clarity_score", 90);
            List<String> upgrades = new ArrayList<>();
            JSONArray arr = obj.optJSONArray("upgrades");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    String u = arr.optString(i, "").trim();
                    if (!u.isEmpty()) upgrades.add(u);
                }
            }
            if (!optimized.isEmpty()) {
                return new PromptOptimizationResult(original, framework, optimized, score, upgrades);
            }
        } catch (Exception ignored) {
        }
        List<String> fallbackUpgrades = new ArrayList<>();
        fallbackUpgrades.add("Structured with " + framework + " lens");
        return new PromptOptimizationResult(original, framework, rawContent.trim(), 88, fallbackUpgrades);
    }

    private String extractJsonBlock(String text) {
        if (text == null) return "{}";
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
            trimmed = trimmed.trim();
        }
        int firstBrace = trimmed.indexOf('{');
        int firstBracket = trimmed.indexOf('[');
        if (firstBrace == -1 && firstBracket == -1) {
            return trimmed;
        }
        if (firstBracket != -1 && (firstBrace == -1 || firstBracket < firstBrace)) {
            int lastBracket = trimmed.lastIndexOf(']');
            if (lastBracket > firstBracket) {
                return trimmed.substring(firstBracket, lastBracket + 1);
            }
        }
        if (firstBrace != -1) {
            int lastBrace = trimmed.lastIndexOf('}');
            if (lastBrace > firstBrace) {
                return trimmed.substring(firstBrace, lastBrace + 1);
            }
        }
        return trimmed;
    }

    private String readStream(InputStream is) throws Exception {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private <T> void postError(Callback<T> callback, Exception e) {
        final String msg = e.getMessage() != null ? e.getMessage() : "Unexpected AI request error";
        mainHandler.post(() -> callback.onError(msg));
    }
}
