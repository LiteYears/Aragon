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
                description = "Manage isolated OpenSandbox microVM/container instances (inspect health/status, spawn custom container image, or terminate).",
                parameters = listOf(
                    ToolParameter("action", "string", "Action to perform: 'status', 'spawn', 'terminate'", required = true),
                    ToolParameter("image", "string", "Optional container image (e.g. 'opensandbox/python:3.12', 'ubuntu:22.04')", required = false)
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


