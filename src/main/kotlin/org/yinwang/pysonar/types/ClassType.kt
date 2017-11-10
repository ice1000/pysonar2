package org.yinwang.pysonar.types

import org.yinwang.pysonar.State

class ClassType(var name: String, parent: State? = null) : Type() {
	var canon: InstanceType? = null
		get() {
			if (field == null)
				field = InstanceType(this)
			return field
		}

	var superclass: Type? = null

	init {
		table = State(parent, State.StateType.CLASS)
		table.type = this
		if (parent != null) table.path = parent.extendPath(name) else table.path = name
	}

	constructor(name: String, parent: State? = null, superClass: ClassType? = null) : this(name, parent) {
		if (superClass != null) addSuper(superClass)
	}

	fun addSuper(superclass: Type) {
		this.superclass = superclass
		table.addSuper(superclass.table)
	}

	override fun typeEquals(other: Any?) = if (other is ClassType) canon === other.canon else false

	override fun printType(ctr: Type.CyclicTypeRecorder): String {
		val sb = StringBuilder()
		sb.append("<").append(name).append(">")
		return sb.toString()
	}
}
