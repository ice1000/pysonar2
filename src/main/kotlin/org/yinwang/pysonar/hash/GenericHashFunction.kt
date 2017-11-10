package org.yinwang.pysonar.hash


class GenericHashFunction : HashFunction {
	override fun hash(o: Any) = o.hashCode()
}
