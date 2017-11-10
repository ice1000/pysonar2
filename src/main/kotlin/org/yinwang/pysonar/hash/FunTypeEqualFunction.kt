package org.yinwang.pysonar.hash

import org.yinwang.pysonar.types.FunType

class FunTypeEqualFunction : EqualFunction {
	override fun equals(x: Any, y: Any): Boolean {
		return if (x is FunType && y is FunType) {
			x === y || x.table.path == y.table.path
		} else {
			x == y
		}
	}
}
