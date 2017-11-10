package org.yinwang.pysonar.types

import java.util.*

class UnionType() : Type() {
	var types: MutableSet<Type> = hashSetOf()

	val isEmpty get() = types.isEmpty()

	constructor(vararg initialTypes: Type) : this() {
		initialTypes.forEach(this::addType)
	}

	fun addType(t: Type) {
		if (t is UnionType) types.addAll(t.types) else types.add(t)
	}

	operator fun contains(t: Type) = t in types
	/// improvement
	fun firstUseful() = types.firstOrNull { !it.isUnknownType && it !== Type.NONE }

	override fun typeEquals(other: Any?): Boolean {
		if (Type.typeStack.contains(this, other!!)) return true
		else if (other is UnionType) {
			val types1 = types
			val types2 = other.types
			if (types1.size != types2.size) return false
			else {
				/// improvement
				types2
						.filterNot { it in types1 }
						.forEach { return false }
				return types1.any { it in types2 }
			}
		} else return false
	}


	override fun hashCode() = "UnionType".hashCode()
	override fun printType(ctr: Type.CyclicTypeRecorder): String {
		val sb = StringBuilder()

		val num = ctr.visit(this)
		if (num != null) {
			sb.append("#").append(num)
		} else {
			val newNum = ctr.push(this)
			var first = true
			sb.append("{")

			for (t in types) {
				if (!first) sb.append(" | ")
				sb.append(t.printType(ctr))
				first = false
			}

			if (ctr.isUsed(this)) {
				sb.append("=#").append(newNum).append(":")
			}

			sb.append("}")
			ctr.pop(this)
		}

		return sb.toString()
	}

	companion object {

		/**
		 * Returns true if t1 == t2 or t1 is a union type that contains t2.
		 */
		fun contains(t1: Type, t2: Type) = (t1 as? UnionType)?.contains(t2) ?: (t1 == t2)

		fun remove(t1: Type, t2: Type) = when {
			t1 is UnionType -> {
				val types = HashSet(t1.types)
				types.remove(t2)
				UnionType.newUnion(types)
			}
			t1 !== Type.CONT && t1 === t2 -> Type.UNKNOWN
			else -> t1
		}

		fun newUnion(types: Collection<Type>): Type {
			var t: Type = Type.UNKNOWN
			for (nt in types) {
				t = union(t, nt)
			}
			return t
		}

		// take a union of two types
		// with preference: other > None > Cont > unknown
		/// improvements
		fun union(u: Type, v: Type) = when {
			u == v -> u
			u !== Type.UNKNOWN && v === Type.UNKNOWN -> u
			v !== Type.UNKNOWN && u === Type.UNKNOWN -> v
			u !== Type.NONE && v === Type.NONE -> u
			v !== Type.NONE && v === Type.NONE -> v
			else -> UnionType(u, v)
		}

		fun union(types: Collection<Type>): Type {
			var result: Type = Type.UNKNOWN
			for (type in types) {
				result = UnionType.union(result, type)
			}
			return result
		}

		/// improvement
		fun union(vararg types: Type): Type = union(*types)
	}

}
