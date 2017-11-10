package org.yinwang.pysonar

import org.yinwang.pysonar.ast.Module
import org.yinwang.pysonar.ast.Node
import java.io.*
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Provides a factory for python source ASTs.  Maintains configurable on-disk and
 * in-memory caches to avoid re-parsing files during analysis.
 */
class AstCache {
	private val cache = hashMapOf<String, Node?>()

	/** Clears the memory cache. */
	fun clear() = cache.clear()

	/**
	 * Removes all serialized ASTs from the on-disk cache.
	 *
	 * @return `true` if all cached AST files were removed
	 */
	fun clearDiskCache() = try {
		deleteDirectory(File(Analyzer.self.cacheDir))
		true
	} catch (x: Exception) {
		LOG.log(Level.SEVERE, "Failed to clear disk cache: " + x)
		false
	}

	fun close() = parser.close()
	// clearDiskCache()

	/**
	 * Returns the syntax tree for `path`.  May find and/or create a
	 * cached copy in the mem cache or the disk cache.
	 *
	 * @param path absolute path to a source file
	 * @return the AST, or `null` if the parse failed for any reason
	 */
	fun getAST(path: String): Node? {
		// Cache stores null value if the parse failed.
		if (cache.containsKey(path)) {
			return cache[path]
		}

		// Might be cached on disk but not in memory.
		var node: Node? = getSerializedModule(path)
		if (node != null) {
			LOG.log(Level.FINE, "reusing " + path)
			cache.put(path, node)
			return node
		}

		node = null
		try {
			LOG.log(Level.FINE, "parsing " + path)
			node = parser.parseFile(path)
		} finally {
			cache.put(path, node)  // may be null
		}

		node?.let(this::serialize)
		return node
	}


	/**
	 * Each source file's AST is saved in an object file named for the MD5
	 * checksum of the source file.  All that is needed is the MD5, but the
	 * file's base name is included for ease of debugging.
	 */
	private fun getCachePath(sourcePath: String) = makePathString(Analyzer.self.cacheDir, getFileHash(sourcePath))


	// package-private for testing
	private fun serialize(ast: Node) {
		val path = getCachePath(ast.file)
		var oos: ObjectOutputStream? = null
		var fos: FileOutputStream? = null
		try {
			fos = FileOutputStream(path)
			oos = ObjectOutputStream(fos)
			oos.writeObject(ast)
		} catch (e: Exception) {
			msgln("Failed to serialize: " + path)
		} finally {
			oos?.close()
			fos?.close()
		}
	}


	// package-private for testing
	private fun getSerializedModule(sourcePath: String) = when {
		!File(sourcePath).canRead() -> null
		!File(getCachePath(sourcePath)).canRead() -> null
		else -> deserialize(sourcePath)
	}


	// package-private for testing
	private fun deserialize(sourcePath: String): Module? {
		val cachePath = getCachePath(sourcePath)
		var fis: FileInputStream? = null
		var ois: ObjectInputStream? = null
		return try {
			fis = FileInputStream(cachePath)
			ois = ObjectInputStream(fis)
			ois.readObject() as Module
		} catch (e: Exception) {
			null
		} finally {
			ois?.close()
			fis?.close()
		}
	}

	companion object {
		private val LOG = Logger.getLogger(AstCache::class.java.canonicalName)
		private val parser = Parser()
	}
}