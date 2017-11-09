@file:JvmName("JSONDump")
@file:JvmMultifileClass

package org.yinwang.pysonar

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.google.common.collect.Lists
import org.yinwang.pysonar.ast.Node
import org.yinwang.pysonar.types.*
import java.io.*
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

private val log = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME)

private val seenDef = hashSetOf<String>()
private val seenRef = hashSetOf<String>()
private val seenDocs = hashSetOf<String>()

private fun dirname(path: String): String = File(path).parent

private fun newAnalyzer(srcpath: String, inclpaths: Array<String>): Analyzer {
	val idx = Analyzer()
	for (inclpath in inclpaths) {
		idx.addPath(inclpath)
	}

	idx.analyze(srcpath)
	idx.finish()

	if (idx.semanticErrors.isNotEmpty()) {
		log.info("Analyzer errors:")
		for ((k, diagnostics) in idx.semanticErrors) {
			log.info("  Key: " + k)
			for (d in diagnostics) log.info("    " + d)
		}
	}

	return idx
}

private fun writeSymJson(binding: Binding, json: JsonGenerator) {
	if (binding.start < 0) return

	val name = binding.name
	val isExported = !(Binding.Kind.VARIABLE == binding.kind ||
			Binding.Kind.PARAMETER == binding.kind ||
			Binding.Kind.SCOPE == binding.kind ||
			Binding.Kind.ATTRIBUTE == binding.kind ||
			name.isEmpty() || name[0] == '_' || name.startsWith("lambda%"))

	val path = binding.qname.replace('.', '/').replace("%20", ".")

	if (path !in seenDef) {
		seenDef.add(path)
		json.writeStartObject()
		json.writeStringField("name", name)
		json.writeStringField("path", path)
		json.writeStringField("file", binding.fileOrUrl)
		json.writeNumberField("identStart", binding.start)
		json.writeNumberField("identEnd", binding.end)
		json.writeNumberField("defStart", binding.bodyStart)
		json.writeNumberField("defEnd", binding.bodyEnd)
		json.writeBooleanField("exported", isExported)
		json.writeStringField("kind", binding.kind.toString())

		if (Binding.Kind.FUNCTION == binding.kind ||
				Binding.Kind.METHOD == binding.kind ||
				Binding.Kind.CONSTRUCTOR == binding.kind) {
			json.writeObjectFieldStart("funcData")

			// get args expression
			var argExpr: String? = null
			var t: Type? = binding.type
			if (t is UnionType) t = t.firstUseful()
			if (t != null && t is FunType) {
				val func = t.func
				if (func != null) {
					argExpr = func.argumentExpr
				}
			}

			val typeExpr = binding.type.toString()
			json.writeNullField("params")
			val signature = if (argExpr == null) "" else argExpr + "\n" + typeExpr
			json.writeStringField("signature", signature)
			json.writeEndObject()
		}

		json.writeEndObject()
	}
}

private fun writeRefJson(ref: Node, binding: Binding, json: JsonGenerator) {
	if (binding.file != null) {
		val path = binding.qname.replace(".", "/").replace("%20", ".")
		val key = ref.file + ":" + ref.start
		if (!seenRef.contains(key)) {
			seenRef.add(key)
			if (binding.start >= 0 && ref.start >= 0 && !binding.isBuiltin) {
				json.writeStartObject()
				json.writeStringField("sym", path)
				json.writeStringField("file", ref.file)
				json.writeNumberField("start", ref.start)
				json.writeNumberField("end", ref.end)
				json.writeBooleanField("builtin", binding.isBuiltin)
				json.writeEndObject()
			}
		}
	}
}

private fun writeDocJson(binding: Binding, idx: Analyzer, json: JsonGenerator) {
	val path = binding.qname.replace('.', '/').replace("%20", ".")

	if (path !in seenDocs) {
		seenDocs.add(path)

		val doc = binding.docstring

		if (doc != null) {
			json.writeStartObject()
			json.writeStringField("sym", path)
			json.writeStringField("file", binding.fileOrUrl)
			json.writeStringField("body", doc.value)
			json.writeNumberField("start", doc.start)
			json.writeNumberField("end", doc.end)
			json.writeEndObject()
		}
	}
}

/*
 * Precondition: srcpath and inclpaths are absolute paths
 */
private fun graph(
		srcpath: String,
		inclpaths: Array<String>,
		symOut: OutputStream,
		refOut: OutputStream,
		docOut: OutputStream) {
	// Compute parent dirs, sort by length so potential prefixes show up first
	val parentDirs = Lists.newArrayList(*inclpaths)
	parentDirs.add(dirname(srcpath))
	Collections.sort(parentDirs) { s1, s2 ->
		val diff = s1.length - s2.length
		if (0 == diff) s1.compareTo(s2) else diff
	}

	val idx = newAnalyzer(srcpath, inclpaths)
	idx.multilineFunType = true
	val jsonFactory = JsonFactory()
	val symJson = jsonFactory.createGenerator(symOut)
	val refJson = jsonFactory.createGenerator(refOut)
	val docJson = jsonFactory.createGenerator(docOut)
	val allJson = arrayOf(symJson, refJson, docJson)
	for (json in allJson) {
		json.writeStartArray()
	}

	for (b in idx.getAllBindings()) {
		val path = b.qname.replace('.', '/').replace("%20", ".")

		if (b.file != null) {
			if (b.file!!.startsWith(srcpath)) {
				writeSymJson(b, symJson)
				writeDocJson(b, idx, docJson)
			}
		}

		b.refs
				.filter { it.file != null && it.file.startsWith(srcpath) }
				.forEach { writeRefJson(it, b, refJson) }
	}

	allJson.forEach { json ->
		json.writeEndArray()
		json.close()
	}
}


private fun info(msg: Any) {
	println(msg)
}


private fun usage() {
	info("Usage: java org.yinwang.pysonar.dump <source-path> <include-paths> <out-root> [verbose]")
	info("  <source-path> is path to source unit (package directory or module file) that will be graphed")
	info("  <include-paths> are colon-separated paths to included libs")
	info("  <out-root> is the prefix of the output files.  There are 3 output files: <out-root>-doc, <out-root>-sym, <out-root>-ref")
	info("  [verbose] if set, then verbose logging is used (optional)")
}

fun main(args: Array<String>) {
	if (args.size < 3 || args.size > 4) {
		usage()
		return
	}

	log.level = Level.SEVERE
	if (args.size >= 4) {
		log.level = Level.ALL
		log.info("LOGGING VERBOSE")
		log.info("ARGS: " + Arrays.toString(args))
	}

	val srcpath = args[0]
	val inclpaths = args[1].split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
	val outroot = args[2]

	val symFilename = outroot + "-sym"
	val refFilename = outroot + "-ref"
	val docFilename = outroot + "-doc"
	var symOut: OutputStream? = null
	var refOut: OutputStream? = null
	var docOut: OutputStream? = null
	try {
		docOut = BufferedOutputStream(FileOutputStream(docFilename))
		symOut = BufferedOutputStream(FileOutputStream(symFilename))
		refOut = BufferedOutputStream(FileOutputStream(refFilename))
		msgln("graphing: " + srcpath)
		graph(srcpath, inclpaths, symOut, refOut, docOut)
		docOut.flush()
		symOut.flush()
		refOut.flush()
	} catch (e: FileNotFoundException) {
		System.err.println("Could not find file: " + e)
		return
	} finally {
		docOut?.close()
		symOut?.close()
		refOut?.close()
	}
	log.info("SUCCESS")
}
