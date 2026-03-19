# File Starter

File Starter provides three core capabilities for developers:
- Data import
- Data export
- Document export (Word/PDF)

This document focuses on developer usage and API-level examples.

## Code Structure

- `excel/export/strategy`: export strategy selection and concrete export implementations
- `excel/export/support`: shared export support components such as data fetch, template resolve, writer, upload, and custom export hooks
- `excel/imports`: import pipeline, handler factory, failure collection, persistence, and custom import hook
- `excel/style`: shared Excel style handlers
- `file/`: document file generators (Word, PDF)

## Dependency
```xml
<dependency>
  <groupId>io.softa</groupId>
  <artifactId>file-starter</artifactId>
  <version>${softa.version}</version>
</dependency>
```

## Requirements
- OSS storage (Minio or other supported providers) for template files and generated files.
- Pulsar is required if you use async import.
- Database contains file metadata tables and file-starter tables:
  ImportTemplate, ImportTemplateField, ImportHistory,
  ExportTemplate, ExportTemplateField, ExportHistory,
  DocumentTemplate.

## Configuration
### MQ topics (async import)
```yml
mq:
  topics:
    async-import:
      topic: dev_demo_async_import
      sub: dev_demo_async_import_sub
```

### OSS Configuration
```yml
oss:
  type: minio
  endpoint: http://minio:9000
  access-key: minioadmin
  secret-key: minioadmin
  bucket: dev-demo
```

### Storage Policy
- General path: `modelName/uuid/fileName`
- Multi-tenancy path: `tenantId/modelName/uuid/fileName`

## A. Data Import
File Starter supports two import modes:
- Import by configured template (ImportTemplate + ImportTemplateField)
- Dynamic mapping import (no template, mapping provided in request)

### ImportTemplate Configuration Table
| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `name` | String | `null` | Template name |
| `modelName` | String | `null` | Model name to import |
| `importRule` | ImportRule | `null` | Import rule: CreateOrUpdate / OnlyCreate / OnlyUpdate |
| `uniqueConstraints` | List<String> | `null` | Unique key fields used by CreateOrUpdate |
| `ignoreEmpty` | Boolean | `null` | Ignore empty values when importing |
| `skipException` | Boolean | `null` | Continue when a row fails |
| `customHandler` | String | `null` | Spring bean name for CustomImportHandler |
| `syncImport` | Boolean | `null` | If true, import runs synchronously; otherwise async |
| `includeDescription` | Boolean | `null` | Whether to include description in template output |
| `description` | String | `null` | Description text |
| `importFields` | List<ImportTemplateField> | `null` | Import field list |

### ImportTemplateField Configuration Table
| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `templateId` | Long | `null` | ImportTemplate id |
| `fieldName` | String | `null` | Model field name |
| `customHeader` | String | `null` | Custom Excel header |
| `sequence` | Integer | `null` | Field order in template |
| `required` | Boolean | `null` | Required field |
| `defaultValue` | String | `null` | Default value (supports `{{ expr }}`) |
| `description` | String | `null` | Description text |

### A1. Import By Template (Configured)
1. Configure ImportTemplate and ImportTemplateField

ImportTemplate key fields:
- `name`, `modelName`, `importRule`
- `uniqueConstraints` (for CreateOrUpdate)
- `ignoreEmpty`, `skipException`, `customHandler`, `syncImport`

ImportTemplateField key fields:
- `fieldName`, `customHeader`, `sequence`, `required`, `defaultValue`

Notes:
- Default values in ImportTemplateField support placeholders `{{ expr }}`. Simple variables are resolved from `env`, and expressions are evaluated against `env`.
- If `syncImport = true`, import is executed in-process.
- If `syncImport = false`, an async import message is sent to MQ.

2. Download the template file (optional)

Endpoint:
- `GET /ImportTemplate/getTemplateFile?id={templateId}`

The generated template uses field labels as headers. Required headers are styled.

3. Import by template

Endpoint:
- `POST /import/importByTemplate`

Parameters:
- `templateId`: ImportTemplate id
- `file`: Excel file
- `env`: JSON string for environment variables

Example:
```bash
curl -X POST http://localhost:8080/import/importByTemplate \
  -F templateId=1001 \
  -F env='{"deptId": 10, "source": "manual"}' \
  -F file=@/path/to/import.xlsx
```

### A2. Dynamic Mapping Import (No Template)
Endpoint:
- `POST /import/dynamicImport`

This endpoint accepts a `multipart/form-data` payload with:
- `file`: uploaded Excel file
- `wizard`: JSON payload for `ImportWizard`

Key fields:
- `modelName`
- `importRule`: `CreateOrUpdate` | `OnlyCreate` | `OnlyUpdate`
- `uniqueConstraints`: comma-separated field names
- `importFieldDTOList`: header-to-field mappings
- `ignoreEmpty`, `skipException`, `customHandler`, `syncImport`

Example:
```bash
curl -X POST http://localhost:8080/import/dynamicImport \
  -F file=@/path/to/import.xlsx \
  -F 'wizard={
    "modelName":"Product",
    "importRule":"CreateOrUpdate",
    "uniqueConstraints":"productCode",
    "importFieldDTOList":[
      {"header":"Product Code","fieldName":"productCode","required":true},
      {"header":"Product Name","fieldName":"productName","required":true},
      {"header":"Price","fieldName":"price"}
    ],
    "syncImport":true
  };type=application/json'
```

### A3. Import Result and Failed Rows
- Import returns `ImportHistory`.
- If any row fails, a “failed data” Excel file is generated and saved, with a `Failed Reason` column.
- Import status can be `PROCESSING`, `SUCCESS`, `FAILURE`, `PARTIAL_FAILURE`.

### A4. Custom Import Handler
You can register a Spring bean implementing `CustomImportHandler` and reference it by name in
`ImportTemplate.customHandler` or `ImportWizard.customHandler`.

```java
import io.softa.starter.file.excel.imports.CustomImportHandler;

@Component("productImportHandler")
public class ProductImportHandler implements CustomImportHandler {
    @Override
    public void handleImportData(List<Map<String, Object>> rows, Map<String, Object> env) {
        // custom preprocessing
    }
}
```

Contract:
- You may update row values in place.
- You may mark a row failed by writing `FileConstant.FAILED_REASON`.
- Do not add, remove, reorder, or replace row objects.

## B. Data Export
File Starter supports three export modes:
- Export by template fields
- Export by file template
- Dynamic export

### ExportTemplate Configuration Table
| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `fileName` | String | `null` | Export file name |
| `sheetName` | String | `null` | Sheet name |
| `modelName` | String | `null` | Model name to export |
| `fileId` | Long | `null` | Template file id (for file-template export) |
| `filters` | Filters | `null` | Default filters |
| `orders` | Orders | `null` | Default orders |
| `customHandler` | String | `null` | Spring bean name for CustomExportHandler |
| `enableTranspose` | Boolean | `null` | Whether to transpose output (not implemented in starter) |

### ExportTemplateField Configuration Table
| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `templateId` | Long | `null` | ExportTemplate id |
| `fieldName` | String | `null` | Model field name |
| `customHeader` | String | `null` | Custom column header |
| `sequence` | Integer | `null` | Field order in export |
| `ignored` | Boolean | `null` | Whether to ignore the field in output |

### B1. Export By Template Fields
1. Configure ExportTemplate and ExportTemplateField

ExportTemplate key fields:
- `fileName`, `sheetName`, `modelName`
- `filters`, `orders`, `customHandler`

ExportTemplateField key fields:
- `fieldName`, `customHeader`, `sequence`, `ignored`

2. Export by template

Endpoint:
- `POST /export/exportByTemplate?exportTemplateId={id}`

Request body:
- `ExportParams` (fields, filters, orders, agg, groupBy, limit, effectiveDate)

Example:
```bash
curl -X POST http://localhost:8080/export/exportByTemplate?exportTemplateId=2001 \
  -H 'Content-Type: application/json' \
  -d @- <<'JSON'
{
  "fields": ["id", "name", "code", "status"],
  "filters": ["status", "=", "ACTIVE"],
  "orders": ["createdTime", "DESC"],
  "limit": 200,
  "groupBy": [],
  "effectiveDate": "2026-03-03"
}
JSON
```

### B2. Export By File Template (Upload Template File)
This mode uses an uploaded Excel template file with placeholders like `{{ field }}` or `{{ object.field }}`.
The system extracts variables from the template to decide which fields to query.

Endpoint:
- `POST /export/exportByFileTemplate?exportTemplateId={id}`

Example:
```bash
curl -X POST http://localhost:8080/export/exportByFileTemplate?exportTemplateId=2002 \
  -H 'Content-Type: application/json' \
  -d @- <<'JSON'
{
  "fields": ["id", "name", "code", "status"],
  "filters": ["status", "=", "ACTIVE"],
  "orders": ["createdTime", "DESC"],
  "limit": 200,
  "groupBy": [],
  "effectiveDate": "2026-03-03"
}
JSON
```

### B3. Dynamic Export
Export without a template by providing fields and filters directly.

Endpoint:
- `POST /export/dynamicExport?modelName={model}&fileName={fileName}&sheetName={sheetName}`

Example:
```bash
curl -X POST 'http://localhost:8080/export/dynamicExport?modelName=Product&fileName=Products&sheetName=Sheet1' \
  -H 'Content-Type: application/json' \
  -d @- <<'JSON'
{
  "fields": ["id", "name", "code", "status"],
  "filters": ["status", "=", "ACTIVE"],
  "orders": ["createdTime", "DESC"],
  "limit": 200,
  "groupBy": [],
  "effectiveDate": "2026-03-03"
}
JSON
```

### B4. Custom Export Handler
You can register a Spring bean implementing `CustomExportHandler` and reference it by name in
`ExportTemplate.customHandler`.

```java
import io.softa.starter.file.excel.export.support.CustomExportHandler;

@Component("productExportHandler")
public class ProductExportHandler implements CustomExportHandler {
    @Override
    public void handleExportData(List<Map<String, Object>> rows) {
        // custom post-processing
    }
}
```

Contract:
- You may update row values in place.
- You should not replace row map objects.

## C. Document Export (Word/PDF)
Document templates are stored in `DocumentTemplate` and rendered as Word or PDF.

### DocumentTemplate Configuration Table
| Field          | Type | Default | Description |
|----------------| --- | --- | --- |
| `modelName`    | String | required | Model name to fetch data |
| `fileName`     | String | required | Output file name |
| `templateType` | DocumentTemplateType | `WORD` | `WORD`, `RICH_TEXT`, or `PDF` |
| `fileId`       | Long | `null` | Template file id (required for WORD type) |
| `htmlTemplate`  | String | `null` | HTML with `{{ }}` placeholders (required for RICH_TEXT type) |
| `convertToPdf` | Boolean | `null` | Convert WORD output to PDF if true |

### Template Types and Generation Pipeline

```
templateType = WORD
  1. Extract variables from .docx via poi-tl (skip # and > plugin tags)
  2. Build SubQueries for OneToMany fields (LoopRowTableRenderPolicy)
  3. Fetch data: modelService.getById(modelName, rowId, fields, subQueries, ConvertType.DISPLAY)
  4. Render .docx via poi-tl (WordFileGenerator)
  5. If convertToPdf=true, convert DOCX to PDF via docx4j
  6. Upload to OSS -> return FileInfo

templateType = RICH_TEXT
  1. Extract {{ }} variables from htmlTemplate (HTML) via PlaceholderUtils
  2. Build SubQueries for OneToMany fields
  3. Fetch data: modelService.getById(modelName, rowId, fields, subQueries, ConvertType.DISPLAY)
  4. Convert {{ }} -> ${} and render HTML via FreeMarker (PdfFileGenerator)
  5. Convert HTML to PDF via OpenPDF
  6. Upload to OSS -> return FileInfo
```

### WORD Template Syntax
- Uses `{{ variable }}` placeholder syntax with Spring EL support.
- Use `{{#fieldName}}` for OneToMany fields rendered as looping table rows via `LoopRowTableRenderPolicy`.
- OneToMany fields are auto-detected from model metadata; SubQueries are built automatically to load related data.

### RICH_TEXT Template
- `htmlTemplate` stores HTML with `{{ variable }}` placeholders.
- Placeholders are converted to FreeMarker `${}` syntax before rendering.
- The rendered HTML is converted to PDF via OpenPDF.

### Endpoint
- `GET /DocumentTemplate/generateDocument?templateId={id}&rowId={rowId}`

Example:
```bash
curl -X GET 'http://localhost:8080/DocumentTemplate/generateDocument?templateId=3001&rowId=10001'
```

## REST APIs (Summary)
- Import
  - `POST /import/importByTemplate`
  - `POST /import/dynamicImport`
  - `GET /ImportTemplate/getTemplateFile`
- Export
  - `POST /export/exportByTemplate`
  - `POST /export/exportByFileTemplate`
  - `POST /export/dynamicExport`
- Document
  - `GET /DocumentTemplate/generateDocument`
- Template Listing
  - `POST /ImportTemplate/listByModel`
  - `POST /ExportTemplate/listByModel`

## Examples
Export params:
```json
{
  "fields": ["id", "name", "code", "status"],
  "filters": ["status", "=", "ACTIVE"],
  "orders": ["createdTime", "DESC"],
  "limit": 200,
  "groupBy": [],
  "effectiveDate": "2026-03-03"
}
```

Import field mapping:
```json
[
  {"header": "Product Code", "fieldName": "productCode", "required": true},
  {"header": "Product Name", "fieldName": "productName", "required": true},
  {"header": "Price", "fieldName": "price"}
]
```

Import env:
```json
{
  "deptId": 10,
  "source": "manual"
}
```
