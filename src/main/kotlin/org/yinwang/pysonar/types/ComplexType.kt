package org.yinwang.pysonar.types

class ComplexType : Type() {
	override fun typeEquals(other: Any): Boolean = other is ComplexType
	override fun printType(ctr: Type.CyclicTypeRecorder): String = "float"
}
