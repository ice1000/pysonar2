@file:JvmName("$")
@file:JvmMultifileClass
@file:Suppress("unused")

package org.yinwang.pysonar

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import sun.net.www.protocol.file.FileURLConnection
import java.io.*
import java.lang.management.ManagementFactory
import java.net.JarURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.security.MessageDigest
import java.text.DecimalFormat
import java.util.*
import java.util.jar.JarFile

/**
 * unsorted utility class
 */
val UTF_8: Charset = Charset.forName("UTF-8")

val systemTempDir: String
	get() {
		val tmp = System.getProperty("java.io.tmpdir")
		val sep = System.getProperty("file.separator")
		return if (tmp.endsWith(sep)) tmp else tmp + sep
	}

val gcStats: String
	get() {
		var totalGC = 0L
		var gcTime = 0L

		for (gc in ManagementFactory.getGarbageCollectorMXBeans()) {
			val count = gc.collectionCount
			if (count >= 0) totalGC += count
			val time = gc.collectionTime
			if (time >= 0) gcTime += time
		}

		val sb = StringBuilder()
		sb.append(banner("memory stats"))
		sb.append("\n- total collections: " + totalGC)
		sb.append("\n- total collection time: " + formatTime(gcTime))

		val runtime = Runtime.getRuntime()
		sb.append("\n- total memory: " + printMem(runtime.totalMemory()))

		return sb.toString()
	}


fun baseFileName(filename: String): String = File(filename).name

fun hashFileName(filename: String): String = Integer.toString(filename.hashCode())

fun same(o1: Any?, o2: Any?): Boolean = if (o1 == null) o2 == null else o1 == o2


/**
 * Returns the parent qname of `qname` -- everything up to the
 * last dot (exclusive), or if there are no dots, the empty string.
 */
fun getQnameParent(qname: String?): String {
	if (qname == null || qname.isEmpty()) return ""
	val index = qname.lastIndexOf(".")
	return if (index == -1) "" else qname.substring(0, index)
}


fun moduleQname(file_: String): String? {
	var file = file_
	val f = File(file)

	if (f.name.endsWith("__init__.py")) file = f.parent else {
		if (file.endsWith(Analyzer.self.suffix)) file = file.substring(0, file.length - Analyzer.self.suffix.length)
	}
	return file.replace(".", "%20").replace('/', '.').replace('\\', '.')
}


/**
 * Given an absolute `path` to a file (not a directory),
 * returns the module name for the file.  If the file is an __init__.py,
 * returns the last component of the file's parent directory, else
 * returns the filename without path or extension.
 */
fun moduleName(path: String): String {
	val f = File(path)
	val name = f.name
	return when {
		name == "__init__.py" -> f.parentFile.name
		name.endsWith(Analyzer.self.suffix) -> name.substring(0, name.length - Analyzer.self.suffix.length)
		else -> name
	}
}

fun arrayToString(strings: Collection<String>): String {
	val sb = StringBuffer()
	for (s in strings) {
		sb.append(s).append("\n")
	}
	return sb.toString()
}

fun arrayToSortedStringSet(strings: Collection<String>): String {
	val sorter = TreeSet<String>()
	sorter.addAll(strings)
	return arrayToString(sorter)
}

fun writeFile(path: String, contents: String) {
	var out: PrintWriter? = null
	try {
		out = PrintWriter(BufferedWriter(FileWriter(path)))
		out.print(contents)
		out.flush()
	} catch (e: Exception) {
		die("Failed to write: " + path)
	} finally {
		out?.close()
	}
}

fun readFile(path: String): String? {
	// Don't use line-oriented file read -- need to retain CRLF if present
	// so the style-run and link offsets are correct.
	val content = getBytesFromFile(path)
	return if (content == null) {
		null
	} else {
		String(content, UTF_8)
	}
}

fun getBytesFromFile(filename: String) = try {
	FileUtils.readFileToByteArray(File(filename))
} catch (e: Exception) {
	null
}

internal fun isReadableFile(path: String): Boolean {
	val f = File(path)
	return f.canRead() && f.isFile
}

@Throws(IOException::class)
fun readWhole(allI: InputStream): String {
	val sb = StringBuilder()
	val bytes = ByteArray(8192)

	var nRead = -1
	while (allI.read(bytes, 0, 8192).let { nRead = it; it > 0 }) {
		sb.append(String(bytes, 0, nRead))
	}
	return sb.toString()
}


@Throws(Exception::class)
fun copyResourcesRecursively(originUrl: URL, destination: File) {
	val urlConnection = originUrl.openConnection()
	when (urlConnection) {
		is JarURLConnection -> copyJarResourcesRecursively(destination, urlConnection)
		is FileURLConnection -> FileUtils.copyDirectory(File(originUrl.path), destination)
		else -> die("Unsupported URL type: " + urlConnection)
	}
}


fun copyJarResourcesRecursively(destination: File, jarConnection: JarURLConnection) {
	val jarFile: JarFile
	try {
		jarFile = jarConnection.jarFile
	} catch (e: Exception) {
		die("Failed to get jar file)")
		return
	}

	val em = jarFile.entries()
	while (em.hasMoreElements()) {
		val entry = em.nextElement()
		if (entry.name.startsWith(jarConnection.entryName)) {
			val fileName = StringUtils.removeStart(entry.name, jarConnection.entryName)
			if (fileName != "/") {  // exclude the directory
				var entryInputStream: InputStream? = null
				try {
					entryInputStream = jarFile.getInputStream(entry)
					FileUtils.copyInputStreamToFile(entryInputStream!!, File(destination, fileName))
				} catch (e: Exception) {
					die("Failed to copy resource: " + fileName)
				} finally {
					entryInputStream?.close()
				}
			}
		}
	}
}

fun readResource(resource: String) = readWholeStream(Thread.currentThread().contextClassLoader.getResourceAsStream(resource))

/**
 * get unique hash according to file content and filename
 */
fun getFileHash(path: String) = getContentHash(path.toByteArray()) + "." + getContentHash(getBytesFromFile(path))

fun getContentHash(fileContents: ByteArray?): String {
	val algorithm: MessageDigest

	try {
		algorithm = MessageDigest.getInstance("SHA-1")
	} catch (e: Exception) {
		die("Failed to get SHA, shouldn't happen")
		return ""
	}

	algorithm.reset()
	algorithm.update(fileContents!!)
	val messageDigest = algorithm.digest()
	val sb = StringBuilder()
	for (aMessageDigest in messageDigest) sb.append(String.format("%02x", (0xFF and aMessageDigest.toInt()).toByte()))
	return sb.toString()
}

fun escapeQname(s: String) = s.replace("[.&@%-]".toRegex(), "_")

fun escapeWindowsPath(path: String) = path.replace("\\", "\\\\")

fun toStringCollection(collection: Collection<Int>) = collection.mapTo(ArrayList()) { it.toString() }

fun joinWithSep(ls: Collection<String>, sep: String, start: String?, end: String?): String {
	val sb = StringBuilder()
	if (start != null && ls.size > 1) sb.append(start)
	for ((i, s) in ls.withIndex()) {
		if (i > 0) sb.append(sep)
		sb.append(s)
	}
	if (end != null && ls.size > 1) sb.append(end)
	return sb.toString()
}

fun msgln(m: String) {
	if (Analyzer.self != null && !Analyzer.self.hasOption("quiet")) println(m)
}

fun msg(m: String) {
	if (Analyzer.self != null && !Analyzer.self.hasOption("quiet")) print(m)
}

fun testmsg(m: String) {
	println(m)
}

@JvmOverloads
fun die(msg: String, e: Exception? = null) {
	System.err.println(msg)

	if (e != null) {
		System.err.println("Exception: " + e + "\n")
	}

	Thread.dumpStack()
	System.exit(2)
}

fun readWholeFile(filename: String): String? = try {
	Scanner(File(filename)).useDelimiter("PYSONAR2END").next()
} catch (e: FileNotFoundException) {
	null
}

fun readWholeStream(`in`: InputStream): String = Scanner(`in`).useDelimiter("\\Z").next()

fun percent(num: Long, total: Long): String = if (total == 0L) "100%" else {
	val pct = (num * 100 / total).toInt()
	String.format("%1$3d", pct) + "%"
}

fun formatTime(millis: Long): String {
	var sec = millis / 1000
	var min = sec / 60
	sec %= 60
	val hr = min / 60
	min %= 60

	return hr.toString() + ":" + min + ":" + sec
}

/**
 * format number with fixed width
 */
fun formatNumber(n: Any, length_: Int): String {
	var length = length_
	if (length == 0) {
		length = 1
	}

	return when (n) {
		is Int -> String.format("%1$" + length + "d", n)
		is Long -> String.format("%1$" + length + "d", n)
		else -> String.format("%1$" + length + "s", n.toString())
	}
}


fun deleteDirectory(directory: File): Boolean {
	if (directory.exists()) {
		val files = directory.listFiles()
		if (files != null) {
			for (f in files) {
				if (f.isDirectory) deleteDirectory(f) else f.delete()
			}
		}
	}
	return directory.delete()
}

fun newSessionId(): String = UUID.randomUUID().toString()

fun makePath(vararg files: String): File {
	var ret = File(files[0])
	for (i in 1 until files.size) ret = File(ret, files[i])
	return ret
}

fun makePathString(vararg files: String): String = unifyPath(makePath(*files).path)

fun unifyPath(filename: String): String = unifyPath(File(filename))

fun unifyPath(file: File): String = try {
	file.canonicalPath
} catch (e: Exception) {
	die("Failed to get canonical path")
	""
}

fun relPath(path1: String, path2: String): String? {
	val a = unifyPath(path1)
	val b = unifyPath(path2)

	val allA = a.split("[/\\\\]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
	val bs = b.split("[/\\\\]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

	var i = 0
	while (i < Math.min(allA.size, bs.size)) {
		if (allA[i] != bs[i]) {
			break
		}
		i++
	}

	val ups = allA.size - i - 1

	var res: File? = null
	for (x in 0 until ups) res = File(res, "..")
	for (y in i until bs.size) res = File(res, bs[y])
	return res?.path
}

fun projRelPath(file: String): String =
		if (file.startsWith(Analyzer.self.projectDir)) file.substring(Analyzer.self.projectDir.length + 1) else file

fun projAbsPath(file: String): String =
		if (file.startsWith("/") || file.startsWith(Analyzer.self.projectDir)) file
		else makePathString(Analyzer.self.projectDir, file)

fun joinPath(dir: File, file: String): File = joinPath(dir.absolutePath, file)

fun joinPath(dir: String, file: String): File = File(File(dir), file)

fun locateTmp(file: String): String {
	val tmpDir = systemTempDir
	return makePathString(tmpDir, "pysonar2", file + "." + Analyzer.self.sid)
}

fun banner(msg: String): String = "---------------- $msg ----------------"

fun printMem(bytes: Long): String {
	val dbytes = bytes.toDouble()
	val df = DecimalFormat("#.##")

	return when {
		dbytes < 1024 -> df.format(bytes)
		dbytes < 1024 * 1024 -> df.format(dbytes / 1024)
		dbytes < 1024 * 1024 * 1024 -> df.format(dbytes / 1024.0 / 1024.0) + "M"
		dbytes < 1024 * 1024 * 1024 * 1024L -> df.format(dbytes / 1024.0 / 1024.0 / 1024.0) + "G"
		else -> "Too big to show you"
	}
}

fun correlateBindings(bindings: List<Binding>): List<List<Binding>> {
	val bdHash = hashMapOf<Int, List<Binding>>()
	for (b in bindings) {
		val hash = b.hashCode()
		if (!bdHash.containsKey(hash)) bdHash.put(hash, ArrayList())
		val bs = bdHash[hash]!!.toMutableList()
		bs.add(b)
	}
	return ArrayList(bdHash.values)
}
