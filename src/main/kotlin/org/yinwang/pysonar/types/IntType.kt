package org.yinwang.pysonar.types

class IntType : Type() {
	override fun typeEquals(other: Any): Boolean = other is IntType
	override fun printType(ctr: Type.CyclicTypeRecorder): String = "int"
}
