package org.yinwang.pysonar.types

import org.yinwang.pysonar.Analyzer
import org.yinwang.pysonar.State

class BoolType : Type {

	var value: Value
	var s1: State? = null
	var s2: State? = null

	constructor(value: Value) {
		this.value = value
	}

	constructor(s1: State?, s2: State?) {
		this.value = Value.Undecided
		this.s1 = s1
		this.s2 = s2
	}

	fun swap(): BoolType = BoolType(s2, s1)

	override fun typeEquals(other: Any): Boolean = other is BoolType

	override fun printType(ctr: Type.CyclicTypeRecorder) = if (Analyzer.self.hasOption("debug")) "bool($value)" else "bool"

	enum class Value {
		True,
		False,
		Undecided
	}
}
