package org.yinwang.pysonar

import org.yinwang.pysonar.ast.Node
import org.yinwang.pysonar.types.Type
import java.util.HashSet

class CallStack {
	private val stack = HashSet<Node>()

	fun push(call: Node, type: Type) = stack.add(call)
	fun pop(call: Node, type: Type) = stack.remove(call)
	fun contains(call: Node, type: Type) = stack.contains(call)
}