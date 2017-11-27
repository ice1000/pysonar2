package org.yinwang.pysonar.ast

class ExtSlice(var dims: List<Node>, file: String, start: Int, end: Int) : Node(NodeType.EXTSLICE, file, start, end) {
	init {
		addChildren(dims)
	}

	override fun toString(): String = "<ExtSlice:$dims>"
}

class For(var target: Node, var iter: Node, var body: Block, var orelse: Block, isAsync: Boolean,
          file: String, start: Int, end: Int) : Node(NodeType.FOR, file, start, end) {
	var isAsync = false

	init {
		this.isAsync = isAsync
		addChildren(target, iter, body, orelse)
	}

	override fun toString() = "<For:$target:$iter:$body:$orelse>"
}

class FunctionDef(name: Name?, var args: List<Node>, var body: Node, var defaults: List<Node>,
                  var vararg: Name?  /* *args */, var kwarg: Name?   /* **kwarg */, file: String, isAsync: Boolean,
                  start: Int, end: Int) : Node(NodeType.FUNCTIONDEF, file, start, end) {
	lateinit var thisName: Name
	var afterRest: List<Node>? = null   // after rest arg of Ruby
	var called = false
	var isLamba = false
	var isAsync = false

	val argumentExpr: String
		get() {
			val argExpr = StringBuilder()
			argExpr.append("(")
			var first = true

			for (n in args) {
				if (!first) {
					argExpr.append(", ")
				}
				first = false
				argExpr.append(n.toDisplay())
			}

			if (vararg != null) {
				if (!first) {
					argExpr.append(", ")
				}
				first = false
				argExpr.append("*" + vararg!!.toDisplay())
			}

			if (kwarg != null) {
				if (!first) {
					argExpr.append(", ")
				}
				argExpr.append("**" + kwarg!!.toDisplay())
			}

			argExpr.append(")")
			return argExpr.toString()
		}

	init {
		if (name != null) {
			this.thisName = name
		} else {
			isLamba = true
			val fn = genLambdaName()
			this.thisName = Name(fn, file, start, start + "lambda".length)
			addChildren(this.thisName)
		}
		this.isAsync = isAsync
		addChildren(name)
		addChildren(args)
		addChildren(defaults)
		addChildren(vararg, kwarg, this.body)
	}

	override fun toString() = "(func:$start:$thisName)"

	companion object {
		private var lambdaCounter = 0
		fun genLambdaName() = "lambda%" + ++lambdaCounter
	}

}