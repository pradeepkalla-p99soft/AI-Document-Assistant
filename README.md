# AI Document Assistant - Backend

## Overview & Current Status

This repository is the backend service for the **AI Document Assistant**. The service enables uploading PDF documents, extracting their contents, indexing them into PostgreSQL using embeddings and `pgvector`, and answering questions via Retrieval-Augmented Generation (RAG).

**Current Milestone:** Milestone 2 — Document Upload, Validation, and Persistence.
- Milestone 1: Spring Boot foundation, Spring AI BOM 2.0.2 with Ollama chat integration, and `/api/dev/chat`.
- Milestone 2 (Current): Safe file storage adapter, PDF magic byte validation, PostgreSQL with `pgvector` container setup, Flyway database migrations, and document management REST APIs (`POST /api/documents`, `GET /api/documents`, `GET /api/documents/{id}`).
- Subsequent Milestones: PDF text extraction, chunking, embeddings, vector search, and RAG.

---

## Architecture & Package Structure

The system uses a clean layered design enforcing single responsibility, interface boundaries, and defensive storage handling:

```
src/main/java/com/example/docassistant/
├── DocAssistantApplication.java         # Main Spring Boot application entry point
├── config/
│   └── ApplicationConfig.java          # Configuration and infrastructure beans
├── controller/
│   ├── DevChatController.java          # Temporary dev chat endpoint (/api/dev/chat)
│   └── DocumentController.java         # Document upload and retrieval (/api/documents)
├── domain/
│   ├── DocumentRecord.java             # JPA Entity mapping to documents table
│   └── DocumentStatus.java             # Enum: UPLOADED, PROCESSING, READY, FAILED, DELETING
├── dto/
│   ├── ChatRequest.java                # Chat prompt payload
│   ├── ChatResponse.java               # Chat response payload
│   └── DocumentResponse.java           # Safe Document DTO response (never exposes JPA entity)
├── exception/
│   ├── AiProviderException.java        # AI provider failure exception
│   ├── DocumentNotFoundException.java  # Entity not found exception
│   ├── InvalidFileException.java       # PDF validation and format exception
│   ├── StorageException.java           # Filesystem I/O exception
│   └── GlobalExceptionHandler.java     # RFC 7807 ProblemDetail exception handler
├── repository/
│   └── DocumentRepository.java         # Spring Data JPA repository with pagination & sorting
├── service/
│   ├── AiChatService.java              # AI chat service interface
│   ├── AiChatServiceImpl.java          # Spring AI ChatClient implementation
│   ├── DocumentService.java            # Document management use case interface
│   └── DocumentServiceImpl.java        # Implements validation, safe storage, and compensating rollback
├── storage/
│   ├── DocumentStorage.java            # Storage Strategy/Adapter interface
│   └── LocalFileSystemDocumentStorage.java # Safe filesystem implementation (prevents traversal)
└── validation/
    └── PdfFileValidator.java           # Validates size, extension, and %PDF- magic bytes
```

---

## Technology Stack & Versions

- **Java:** 21 (LTS)
- **Spring Boot:** 4.1.2 (`spring-boot-starter-webmvc`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway`)
- **Database & Migrations:** PostgreSQL JDBC Driver, Flyway (`flyway-database-postgresql`), Docker image `pgvector/pgvector:pg17`
- **Spring AI:** 2.0.2 (via `spring-ai-bom` 2.0.2 and `spring-ai-starter-model-ollama`)
- **Testing:** JUnit 5, Mockito, MockMvc via `spring-boot-starter-webmvc-test`, in-memory H2 database (test scope only)
- **Build Tool:** Maven Wrapper (`mvnw` / `mvnw.cmd`)

---

## Local Development & Docker Setup

### 1. Starting PostgreSQL with pgvector
A `docker-compose.yml` is provided at the repository root using the official `pgvector/pgvector:pg17` image.

Start the database container:
```bash
docker compose up -d
```

Verify the container and healthcheck:
```bash
docker compose ps
```

The database container will run with a persistent volume (`postgres_data`) and expose port `5432`.

### 2. Environment Variables & Configuration
Safe defaults are provided for local development. In production or custom environments, configure the following variables:

| Variable | Default Value | Description |
|---|---|---|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `docassistant` | Database name |
| `DB_USER` | `postgres` | Database username |
| `DB_PASSWORD` | `postgres` | Database password |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama API URL |
| `OLLAMA_CHAT_MODEL`| `llama3.1` | Chat model name |
| `STORAGE_LOCAL_ROOT` | `uploads/documents` | Directory where PDF files are stored |
| `MAX_FILE_SIZE` | `10MB` | Maximum single PDF file size limit |
| `MAX_REQUEST_SIZE` | `12MB` | Maximum total multipart request size limit |

A template file [.env.example](file:///.env.example) is included.

### 3. Database Migrations
Migrations are managed automatically on startup via Flyway:
- Migration location: `src/main/resources/db/migration`
- `V1__init_document_schema.sql`: Creates the `documents` table with primary key `UUID`, indexes on `status` and `created_at`, unique `storage_key`, and constraints.
- In production, `spring.jpa.hibernate.ddl-auto` is set to `validate`, ensuring Hibernate does not automatically modify the schema.

---

## Document Validation & Storage Rules

- **Magic Byte Verification:** Inspects the first 5 bytes (`%PDF-` / `0x25, 0x50, 0x44, 0x46, 0x2D`) to ensure authentic PDF structure, not relying solely on file extensions or client-reported MIME types.
- **Size Limits:** Enforced both at the servlet layer (`spring.servlet.multipart.max-file-size`) and in `PdfFileValidator` (default 10 MB).
- **Safe Storage Keys:** Filenames are never used directly as filesystem paths. Storage keys are generated as random UUIDs (`<uuid>.pdf`).
- **Path Traversal Protection:** `LocalFileSystemDocumentStorage` normalizes and validates paths to ensure no writes or reads escape the configured root directory.
- **Compensating Rollback:** If a file is written to disk but database persistence fails, the physical file is deleted to avoid orphan storage leaks.

---

## API Endpoints

### 1. Upload a Document
- **Method:** `POST`
- **URL:** `http://localhost:8080/api/documents`
- **Content-Type:** `multipart/form-data`
- **Field:** `file` (binary PDF)

```bash
curl -X POST http://localhost:8080/api/documents \
  -F "file=@/path/to/my-document.pdf"
```

#### Sample 201 Response:
```json
{
  "id": "e931ca8f-b9be-48dc-b620-3b4cb5876378",
  "filename": "my-document.pdf",
  "storageKey": "3a0ef641-fcb2-4d70-a352-0692795ec25c.pdf",
  "contentType": "application/pdf",
  "sizeBytes": 1048576,
  "status": "UPLOADED",
  "errorMessage": null,
  "createdAt": "2026-10-26T16:35:00+05:30",
  "updatedAt": "2026-10-26T16:35:00+05:30"
}
```

### 2. Get Document by ID
- **Method:** `GET`
- **URL:** `http://localhost:8080/api/documents/{id}`

```bash
curl -X GET http://localhost:8080/api/documents/e931ca8f-b9be-48dc-b620-3b4cb5876378
```

#### Sample 200 Response:
```json
{
  "id": "e931ca8f-b9be-48dc-b620-3b4cb5876378",
  "filename": "my-document.pdf",
  "storageKey": "3a0ef641-fcb2-4d70-a352-0692795ec25c.pdf",
  "contentType": "application/pdf",
  "sizeBytes": 1048576,
  "status": "UPLOADED",
  "errorMessage": null,
  "createdAt": "2026-10-26T16:35:00+05:30",
  "updatedAt": "2026-10-26T16:35:00+05:30"
}
```

### 3. List Documents (Paginated)
- **Method:** `GET`
- **URL:** `http://localhost:8080/api/documents?page=0&size=20&sort=createdAt,desc`

```bash
curl -X GET "http://localhost:8080/api/documents?page=0&size=20"
```

#### Sample 200 Response:
```json
{
  "content": [
    {
      "id": "e931ca8f-b9be-48dc-b620-3b4cb5876378",
      "filename": "my-document.pdf",
      "storageKey": "3a0ef641-fcb2-4d70-a352-0692795ec25c.pdf",
      "contentType": "application/pdf",
      "sizeBytes": 1048576,
      "status": "UPLOADED",
      "errorMessage": null,
      "createdAt": "2026-10-26T16:35:00+05:30",
      "updatedAt": "2026-10-26T16:35:00+05:30"
    }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### 4. Error Responses (RFC 7807 Problem Detail)

- **Invalid File Type / Fake PDF (400 Bad Request):**
```json
{
  "type": "urn:problem-type:invalid-file",
  "title": "Invalid File Upload",
  "status": 400,
  "detail": "File content is not a valid PDF document (invalid file signature)"
}
```

- **File Exceeds Max Upload Limit (413 Payload Too Large):**
```json
{
  "type": "urn:problem-type:payload-too-large",
  "title": "Payload Too Large",
  "status": 413,
  "detail": "Uploaded file exceeds the maximum permitted upload size"
}
```

- **Document Not Found (404 Not Found):**
```json
{
  "type": "urn:problem-type:document-not-found",
  "title": "Document Not Found",
  "status": 404,
  "detail": "Document not found with ID: ..."
}
```

---

## Commands to Build & Test

### Run All Automated Tests
Unit tests and Web MVC slice tests do not require Ollama or a live PostgreSQL instance (they run with in-memory mocks and H2):
```powershell
.\mvnw.cmd test
```

### Start the Application
Make sure Docker Compose is running for the database:
```bash
docker compose up -d
```
Then start the Spring Boot app:
```powershell
.\mvnw.cmd spring-boot:run
```

---

## Troubleshooting

1. **`Connection refused` on PostgreSQL:**
   Ensure the Docker container is up: `docker compose ps`. If running PostgreSQL locally outside Docker, update `DB_PORT`, `DB_USER`, and `DB_PASSWORD` in your environment.
2. **`listen tcp 127.0.0.1:11434: bind: Only one usage...`:**
   Ollama is already running as a system tray or service in the background on your machine. You do not need to run `ollama serve`.
3. **`Ambiguous @ExceptionHandler` on upload size:**
   Resolved by extending `ResponseEntityExceptionHandler.handleMaxUploadSizeExceededException`.

---

## Current Status & Next Planned Milestone

### Completed in Milestones 1 & 2:
- Spring AI ChatClient integration with Ollama.
- PostgreSQL schema migrations with Flyway and pgvector image configuration.
- Safe local file storage with UUID storage keys and path traversal defense.
- PDF signature validation (header magic bytes `%PDF-`).
- Document metadata entity and Spring Data repository.
- Document upload, retrieval, and pagination REST APIs with error handling.

### Next Planned Milestone (Milestone 3):
- Apache Tika / PDF parsing and text extraction.
- Token-aware document chunking.
- Processing workflow transition from `UPLOADED` to `PROCESSING` and `READY`.
