package org.yinwang.pysonar.ast

class Exec(var body: Node, var globals: Node, var locals: Node, file: String, start: Int, end: Int) : Node(NodeType.EXEC, file, start, end) {

	init {
		addChildren(body, globals, locals)
	}

	override fun toString(): String = "<Exec:$start:$end>"

}
