# Spring AI PDF Viewer

A Spring Boot (Kotlin) service that converts PDF files into clean Markdown. It extracts text from a PDF with Spring AI's `PagePdfDocumentReader` and then asks a chat model (local via Ollama, or a hosted provider like OpenAI) to re-express that content as well-structured Markdown, preserving the original data.

## Features

- `POST /api/pdf/convert` — upload a PDF, get back a `.md` file
- Support for password-protected PDFs
- Pluggable chat model backend via Spring AI (Ollama by default, OpenAI supported)
- Reactive stack (WebFlux + Kotlin coroutines)

## Prerequisites

- Java 17+
- Maven (or use the included `./mvnw` wrapper)
- A running model backend — either:
  - [Ollama](https://ollama.com) running locally with a model pulled, or
  - An OpenAI API key

## Getting Started

1. Clone/open the project.
2. Configure your model backend (see [Configuration](#configuration) below).
3. Run the app:

   ```bash
   ./mvnw spring-boot:run
   ```

   The server starts on `http://localhost:8081` (configurable via `server.port` in `application.yaml`).

4. Convert a PDF:

   ```bash
   curl -F "file=@document.pdf" http://localhost:8081/api/pdf/convert -o document.md
   ```

   For a password-protected PDF, pass the password as an additional form field:

   ```bash
   curl -F "file=@document.pdf" -F "password=secret" http://localhost:8081/api/pdf/convert -o document.md
   ```

   If the PDF is encrypted and no password (or the wrong one) is supplied, the API responds with `401 Unauthorized` and an explanatory message.

## Configuration

All configuration lives in `src/main/resources/application.yaml`. You can override any of it with environment variables or `-D` system properties at runtime, or with a `SPRING_AI_...` env var equivalent.

### Option A: Ollama (default, local models)

This project ships configured for Ollama out of the box via the `spring-ai-starter-model-ollama` dependency already in `pom.xml`.

1. Install and start [Ollama](https://ollama.com/download).
2. Pull a model, e.g.:

   ```bash
   ollama pull qwen3-coder:latest
   ```

3. Point the app at your Ollama instance in `application.yaml`:

   ```yaml
   spring:
     ai:
       ollama:
         base-url: http://localhost:11434   # default Ollama port
         chat:
           options:
             model: qwen3-coder:latest
           timeout: 300s
   ```

   Note: the repo's default `base-url` is `http://localhost:1234` (useful if you're running Ollama behind a proxy, e.g. LM Studio's OpenAI-compatible port). Change it to `http://localhost:11434` if you're running plain Ollama on its default port.

### Option B: OpenAI

To use OpenAI instead of (or in addition to) Ollama:

1. Add the OpenAI starter to `pom.xml`:

   ```xml
   <dependency>
       <groupId>org.springframework.ai</groupId>
       <artifactId>spring-ai-starter-model-openai</artifactId>
   </dependency>
   ```

   If you no longer need Ollama, remove the `spring-ai-starter-model-ollama` dependency (Spring AI autoconfigures a `ChatClient.Builder` based on whichever starter(s) are on the classpath; having more than one active model type may require explicitly qualifying the bean).

2. Set your API key as an environment variable (don't commit it):

   ```bash
   export OPENAI_API_KEY=sk-...
   ```

3. Configure the model in `application.yaml`:

   ```yaml
   spring:
     ai:
       openai:
         api-key: ${OPENAI_API_KEY}
         chat:
           options:
             model: gpt-4o-mini
   ```

4. Rebuild and run as usual.

### Other settings

```yaml
server:
  port: 8081                # HTTP port

spring:
  webflux:
    multipart:
      max-in-memory-size: 10MB
      max-disk-usage-per-part: 50MB   # raise this for larger PDFs
```

## Project Structure

```
src/main/kotlin/org/radhe/spring_ai_pdf_viewer/
├── SpringAiPdfViewerApplication.kt   # Spring Boot entry point
├── controller/PdfController.kt       # REST endpoint: /api/pdf/convert
└── service/PdfMarkdownService.kt     # PDF text extraction + Markdown conversion via ChatClient
```

## Tech Stack

- [Spring Boot](https://spring.io/projects/spring-boot) 4.1 (WebFlux)
- [Spring AI](https://spring.io/projects/spring-ai) 2.0 (PDF document reader, Ollama/OpenAI chat model)
- Kotlin + Kotlin Coroutines
- Apache PDFBox (via Spring AI's PDF reader, for password/decryption handling)

## Running Tests

```bash
./mvnw test
```

## License

This project is licensed under the MIT License — see [LICENSE](LICENSE) for details.
