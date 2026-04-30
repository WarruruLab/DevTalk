package com.devtalk.devtalk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
    private String mode;
    private Default defaultConfig;
    private Context context;
    private Stream stream;
    private Mock mock;
    private Gemini gemini;
    private Ollama ollama;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Default getDefault() {
        return defaultConfig;
    }

    public void setDefault(Default defaultConfig) {
        this.defaultConfig = defaultConfig;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public Stream getStream() {
        return stream;
    }

    public void setStream(Stream stream) {
        this.stream = stream;
    }

    public Mock getMock() {
        return mock;
    }

    public void setMock(Mock mock) {
        this.mock = mock;
    }

    public Gemini getGemini() {
        return gemini;
    }

    public void setGemini(Gemini gemini) {
        this.gemini = gemini;
    }

    public Ollama getOllama() {
        return ollama;
    }

    public void setOllama(Ollama ollama) {
        this.ollama = ollama;
    }

    public String resolvedMode() {
        return (mode == null || mode.isBlank()) ? "mock" : mode;
    }

    public Mock resolvedMock() {
        return mock == null ? new Mock(false) : mock;
    }

    public Default resolvedDefaults() {
        return defaultConfig == null ? new Default(0.2, 65536) : defaultConfig;
    }

    public Context resolvedContext() {
        return context == null ? new Context(
            new Tail(12, 6000),
            new Summary(1000, 1200, 12),
            new Continue(2, 200)
        ) : context;
    }

    public Stream resolvedStream() {
        return stream == null ? new Stream(180000, new Executor(4, 8, 100)) : stream;
    }

    public Gemini resolvedGemini() {
        return gemini == null ? new Gemini(
            "https://generativelanguage.googleapis.com",
            "",
            "gemini-2.5-flash",
            3000,
            15000,
            60000
        ) : gemini;
    }

    public Ollama resolvedOllama() {
        return ollama == null ? new Ollama(
            "http://ollama:11434",
            "qwen2.5:3b",
            3000,
            60000,
            120000
        ) : ollama;
    }

    public record Mock(boolean alwaysFail) {}

    public record Default(
        Double temperature,
        Integer maxTokens
    ) {}

    public static class Context {
        private Tail tail;
        private Summary summary;
        private Continue continueConfig;

        public Context() {
        }

        public Context(Tail tail, Summary summary, Continue continueConfig) {
            this.tail = tail;
            this.summary = summary;
            this.continueConfig = continueConfig;
        }

        public Tail getTail() {
            return tail;
        }

        public void setTail(Tail tail) {
            this.tail = tail;
        }

        public Summary getSummary() {
            return summary;
        }

        public void setSummary(Summary summary) {
            this.summary = summary;
        }

        public Continue getContinue() {
            return continueConfig;
        }

        public void setContinue(Continue continueConfig) {
            this.continueConfig = continueConfig;
        }

        public Tail resolvedTail() {
            return tail == null ? new Tail(12, 6000) : tail;
        }

        public Summary resolvedSummary() {
            return summary == null ? new Summary(1000, 1200, 12) : summary;
        }

        public Continue resolvedContinue() {
            return continueConfig == null ? new Continue(2, 200) : continueConfig;
        }
    }

    public record Tail(
        int maxMessages,
        int maxChars
    ) {}

    public record Summary(
        int promptMaxChars,
        int hardMaxChars,
        int keepTailMessages
    ) {}

    public record Continue(
        int maxRounds,
        int anchorChars
    ) {}

    public record Stream(
        long sseEmitterTimeoutMs,
        Executor executor
    ) {
        public Executor resolvedExecutor() {
            return executor == null ? new Executor(4, 8, 100) : executor;
        }
    }

    public record Executor(
        int corePoolSize,
        int maxPoolSize,
        int queueCapacity
    ) {}

    public record Gemini(
        String baseUrl,
        String apiKey,
        String model,
        long connectTimeoutMs,
        long readTimeoutMs,
        long streamResponseTimeoutMs
    ) {}

    public record Ollama(
        String baseUrl,
        String model,
        long connectTimeoutMs,
        long readTimeoutMs,
        long streamResponseTimeoutMs
    ) {}
}
