package com.example.aragon.data.local

import androidx.room.TypeConverter
import com.example.aragon.domain.model.AgentMode
import com.example.aragon.domain.model.StepStatus
import com.example.aragon.domain.model.TaskStatus
import com.example.aragon.domain.model.TimelineEventType
import org.json.JSONArray

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        if (value == null) return "[]"
        val array = JSONArray()
        value.forEach { array.put(it) }
        return array.toString()
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(value)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromTaskStatus(status: TaskStatus?): String = status?.name ?: TaskStatus.CREATED.name

    @TypeConverter
    fun toTaskStatus(value: String?): TaskStatus =
        value?.let { runCatching { TaskStatus.valueOf(it) }.getOrDefault(TaskStatus.CREATED) }
            ?: TaskStatus.CREATED

    @TypeConverter
    fun fromAgentMode(mode: AgentMode?): String = mode?.name ?: AgentMode.AGENT.name

    @TypeConverter
    fun toAgentMode(value: String?): AgentMode =
        value?.let { runCatching { AgentMode.valueOf(it) }.getOrDefault(AgentMode.AGENT) }
            ?: AgentMode.AGENT

    @TypeConverter
    fun fromStepStatus(status: StepStatus?): String = status?.name ?: StepStatus.PENDING.name

    @TypeConverter
    fun toStepStatus(value: String?): StepStatus =
        value?.let { runCatching { StepStatus.valueOf(it) }.getOrDefault(StepStatus.PENDING) }
            ?: StepStatus.PENDING

    @TypeConverter
    fun fromTimelineEventType(type: TimelineEventType?): String =
        type?.name ?: TimelineEventType.STATUS_CHANGE.name

    @TypeConverter
    fun toTimelineEventType(value: String?): TimelineEventType =
        value?.let { runCatching { TimelineEventType.valueOf(it) }.getOrDefault(TimelineEventType.STATUS_CHANGE) }
            ?: TimelineEventType.STATUS_CHANGE

    @TypeConverter
    fun fromArtifactStage(stage: com.example.aragon.domain.model.ArtifactStage?): String =
        stage?.name ?: com.example.aragon.domain.model.ArtifactStage.PRODUCT.name

    @TypeConverter
    fun toArtifactStage(value: String?): com.example.aragon.domain.model.ArtifactStage =
        value?.let { runCatching { com.example.aragon.domain.model.ArtifactStage.valueOf(it) }.getOrDefault(com.example.aragon.domain.model.ArtifactStage.PRODUCT) }
            ?: com.example.aragon.domain.model.ArtifactStage.PRODUCT
}
