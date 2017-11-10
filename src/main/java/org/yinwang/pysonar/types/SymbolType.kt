package org.yinwang.pysonar.types

class SymbolType(var name: String) : Type() {
	override fun typeEquals(other: Any?) = if (other is SymbolType) this.name == other.name else false
	override fun printType(ctr: Type.CyclicTypeRecorder) = ":" + name
}
