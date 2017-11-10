package org.yinwang.pysonar.hash


class GenericEqualFunction : EqualFunction {
	override fun equals(x: Any, y: Any) = x == y
}
