package org.yinwang.pysonar.ast

class Bytes(value: Any, file: String, start: Int, end: Int) : Node(NodeType.BYTES, file, start, end) {
	var value: Any = value.toString()

	override fun toString(): String = "(bytes: $value)"

}
