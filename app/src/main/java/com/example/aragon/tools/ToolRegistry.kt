package com.example.aragon.tools

class ToolRegistry {

    private val tools = mutableMapOf<String, ToolDefinition>()

    init {
        registerDefaultTools()
    }

    fun register(tool: ToolDefinition) {
        tools[tool.name] = tool
    }

    fun getTool(name: String): ToolDefinition? = tools[name]

    fun getAllTools(): List<ToolDefinition> = tools.values.toList()

    private fun registerDefaultTools() {
        register(
            ToolDefinition(
                name = "run_command",
                description = "Execute a command in the Ubuntu/Linux environment shell with stdout and stderr output.",
                parameters = listOf(
                    ToolParameter("command", "string", "The shell command line string to execute", required = true),
                    ToolParameter("workingDirectory", "string", "Logical directory path to execute in (default: /workspace)", required = false),
                    ToolParameter("timeoutMs", "integer", "Maximum execution time in milliseconds (default: 30000)", required = false)
                ),
                permission = ToolPermission.NORMAL
            )
        )

        register(
            ToolDefinition(
                name = "python_execute",
                description = "Execute a Python script safely by saving code to disk, verifying SHA-256 integrity, and running in the Python environment.",
                parameters = listOf(
                    ToolParameter("code", "string", "The complete Python code to execute", required = true),
                    ToolParameter("arguments", "string", "Optional command line arguments to pass to the script", required = false),
                    ToolParameter("timeoutMs", "integer", "Maximum execution time in milliseconds (default: 30000)", required = false)
                ),
                permission = ToolPermission.NORMAL
            )
        )

        register(
            ToolDefinition(
                name = "file_list",
                description = "List files and directories in a given path with sizes and types.",
                parameters = listOf(
                    ToolParameter("path", "string", "Logical directory path (e.g. /workspace)", required = true),
                    ToolParameter("recursive", "boolean", "Whether to list subdirectories recursively", required = false)
                ),
                permission = ToolPermission.SAFE
            )
        )

        register(
            ToolDefinition(
                name = "file_read",
                description = "Read text content of a file within the workspace.",
                parameters = listOf(
                    ToolParameter("path", "string", "Logical path to the file to read (e.g. /workspace/notes.txt)", required = true),
                    ToolParameter("startLine", "integer", "Optional 1-based start line number", required = false),
                    ToolParameter("lineCount", "integer", "Optional maximum number of lines to read", required = false)
                ),
                permission = ToolPermission.SAFE
            )
        )

        register(
            ToolDefinition(
                name = "file_write",
                description = "Write or overwrite content to a file in the workspace.",
                parameters = listOf(
                    ToolParameter("path", "string", "Logical destination path (e.g. /workspace/script.py)", required = true),
                    ToolParameter("content", "string", "The raw text or code content to write", required = true),
                    ToolParameter("overwrite", "boolean", "Whether to overwrite if file exists (default: true)", required = false)
                ),
                permission = ToolPermission.NORMAL
            )
        )

        register(
            ToolDefinition(
                name = "file_delete",
                description = "Delete a file or directory in the workspace.",
                parameters = listOf(
                    ToolParameter("path", "string", "Logical path to delete", required = true),
                    ToolParameter("recursive", "boolean", "Whether to delete directory recursively", required = false)
                ),
                permission = ToolPermission.SENSITIVE
            )
        )

        register(
            ToolDefinition(
                name = "file_move",
                description = "Move or rename a file or directory within the workspace.",
                parameters = listOf(
                    ToolParameter("source", "string", "Logical source path", required = true),
                    ToolParameter("destination", "string", "Logical destination path", required = true)
                ),
                permission = ToolPermission.NORMAL
            )
        )

        register(
            ToolDefinition(
                name = "file_copy",
                description = "Copy a file or directory within the workspace.",
                parameters = listOf(
                    ToolParameter("source", "string", "Logical source path", required = true),
                    ToolParameter("destination", "string", "Logical destination path", required = true)
                ),
                permission = ToolPermission.NORMAL
            )
        )

        register(
            ToolDefinition(
                name = "directory_create",
                description = "Create a directory (including parent directories if needed).",
                parameters = listOf(
                    ToolParameter("path", "string", "Logical path of the directory to create (e.g. /workspace/src/components)", required = true)
                ),
                permission = ToolPermission.SAFE
            )
        )

        register(
            ToolDefinition(
                name = "search_files",
                description = "Search for files by filename pattern or text content in the workspace.",
                parameters = listOf(
                    ToolParameter("path", "string", "Logical directory path to search within", required = true),
                    ToolParameter("pattern", "string", "Filename pattern (e.g. *.py, *.md)", required = false),
                    ToolParameter("contentQuery", "string", "Text string to search for inside files", required = false)
                ),
                permission = ToolPermission.SAFE
            )
        )

        register(
            ToolDefinition(
                name = "inspect_file",
                description = "Inspect file metadata, size, binary header, and MIME type without loading entire content.",
                parameters = listOf(
                    ToolParameter("path", "string", "Logical path to inspect", required = true)
                ),
                permission = ToolPermission.SAFE
            )
        )

        register(
            ToolDefinition(
                name = "web_search",
                description = "Perform real web search queries to gather research information.",
                parameters = listOf(
                    ToolParameter("query", "string", "Search query terms", required = true),
                    ToolParameter("maxResults", "integer", "Number of results to retrieve (default: 5)", required = false)
                ),
                permission = ToolPermission.NORMAL
            )
        )

        register(
            ToolDefinition(
                name = "web_fetch",
                description = "Fetch and extract readable text and structure from a web URL.",
                parameters = listOf(
                    ToolParameter("url", "string", "The HTTP or HTTPS URL to fetch", required = true),
                    ToolParameter("extractTextOnly", "boolean", "Whether to extract clean text without HTML tags (default: true)", required = false)
                ),
                permission = ToolPermission.NORMAL
            )
        )

        register(
            ToolDefinition(
                name = "artifact_inspect",
                description = "Inspect and validate a generated artifact file (e.g. verify DOCX ZIP structure, PDF magic signature, image dimensions).",
                parameters = listOf(
                    ToolParameter("path", "string", "Logical path of the artifact to inspect", required = true)
                ),
                permission = ToolPermission.SAFE
            )
        )

        register(
            ToolDefinition(
                name = "text_editor",
                description = "Structured text editor for precise viewing, creating, replacing, patching, appending, and deleting code or text files.",
                parameters = listOf(
                    ToolParameter("operation", "string", "Operation type: 'view', 'create', 'replace', 'patch', 'append', 'delete'", required = true),
                    ToolParameter("path", "string", "Logical file path (e.g. /workspace/report.py)", required = true),
                    ToolParameter("content", "string", "Content for create or append", required = false),
                    ToolParameter("targetContent", "string", "Exact target content block for replace or patch", required = false),
                    ToolParameter("replacementContent", "string", "Replacement content for replace or patch", required = false),
                    ToolParameter("startLine", "integer", "Optional starting line for view operation", required = false),
                    ToolParameter("lineCount", "integer", "Optional number of lines to view", required = false)
                ),
                permission = ToolPermission.NORMAL
            )
        )

        register(
            ToolDefinition(
                name = "browser_action",
                description = "Navigate web pages and interact using headless browser session.",
                parameters = listOf(
                    ToolParameter("action", "string", "Action to perform: 'navigate', 'click', 'input', 'scroll', 'extract'", required = true),
                    ToolParameter("url", "string", "URL for navigate action", required = false),
                    ToolParameter("selector", "string", "CSS/text selector for click or input", required = false),
                    ToolParameter("text", "string", "Text to type for input action", required = false),
                    ToolParameter("direction", "string", "Scroll direction: 'up', 'down'", required = false)
                ),
                permission = ToolPermission.NORMAL
            )
        )

        register(
            ToolDefinition(
                name = "verify_objective",
                description = "Run deterministic verification checks against requested objective and generated artifacts.",
                parameters = listOf(
                    ToolParameter("objective", "string", "The objective to verify", required = true)
                ),
                permission = ToolPermission.SAFE
            )
        )

        register(
            ToolDefinition(
                name = "sandbox_manage",
                description = "Manage isolated OpenSandbox microVM/container instances (inspect health/status, spawn custom container image, inspect metrics, or terminate).",
                parameters = listOf(
                    ToolParameter("action", "string", "Action to perform: 'status', 'spawn', 'metrics', 'packages', 'terminate'", required = true),
                    ToolParameter("image", "string", "Optional container image (e.g. 'opensandbox/python:3.12', 'ubuntu:22.04')", required = false)
                ),
                permission = ToolPermission.NORMAL
            )
        )

        register(
            ToolDefinition(
                name = "spreadsheet_create",
                description = "Create a structured, validated OpenXML XLSX spreadsheet workbook deliverable with sheets, column headers, and data rows. Automatically saves to /artifacts/<filename>.xlsx",
                parameters = listOf(
                    ToolParameter("filename", "string", "Destination filename or logical path (e.g. /artifacts/metrics.xlsx or data.xlsx)", required = true),
                    ToolParameter("sheetName", "string", "Title of the primary worksheet (default: 'Sheet1')", required = false),
                    ToolParameter("headers", "string", "Column headers (JSON array of strings or comma-separated list)", required = true),
                    ToolParameter("rows", "string", "Data rows (JSON array of row arrays or comma-separated rows)", required = true)
                ),
                permission = ToolPermission.NORMAL
            )
        )

        register(
            ToolDefinition(
                name = "json_query",
                description = "Query, extract, or filter fields in JSON files or JSON strings using JSONPath / key selector.",
                parameters = listOf(
                    ToolParameter("path", "string", "Logical path to the JSON file (or provide 'jsonContent')", required = false),
                    ToolParameter("jsonContent", "string", "Raw JSON string if not reading from file", required = false),
                    ToolParameter("query", "string", "Key path or query (e.g. 'metrics.latency', 'items[0].id')", required = true)
                ),
                permission = ToolPermission.SAFE
            )
        )

        register(
            ToolDefinition(
                name = "csv_analyze",
                description = "Parse, inspect columns, calculate summary statistics (count, sum, mean, min, max), and query records in CSV data files.",
                parameters = listOf(
                    ToolParameter("path", "string", "Logical path to the CSV file (e.g. /workspace/data.csv)", required = true),
                    ToolParameter("column", "string", "Optional specific column name for numeric aggregation", required = false),
                    ToolParameter("limit", "integer", "Number of preview rows (default: 10)", required = false)
                ),
                permission = ToolPermission.SAFE
            )
        )

        register(
            ToolDefinition(
                name = "http_request",
                description = "Send custom HTTP/REST requests (GET, POST, PUT, DELETE) with custom headers, query params, and request body.",
                parameters = listOf(
                    ToolParameter("url", "string", "Target HTTP or HTTPS URL", required = true),
                    ToolParameter("method", "string", "HTTP method: GET, POST, PUT, DELETE, HEAD (default: GET)", required = false),
                    ToolParameter("headers", "string", "JSON object of HTTP headers", required = false),
                    ToolParameter("body", "string", "Request body payload for POST/PUT", required = false)
                ),
                permission = ToolPermission.NORMAL
            )
        )

        register(
            ToolDefinition(
                name = "archive_manage",
                description = "Create or extract ZIP archives of workspace files and deliverables.",
                parameters = listOf(
                    ToolParameter("operation", "string", "Archive operation: 'create_zip' or 'extract_zip'", required = true),
                    ToolParameter("archivePath", "string", "Logical path of zip file (e.g. /artifacts/bundle.zip)", required = true),
                    ToolParameter("sourcePaths", "string", "JSON array or comma-separated list of paths to include (for create_zip)", required = false),
                    ToolParameter("destinationDir", "string", "Destination directory to extract into (for extract_zip)", required = false)
                ),
                permission = ToolPermission.NORMAL
            )
        )

        register(
            ToolDefinition(
                name = "document_create",
                description = "Generate a verified OpenXML DOCX document deliverable with structured sections, executive summary, bullet points, and data tables. Automatically saves to /artifacts/<filename>.docx",
                parameters = listOf(
                    ToolParameter("filename", "string", "Destination filename or logical path (e.g. /artifacts/report.docx or report.docx)", required = true),
                    ToolParameter("title", "string", "Document main title (e.g. 'Executive Administrative Report')", required = true),
                    ToolParameter("subtitle", "string", "Optional document subtitle or inspection header", required = false),
                    ToolParameter("paragraphs", "string", "Document body paragraphs (JSON array of strings or newline-separated text)", required = false),
                    ToolParameter("bulletPoints", "string", "Key findings or bullet points (JSON array of strings or newline-separated text)", required = false),
                    ToolParameter("tableHeaders", "string", "Table column headers (JSON array of strings, e.g. [\"Metric\", \"Value\", \"Status\"])", required = false),
                    ToolParameter("tableRows", "string", "Table data rows (JSON array of string arrays or comma-separated rows)", required = false)
                ),
                permission = ToolPermission.NORMAL
            )
        )

        register(
            ToolDefinition(
                name = "docx_generate",
                description = "Alias for document_create: generate a verified OpenXML DOCX document deliverable.",
                parameters = listOf(
                    ToolParameter("filename", "string", "Destination filename or logical path (e.g. /artifacts/report.docx)", required = true),
                    ToolParameter("title", "string", "Document main title", required = true),
                    ToolParameter("subtitle", "string", "Optional document subtitle", required = false),
                    ToolParameter("paragraphs", "string", "Document body paragraphs", required = false),
                    ToolParameter("bulletPoints", "string", "Key findings or bullet points", required = false),
                    ToolParameter("tableHeaders", "string", "Table column headers", required = false),
                    ToolParameter("tableRows", "string", "Table data rows", required = false)
                ),
                permission = ToolPermission.NORMAL
            )
        )

        register(
            ToolDefinition(
                name = "playwright_browser",
                description = "Automate web browsing with headless Playwright engine. Supports navigating to URLs, extracting clean markdown or raw text, interacting with selectors (click, fill, type), evaluating JavaScript, and capturing full-page PNG screenshots saved to artifacts.",
                parameters = listOf(
                    ToolParameter("action", "string", "Action to perform: 'navigate', 'get_content', 'extract_markdown', 'screenshot', 'click', 'fill', 'evaluate', 'search'", required = true),
                    ToolParameter("url", "string", "Target URL to navigate or inspect", required = false),
                    ToolParameter("selector", "string", "CSS or text selector for click/fill interactions", required = false),
                    ToolParameter("text", "string", "Text content to type for fill action or query for search", required = false),
                    ToolParameter("script", "string", "JavaScript code snippet to evaluate in page context", required = false),
                    ToolParameter("outputPath", "string", "Optional destination path for screenshot artifact (e.g. /artifacts/page.png)", required = false),
                    ToolParameter("waitFor", "string", "Optional wait condition: 'domcontentloaded', 'networkidle'", required = false)
                ),
                permission = ToolPermission.NORMAL
            )
        )

        register(
            ToolDefinition(
                name = "web_search",
                description = "Search the public web for real-time information, documentation, packages, and technical specifications with ranked search snippets.",
                parameters = listOf(
                    ToolParameter("query", "string", "The search query keywords", required = true)
                ),
                permission = ToolPermission.SAFE
            )
        )

        register(
            ToolDefinition(
                name = "mcp_client",
                description = "Execute tools or read resources from Model Context Protocol (MCP) servers (embedded filesystem, playwright, data servers, or remote SSE/HTTP MCP endpoints).",
                parameters = listOf(
                    ToolParameter("toolName", "string", "The MCP tool name to execute (e.g. 'fs_read_file', 'fs_write_file', 'data_sqlite_query')", required = true),
                    ToolParameter("arguments", "string", "JSON object of tool arguments", required = true),
                    ToolParameter("serverName", "string", "Optional MCP server name (e.g. 'aragon-filesystem', 'aragon-playwright', 'aragon-data')", required = false)
                ),
                permission = ToolPermission.NORMAL
            )
        )

        register(
            ToolDefinition(
                name = "mcp_manage",
                description = "Manage Model Context Protocol (MCP) servers and inspect discovered tools, resources, and prompt templates.",
                parameters = listOf(
                    ToolParameter("action", "string", "Action: 'list_servers', 'list_tools', 'register_server'", required = true),
                    ToolParameter("serverName", "string", "Server identifier", required = false),
                    ToolParameter("endpointUrl", "string", "SSE or HTTP endpoint URL when registering remote server", required = false)
                ),
                permission = ToolPermission.SAFE
            )
        )

        register(
            ToolDefinition(
                name = "file_patch",
                description = "Safely build upon and modify existing files. Supports exact replacement, flexible whitespace matching, line-range replacement, anchor insertion (insert_after/insert_before), append, and rollback.",
                parameters = listOf(
                    ToolParameter("operation", "string", "Operation: 'patch', 'replace', 'replace_lines', 'insert_after', 'insert_before', 'append', 'view', 'rollback'", required = true),
                    ToolParameter("path", "string", "Path to file in workspace or artifacts", required = true),
                    ToolParameter("targetContent", "string", "Exact code block or anchor string to target", required = false),
                    ToolParameter("replacementContent", "string", "New code block or replacement content", required = false),
                    ToolParameter("startLine", "integer", "Starting line number for line-based edits", required = false),
                    ToolParameter("lineCount", "integer", "Number of lines to replace", required = false)
                ),
                permission = ToolPermission.NORMAL
            )
        )

        register(
            ToolDefinition(
                name = "edit_file",
                description = "Alias for file_patch: incrementally edit and build upon an existing workspace file.",
                parameters = listOf(
                    ToolParameter("path", "string", "Path to target file", required = true),
                    ToolParameter("targetContent", "string", "Target block to replace", required = false),
                    ToolParameter("replacementContent", "string", "Replacement block", required = false),
                    ToolParameter("operation", "string", "Operation: 'replace', 'patch', 'append' (default: 'replace')", required = false)
                ),
                permission = ToolPermission.NORMAL
            )
        )

        register(
            ToolDefinition(
                name = "complete_task",
                description = "Explicitly signal that all requested objectives, files, or analysis have been successfully created and completed.",
                parameters = listOf(
                    ToolParameter("summary", "string", "Summary of completed deliverables and achievements", required = true)
                ),
                permission = ToolPermission.SAFE
            )
        )
    }
}


