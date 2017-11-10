package org.yinwang.pysonar.hash

interface HashFunction {
	fun hash(o: Any): Int
}
