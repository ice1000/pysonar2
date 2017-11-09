package org.yinwang.pysonar

class TypeStack {
	private val stack = arrayListOf<Pair<Any, Any>>()

	fun push(first: Any, second: Any) {
		stack += first to second
	}

	fun pop(first: Any, second: Any) {
		stack.removeAt(stack.size - 1)
	}

	fun contains(first: Any, second: Any): Boolean =
			stack.any { (a, b) -> a == first && b == second || a == second && b == first }
}
