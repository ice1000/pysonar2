package org.yinwang.pysonar

class Options(args: Array<String>) {
	val optionsMap = linkedMapOf<String, Any>()
	val args = arrayListOf<String>()

	init {
		var i = 0
		while (i < args.size) {
			var key = args[i]
			when {
				key.startsWith("--") -> if (i + 1 >= args.size) {
					die("option needs a value: " + key)
				} else {
					key = key.substring(2)
					val value = args[i + 1]
					if (!value.startsWith("-")) {
						optionsMap.put(key, value)
						i++
					}
				}
				key.startsWith("-") -> {
					key = key.substring(1)
					optionsMap.put(key, true)
				}
				else -> this.args.add(key)
			}
			i++
		}
	}

	operator fun get(key: String): Any = optionsMap[key] ?: die("$key not found.")

	fun hasOption(key: String): Boolean {
		val v = optionsMap[key]
		return v as? Boolean ?: false
	}

	fun put(key: String, value: Any) {
		optionsMap.put(key, value)
	}

	companion object {

		@JvmStatic
		fun main(args: Array<String>) {
			val options = Options(args)
			for (key in options.optionsMap.keys) println(key + " = " + options[key])
		}
	}

}
