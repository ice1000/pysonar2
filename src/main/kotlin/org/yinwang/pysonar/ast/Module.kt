package org.yinwang.pysonar.ast

import org.yinwang.pysonar.*

class Module(var body: Block, file: String, start: Int, end: Int) : Node(NodeType.MODULE, file, start, end) {

	init {
		this.name = moduleName(file)
		addChildren(this.body)
	}

	override fun toString(): String = "(module:$file)"
}
