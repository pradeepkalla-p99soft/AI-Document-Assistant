# AI Document Assistant - Backend

## Overview & Current Status

This repository is the backend service for the **AI Document Assistant**. When complete, the service will allow users to upload PDF documents, index their content using embeddings and `pgvector`, and ask questions through Retrieval-Augmented Generation (RAG).

**Current Milestone:** Milestone 1 — Project Bootstrap & Spring AI Integration.
At this stage, the project foundation, clean architectural layers, Spring AI Ollama chat integration, and temporary development endpoint (`POST /api/dev/chat`) are implemented. PDF ingestion, vector databases, and RAG are intentionally deferred to subsequent milestones.

---

## Architecture & Package Structure

The project follows a clean layered structure and adheres to SOLID principles:

```
src/main/java/com/example/docassistant/
├── DocAssistantApplication.java         # Main Spring Boot entry point
├── config/
│   └── ApplicationConfig.java          # Spring application configurations & bean declarations
├── controller/
│   └── DevChatController.java          # Temporary HTTP controller exposing /api/dev/chat
├── dto/
│   ├── ChatRequest.java                # Validated request record
│   └── ChatResponse.java               # Response record
├── exception/
│   ├── AiProviderException.java        # Exception for AI provider / communication failures
│   └── GlobalExceptionHandler.java     # RFC 7807 ProblemDetail exception handler
└── service/
    ├── AiChatService.java              # Service boundary interface for AI chat
    └── AiChatServiceImpl.java          # Implementation backed by Spring AI's ChatClient
```

---

## Technology Stack & Versions

- **Java:** 21 (LTS)
- **Spring Boot:** 4.1.2 (using `spring-boot-starter-webmvc`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`)
- **Spring AI:** 2.0.2 (managed via `spring-ai-bom` 2.0.2, using `spring-ai-starter-model-ollama`)
- **Testing:** JUnit 5 (JUnit Jupiter), Mockito, MockMvc via `spring-boot-starter-webmvc-test`
- **Build Tool:** Maven Wrapper (`mvnw` / `mvnw.cmd`)

---

## Prerequisites & Setup Instructions

- **JDK 21** installed and configured in your environment (`JAVA_HOME` pointing to JDK 21).
- Internet connection for Maven dependency downloads (or pre-populated local Maven cache).
- (Optional for running the application live) **Ollama** installed and running.

---

## Ollama Installation & Model Configuration

1. **Install Ollama:**
   - **macOS / Linux:** Follow instructions at [ollama.com](https://ollama.com) or run `curl -fsSL https://ollama.com/install.sh | sh`.
   - **Windows:** Download and run the Windows installer from [ollama.com/download/windows](https://ollama.com/download/windows).

2. **Pull the configured model:**
   By default, the application is configured to use `llama3.2`. Pull the model using:
   ```bash
   ollama pull llama3.2
   ```

3. **Start the Ollama server:**
   Ensure Ollama is running and listening on `http://localhost:11434`:
   ```bash
   ollama serve
   ```

---

## Environment Variables & Configuration Properties

All settings are externalized and backed by environment variables with fallback defaults in `src/main/resources/application.yml`:

| Property | Environment Variable | Default Value | Description |
|---|---|---|---|
| `spring.ai.ollama.base-url` | `OLLAMA_BASE_URL` | `http://localhost:11434` | Base URL for Ollama REST API |
| `spring.ai.ollama.chat.options.model` | `OLLAMA_CHAT_MODEL` | `llama3.2` | Chat model name |
| `spring.ai.ollama.init.pull-model-strategy` | — | `never` | Disables automated model pulls on startup |
| `management.endpoints.web.exposure.include` | — | `health` | Exposes only the actuator health endpoint |
| `management.endpoint.health.show-details` | — | `never` | Conceals internal environment details |
| `server.port` | `PORT` | `8080` | HTTP port |

> **Note:** Startup does not require a running Ollama server. Connection occurs lazily upon invoking chat requests.

---

## Commands to Build & Run

### Running Automated Tests
Run unit and web-slice tests without requiring a live Ollama instance:
- **Windows:**
  ```powershell
  .\mvnw.cmd test
  ```
- **Linux / macOS:**
  ```bash
  ./mvnw test
  ```

### Running the Application
- **Windows:**
  ```powershell
  .\mvnw.cmd spring-boot:run
  ```
- **Linux / macOS:**
  ```bash
  ./mvnw spring-boot:run
  ```

---

## Endpoints

### 1. Temporary Development Chat Endpoint

- **Method:** `POST`
- **Path:** `/api/dev/chat`
- **Content-Type:** `application/json`

#### Validation Rules:
- `message`: Required, must not be blank, maximum 2,000 characters.

#### Sample Request:
```bash
curl -X POST http://localhost:8080/api/dev/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Explain dependency injection in Spring"}'
```

#### Sample 200 Response:
```json
{
  "response": "Dependency injection (DI) is a design pattern in Spring where the IoC container injects dependent objects into a bean rather than having the bean construct them itself..."
}
```

#### Sample 400 Validation Error Response:
```json
{
  "type": "urn:problem-type:validation-error",
  "title": "Invalid Request Content",
  "status": 400,
  "detail": "Validation failed for one or more fields",
  "errors": {
    "message": "Message must not be blank"
  }
}
```

#### Sample 502 AI Provider Error Response (e.g., Ollama offline):
```json
{
  "type": "urn:problem-type:ai-provider-error",
  "title": "AI Provider Error",
  "status": 502,
  "detail": "AI service is currently unavailable or encountered an error"
}
```

### 2. Actuator Health Endpoint

- **Method:** `GET`
- **Path:** `/actuator/health`

#### Sample Response:
```json
{
  "status": "UP"
}
```

---

## Current Limitations & Next Planned Milestone

### Current Limitations
- Document storage and PDF ingestion are not yet implemented.
- Embeddings and vector stores (pgvector) are not configured.
- Retrieval-Augmented Generation (RAG) is not active; responses rely solely on direct model prompting.
- The `/api/dev/chat` endpoint is temporary and will be replaced in later milestones.

### Next Planned Milestone (Milestone 2)
- Configure PostgreSQL with the `pgvector` extension.
- Integrate Spring AI vector store support.
- Implement PDF ingestion, text splitting, and token-aware chunking pipeline.
