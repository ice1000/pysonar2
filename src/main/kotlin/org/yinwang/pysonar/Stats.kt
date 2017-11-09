package org.yinwang.pysonar

import java.util.*

class Stats {
	private var contents = HashMap<String, Any>()

	fun putInt(key: String, value: Long) {
		contents.put(key, value)
	}

	@JvmOverloads
	fun inc(key: String, x: Long = 1) {
		val old = getInt(key)

		if (old == null) contents.put(key, 1) else contents.put(key, old + x)
	}

	fun getInt(key: String) = contents[key] as Long?

	fun print(): String {
		val sb = StringBuilder()
		for ((key, value) in contents) sb.append("\n- $key: $value")
		return sb.toString()
	}

}
