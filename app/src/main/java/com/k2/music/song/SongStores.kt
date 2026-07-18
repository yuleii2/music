package com.k2.music.song

import com.k2.music.LocalStoreException
import com.k2.music.MusicTheoryUtils
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap

class SongProjectStore(private val storageFile: File) {
    fun list(): List<SongProject> = synchronized(this) {
        loadRecords().values.sortedWith(compareByDescending<SongProject> { it.updatedAt }.thenBy { it.id })
    }

    fun read(id: String): SongProject? = synchronized(this) { loadRecords()[id.trim()] }

    fun save(project: SongProject): SongProject = synchronized(this) {
        val records = loadRecords()
        ensureProjectCapacity(records.size + if (project.id in records) 0 else 1)
        records[project.id] = project
        persist(records)
        project
    }

    fun add(project: SongProject): SongProject = synchronized(this) {
        val records = loadRecords()
        require(project.id !in records) { "曲谱 ID 已存在：${project.id}" }
        ensureProjectCapacity(records.size + 1)
        records[project.id] = project
        persist(records)
        project
    }

    fun delete(id: String): Boolean = synchronized(this) {
        val records = loadRecords()
        if (records.remove(id.trim()) == null) return@synchronized false
        persist(records)
        true
    }

    fun replaceAll(projects: List<SongProject>) = synchronized(this) {
        ensureProjectCapacity(projects.size)
        val records = uniqueMap(projects, { it.id }, "曲谱")
        persist(records)
    }

    fun clear() = replaceAll(emptyList())
    fun file(): File = storageFile.absoluteFile

    private fun loadRecords(): LinkedHashMap<String, SongProject> = try {
        SongStoreIo.readWithBackup(storageFile.absoluteFile) { input ->
            if (input.readInt() != MAGIC) throw IOException("曲谱文件头损坏。")
            val version = input.readInt()
            if (version !in 1..SCHEMA_VERSION) throw IOException("不支持的曲谱 Store schema：$version。")
            val count = input.readBoundedCount(SongLimits.MAX_PROJECTS, "曲谱")
            val result = LinkedHashMap<String, SongProject>()
            repeat(count) {
                val project = readProject(input, version)
                if (result.put(project.id, project) != null) throw IOException("曲谱文件包含重复 ID：${project.id}")
            }
            result
        } ?: LinkedHashMap()
    } catch (error: Exception) {
        if (error is LocalStoreException) throw error
        throw LocalStoreException("无法读取本地曲谱：${error.message ?: "数据损坏"}", error)
    }

    private fun persist(records: LinkedHashMap<String, SongProject>) {
        try {
            SongStoreIo.writeAtomically(storageFile.absoluteFile) { output ->
                output.writeInt(MAGIC)
                output.writeInt(SCHEMA_VERSION)
                output.writeInt(records.size)
                records.values.forEach { writeProject(output, it) }
            }
        } catch (error: Exception) {
            throw LocalStoreException("无法保存本地曲谱：${error.message ?: "写入失败"}", error)
        }
    }

    companion object {
        const val SCHEMA_VERSION = 2
        private const val MAGIC = 0x4B325350 // K2SP
        fun defaultFile(filesDir: File) = File(filesDir, "song-projects-v1.bin")
    }
}

class SongPracticeRunStore(private val storageFile: File) {
    fun list(): List<SongPracticeRun> = synchronized(this) {
        loadRecords().values.sortedWith(compareByDescending<SongPracticeRun> { it.startedAt }.thenBy { it.id })
    }

    fun forSong(songId: String): List<SongPracticeRun> = list().filter { it.songId == songId }
    fun read(id: String): SongPracticeRun? = synchronized(this) { loadRecords()[id.trim()] }

    fun save(run: SongPracticeRun): SongPracticeRun = synchronized(this) {
        val records = loadRecords()
        ensureCapacity(records.size + if (run.id in records) 0 else 1)
        records[run.id] = run
        persist(records)
        run
    }

    fun replaceAll(runs: List<SongPracticeRun>) = synchronized(this) {
        ensureCapacity(runs.size)
        persist(uniqueMap(runs, { it.id }, "曲谱练习记录"))
    }

    fun deleteForSong(songId: String): Int = synchronized(this) {
        val records = loadRecords()
        val removed = records.values.count { it.songId == songId }
        if (removed > 0) {
            records.entries.removeAll { it.value.songId == songId }
            persist(records)
        }
        removed
    }

    fun clear() = replaceAll(emptyList())
    fun file(): File = storageFile.absoluteFile

    private fun loadRecords(): LinkedHashMap<String, SongPracticeRun> = try {
        SongStoreIo.readWithBackup(storageFile.absoluteFile) { input ->
            if (input.readInt() != MAGIC) throw IOException("曲谱练习记录文件头损坏。")
            val version = input.readInt()
            if (version !in 1..SCHEMA_VERSION) throw IOException("不支持的曲谱练习 Store schema：$version。")
            val count = input.readBoundedCount(SongLimits.MAX_PRACTICE_RUNS, "曲谱练习记录")
            val result = LinkedHashMap<String, SongPracticeRun>()
            repeat(count) {
                val run = readPracticeRun(input, version)
                if (result.put(run.id, run) != null) throw IOException("曲谱练习记录包含重复 ID：${run.id}")
            }
            result
        } ?: LinkedHashMap()
    } catch (error: Exception) {
        if (error is LocalStoreException) throw error
        throw LocalStoreException("无法读取曲谱练习记录：${error.message ?: "数据损坏"}", error)
    }

    private fun persist(records: LinkedHashMap<String, SongPracticeRun>) {
        try {
            SongStoreIo.writeAtomically(storageFile.absoluteFile) { output ->
                output.writeInt(MAGIC)
                output.writeInt(SCHEMA_VERSION)
                output.writeInt(records.size)
                records.values.forEach { writePracticeRun(output, it) }
            }
        } catch (error: Exception) {
            throw LocalStoreException("无法保存曲谱练习记录：${error.message ?: "写入失败"}", error)
        }
    }

    private fun ensureCapacity(count: Int) {
        require(count <= SongLimits.MAX_PRACTICE_RUNS) { "曲谱练习记录数量超过限制。" }
    }

    companion object {
        const val SCHEMA_VERSION = 3
        private const val MAGIC = 0x4B325352 // K2SR
        fun defaultFile(filesDir: File) = File(filesDir, "song-practice-runs-v1.bin")
    }
}

class UserReportedDifficultyStore(private val storageFile: File) {
    fun list(): List<UserReportedDifficulty> = synchronized(this) {
        loadRecords().values.sortedWith(compareByDescending<UserReportedDifficulty> { it.reportedAt }.thenBy { it.id })
    }

    fun forSong(songId: String): List<UserReportedDifficulty> = list().filter { it.songId == songId }
    fun read(id: String): UserReportedDifficulty? = synchronized(this) { loadRecords()[id.trim()] }

    fun save(difficulty: UserReportedDifficulty): UserReportedDifficulty = synchronized(this) {
        val records = loadRecords()
        ensureCapacity(records.size + if (difficulty.id in records) 0 else 1)
        records[difficulty.id] = difficulty
        persist(records)
        difficulty
    }

    fun setResolved(id: String, resolved: Boolean): UserReportedDifficulty? = synchronized(this) {
        val records = loadRecords()
        val current = records[id.trim()] ?: return@synchronized null
        val updated = current.copy(resolved = resolved)
        records[updated.id] = updated
        persist(records)
        updated
    }

    fun replaceAll(difficulties: List<UserReportedDifficulty>) = synchronized(this) {
        ensureCapacity(difficulties.size)
        persist(uniqueMap(difficulties, { it.id }, "困难切换"))
    }

    fun deleteForSong(songId: String): Int = synchronized(this) {
        val records = loadRecords()
        val removed = records.values.count { it.songId == songId }
        if (removed > 0) {
            records.entries.removeAll { it.value.songId == songId }
            persist(records)
        }
        removed
    }

    fun clear() = replaceAll(emptyList())
    fun file(): File = storageFile.absoluteFile

    private fun loadRecords(): LinkedHashMap<String, UserReportedDifficulty> = try {
        SongStoreIo.readWithBackup(storageFile.absoluteFile) { input ->
            if (input.readInt() != MAGIC) throw IOException("困难切换文件头损坏。")
            val version = input.readInt()
            if (version != SCHEMA_VERSION) throw IOException("不支持的困难切换 Store schema：$version。")
            val count = input.readBoundedCount(SongLimits.MAX_DIFFICULTIES, "困难切换")
            val result = LinkedHashMap<String, UserReportedDifficulty>()
            repeat(count) {
                val difficulty = readDifficulty(input)
                if (result.put(difficulty.id, difficulty) != null) {
                    throw IOException("困难切换文件包含重复 ID：${difficulty.id}")
                }
            }
            result
        } ?: LinkedHashMap()
    } catch (error: Exception) {
        if (error is LocalStoreException) throw error
        throw LocalStoreException("无法读取困难切换：${error.message ?: "数据损坏"}", error)
    }

    private fun persist(records: LinkedHashMap<String, UserReportedDifficulty>) {
        try {
            SongStoreIo.writeAtomically(storageFile.absoluteFile) { output ->
                output.writeInt(MAGIC)
                output.writeInt(SCHEMA_VERSION)
                output.writeInt(records.size)
                records.values.forEach { writeDifficulty(output, it) }
            }
        } catch (error: Exception) {
            throw LocalStoreException("无法保存困难切换：${error.message ?: "写入失败"}", error)
        }
    }

    private fun ensureCapacity(count: Int) {
        require(count <= SongLimits.MAX_DIFFICULTIES) { "困难切换数量超过限制。" }
    }

    companion object {
        const val SCHEMA_VERSION = 1
        private const val MAGIC = 0x4B325344 // K2SD
        fun defaultFile(filesDir: File) = File(filesDir, "song-difficulties-v1.bin")
    }
}

private object SongStoreIo {
    private const val MAX_STRING_BYTES = 2_000_000

    fun <T> readWithBackup(target: File, reader: (DataInputStream) -> T): T? {
        var primaryError: IOException? = null
        if (target.isFile) {
            try {
                return readOne(target, reader)
            } catch (error: IOException) {
                primaryError = error
            }
        }
        val backup = File(target.path + ".bak")
        if (backup.isFile) {
            try {
                return readOne(backup, reader)
            } catch (backupError: IOException) {
                primaryError?.let(backupError::addSuppressed)
                throw backupError
            }
        }
        primaryError?.let { throw it }
        return null
    }

    fun writeAtomically(target: File, writer: (DataOutputStream) -> Unit) {
        val absolute = target.absoluteFile
        val parent = absolute.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory) {
            throw IOException("无法创建曲谱存储目录。")
        }
        val temporary = File(absolute.path + ".tmp")
        val backup = File(absolute.path + ".bak")
        if (temporary.exists() && !temporary.delete()) throw IOException("无法替换临时曲谱文件。")
        try {
            FileOutputStream(temporary).use { fileOutput ->
                DataOutputStream(fileOutput).use { output ->
                    writer(output)
                    output.flush()
                    fileOutput.fd.sync()
                }
            }
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
        if (backup.exists() && !backup.delete()) {
            temporary.delete()
            throw IOException("无法轮换曲谱备份文件。")
        }
        val rotated = absolute.exists()
        if (rotated && !absolute.renameTo(backup)) {
            temporary.delete()
            throw IOException("无法备份当前曲谱文件。")
        }
        if (!temporary.renameTo(absolute)) {
            if (rotated) backup.renameTo(absolute)
            temporary.delete()
            throw IOException("无法安装新的曲谱文件。")
        }
    }

    fun writeString(output: DataOutputStream, value: String?) {
        val bytes = value.orEmpty().toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > MAX_STRING_BYTES) throw IOException("曲谱文本超过存储限制。")
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    fun readString(input: DataInputStream): String {
        val length = input.readInt()
        if (length !in 0..MAX_STRING_BYTES) throw IOException("曲谱字符串长度损坏：$length")
        val bytes = ByteArray(length)
        try {
            input.readFully(bytes)
        } catch (error: EOFException) {
            throw IOException("曲谱文件被截断。", error)
        }
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun <T> readOne(file: File, reader: (DataInputStream) -> T): T =
        FileInputStream(file).use { stream ->
            DataInputStream(stream).use { input ->
                val value = try {
                    reader(input)
                } catch (error: IllegalArgumentException) {
                    throw IOException("曲谱数据字段无效：${error.message}", error)
                }
                if (input.read() != -1) throw IOException("曲谱文件包含意外尾随数据。")
                value
            }
        }
}

private fun ensureProjectCapacity(count: Int) {
    require(count <= SongLimits.MAX_PROJECTS) { "曲谱数量超过 ${SongLimits.MAX_PROJECTS} 首。" }
}

private fun <T> uniqueMap(values: List<T>, id: (T) -> String, label: String): LinkedHashMap<String, T> {
    val result = LinkedHashMap<String, T>()
    values.forEach { value ->
        val key = id(value)
        require(result.put(key, value) == null) { "$label ID 重复：$key" }
    }
    return result
}

private fun DataInputStream.readBoundedCount(max: Int, label: String): Int {
    val count = readInt()
    if (count !in 0..max) throw IOException("$label 数量损坏：$count")
    return count
}

private fun writeProject(output: DataOutputStream, project: SongProject) {
    output.writeInt(project.schemaVersion)
    output.writeInt(project.parserVersion)
    SongStoreIo.writeString(output, project.id)
    SongStoreIo.writeString(output, project.title)
    SongStoreIo.writeString(output, project.artist)
    SongStoreIo.writeString(output, project.originalText)
    SongStoreIo.writeString(output, project.originalKey)
    output.writeInt(project.transposeSemitones)
    output.writeInt(project.capoFret)
    output.writeInt(project.bpm)
    SongStoreIo.writeString(output, project.timeSignature)
    SongStoreIo.writeString(output, project.timingState.name)
    SongStoreIo.writeString(output, project.notes)
    output.writeLong(project.createdAt)
    output.writeLong(project.updatedAt)
    output.writeInt(project.sections.size)
    project.sections.forEach { section ->
        SongStoreIo.writeString(output, section.id)
        SongStoreIo.writeString(output, section.name)
        SongStoreIo.writeString(output, section.type.name)
        output.writeInt(section.order)
        output.writeInt(section.repeatCount)
        output.writeInt(section.rows.size)
        section.rows.forEach { row ->
            SongStoreIo.writeString(output, row.id)
            SongStoreIo.writeString(output, row.lyricText)
            SongStoreIo.writeString(output, row.rawChordText)
            output.writeInt(row.order)
            output.writeInt(row.chordEvents.size)
            row.chordEvents.forEach { event ->
                SongStoreIo.writeString(output, event.id)
                SongStoreIo.writeString(output, event.chordSymbol)
                SongStoreIo.writeString(output, event.normalizedChordSymbol)
                output.writeNullableInt(event.characterPosition)
                output.writeNullableDouble(event.durationBeats)
                SongStoreIo.writeString(output, event.selectedVoicingId)
                output.writeNullableInt(event.measureIndex)
                output.writeInt(event.order)
            }
        }
    }
    SongStoreIo.writeString(output, project.accidentalPreference.name)
}

private fun readProject(input: DataInputStream, storageSchemaVersion: Int): SongProject {
    val storedProjectSchemaVersion = input.readInt()
    if (storedProjectSchemaVersion !in 1..SongLimits.PROJECT_SCHEMA_VERSION) {
        throw IOException("不支持的曲谱数据 schema：$storedProjectSchemaVersion。")
    }
    val parserVersion = input.readInt()
    val id = SongStoreIo.readString(input)
    val title = SongStoreIo.readString(input)
    val artist = SongStoreIo.readString(input)
    val originalText = SongStoreIo.readString(input)
    val originalKey = SongStoreIo.readString(input)
    val transpose = input.readInt()
    val capo = input.readInt()
    val bpm = input.readInt()
    val signature = SongStoreIo.readString(input)
    val timing = enumValue<SongTimingState>(SongStoreIo.readString(input), "节奏状态")
    val notes = SongStoreIo.readString(input)
    val createdAt = input.readLong()
    val updatedAt = input.readLong()
    val sectionCount = input.readBoundedCount(SongLimits.MAX_SECTIONS, "曲谱段落")
    val sections = List(sectionCount) {
        val sectionId = SongStoreIo.readString(input)
        val name = SongStoreIo.readString(input)
        val type = enumValue<SongSectionType>(SongStoreIo.readString(input), "段落类型")
        val order = input.readInt()
        val repeat = input.readInt()
        val rowCount = input.readBoundedCount(SongLimits.MAX_ROWS_PER_PROJECT, "曲谱行")
        val rows = List(rowCount) {
            val rowId = SongStoreIo.readString(input)
            val lyric = SongStoreIo.readString(input)
            val rawChord = SongStoreIo.readString(input)
            val rowOrder = input.readInt()
            val eventCount = input.readBoundedCount(1_000, "单行和弦事件")
            val events = List(eventCount) {
                SongChordEvent(
                    SongStoreIo.readString(input),
                    SongStoreIo.readString(input),
                    SongStoreIo.readString(input),
                    input.readNullableInt(),
                    input.readNullableDouble(),
                    SongStoreIo.readString(input).ifBlank { null },
                    input.readNullableInt(),
                    input.readInt(),
                )
            }
            SongRow(rowId, lyric, rawChord, events, rowOrder)
        }
        SongSection(sectionId, name, type, order, repeat, rows)
    }
    val accidentalPreference = if (storageSchemaVersion >= 2) {
        enumValue<MusicTheoryUtils.AccidentalPreference>(SongStoreIo.readString(input), "升降号偏好")
    } else {
        MusicTheoryUtils.AccidentalPreference.AUTO
    }
    return SongProject(
        SongLimits.PROJECT_SCHEMA_VERSION, parserVersion, id, title, artist, originalText, originalKey, transpose,
        capo, bpm, signature, timing, sections, notes, createdAt, updatedAt, accidentalPreference,
    )
}

private fun writePracticeRun(output: DataOutputStream, run: SongPracticeRun) {
    SongStoreIo.writeString(output, run.id)
    SongStoreIo.writeString(output, run.songId)
    SongStoreIo.writeString(output, run.sectionId)
    SongStoreIo.writeString(output, run.mode.name)
    output.writeInt(run.bpm)
    output.writeInt(run.transposeSemitones)
    output.writeInt(run.capoFret)
    output.writeLong(run.startedAt)
    output.writeLong(run.endedAt)
    output.writeInt(run.actualDurationSeconds)
    output.writeBoolean(run.completed)
    output.writeInt(run.reportedDifficultTransitions.size)
    run.reportedDifficultTransitions.forEach {
        SongStoreIo.writeString(output, it.fromChord)
        SongStoreIo.writeString(output, it.toChord)
    }
    output.writeBoolean(run.loopEnabled)
    output.writeBoolean(run.showFretboard)
    output.writeInt(run.selectedVoicingIds.size)
    run.selectedVoicingIds.toSortedMap().forEach { (eventId, voicingId) ->
        SongStoreIo.writeString(output, eventId)
        SongStoreIo.writeString(output, voicingId)
    }
}

private fun readPracticeRun(input: DataInputStream, storageSchemaVersion: Int): SongPracticeRun {
    val id = SongStoreIo.readString(input)
    val songId = SongStoreIo.readString(input)
    val sectionId = SongStoreIo.readString(input).ifBlank { null }
    val mode = enumValue<SongPracticeMode>(SongStoreIo.readString(input), "曲谱练习模式")
    val bpm = input.readInt()
    val transpose = input.readInt()
    val capo = input.readInt()
    val started = input.readLong()
    val ended = input.readLong()
    val duration = input.readInt()
    val completed = input.readBoolean()
    val transitionCount = input.readBoundedCount(1_000, "困难切换")
    val transitions = List(transitionCount) {
        SongTransition(SongStoreIo.readString(input), SongStoreIo.readString(input))
    }
    val loopEnabled = if (storageSchemaVersion >= 2) input.readBoolean() else true
    val showFretboard = if (storageSchemaVersion >= 2) input.readBoolean() else true
    val selectedVoicingIds = if (storageSchemaVersion >= 3) {
        val count = input.readBoundedCount(SongLimits.MAX_CHORD_EVENTS_PER_PROJECT, "练习固定指法快照")
        buildMap {
            repeat(count) {
                val eventId = SongStoreIo.readString(input)
                val voicingId = SongStoreIo.readString(input)
                if (put(eventId, voicingId) != null) throw IOException("练习固定指法快照包含重复事件 ID。")
            }
        }
    } else emptyMap()
    return SongPracticeRun(id, songId, sectionId, mode, bpm, transpose, capo, started, ended,
        duration, completed, transitions, loopEnabled, showFretboard, selectedVoicingIds)
}

private fun writeDifficulty(output: DataOutputStream, difficulty: UserReportedDifficulty) {
    SongStoreIo.writeString(output, difficulty.id)
    SongStoreIo.writeString(output, difficulty.songId)
    SongStoreIo.writeString(output, difficulty.sectionId)
    SongStoreIo.writeString(output, difficulty.fromChord)
    SongStoreIo.writeString(output, difficulty.toChord)
    output.writeLong(difficulty.reportedAt)
    output.writeBoolean(difficulty.resolved)
    SongStoreIo.writeString(output, difficulty.note)
}

private fun readDifficulty(input: DataInputStream) = UserReportedDifficulty(
    SongStoreIo.readString(input),
    SongStoreIo.readString(input),
    SongStoreIo.readString(input).ifBlank { null },
    SongStoreIo.readString(input),
    SongStoreIo.readString(input),
    input.readLong(),
    input.readBoolean(),
    SongStoreIo.readString(input),
)

private inline fun <reified T : Enum<T>> enumValue(raw: String, label: String): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: throw IOException("$label 无效：$raw")

private fun DataOutputStream.writeNullableInt(value: Int?) {
    writeBoolean(value != null)
    value?.let(::writeInt)
}

private fun DataInputStream.readNullableInt(): Int? = if (readBoolean()) readInt() else null

private fun DataOutputStream.writeNullableDouble(value: Double?) {
    writeBoolean(value != null)
    value?.let(::writeDouble)
}

private fun DataInputStream.readNullableDouble(): Double? = if (readBoolean()) readDouble() else null
