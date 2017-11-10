package org.yinwang.pysonar.ast

/// improvement
class Alias(var names: List<Name>, var asname: Name, file: String, start: Int, end: Int) : Node(NodeType.ALIAS, file, start, end) {
	init {
		addChildren(names)
		addChildren(asname)
	}

	override fun toString() = "<Alias:$names as $asname>"
}
