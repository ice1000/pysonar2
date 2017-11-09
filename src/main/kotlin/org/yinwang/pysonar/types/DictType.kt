package org.yinwang.pysonar.types

import org.yinwang.pysonar.Analyzer

class DictType(var keyType: Type, var valueType: Type) : Type() {

	init {
		table.addSuper(Analyzer.self.builtins.BaseDict.table)
		table.path = Analyzer.self.builtins.BaseDict.table.path
	}

	fun add(key: Type, value: Type) {
		keyType = UnionType.union(keyType, key)
		valueType = UnionType.union(valueType, value)
	}

	fun toTupleType(n: Int): TupleType {
		val ret = TupleType()
		for (i in 0 until n) ret.add(keyType)
		return ret
	}

	override fun typeEquals(other: Any) = when {
		Type.typeStack.contains(this, other) -> true
		other is DictType -> {
			Type.typeStack.push(this, other)
			val result = other.keyType.typeEquals(keyType) && other.valueType.typeEquals(valueType)
			Type.typeStack.pop(this, other)
			result
		}
		else -> false
	}

	override fun hashCode(): Int = "DictType".hashCode()

	override fun printType(ctr: Type.CyclicTypeRecorder): String =//		val sb = StringBuilder()
//
//		val num = ctr.visit(this)
//		if (num != null) {
//			sb.append("#").append(num)
//		} else {
//			ctr.push(this)
//			sb.append("{")
//			sb.append(keyType.printType(ctr))
//			sb.append(" : ")
//			sb.append(valueType.printType(ctr))
//			sb.append("}")
//			ctr.pop(this)
//		}
//
//		return sb.toString()
			"dict"

}
