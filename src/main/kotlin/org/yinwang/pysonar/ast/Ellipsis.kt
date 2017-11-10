package org.yinwang.pysonar.ast

class Ellipsis(file: String, start: Int, end: Int) : Node(NodeType.ELLIPSIS, file, start, end) {

	override fun toString(): String = "..."

}
