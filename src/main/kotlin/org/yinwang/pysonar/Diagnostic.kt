package org.yinwang.pysonar

class Diagnostic(var file: String, var category: Category, var start: Int, var end: Int, var msg: String) {
	override fun toString() = "<Diagnostic:$file:$category:$msg>"
	enum class Category { INFO, WARNING, ERROR }
}