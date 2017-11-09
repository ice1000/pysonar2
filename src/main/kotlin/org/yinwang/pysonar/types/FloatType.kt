package org.yinwang.pysonar.types

class FloatType : Type() {
	override fun equals(other: Any?): Boolean = other is FloatType
	override fun typeEquals(other: Any): Boolean = other is FloatType
	override fun printType(ctr: Type.CyclicTypeRecorder): String = "float"
}
