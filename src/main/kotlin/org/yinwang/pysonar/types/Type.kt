package org.yinwang.pysonar.types

import org.yinwang.pysonar.*

abstract class Type {
	var table = State(null, State.StateType.SCOPE)
	var file: String? = null
	val isNumType get() = this is IntType || this is FloatType
	val isUnknownType get() = this === Type.UNKNOWN

	override fun equals(other: Any?) = typeEquals(other)
	abstract fun typeEquals(other: Any?): Boolean

	/// improvement #0
	fun asModuleType(): ModuleType {
		return when {
			this is UnionType -> {
				for (t in this.types) if (t is ModuleType) return t.asModuleType()
				die("Not containing a ModuleType")
			}
			this is ModuleType -> this
			else -> die("Not a ModuleType")
		}
	}

	abstract fun printType(ctr: CyclicTypeRecorder): String
	override fun toString() = printType(CyclicTypeRecorder())

	/**
	 * Internal class to support printing in the presence of type-graph cycles.
	 */
	inner class CyclicTypeRecorder {
		private var count = 0
		private val elements = hashMapOf<Type, Int>()
		private val used = hashSetOf<Type>()

		fun push(t: Type): Int {
			count += 1
			elements.put(t, count)
			return count
		}

		fun pop(t: Type) {
			elements.remove(t)
			used.remove(t)
		}

		fun visit(t: Type) = elements[t]?.apply { used.add(t) }
		fun isUsed(t: Type) = used.contains(t)
	}

	companion object {
		@JvmStatic fun getINT() = INT
		@JvmField
		var UNKNOWN = InstanceType(ClassType("?"))
		@JvmField
		var CONT = InstanceType(ClassType("None"))
		@JvmField
		var NONE = InstanceType(ClassType("None"))
		@JvmField
		var STR = StrType(null)
		@JvmField
		var INT = IntType()
		@JvmField
		var FLOAT = FloatType()
		@JvmField
		var COMPLEX = ComplexType()
		@JvmField
		var BOOL = BoolType(BoolType.Value.Undecided)
		@JvmField
		var typeStack = TypeStack()
	}
}
