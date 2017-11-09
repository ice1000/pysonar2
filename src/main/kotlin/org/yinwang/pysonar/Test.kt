package org.yinwang.pysonar

import com.google.gson.GsonBuilder
import org.yinwang.pysonar.ast.Dummy
import java.io.File
import java.util.ArrayList
import java.util.LinkedHashMap

@Suppress("UNCHECKED_CAST")
class Test private constructor(private val inputDir: String) {
	internal var analyzer: Analyzer
	private var expectRefsFile: String
	private var failedRefsFile: String

	init {
		// make a quiet analyzer
		val options = hashMapOf<String, Any>()
		options.put("quiet", true)
		this.analyzer = Analyzer(options)
		if (File(inputDir).isDirectory) {
			expectRefsFile = makePathString(inputDir, "refs.json")
			failedRefsFile = makePathString(inputDir, "failed_refs.json")
		} else {
			expectRefsFile = makePathString(inputDir + ".refs.json")
			failedRefsFile = makePathString(inputDir, ".failed_refs.json")
		}
	}

	private fun runAnalysis(dir: String) {
		analyzer.analyze(dir)
		analyzer.finish()
	}

	private fun generateRefs() {

		val refs = ArrayList<Map<String, Any>>()
		for ((key, value) in analyzer.references) {

			var file = key.file

			// only record those in the inputDir
			if (file != null && file.startsWith(Analyzer.self.projectDir)) {
				file = projRelPath(file)
				val writeout = LinkedHashMap<String, Any>()

				val ref = LinkedHashMap<String, Any>()
				ref.put("name", key.name)
				ref.put("file", file)
				ref.put("start", key.start)
				ref.put("end", key.end)

				val dests = ArrayList<Map<String, Any>>()
				for (b in value) {
					var destFile = b.file
					if (destFile != null && destFile.startsWith(Analyzer.self.projectDir)) {
						destFile = projRelPath(destFile)
						val dest = LinkedHashMap<String, Any>()
						dest.put("name", b.name)
						dest.put("file", destFile)
						dest.put("start", b.start)
						dest.put("end", b.end)
						dests.add(dest)
					}
				}
				if (!dests.isEmpty()) {
					writeout.put("ref", ref)
					writeout.put("dests", dests)
					refs.add(writeout)
				}
			}
		}

		val json = gson.toJson(refs)
		writeFile(expectRefsFile, json)
	}

	private fun checkRefs(): Boolean {
		val failedRefs = ArrayList<Map<String, Any>>()
		val json = readFile(expectRefsFile)
		if (json == null) {
			msgln("Expected refs not found in: $expectRefsFile Please run Test with -exp to generate")
			return false
		}
		val expectedRefs = (gson.fromJson(json, List::class.java) as List<Map<String, Any>>)
		for (r in expectedRefs) {
			val refMap = r["ref"] as Map<String, Any>
			val dummy = makeDummy(refMap)

			val dests = r.get("dests") as List<Map<String, Any>>
			val actualDests = analyzer.references[dummy]
			val failedDests = ArrayList<Map<String, Any>>()

			for (d in dests) {
				// names are ignored, they are only for human readers
				val file = projAbsPath(d["file"] as String)
				val start = Math.floor(d["start"] as Double).toInt()
				val end = Math.floor(d["end"] as Double).toInt()

				if (!checkBindingExist(actualDests, file, start, end)) {
					failedDests.add(d)
				}
			}

			// record the ref & failed dests if any
			if (!failedDests.isEmpty()) {
				val failedRef = LinkedHashMap<String, Any>()
				failedRef.put("ref", refMap)
				failedRef.put("dests", failedDests)
				failedRefs.add(failedRef)
			}
		}

		if (failedRefs.isEmpty()) {
			return true
		} else {
			val failedJson = gson.toJson(failedRefs)
			testmsg("Failed to find refs: " + failedJson)
			writeFile(failedRefsFile, failedJson)
			return false
		}
	}

	// ------------------------- static part -----------------------

	private fun checkBindingExist(bs: List<Binding>?, file: String?, start: Int, end: Int) =
			bs?.any { (it.file == null && file == null || it.file != null && file != null && it.file == file) && it.start == start && it.end == end } ?: false

	fun generateTest() {
		runAnalysis(inputDir)
		generateRefs()
		testmsg("  * " + inputDir)
	}

	fun runTest(): Boolean {
		runAnalysis(inputDir)
		testmsg("  * " + inputDir)
		return checkRefs()
	}

	companion object {

		private val gson = GsonBuilder().setPrettyPrinting().create()

		private fun makeDummy(m: Map<String, Any>): Dummy {
			val file = projAbsPath(m["file"] as String)
			val start = Math.floor(m["start"] as Double).toInt()
			val end = Math.floor(m["end"] as Double).toInt()
			return Dummy(file, start, end)
		}

		private fun testAll(path: String, exp: Boolean) {
			val failed = ArrayList<String>()
			if (exp) {
				testmsg("generating tests")
			} else {
				testmsg("verifying tests")
			}

			testRecursive(path, exp, failed)

			when {
				exp -> testmsg("all tests generated")
				failed.isEmpty() -> testmsg("all tests passed!")
				else -> {
					testmsg("failed some tests: ")
					for (f in failed) testmsg("  * " + f)
				}
			}
		}

		private fun testRecursive(path: String, exp: Boolean, failed: MutableList<String>) {
			val fileOrDir = File(path)

			if (fileOrDir.isDirectory) {
				if (path.endsWith(".test")) {
					val test = Test(path)
					if (exp) {
						test.generateTest()
					} else {
						if (!test.runTest()) {
							failed.add(path)
						}
					}
				} else {
					for (file in fileOrDir.listFiles()!!) {
						testRecursive(file.path, exp, failed)
					}
				}
			}
		}

		@JvmStatic
		fun main(args: Array<String>) {
			val options = Options(args)
			val argsList = options.args
			val inputDir = unifyPath(argsList[0])

			// generate expected file?
			val exp = options.hasOption("exp")
			testAll(inputDir, exp)
		}
	}
}