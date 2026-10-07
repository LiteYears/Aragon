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
    }
}
