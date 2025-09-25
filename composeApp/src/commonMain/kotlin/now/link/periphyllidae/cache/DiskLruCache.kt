package now.link.periphyllidae.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import now.link.periphyllidae.utils.LogManager
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import java.security.MessageDigest
import kotlin.collections.LinkedHashMap

/**
 * A Disk LRU cache implementation for storing and retrieving files.
 * Thread-safe with configurable maximum size and automatic eviction of least recently used entries.
 */
class DiskLruCache private constructor(
    private val directory: Path,
    private val maxSize: Long,
    private val fileSystem: FileSystem = FileSystem.SYSTEM
) {
    companion object {
        private const val TAG = "DiskLruCache"
        private const val JOURNAL_FILE = "journal"
        private const val JOURNAL_FILE_TEMP = "journal.tmp"
        private const val JOURNAL_FILE_BACKUP = "journal.bkp"
        private const val MAGIC = "libcore.io.DiskLruCache"
        private const val VERSION_1 = "1"
        private const val APP_VERSION = 1
        private const val VALUE_COUNT = 1
        
        fun open(directory: Path, maxSize: Long): DiskLruCache {
            val cache = DiskLruCache(directory, maxSize)
            cache.initialize()
            return cache
        }
    }
    
    private val mutex = Mutex()
    private val lruEntries = LinkedHashMap<String, Entry>(0, 0.75f, true)
    private var size: Long = 0
    private var journalWriter: okio.BufferedSink? = null
    private var redundantOpCount = 0
    
    private val journalFile = directory / JOURNAL_FILE
    private val journalFileTmp = directory / JOURNAL_FILE_TEMP
    private val journalFileBackup = directory / JOURNAL_FILE_BACKUP
    
    private var initialized = false
    
    private fun initialize() {
        LogManager.d(TAG, "Initializing DiskLruCache at: $directory")
        
        if (!fileSystem.exists(directory)) {
            fileSystem.createDirectories(directory)
            LogManager.d(TAG, "Created cache directory: $directory")
        }
        
        if (fileSystem.exists(journalFileBackup)) {
            if (fileSystem.exists(journalFile)) {
                fileSystem.delete(journalFileBackup)
            } else {
                renameTo(journalFileBackup, journalFile)
            }
        }
        
        if (fileSystem.exists(journalFile)) {
            try {
                readJournal()
                processJournal()
                initialized = true
                LogManager.i(TAG, "DiskLruCache initialized successfully")
                return
            } catch (journalIsCorrupt: Exception) {
                LogManager.w(TAG, "DiskLruCache journal is corrupt, recreating", journalIsCorrupt)
                delete()
            }
        }
        
        rebuildJournal()
        initialized = true
        LogManager.i(TAG, "DiskLruCache initialized with new journal")
    }
    
    private fun readJournal() {
        fileSystem.source(journalFile).buffer().use { source ->
            val magic = source.readUtf8LineStrict()
            val version = source.readUtf8LineStrict()
            val appVersionString = source.readUtf8LineStrict()
            val valueCountString = source.readUtf8LineStrict()
            val blank = source.readUtf8LineStrict()
            
            if (magic != MAGIC || version != VERSION_1 || 
                appVersionString != APP_VERSION.toString() || 
                valueCountString != VALUE_COUNT.toString() || 
                blank.isNotEmpty()) {
                throw IllegalStateException("Unexpected journal header: [$magic, $version, $appVersionString, $valueCountString, $blank]")
            }
            
            var lineCount = 0
            while (true) {
                try {
                    readJournalLine(source.readUtf8LineStrict())
                    lineCount++
                } catch (endOfJournal: Exception) {
                    break
                }
            }
            redundantOpCount = lineCount - lruEntries.size
            
            if (!source.exhausted()) {
                throw IllegalStateException("Journal has more entries than expected")
            }
        }
    }
    
    private fun readJournalLine(line: String) {
        val firstSpace = line.indexOf(' ')
        if (firstSpace == -1) {
            throw IllegalStateException("Unexpected journal line: $line")
        }
        
        val keyBegin = firstSpace + 1
        val secondSpace = line.indexOf(' ', keyBegin)
        val key: String
        
        if (secondSpace == -1) {
            key = line.substring(keyBegin)
            if (firstSpace == "REMOVE".length && line.startsWith("REMOVE")) {
                lruEntries.remove(key)
                return
            }
        } else {
            key = line.substring(keyBegin, secondSpace)
        }
        
        var entry = lruEntries[key]
        if (entry == null) {
            entry = Entry(key)
            lruEntries[key] = entry
        }
        
        when {
            secondSpace != -1 && firstSpace == "CLEAN".length && line.startsWith("CLEAN") -> {
                val parts = line.substring(secondSpace + 1).split(' ')
                entry.readable = true
                entry.currentEditor = null
                entry.setLengths(parts)
            }
            secondSpace == -1 && firstSpace == "DIRTY".length && line.startsWith("DIRTY") -> {
                entry.currentEditor = Editor(entry)
            }
            secondSpace == -1 && firstSpace == "READ".length && line.startsWith("READ") -> {
                // This work was already done by calling lruEntries.get()
            }
            else -> {
                throw IllegalStateException("Unexpected journal line: $line")
            }
        }
    }
    
    private fun processJournal() {
        deleteIfExists(journalFileTmp)
        
        val iterator = lruEntries.values.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.currentEditor == null) {
                for (t in 0 until VALUE_COUNT) {
                    size += entry.lengths[t]
                }
            } else {
                entry.currentEditor = null
                for (t in 0 until VALUE_COUNT) {
                    deleteIfExists(entry.getCleanFile(t))
                    deleteIfExists(entry.getDirtyFile(t))
                }
                iterator.remove()
            }
        }
    }
    
    private fun rebuildJournal() {
        journalWriter?.close()
        
        fileSystem.sink(journalFileTmp).buffer().use { writer ->
            writer.writeUtf8(MAGIC).writeByte('\n'.code)
            writer.writeUtf8(VERSION_1).writeByte('\n'.code)
            writer.writeUtf8(APP_VERSION.toString()).writeByte('\n'.code)
            writer.writeUtf8(VALUE_COUNT.toString()).writeByte('\n'.code)
            writer.writeByte('\n'.code)
            
            for (entry in lruEntries.values) {
                if (entry.currentEditor != null) {
                    writer.writeUtf8("DIRTY").writeByte(' '.code)
                    writer.writeUtf8(entry.key).writeByte('\n'.code)
                } else {
                    writer.writeUtf8("CLEAN").writeByte(' '.code)
                    writer.writeUtf8(entry.key)
                    entry.writeLengths(writer)
                    writer.writeByte('\n'.code)
                }
            }
        }
        
        if (fileSystem.exists(journalFile)) {
            renameTo(journalFile, journalFileBackup)
        }
        renameTo(journalFileTmp, journalFile)
        deleteIfExists(journalFileBackup)
        
        journalWriter = newJournalWriter()
        redundantOpCount = 0
    }
    
    private fun newJournalWriter(): okio.BufferedSink {
        return fileSystem.appendingSink(journalFile).buffer()
    }
    
    suspend fun get(key: String): Snapshot? = mutex.withLock {
        checkNotClosed()
        validateKey(key)
        
        val entry = lruEntries[key] ?: return null
        val snapshot = entry.snapshot() ?: return null
        
        redundantOpCount++
        journalWriter?.apply {
            writeUtf8("READ").writeByte(' '.code)
            writeUtf8(key).writeByte('\n'.code)
            flush()
        }
        
        if (journalRebuildRequired()) {
            rebuildJournal()
        }
        
        return snapshot
    }
    
    suspend fun edit(key: String): Editor? = mutex.withLock {
        checkNotClosed()
        validateKey(key)
        
        var entry = lruEntries[key]
        if (entry?.currentEditor != null) {
            return null // Another edit is in progress
        }
        
        if (entry != null && entry.lockingSnapshotCount != 0) {
            return null // A snapshot is being read
        }
        
        if (entry == null) {
            entry = Entry(key)
            lruEntries[key] = entry
        }
        
        val editor = Editor(entry)
        entry.currentEditor = editor
        
        // Flush before creating files to ensure ordering
        journalWriter?.apply {
            writeUtf8("DIRTY").writeByte(' '.code)
            writeUtf8(key).writeByte('\n'.code)
            flush()
        }
        
        return editor
    }
    
    private suspend fun completeEdit(editor: Editor, success: Boolean) {
        val entry = editor.entry
        if (entry.currentEditor != editor) {
            throw IllegalStateException()
        }
        
        // If this edit is creating the entry for the first time, we need at least
        // one of the values.
        if (success && !entry.readable) {
            for (i in 0 until VALUE_COUNT) {
                if (!editor.written[i]) {
                    editor.abort()
                    throw IllegalStateException("Newly created entry didn't create value for index $i")
                }
                if (!fileSystem.exists(entry.getDirtyFile(i))) {
                    editor.abort()
                    return
                }
            }
        }
        
        for (i in 0 until VALUE_COUNT) {
            val dirty = entry.getDirtyFile(i)
            if (success) {
                if (fileSystem.exists(dirty)) {
                    val clean = entry.getCleanFile(i)
                    renameTo(dirty, clean)
                    val oldLength = entry.lengths[i]
                    val newLength = fileSystem.metadata(clean).size ?: 0
                    entry.lengths[i] = newLength
                    size = size - oldLength + newLength
                }
            } else {
                deleteIfExists(dirty)
            }
        }
        
        redundantOpCount++
        entry.currentEditor = null
        if (entry.readable || success) {
            entry.readable = true
            journalWriter?.apply {
                writeUtf8("CLEAN").writeByte(' '.code)
                writeUtf8(entry.key)
                entry.writeLengths(this)
                writeByte('\n'.code)
                flush()
            }
            
            if (success) {
                entry.sequenceNumber = nextSequenceNumber++
            }
        } else {
            lruEntries.remove(entry.key)
            journalWriter?.apply {
                writeUtf8("REMOVE").writeByte(' '.code)
                writeUtf8(entry.key).writeByte('\n'.code)
                flush()
            }
        }
        
        if (size > maxSize || journalRebuildRequired()) {
            trimToSize()
        }
    }
    
    private var nextSequenceNumber: Long = 0
    
    private suspend fun trimToSize() {
        while (size > maxSize) {
            val toEvict = lruEntries.values.first()
            remove(toEvict.key)
        }
        
        if (journalRebuildRequired()) {
            rebuildJournal()
        }
    }
    
    suspend fun remove(key: String): Boolean = mutex.withLock {
        checkNotClosed()
        validateKey(key)
        
        val entry = lruEntries[key] ?: return false
        return removeEntry(entry)
    }
    
    private fun removeEntry(entry: Entry): Boolean {
        if (entry.lockingSnapshotCount > 0) {
            // Snapshots are still open for this entry
            journalWriter?.apply {
                writeUtf8("DIRTY").writeByte(' '.code)
                writeUtf8(entry.key).writeByte('\n'.code)
                flush()
            }
        }
        
        if (entry.currentEditor != null) {
            entry.currentEditor!!.detach()
        }
        
        for (i in 0 until VALUE_COUNT) {
            deleteIfExists(entry.getCleanFile(i))
            size -= entry.lengths[i]
            entry.lengths[i] = 0
        }
        
        redundantOpCount++
        journalWriter?.apply {
            writeUtf8("REMOVE").writeByte(' '.code)
            writeUtf8(entry.key).writeByte('\n'.code)
            flush()
        }
        lruEntries.remove(entry.key)
        
        if (journalRebuildRequired()) {
            rebuildJournal()
        }
        
        return true
    }
    
    private fun journalRebuildRequired(): Boolean {
        val redundantOpCompactThreshold = 2000
        return redundantOpCount >= redundantOpCompactThreshold && redundantOpCount >= lruEntries.size
    }
    
    suspend fun close() {
        if (!initialized || closed) {
            closed = true
            return
        }
        
        // Clone entries to avoid issues with concurrent modification
        val entries = lruEntries.values.toList()
        for (entry in entries) {
            if (entry.currentEditor != null) {
                entry.currentEditor!!.abort()
            }
        }
        
        trimToSize()
        journalWriter?.close()
        journalWriter = null
        closed = true
        LogManager.i(TAG, "DiskLruCache closed")
    }
    
    private var closed = false
    
    private fun checkNotClosed() {
        if (closed) {
            throw IllegalStateException("cache is closed")
        }
    }
    
    fun delete() {
        // Simple synchronous delete without calling close()
        deleteContents(directory)
        LogManager.i(TAG, "DiskLruCache deleted")
    }
    
    suspend fun deleteAsync() {
        close()
        deleteContents(directory)
        LogManager.i(TAG, "DiskLruCache deleted asynchronously")
    }
    
    private fun deleteContents(directory: Path) {
        if (fileSystem.exists(directory)) {
            fileSystem.deleteRecursively(directory)
        }
    }
    
    private fun validateKey(key: String) {
        if (key.contains(' ') || key.contains('\n') || key.contains('\r')) {
            throw IllegalArgumentException("keys must not contain spaces or newlines: \"$key\"")
        }
    }
    
    private fun deleteIfExists(file: Path) {
        try {
            fileSystem.delete(file)
        } catch (ignored: Exception) {
            // Ignored
        }
    }
    
    private fun renameTo(from: Path, to: Path) {
        deleteIfExists(to)
        fileSystem.atomicMove(from, to)
    }
    
    fun getDirectory(): Path = directory
    
    fun getMaxSize(): Long = maxSize
    
    fun size(): Long = size
    
    // Entry class represents a cache entry
    inner class Entry(val key: String) {
        val lengths: LongArray = LongArray(VALUE_COUNT)
        var readable: Boolean = false
        var currentEditor: Editor? = null
        var sequenceNumber: Long = 0
        var lockingSnapshotCount: Int = 0
        
        fun snapshot(): Snapshot? {
            if (!Thread.holdsLock(this@DiskLruCache.mutex)) {
                throw AssertionError()
            }
            
            if (!readable) {
                return null
            }
            
            if (currentEditor != null) {
                return null
            }
            
            for (i in 0 until VALUE_COUNT) {
                if (!fileSystem.exists(getCleanFile(i))) {
                    return null
                }
            }
            
            lockingSnapshotCount++
            return Snapshot(this)
        }
        
        fun getCleanFile(i: Int): Path {
            return directory / "$key.$i"
        }
        
        fun getDirtyFile(i: Int): Path {
            return directory / "$key.$i.tmp"
        }
        
        fun setLengths(strings: List<String>) {
            if (strings.size != VALUE_COUNT) {
                throw IllegalStateException("Unexpected journal line: $strings")
            }
            
            try {
                for (i in strings.indices) {
                    lengths[i] = strings[i].toLong()
                }
            } catch (e: NumberFormatException) {
                throw IllegalStateException("Unexpected journal line: $strings")
            }
        }
        
        fun writeLengths(writer: okio.BufferedSink) {
            for (length in lengths) {
                writer.writeByte(' '.code).writeLong(length)
            }
        }
    }
    
    // Snapshot class provides read access to a cache entry
    inner class Snapshot internal constructor(private val entry: Entry) {
        fun getInputStream(index: Int): okio.Source {
            return fileSystem.source(entry.getCleanFile(index))
        }
        
        fun getFile(index: Int): Path {
            return entry.getCleanFile(index)
        }
        
        fun close() {
            entry.lockingSnapshotCount--
            if (entry.lockingSnapshotCount == 0 && entry.currentEditor == null) {
                // The entry is eligible for removal
            }
        }
        
        fun key(): String = entry.key
        
        fun length(index: Int): Long = entry.lengths[index]
    }
    
    // Editor class provides write access to a cache entry
    inner class Editor internal constructor(internal val entry: Entry) {
        val written: BooleanArray = BooleanArray(VALUE_COUNT)
        private var committed = false
        
        fun newOutputStream(index: Int): okio.Sink {
            if (index < 0 || index >= VALUE_COUNT) {
                throw IllegalArgumentException("Expected index $index to be greater than 0 and less than the maximum value count of $VALUE_COUNT")
            }
            
            written[index] = true
            val dirtyFile = entry.getDirtyFile(index)
            return fileSystem.sink(dirtyFile)
        }
        
        fun getFile(index: Int): Path {
            return entry.getDirtyFile(index)
        }
        
        suspend fun commit() {
            completeEdit(this, true)
            committed = true
        }
        
        suspend fun abort() {
            completeEdit(this, false)
        }
        
        fun detach() {
            if (entry.currentEditor == this) {
                for (i in 0 until VALUE_COUNT) {
                    try {
                        deleteIfExists(entry.getDirtyFile(i))
                    } catch (ignored: Exception) {
                        // Ignored
                    }
                }
                entry.currentEditor = null
            }
        }
    }
    
    fun isClosed(): Boolean = closed
}

// Utility function to hash a string to create a valid cache key
fun String.toCacheKey(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(this.toByteArray())
    return hashBytes.joinToString("") { "%02x".format(it) }
}
