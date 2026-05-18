# MMDP Backend MVP

This backend implements the first MVP loop:

`create task -> upload file to MinIO -> persist data_file -> run basic QC -> persist qc_report -> update task status -> query report`

Current scope is limited to the Spring Boot backend API. It does not include auth, Redis, RabbitMQ, async pipelines, or complex workflow orchestration.

## Stack

- Java 21
- Spring Boot 3
- MyBatis-Plus
- MySQL
- MinIO

## Before You Start

1. Create a MySQL database such as `mmdp_db`.
2. Run [schema.sql](D:/project/multimodal-data-platform/mmdp-backend/src/main/resources/schema.sql).
3. Start MinIO and make sure the endpoint is reachable.
4. Provide local configuration through environment variables or local overrides.

## Configuration

The committed `application.yml` is safe to publish. Real credentials must be provided locally.

Supported environment variables:

```bash
MMDP_DB_URL=jdbc:mysql://localhost:3306/mmdp_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
MMDP_DB_USERNAME=root
MMDP_DB_PASSWORD=your-password
MMDP_MINIO_ENDPOINT=http://localhost:29010
MMDP_MINIO_ACCESS_KEY=minioadmin
MMDP_MINIO_SECRET_KEY=your-secret
MMDP_MINIO_BUCKET=mmdp-bucket
```

Spring also supports OS-level environment variables or IDE run configuration variables.

## Run

From the backend directory:

```bash
mvn spring-boot:run
```

Or:

```bash
mvn clean package
java -jar target/mmdp-backend-1.0.0-SNAPSHOT.jar
```

## Database Setup

Run:

```sql
SOURCE src/main/resources/schema.sql;
```

Or copy the contents of `schema.sql` into your MySQL client and execute them there.

## API Endpoints

- `POST /api/tasks`
- `GET /api/tasks`
- `GET /api/tasks/{taskId}`
- `POST /api/tasks/{taskId}/files`
- `GET /api/files/{fileId}`
- `GET /api/tasks/{taskId}/qc-report`

## cURL Examples

### 1. Create a task

```bash
curl -X POST "http://localhost:19021/api/tasks" \
  -H "Content-Type: application/json" \
  -d "{\"taskName\":\"Demo Task 001\",\"subjectCode\":\"S001\",\"actionName\":\"Wave\",\"deviceType\":\"IMU_CLOTH\",\"modality\":\"IMU\",\"collectDate\":\"2026-05-14\",\"remark\":\"MVP smoke test\"}"
```

### 2. Query task list

```bash
curl "http://localhost:19021/api/tasks?current=1&size=10"
```

### 3. Upload a sample file

Use your own local sample file path:

```bash
curl -X POST "http://localhost:19021/api/tasks/1/files" \
  -F "file=@C:/path/to/your/sample.txt"
```

### 4. Query file details

```bash
curl "http://localhost:19021/api/files/1"
```

### 5. Query QC report

```bash
curl "http://localhost:19021/api/tasks/1/qc-report"
```

## Current QC Rules

### TXT time-series files without a standard header

- file is not empty
- file size is within a reasonable range
- file can be parsed line by line
- first column looks like a timestamp
- sampled row and column counts are stable
- numeric columns can be parsed

### CSV

- file is not empty
- header and sample rows can be read
- `timestamp` header exists
- at least one `acc` or `gyro` related column exists

## Notes

- The service stores `report_json` as structured JSON text for direct frontend rendering.
- Upload and QC currently run synchronously to keep the MVP loop simple.
