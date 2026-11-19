# AI Document Assistant — Backend

## Overview

This is the backend service for the **AI Document Assistant** — a Spring Boot application that lets authenticated users upload complex PDF documents, extract structured text and visual assets, index them into PostgreSQL using `pgvector`, and ask natural-language questions against their own documents via multimodal Retrieval-Augmented Generation (RAG).

**Key capabilities:**
- **JWT-authenticated multi-tenancy** — every user sees and queries only their own PDFs
- **Layout-aware PDF ingestion** — multi-column text, embedded figures, charts, tables, and scanned pages
- **Multimodal RAG** — semantic vector retrieval with grounded LLM answers and citation markers
- **PostgreSQL vector storage** — persistent HNSW cosine-distance indexing via `pgvector`

---

## Milestone History

| Milestone | Description |
|---|---|
| **M1** | Spring Boot foundation, Spring AI BOM, Ollama integration |
| **M2** | File storage adapter, PDF magic-byte validation, PostgreSQL persistence, Flyway migrations, document CRUD APIs |
| **M3** | PDF text extraction, token-aware chunking, embedding generation, vector store persistence |
| **M4** | RAG question-answering pipeline with `ChatClient`, metadata-scoped retrieval, grounded prompts, citation markers, insufficient-evidence handling, RFC 7807 problem details |
| **M5** | Layout-aware extraction (PDFBox), visual asset pipeline, multimodal understanding, OCR fallback, PgVector HNSW storage |
| **M6** | JWT authentication, per-user document isolation, `users` table, `owner_id` FK on `documents`, `BCrypt` password hashing |

---

## Architecture & Request Flows

### PDF Upload Pipeline

```
[POST /api/documents]  ←  Bearer JWT required
        │
        ▼
1. JWT Authentication  (JwtAuthenticationFilter → UserPrincipal → owner UUID)
        │
        ▼
2. File Validation  (PdfFileValidator: size ≤ 10 MB, .pdf extension, %PDF- magic bytes)
        │
        ▼
3. Storage & DB Persist  (DocumentServiceImpl → LocalFileSystemDocumentStorage → documents table)
        │   owner_id = authenticated user's UUID
        ▼
4. Layout-Aware Extraction  (LayoutAwarePdfContentExtractor + PdfBoxCoordinateExtractor)
        ├── Text block sorting  (multi-column reading-order detection)
        ├── Visual asset extraction  (embedded figures, charts → uploads/assets + visual_assets table)
        ├── Multimodal analysis  (MultimodalImageUnderstandingService → structured visual descriptions)
        └── OCR fallback  (TesseractCliOcrService for low-density text pages)
        │
        ▼
5. Token-Bounded Chunking  (TokenDocumentChunker)
        │   Metadata: documentId, filename, pageNumber, chunkIndex, sourceType
        ▼
6. Vector Indexing  (PgVectorStore)
        │   768-dim embeddings via Ollama nomic-embed-text → vector_store table
        ▼
7. Document status → READY
```

### RAG Question-Answering Pipeline

```
[POST /api/documents/{documentId}/ask]  ←  Bearer JWT required
        │
        ▼
1. JWT Authentication  (verify token, extract owner UUID)
        │
        ▼
2. Ownership Check  (documentService.getDocument(id, ownerId) → 404 if not owned)
        │
        ▼
3. Document State Verification  (status must be READY)
        │
        ▼
4. Semantic Vector Retrieval  (DocumentRetrievalService)
        │   Filter: documentId == '{uuid}'   Top-K + similarity threshold
        │
        ├── [No chunks found] → Immediate fallback, model NOT invoked
        │                       "The document does not contain sufficient information..."
        │
        └── [Chunks found]
                │
                ▼
5. Context Formatting & Citation Mapping  ([S1], [S2] markers, page numbers, visual refs)
        │
        ▼
6. LLM Prompt Execution  (AnswerGenerationService / ChatClient)
        │   Grounded system prompt: no speculation, no prompt-injection propagation
        ▼
7. Citation Validation & Sanitization  (drops fabricated [S#] markers)
        │
        ▼
[AskQuestionResponse]  { answer, sources[], contextSupplied: true }
```

---

## Package Structure

```
src/main/java/com/example/docassistant/
├── DocAssistantApplication.java
├── config/
│   └── ApplicationConfig.java               # Spring beans (PageableHandlerMethodArgumentResolver, etc.)
├── controller/
│   ├── AuthController.java                  # POST /api/auth/register|login, GET /api/auth/me
│   └── DocumentController.java             # POST|GET /api/documents, POST /{id}/ask
├── domain/
│   ├── DocumentRecord.java                  # JPA entity → documents table (owner_id FK)
│   ├── DocumentStatus.java                  # UPLOADED, PROCESSING, READY, FAILED, DELETING
│   ├── UserRecord.java                      # JPA entity → users table
│   ├── VisualAssetProcessingStatus.java     # PENDING, PROCESSED, SKIPPED, FAILED
│   ├── VisualAssetRecord.java               # JPA entity → visual_assets table
│   └── VisualAssetType.java                 # EMBEDDED_FIGURE, RENDERED_PAGE, RENDERED_REGION
├── dto/
│   ├── AskQuestionRequest.java              # { question } — @NotBlank, @Size(max=2000)
│   ├── AskQuestionResponse.java             # { documentId, answer, sources[], contextSupplied }
│   ├── AuthResponse.java                    # { token, tokenType, userId, email, fullName }
│   ├── DocumentResponse.java                # Safe document DTO (never exposes JPA entity)
│   ├── LoginRequest.java                    # { email, password }
│   ├── RegisterRequest.java                 # { email, password, fullName } — validated
│   ├── SourceReference.java                 # { documentId, filename, pageNumber, excerpt, sourceType, visualAssetId }
│   └── UserProfileResponse.java             # { id, email, fullName, createdAt }
├── exception/
│   ├── AiProviderException.java             # → 502 Bad Gateway
│   ├── DocumentNotFoundException.java       # → 404 Not Found
│   ├── DocumentNotReadyException.java       # → 409 Conflict
│   ├── GlobalExceptionHandler.java          # RFC 7807 ProblemDetail for all exceptions
│   ├── InvalidFileException.java            # → 400 Bad Request
│   ├── PdfExtractionException.java          # → 422 Unprocessable Entity
│   └── StorageException.java                # → 500 Internal Server Error
├── repository/
│   ├── DocumentRepository.java              # findByOwnerId, findByIdAndOwnerId
│   ├── UserRepository.java                  # findByEmailIgnoreCase, existsByEmailIgnoreCase
│   └── VisualAssetRepository.java
├── security/
│   ├── CustomUserDetailsService.java        # Loads UserDetails by email or UUID
│   ├── JwtAuthenticationFilter.java         # OncePerRequestFilter — extracts Bearer token
│   ├── JwtService.java                      # HMAC-SHA JWT generation & validation (jjwt 0.12.6)
│   ├── SecurityConfig.java                  # SecurityFilterChain, BCrypt, stateless sessions
│   └── UserPrincipal.java                   # Spring Security UserDetails wrapping UserRecord
├── service/
│   ├── DocumentService.java                 # Upload, getDocument, listDocuments (owner-scoped overloads)
│   ├── DocumentServiceImpl.java             # Compensating rollback on DB failure
│   ├── auth/
│   │   ├── AuthService.java                 # register, login, getCurrentUserProfile
│   │   └── AuthServiceImpl.java             # BCrypt verify, JWT generation
│   ├── chunking/
│   │   ├── DocumentChunker.java
│   │   └── TokenDocumentChunker.java        # TokenTextSplitter + UUID chunk IDs + metadata
│   ├── extraction/
│   │   ├── DocumentContentExtractor.java
│   │   ├── LayoutAwarePdfContentExtractor.java  # Orchestrates text, visuals, OCR
│   │   ├── PagePdfDocumentExtractor.java        # Fallback page-by-page reader
│   │   ├── layout/
│   │   │   ├── BoundingBox.java
│   │   │   ├── ExtractedImageRegion.java
│   │   │   ├── ExtractedTextBlock.java
│   │   │   ├── LayoutAwareDocumentExtractor.java
│   │   │   ├── PdfBoxCoordinateExtractor.java   # Multi-column reading-order engine
│   │   │   ├── StructuredDocumentResult.java
│   │   │   └── StructuredPageContent.java
│   │   └── ocr/
│   │       ├── OcrService.java
│   │       └── TesseractCliOcrService.java
│   ├── indexing/
│   │   ├── DocumentProcessingService.java
│   │   └── DocumentProcessingServiceImpl.java   # extract → chunk → embed → pgvector
│   ├── rag/
│   │   ├── AnswerGenerationService.java
│   │   ├── AnswerGenerationServiceImpl.java      # ChatClient with grounded prompt
│   │   ├── DocumentRetrievalService.java
│   │   ├── DocumentRetrievalServiceImpl.java     # Scoped similarity search + filter
│   │   ├── QuestionAnsweringService.java
│   │   └── QuestionAnsweringServiceImpl.java     # RAG orchestration
│   └── vision/
│       ├── ImageUnderstandingService.java
│       ├── MultimodalImageUnderstandingService.java
│       └── VisualAnalysisDescription.java
├── storage/
│   ├── DocumentStorage.java
│   ├── LocalFileSystemDocumentStorage.java
│   ├── VisualAssetStorage.java
│   └── LocalFileSystemVisualAssetStorage.java
└── validation/
    └── PdfFileValidator.java                # Size, .pdf extension, %PDF- magic bytes
```

---

## Database Schema

Flyway manages all migrations under `src/main/resources/db/migration/`:

| Migration | Description |
|---|---|
| `V1__init_document_schema.sql` | `documents` table (id, filename, storage_key, content_type, size_bytes, status, error_message, timestamps) |
| `V2__create_visual_assets.sql` | `visual_assets` table (id, document_id FK, asset_type, page_number, storage_key, mime_type, bounding_box, caption, description, processing_status, timestamps) |
| `V3__create_users_and_ownership.sql` | `users` table + `owner_id UUID` column on `documents` + FK `fk_documents_owner → users(id) ON DELETE CASCADE` + indexes |

The `vector_store` table is auto-managed by Spring AI's `PgVectorStore` (HNSW, cosine distance, 768 dimensions).

---

## API Reference

All document endpoints require a valid `Authorization: Bearer <token>` header. Auth endpoints are public.

### Authentication

#### Register
- **Method:** `POST`
- **URL:** `http://<HOST>:<PORT>/api/auth/register`
- **Content-Type:** `application/json`

##### Request Payload:
```json
{
  "email": "<EMAIL>",
  "password": "<PASSWORD_MIN_6_CHARS>",
  "fullName": "<FULL_NAME>"
}
```

##### Example Request:
```bash
curl -X POST http://<HOST>:<PORT>/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "<EMAIL>",
    "password": "<PASSWORD_MIN_6_CHARS>",
    "fullName": "<FULL_NAME>"
  }'
```

##### Successful Response (`201 Created`):
```json
{
  "token": "<JWT_TOKEN>",
  "tokenType": "Bearer",
  "userId": "<USER_UUID>",
  "email": "<EMAIL>",
  "fullName": "<FULL_NAME>"
}
```
**Error `409 Conflict`** — email already registered.

---

#### Login
- **Method:** `POST`
- **URL:** `http://<HOST>:<PORT>/api/auth/login`
- **Content-Type:** `application/json`

##### Request Payload:
```json
{
  "email": "<EMAIL>",
  "password": "<PASSWORD>"
}
```

##### Example Request:
```bash
curl -X POST http://<HOST>:<PORT>/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "<EMAIL>",
    "password": "<PASSWORD>"
  }'
```

##### Successful Response (`200 OK`):
```json
{
  "token": "<JWT_TOKEN>",
  "tokenType": "Bearer",
  "userId": "<USER_UUID>",
  "email": "<EMAIL>",
  "fullName": "<FULL_NAME>"
}
```
**Error `401 Unauthorized`** — invalid credentials.

---

#### Get Current User Profile
- **Method:** `GET`
- **URL:** `http://<HOST>:<PORT>/api/auth/me`
- **Authorization:** `Bearer <TOKEN>`

##### Example Request:
```bash
curl -X GET http://<HOST>:<PORT>/api/auth/me \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

##### Successful Response (`200 OK`):
```json
{
  "id": "<USER_UUID>",
  "email": "<EMAIL>",
  "fullName": "<FULL_NAME>",
  "createdAt": "<TIMESTAMP>"
}
```

---

### Documents

All document endpoints are **scoped to the authenticated user** — users can only access their own documents.

#### Upload Document
- **Method:** `POST`
- **URL:** `http://<HOST>:<PORT>/api/documents`
- **Authorization:** `Bearer <TOKEN>`
- **Content-Type:** `multipart/form-data`

| Parameter | Type | Description |
|---|---|---|
| `file` | File | PDF document (`.pdf`, max 10 MB) |

##### Example Request:
```bash
curl -X POST http://<HOST>:<PORT>/api/documents \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -F "file=@<PATH_TO_PDF_FILE>"
```

##### Successful Response (`201 Created`):
```json
{
  "id": "<DOCUMENT_UUID>",
  "filename": "<SANITIZED_FILENAME>",
  "storageKey": "<STORAGE_KEY>",
  "contentType": "application/pdf",
  "sizeBytes": 1048576,
  "status": "READY",
  "errorMessage": null,
  "createdAt": "<TIMESTAMP>",
  "updatedAt": "<TIMESTAMP>"
}
```

---

#### List Documents (Paginated)
- **Method:** `GET`
- **URL:** `http://<HOST>:<PORT>/api/documents?page=0&size=20`
- **Authorization:** `Bearer <TOKEN>`

Returns only the authenticated user's documents, sorted by `createdAt` descending.

##### Example Request:
```bash
curl -X GET "http://<HOST>:<PORT>/api/documents?page=0&size=20" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

---

#### Get Document by ID
- **Method:** `GET`
- **URL:** `http://<HOST>:<PORT>/api/documents/{id}`
- **Authorization:** `Bearer <TOKEN>`

##### Example Request:
```bash
curl -X GET http://<HOST>:<PORT>/api/documents/<DOCUMENT_UUID> \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

##### Response (`200 OK`): Same shape as upload response.  
**Error `404 Not Found`** — document not found or belongs to another user.

---

#### Ask a Question (RAG)
- **Method:** `POST`
- **URL:** `http://<HOST>:<PORT>/api/documents/{documentId}/ask`
- **Authorization:** `Bearer <TOKEN>`
- **Content-Type:** `application/json`

##### Request Payload:
```json
{
  "question": "<YOUR_QUESTION>"
}
```
Constraints: non-blank, max 2000 characters. Document must be in `READY` status and owned by the authenticated user.

##### Example Request:
```bash
curl -X POST http://<HOST>:<PORT>/api/documents/<DOCUMENT_UUID>/ask \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"question": "<YOUR_QUESTION>"}'
```

##### Response (`200 OK` — with context):
```json
{
  "documentId": "<DOCUMENT_UUID>",
  "answer": "<GENERATED_ANSWER_WITH_CITATIONS>",
  "sources": [
    {
      "documentId": "<DOCUMENT_UUID>",
      "filename": "<FILENAME>",
      "pageNumber": 1,
      "excerpt": "<RELEVANT_EXCERPT>",
      "sourceType": "TEXT",
      "visualAssetId": null,
      "caption": null
    }
  ],
  "contextSupplied": true
}
```

##### Response (`200 OK` — insufficient context, model NOT invoked):
```json
{
  "documentId": "<DOCUMENT_UUID>",
  "answer": "The document does not contain sufficient information to answer this question.",
  "sources": [],
  "contextSupplied": false
}
```

---

### Health Check (public)
```
GET /actuator/health
```
**Response `200 OK`:** `{ "status": "UP" }`

---

## Error Responses

All errors follow [RFC 7807 Problem Details](https://www.rfc-editor.org/rfc/rfc7807):

```json
{
  "type": "urn:problem-type:<type>",
  "title": "<Human-readable title>",
  "status": <HTTP_STATUS_CODE>,
  "detail": "<Specific error message>"
}
```

| Scenario | HTTP Status | `type` |
|---|---|---|
| Validation failure (missing/invalid fields) | `400 Bad Request` | `urn:problem-type:validation-error` |
| Malformed JSON body | `400 Bad Request` | `urn:problem-type:malformed-request` |
| Invalid PDF (wrong type, bad magic bytes) | `400 Bad Request` | `urn:problem-type:invalid-file` |
| Invalid credentials | `401 Unauthorized` | `urn:problem-type:unauthorized` |
| Document not found / not owned | `404 Not Found` | `urn:problem-type:document-not-found` |
| Email already registered | `409 Conflict` | `urn:problem-type:conflict` |
| Document not ready for querying | `409 Conflict` | `urn:problem-type:document-not-ready` |
| PDF extraction failure | `422 Unprocessable Entity` | `urn:problem-type:pdf-extraction-failed` |
| File size exceeds limit | `413 Payload Too Large` | `urn:problem-type:payload-too-large` |
| AI provider unavailable | `502 Bad Gateway` | `urn:problem-type:ai-provider-error` |
| Storage I/O failure | `500 Internal Server Error` | `urn:problem-type:storage-error` |

---

## Configuration & Environment Variables

All settings live in `src/main/resources/application.yml` and can be overridden via environment variables:

| Setting | Environment Variable | Default | Description |
|---|---|---|---|
| Database Host | `DB_HOST` | `localhost` | PostgreSQL host |
| Database Port | `DB_PORT` | `5433` | PostgreSQL port (Docker) |
| Database Name | `DB_NAME` | `doc_assistant_db` | Target database |
| Database User | `DB_USER` | `postgres` | DB username |
| Database Password | `DB_PASSWORD` | *(set at runtime)* | DB password |
| Ollama Base URL | `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama service endpoint |
| Ollama Chat Model | `OLLAMA_CHAT_MODEL` | `llama3.2:3b` | LLM for answer generation |
| Ollama Embedding Model | `OLLAMA_EMBEDDING_MODEL` | `nomic-embed-text` | Embedding model (768 dims) |
| PgVector Dimensions | `PGVECTOR_DIMENSIONS` | `768` | Vector dimension size |
| PgVector Init Schema | `PGVECTOR_INIT_SCHEMA` | `true` | Auto-create `vector_store` table |
| RAG Top-K | `RAG_TOP_K` | `4` | Max candidate chunks retrieved |
| RAG Similarity Threshold | `RAG_SIMILARITY_THRESHOLD` | `0.0` | Min cosine similarity score |
| Vision Enabled | `AI_VISION_ENABLED` | `false` | Enable multimodal visual analysis |
| Vision Model | `AI_VISION_MODEL` | `llava` | Multimodal model name |
| OCR Enabled | `OCR_ENABLED` | `false` | Enable OCR fallback |
| Tesseract Path | `OCR_TESSERACT_PATH` | `tesseract` | System path to Tesseract binary |
| Document Storage Root | `STORAGE_LOCAL_ROOT` | `uploads/documents` | Filesystem path for uploaded PDFs |
| Visual Asset Storage Root | `STORAGE_ASSETS_ROOT` | `uploads/assets` | Filesystem path for visual assets |
| Max Upload Size | `MAX_FILE_SIZE` | `10MB` | Maximum PDF upload size |
| JWT Secret | `JWT_SECRET` | *(set at runtime — min 32 bytes)* | HMAC-SHA signing key |
| JWT Expiry | `JWT_EXPIRATION_HOURS` | `24` | Token validity in hours |

---

## Prerequisites & Infrastructure Setup

### 1. PostgreSQL with `pgvector` via Docker

```bash
docker run -d \
  --name pgvector-db \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=<DB_PASSWORD> \
  -e POSTGRES_DB=doc_assistant_db \
  -p 5433:5432 \
  -v pgdata:/var/lib/postgresql/data \
  pgvector/pgvector:pg17
```

Enable the vector extension:
```bash
docker exec -it pgvector-db psql -U postgres -d doc_assistant_db \
  -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

### 2. Ollama Models

```bash
ollama serve
ollama pull nomic-embed-text
ollama pull llama3.2:3b
# Optional — for multimodal vision:
ollama pull llava
```

### 3. Run the Application

```bash
# Windows
.\mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw spring-boot:run
```

The server starts on port `8080` by default.

---

## Security Design

### JWT Authentication (Stateless)
- Tokens are signed with HMAC-SHA using a configurable secret key (minimum 32 bytes).
- Every request to `/api/documents/**` and `/api/auth/me` requires `Authorization: Bearer <token>`.
- The `JwtAuthenticationFilter` extracts and validates the token on every request; no server-side session is maintained (`SessionCreationPolicy.STATELESS`).
- Passwords are hashed with **BCrypt** before storage. Plain-text passwords are never persisted.

### Per-User Document Isolation (Multi-Tenancy)
- Every document upload records `owner_id` (the authenticated user's UUID) in the `documents` table.
- All document queries (`GET /api/documents`, `GET /api/documents/{id}`, `POST /api/documents/{id}/ask`) use `findByOwnerId` / `findByIdAndOwnerId` to enforce ownership at the database level.
- Attempting to access another user's document returns `404 Not Found` — not `403 Forbidden` — to prevent user enumeration.

### RAG Vector Isolation
- Retrieved chunks are filtered at the vector store query level using `FilterExpressionBuilder: documentId == '{uuid}'`.
- An in-memory double-check guarantees zero chunk bleed across different documents.

### Prompt Injection Defense
- Retrieved PDF text and visual descriptions are treated as **untrusted data**.
- The system prompt instructs the model to: rely only on provided context excerpts, ignore embedded instructions (e.g., "ignore previous instructions and do X"), and refuse to speculate or fabricate citations.

### Public Endpoints
The following endpoints are accessible without authentication:
- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /actuator/health`
- `OPTIONS /**` (CORS preflight)

---

## Automated Tests & Verification

Tests use mocked AI models and in-memory H2 to run deterministically without live services:

```powershell
# Compile only
.\mvnw.cmd compile -o -q

# Test-compile
.\mvnw.cmd test-compile -o -q

# Run all tests
.\mvnw.cmd test

# Run specific tests
.\mvnw.cmd test -Dtest=DocAssistantApplicationTests
.\mvnw.cmd test -Dtest=TokenDocumentChunkerTest
```

The smoke test (`DocAssistantApplicationTests`) mocks `VectorStore`, `EmbeddingModel`, and `UserRepository` to verify the Spring context loads cleanly without any live database or Ollama connection.

---

## Technology Stack

| Component | Technology |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 4.1.2 |
| AI / RAG | Spring AI 2.0.2 |
| Security | Spring Security 7.1.2, jjwt 0.12.6 |
| Database | PostgreSQL 17 (Docker) + pgvector extension |
| ORM | Spring Data JPA / Hibernate |
| Migrations | Flyway |
| PDF Parsing | Apache PDFBox |
| LLM | Ollama (`llama3.2:3b`) |
| Embeddings | Ollama (`nomic-embed-text`, 768 dims) |
| Vector Store | PgVectorStore (HNSW, cosine distance) |
| OCR (optional) | Tesseract CLI |
| Build | Apache Maven (Maven Wrapper) |
