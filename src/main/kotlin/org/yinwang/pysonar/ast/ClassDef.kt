package org.yinwang.pysonar.ast

import org.yinwang.pysonar.Binding
import org.yinwang.pysonar.Builtins
import org.yinwang.pysonar.State
import org.yinwang.pysonar.types.Type

class ClassDef(var theName: Name, var bases: List<Node>, var body: Node, file: String, start: Int, end: Int) : Node(NodeType.CLASSDEF, file, start, end) {

	init {
		addChildren(theName, this.body)
		addChildren(bases)
	}

	fun addSpecialAttribute(s: State, name: String, proptype: Type) {
		val b = Binding(name, Builtins.newTutUrl("classes.html"), proptype, Binding.Kind.ATTRIBUTE)
		s.update(name, b)
		b.markSynthetic()
		b.markStatic()

	}

	override fun toString(): String = "(class:" + theName.id + ":" + start + ")"

}
