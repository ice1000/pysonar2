package org.yinwang.pysonar.ast

class Attribute(var target: Node, var attr: Name, file: String, start: Int, end: Int) : Node(NodeType.ATTRIBUTE, file, start, end) {
	init {
		addChildren(target, attr)
	}

	override fun toString() = "<Attribute:" + start + ":" + target + "." + attr.id + ">"
}
