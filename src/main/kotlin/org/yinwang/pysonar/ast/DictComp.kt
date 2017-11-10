package org.yinwang.pysonar.ast

class DictComp(var key: Node, var value: Node, var generators: List<Comprehension>, file: String, start: Int, end: Int) : Node(NodeType.DICTCOMP, file, start, end) {

	init {
		addChildren(key)
		addChildren(generators)
	}

	override fun toString(): String = "<DictComp:$start:$key>"

}
