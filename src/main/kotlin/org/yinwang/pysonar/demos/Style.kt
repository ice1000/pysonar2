package org.yinwang.pysonar.demos

import org.yinwang.pysonar.same


/**
 * Represents a simple style run for purposes of source highlighting.
 */
class Style(var type: Type, var start: Int, var end: Int) : Comparable<Style> {
	var message: String? = null  // optional hover text
	var url: String? = null      // internal or external link
	var id: String? = null       // for hover highlight
	var highlight: List<String>? = null   // for hover highlight

	override fun equals(o: Any?): Boolean {
		if (o !is Style) {
			return false
		}
		val other = o as Style
		return (other.type == this.type
				&& other.start == this.start
				&& other.end == this.end
				&& same(other.message, this.message)
				&& same(other.url, this.url))
	}

	operator override fun compareTo(other: Style) = when {
		this == other -> 0
		this.start < other.start -> -1
		this.start > other.start -> 1
		else -> this.hashCode() - other.hashCode()
	}

	override fun toString() = "[$type start=$start end=$end]"

	enum class Type {
		KEYWORD,
		COMMENT,
		STRING,
		DOC_STRING,
		IDENTIFIER,
		BUILTIN,
		NUMBER,
		CONSTANT, // ALL_CAPS identifier
		FUNCTION, // function name
		PARAMETER, // function parameter
		LOCAL, // local variable
		DECORATOR, // function decorator
		CLASS, // class name
		ATTRIBUTE, // object attribute
		LINK, // hyperlink
		ANCHOR, // name anchor
		DELIMITER,
		TYPE_NAME, // reference to a type (e.g. function or class name)

		ERROR,
		WARNING,
		INFO
	}
}
