package org.yinwang.pysonar.types

import org.yinwang.pysonar.Analyzer

class StrType(var value: String?) : Type() {
	override fun typeEquals(other: Any?): Boolean = other is StrType
	override fun printType(ctr: Type.CyclicTypeRecorder) =
			if (Analyzer.self.hasOption("debug") && value != null) "str($value)" else "str"
}
