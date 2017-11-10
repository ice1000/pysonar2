package org.yinwang.pysonar

import org.yinwang.pysonar.ast.Node
import org.yinwang.pysonar.types.*
import java.util.*

class State {
	var table: MutableMap<String, Set<Binding>> = HashMap(0)
	var parent: State? = null // all are non-null except global table
	var forwarding: State     // link to the closest non-class scope, for lifting functions out
	private var supers: MutableList<State>? = null
	private var globalNames: MutableSet<String>? = null
	var stateType: StateType
	lateinit var type: Type
	var path = ""

	/**
	 * Returns the global scope (i.e. the module scope for the current module).
	 */
	private val globalTable: State
		get() = getStateOfType(StateType.MODULE) ?: run {
			die("Couldn't find global table. Shouldn't happen")
			this
		}

	val isEmpty: Boolean get() = table.isEmpty()

	constructor(parent: State?, type: StateType) {
		this.parent = parent
		this.stateType = type

		this.forwarding = if (type == StateType.CLASS) parent?.forwarding ?: this else this
	}


	constructor(s: State) {
		this.table = HashMap()
		this.table.putAll(s.table)
		this.parent = s.parent
		this.stateType = s.stateType
		this.forwarding = s.forwarding
		this.supers = s.supers
		this.globalNames = s.globalNames
		this.type = s.type
		this.path = s.path
	}

	// erase and overwrite this to s's contents
	fun overwrite(s: State) {
		this.table = s.table
		this.parent = s.parent
		this.stateType = s.stateType
		this.forwarding = s.forwarding
		this.supers = s.supers
		this.globalNames = s.globalNames
		this.type = s.type
		this.path = s.path
	}

	fun copy(): State = State(this)

	fun merge(other: State) {
		for ((key, b2) in other.table) {
			val b1 = table[key]?.toMutableSet()

			if (b1 != null) {
				b1.addAll(b2)
			} else {
				table.put(key, b2)
			}
		}
	}

	fun addSuper(sup: State) {
		if (supers == null) supers = ArrayList()
		supers!!.add(sup)
	}

	fun addGlobalName(name: String) {
		if (globalNames == null) globalNames = HashSet(1)
		globalNames!!.add(name)
	}

	fun isGlobalName(name: String): Boolean {
		val globalNames = globalNames
		val parent = parent
		return when {
			globalNames != null -> name in globalNames
			parent != null -> parent.isGlobalName(name)
			else -> false
		}
	}

	fun remove(id: String) {
		table.remove(id)
	}

	// create new binding and insert
	fun insert(id: String, node: Node, type: Type, kind: Binding.Kind) {
		val b = Binding(id, node, type, kind)
		b.setQname((type as? ModuleType)?.asModuleType()?.qname ?: extendPath(id))
		update(id, b)
	}

	// directly insert a given binding
	fun update(id: String, bs: Set<Binding>): Set<Binding> {
		table.put(id, bs)
		return bs
	}

	fun update(id: String, b: Binding): Set<Binding> {
		val bs = HashSet<Binding>(1)
		bs.add(b)
		table.put(id, bs)
		return bs
	}

	/**
	 * Look up a name in the current symbol table only. Don't recurse on the
	 * parent table.
	 */
	fun lookupLocal(name: String) = table[name]

	/**
	 * Look up a name (String) in the current symbol table.  If not found,
	 * recurse on the parent table.
	 */
	fun lookup(name: String): Set<Binding>? = getModuleBindingIfGlobal(name) ?: run {
		val ent = lookupLocal(name)
		ent ?: parent?.lookup(name)
	}

	/**
	 * Look up a name in the module if it is declared as global, otherwise look
	 * it up locally.
	 */
	fun lookupScope(name: String): Set<Binding>? {
		val b = getModuleBindingIfGlobal(name)
		return b ?: lookupLocal(name)
	}

	fun lookupAttr(attr: String): Set<Binding>? {
		if (looked.contains(this)) {
			return null
		} else {
			var b = lookupLocal(attr)
			if (b != null) {
				return b
			} else {
				val supers = supers
				if (supers != null && !supers.isEmpty()) {
					looked.add(this)
					for (p in supers) {
						b = p.lookupAttr(attr)
						if (b != null) {
							looked.remove(this)
							return b
						}
					}
					looked.remove(this)
					return null
				} else {
					return null
				}
			}
		}
	}


	/**
	 * Look for a binding named `name` and if found, return its type.
	 */
	fun lookupType(name: String): Type? {
		val bs = lookup(name)
		return if (bs == null) {
			null
		} else {
			makeUnion(bs)
		}
	}


	/**
	 * Look for a attribute named `attr` and if found, return its type.
	 */
	fun lookupAttrType(attr: String): Type? {
		val bs = lookupAttr(attr)
		return if (bs == null) null else makeUnion(bs)
	}

	/**
	 * Find a symbol table of a certain type in the enclosing scopes.
	 */
	private fun getStateOfType(type: StateType): State? {
		val parent = parent
		return when {
			stateType == type -> this
			parent == null -> null
			else -> parent.getStateOfType(type)
		}
	}

	/**
	 * If `name` is declared as a global, return the module binding.
	 */
	private fun getModuleBindingIfGlobal(name: String): Set<Binding>? {
		if (isGlobalName(name)) {
			val module = globalTable
			if (module !== this) return module.lookupLocal(name)
		}
		return null
	}

	fun putAll(other: State) {
		table.putAll(other.table)
	}

	fun keySet() = table.keys

	fun values(): Collection<Binding> {
		val ret = HashSet<Binding>()
		for (bs in table.values) {
			ret.addAll(bs)
		}
		return ret
	}

	fun entrySet() = table.entries

	fun extendPath(name_: String): String {
		var name = name_
		name = moduleName(name)
		return if (path == "") {
			name
		} else path + "." + name
	}

	override fun toString() = "<State:$stateType:${table.keys}>"

	enum class StateType {
		CLASS,
		INSTANCE,
		FUNCTION,
		MODULE,
		GLOBAL,
		SCOPE
	}

	companion object {
		/**
		 * Look up an attribute in the type hierarchy.  Don't look at parent link,
		 * because the enclosing scope may not be a super class. The search is
		 * "depth first, left to right" as in Python's (old) multiple inheritance
		 * rule. The new MRO can be implemented, but will probably not introduce
		 * much difference.
		 */
		private val looked = HashSet<State>()    // circularity prevention

		fun merge(state1: State, state2: State): State {
			val ret = state1.copy()
			ret.merge(state2)
			return ret
		}

		fun makeUnion(bs: Set<Binding>): Type {
			var t: Type = Type.UNKNOWN
			for (b in bs) {
				t = UnionType.union(t, b.type)
			}
			return t
		}
	}

}
