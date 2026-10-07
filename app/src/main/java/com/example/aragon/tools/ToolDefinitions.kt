package com.example.aragon.tools

import org.json.JSONObject

enum class ToolPermission {
    SAFE,
    NORMAL,
    SENSITIVE,
    DANGEROUS
}

data class ToolParameter(
    val name: String,
    val type: String, // "string", "number", "boolean", "integer"
    val description: String,
    val required: Boolean = true,
    val default: Any? = null
)

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter>,
    val permission: ToolPermission = ToolPermission.NORMAL
) {
    /**
     * Converts to OpenAI / NVIDIA NIM tool definition JSON structure.
     */
    fun toOpenAiToolSchema(): JSONObject {
        val root = JSONObject()
        root.put("type", "function")

        val functionObj = JSONObject()
        functionObj.put("name", name)
        functionObj.put("description", description)

        val paramsObj = JSONObject()
        paramsObj.put("type", "object")

        val propertiesObj = JSONObject()
        val requiredList = org.json.JSONArray()

        for (param in parameters) {
            val pObj = JSONObject()
            pObj.put("type", param.type)
            pObj.put("description", param.description)
            propertiesObj.put(param.name, pObj)
            if (param.required) {
                requiredList.put(param.name)
            }
        }

        paramsObj.put("properties", propertiesObj)
        paramsObj.put("required", requiredList)
        functionObj.put("parameters", paramsObj)

        root.put("function", functionObj)
        return root
    }
}
