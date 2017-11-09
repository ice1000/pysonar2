package org.yinwang.pysonar.types

import org.yinwang.pysonar.*

class ModuleType(var name: String, file: String?, parent: State) : Type() {
	var qname: String

	init {
		this.file = file  // null for builtin modules
		var qname_: String? = null
		if (file != null) {
			// This will return null iff specified file is not prefixed by
			// any path in the module search path -- i.e., the caller asked
			// the analyzer to load a file not in the search path.
			qname_ = moduleQname(file)
		}
		qname = qname_ ?: name
		setTable(State(parent, State.StateType.MODULE))
		table.path = qname
		table.type = this

		// null during bootstrapping of built-in types
		if (Analyzer.self.builtins != null) {
			table.addSuper(Analyzer.self.builtins.BaseModule.table)
		}
	}

	override fun hashCode(): Int = "ModuleType".hashCode()

	override fun typeEquals(other: Any): Boolean {
		if (other is ModuleType && file != null) return file == other.file
		return this === other
	}

	override fun printType(ctr: Type.CyclicTypeRecorder): String = name
}
