package org.yinwang.pysonar.types

import org.yinwang.pysonar.State
import org.yinwang.pysonar.ast.Call
import org.yinwang.pysonar.visitor.TypeInferencer

class InstanceType(var classType: Type) : Type() {
	init {
		table.stateType = State.StateType.INSTANCE
		table.addSuper(classType.table)
		table.path = classType.table.path
	}


	constructor(c: Type, call: Call, args: List<Type>, inferencer: TypeInferencer) : this(c) {
		val initFunc = table.lookupAttrType("__init__")

		if (initFunc != null && initFunc is FunType && initFunc.func != null) {
			initFunc.setSelfType(this)
			inferencer.apply(initFunc, args, null, null, null, call)
			initFunc.setSelfType(null)
		}
	}

	override fun typeEquals(other: Any?): Boolean {
		return if (other is InstanceType) {
			classType.typeEquals(other.classType)
		} else false
	}

	override fun hashCode(): Int = classType.hashCode()
	override fun printType(ctr: Type.CyclicTypeRecorder): String = (classType as ClassType).name
}
