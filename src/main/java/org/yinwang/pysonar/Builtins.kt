package org.yinwang.pysonar

import org.yinwang.pysonar.Binding.Kind.*
import org.yinwang.pysonar.ast.Url
import org.yinwang.pysonar.types.*
import org.yinwang.pysonar.types.Type.Companion.INT
import org.yinwang.pysonar.types.Type.Companion.NONE
import org.yinwang.pysonar.types.Type.Companion.STR
import org.yinwang.pysonar.types.Type.Companion.UNKNOWN
import java.util.*

@Suppress("FunctionName", "LocalVariableName")
/**
 * This file is messy. Should clean up.
 */
class Builtins {
	// XXX:  need to model "types" module and reconcile with these types
	lateinit var Builtin: ModuleType
	lateinit var objectType: ClassType
	lateinit var BaseType: ClassType
	lateinit var BaseList: ClassType
	lateinit private var BaseListInst: InstanceType
	lateinit var BaseArray: ClassType
	lateinit var BaseDict: ClassType
	lateinit var BaseTuple: ClassType
	lateinit var BaseModule: ClassType
	lateinit var BaseFile: ClassType
	lateinit var BaseFileInst: InstanceType
	lateinit var BaseException: ClassType
	lateinit var BaseStruct: ClassType
	lateinit var BaseFunction: ClassType  // models functions, lambas and methods
	lateinit private var BaseClass: ClassType  // models classes and instances
	lateinit var Datetime_datetime: ClassType
	lateinit var Datetime_date: ClassType
	lateinit var Datetime_time: ClassType
	lateinit var Datetime_timedelta: ClassType
	lateinit var Datetime_tzinfo: ClassType
	lateinit var Time_struct_time: InstanceType
	internal var builtin_exception_types = arrayOf("ArithmeticError", "AssertionError", "AttributeError", "BaseException", "Exception", "DeprecationWarning", "EOFError", "EnvironmentError", "FloatingPointError", "FutureWarning", "GeneratorExit", "IOError", "ImportError", "ImportWarning", "IndentationError", "IndexError", "KeyError", "KeyboardInterrupt", "LookupError", "MemoryError", "NameError", "NotImplemented", "NotImplementedError", "OSError", "OverflowError", "PendingDeprecationWarning", "ReferenceError", "RuntimeError", "RuntimeWarning", "StandardError", "StopIteration", "SyntaxError", "SyntaxWarning", "SystemError", "SystemExit", "TabError", "TypeError", "UnboundLocalError", "UnicodeDecodeError", "UnicodeEncodeError", "UnicodeError", "UnicodeTranslateError", "UnicodeWarning", "UserWarning", "ValueError", "Warning", "ZeroDivisionError")
	/**
	 * The set of top-level native modules.
	 */
	private val modules = HashMap<String, NativeModule>()

	init {
		buildTypes()
	}

	private fun newClass(name: String, table: State) = newClass(name, table, null)
	internal fun newClass(name: String, table: State?, superClass: ClassType?, vararg moreSupers: ClassType): ClassType {
		val t = ClassType(name, table, superClass)
		for (c in moreSupers) {
			t.addSuper(c)
		}
		return t
	}

	internal fun newModule(name: String) = ModuleType(name, null, Analyzer.self.globaltable)
	internal fun newException(name: String, t: State?) = newClass(name, t, BaseException)
	internal fun newFunc() = FunType()
	internal fun newFunc(type: Type?) = FunType(UNKNOWN, type ?: Type.UNKNOWN)
	@JvmOverloads internal fun newList(type: Type = UNKNOWN): ListType = ListType(type)
	internal fun newDict(ktype: Type, vtype: Type): DictType = DictType(ktype, vtype)
	internal fun newTuple(vararg types: Type): TupleType = TupleType(*types)
	internal fun newUnion(vararg types: Type): UnionType = UnionType(*types)
	internal fun list(vararg names: String): Array<out String> = names
	private fun buildTypes() {
		BuiltinsModule()
		val bt = Builtin.table
		objectType = newClass("object", bt)
		BaseType = newClass("type", bt, objectType)
		BaseTuple = newClass("tuple", bt, objectType)
		BaseList = newClass("list", bt, objectType)
		BaseListInst = InstanceType(BaseList)
		BaseArray = newClass("array", bt)
		BaseDict = newClass("dict", bt, objectType)
		val numClass = newClass("int", bt, objectType)
		BaseModule = newClass("module", bt)
		BaseFile = newClass("file", bt, objectType)
		BaseFileInst = InstanceType(BaseFile)
		BaseFunction = newClass("function", bt, objectType)
		BaseClass = newClass("classobj", bt, objectType)
	}

	fun init() {
		buildObjectType()
		buildTupleType()
		buildArrayType()
		buildListType()
		buildDictType()
		buildNumTypes()
		buildStrType()
		buildModuleType()
		buildFileType()
		buildFunctionType()
		buildClassType()
		modules["__builtin__"]?.initBindings()  // eagerly load these bindings
		ArrayModule()
		AudioopModule()
		BinasciiModule()
		Bz2Module()
		CPickleModule()
		CStringIOModule()
		CMathModule()
		CollectionsModule()
		CryptModule()
		CTypesModule()
		DatetimeModule()
		DbmModule()
		ErrnoModule()
		ExceptionsModule()
		FcntlModule()
		FpectlModule()
		GcModule()
		GdbmModule()
		GrpModule()
		ImpModule()
		ItertoolsModule()
		MarshalModule()
		MathModule()
		Md5Module()
		MmapModule()
		NisModule()
		OperatorModule()
		OsModule()
		ParserModule()
		PosixModule()
		PwdModule()
		PyexpatModule()
		ReadlineModule()
		ResourceModule()
		SelectModule()
		SignalModule()
		ShaModule()
		SpwdModule()
		StropModule()
		StructModule()
		SysModule()
		SyslogModule()
		TermiosModule()
		ThreadModule()
		TimeModule()
		UnicodedataModule()
		ZipimportModule()
		ZlibModule()
	}

	/**
	 * Loads (if necessary) and returns the specified built-in module.
	 */
	operator fun get(name: String): ModuleType? {
		if ("." !in name) {  // unqualified
			return getModule(name)
		}
		val mods = name.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
		val type: Type = getModule(mods[0]) ?: return null
		for (i in 1 until mods.size) {
			type.table.lookupType(mods[i]) as? ModuleType ?: return null
		}
		return type as ModuleType
	}

	private fun getModule(name: String): ModuleType? {
		val wrap = modules[name]
		return wrap?.getModule()
	}

	private fun buildObjectType() {
		val obj_methods = arrayOf("__delattr__", "__format__", "__getattribute__", "__hash__", "__init__", "__new__", "__reduce__", "__reduce_ex__", "__repr__", "__setattr__", "__sizeof__", "__str__", "__subclasshook__")
		for (m in obj_methods) {
			objectType.table.insert(m, newLibUrl("stdtypes"), newFunc(), METHOD)
		}
		objectType.table.insert("__doc__", newLibUrl("stdtypes"), STR, CLASS)
		objectType.table.insert("__class__", newLibUrl("stdtypes"), UNKNOWN, CLASS)
	}

	private fun buildTupleType() {
		val bt = BaseTuple.table
		val tuple_methods = arrayOf("__add__", "__contains__", "__eq__", "__ge__", "__getnewargs__", "__gt__", "__iter__", "__le__", "__len__", "__lt__", "__mul__", "__ne__", "__new__", "__rmul__", "count", "index")
		for (m in tuple_methods) {
			bt.insert(m, newLibUrl("stdtypes"), newFunc(), METHOD)
		}
		bt.insert("__getslice__", newDataModelUrl("object.__getslice__"), newFunc(), METHOD)
		bt.insert("__getitem__", newDataModelUrl("object.__getitem__"), newFunc(), METHOD)
		bt.insert("__iter__", newDataModelUrl("object.__iter__"), newFunc(), METHOD)
	}

	private fun buildArrayType() {
		val array_methods_none = arrayOf("append", "buffer_info", "byteswap", "extend", "fromfile", "fromlist", "fromstring", "fromunicode", "index", "insert", "pop", "read", "remove", "reverse", "tofile", "tolist", "typecode", "write")
		for (m in array_methods_none) {
			BaseArray.table.insert(m, newLibUrl("array"), newFunc(NONE), METHOD)
		}
		val array_methods_num = arrayOf("count", "itemsize")
		for (m in array_methods_num) {
			BaseArray.table.insert(m, newLibUrl("array"), newFunc(INT), METHOD)
		}
		val array_methods_str = arrayOf("tostring", "tounicode")
		for (m in array_methods_str) {
			BaseArray.table.insert(m, newLibUrl("array"), newFunc(STR), METHOD)
		}
	}

	private fun buildListType() {
		BaseList.table.insert("__getslice__", newDataModelUrl("object.__getslice__"),
				newFunc(BaseListInst), METHOD)
		BaseList.table.insert("__getitem__", newDataModelUrl("object.__getitem__"),
				newFunc(BaseList), METHOD)
		BaseList.table.insert("__iter__", newDataModelUrl("object.__iter__"),
				newFunc(BaseList), METHOD)
		val list_methods_none = arrayOf("append", "extend", "index", "insert", "pop", "remove", "reverse", "sort")
		for (m in list_methods_none) {
			BaseList.table.insert(m, newLibUrl("stdtypes"), newFunc(NONE), METHOD)
		}
		val list_methods_num = arrayOf("count")
		for (m in list_methods_num) {
			BaseList.table.insert(m, newLibUrl("stdtypes"), newFunc(INT), METHOD)
		}
	}

	private fun numUrl(): Url = newLibUrl("stdtypes", "typesnumeric")
	private fun buildNumTypes() {
		val bft = Type.FLOAT.table
		val float_methods_num = arrayOf("__abs__", "__add__", "__coerce__", "__div__", "__divmod__", "__eq__", "__float__", "__floordiv__", "__format__", "__ge__", "__getformat__", "__gt__", "__int__", "__le__", "__long__", "__lt__", "__mod__", "__mul__", "__ne__", "__neg__", "__new__", "__nonzero__", "__pos__", "__pow__", "__radd__", "__rdiv__", "__rdivmod__", "__rfloordiv__", "__rmod__", "__rmul__", "__rpow__", "__rsub__", "__rtruediv__", "__setformat__", "__sub__", "__truediv__", "__trunc__", "as_integer_ratio", "fromhex", "is_integer")
		for (m in float_methods_num) {
			bft.insert(m, numUrl(), newFunc(Type.FLOAT), METHOD)
		}
		val bnt = INT.table
		val num_methods_num = arrayOf("__abs__", "__add__", "__and__", "__class__", "__cmp__", "__coerce__", "__delattr__", "__div__", "__divmod__", "__doc__", "__float__", "__floordiv__", "__getattribute__", "__getnewargs__", "__hash__", "__hex__", "__index__", "__init__", "__int__", "__invert__", "__long__", "__lshift__", "__mod__", "__mul__", "__neg__", "__new__", "__nonzero__", "__oct__", "__or__", "__pos__", "__pow__", "__radd__", "__rand__", "__rdiv__", "__rdivmod__", "__reduce__", "__reduce_ex__", "__repr__", "__rfloordiv__", "__rlshift__", "__rmod__", "__rmul__", "__ror__", "__rpow__", "__rrshift__", "__rshift__", "__rsub__", "__rtruediv__", "__rxor__", "__setattr__", "__str__", "__sub__", "__truediv__", "__xor__")
		for (m in num_methods_num) {
			bnt.insert(m, numUrl(), newFunc(INT), METHOD)
		}
		bnt.insert("__getnewargs__", numUrl(), newFunc(newTuple(INT)), METHOD)
		bnt.insert("hex", numUrl(), newFunc(STR), METHOD)
		bnt.insert("conjugate", numUrl(), newFunc(Type.COMPLEX), METHOD)
		val bct = Type.COMPLEX.table
		val complex_methods = arrayOf("__abs__", "__add__", "__div__", "__divmod__", "__float__", "__floordiv__", "__format__", "__getformat__", "__int__", "__long__", "__mod__", "__mul__", "__neg__", "__new__", "__pos__", "__pow__", "__radd__", "__rdiv__", "__rdivmod__", "__rfloordiv__", "__rmod__", "__rmul__", "__rpow__", "__rsub__", "__rtruediv__", "__sub__", "__truediv__", "conjugate")
		for (c in complex_methods) {
			bct.insert(c, numUrl(), newFunc(Type.COMPLEX), METHOD)
		}
		val complex_methods_num = arrayOf("__eq__", "__ge__", "__gt__", "__le__", "__lt__", "__ne__", "__nonzero__", "__coerce__")
		for (cn in complex_methods_num) {
			bct.insert(cn, numUrl(), newFunc(INT), METHOD)
		}
		bct.insert("__getnewargs__", numUrl(), newFunc(newTuple(Type.COMPLEX)), METHOD)
		bct.insert("imag", numUrl(), INT, ATTRIBUTE)
		bct.insert("real", numUrl(), INT, ATTRIBUTE)
	}

	private fun buildStrType() {
		STR.table.insert("__getslice__", newDataModelUrl("object.__getslice__"),
				newFunc(STR), METHOD)
		STR.table.insert("__getitem__", newDataModelUrl("object.__getitem__"),
				newFunc(STR), METHOD)
		STR.table.insert("__iter__", newDataModelUrl("object.__iter__"),
				newFunc(STR), METHOD)
		val str_methods_str = arrayOf("capitalize", "center", "decode", "encode", "expandtabs", "format", "index", "join", "ljust", "lower", "lstrip", "partition", "replace", "rfind", "rindex", "rjust", "rpartition", "rsplit", "rstrip", "strip", "swapcase", "title", "translate", "upper", "zfill")
		for (m in str_methods_str) {
			STR.table.insert(m, newLibUrl("stdtypes.html#str." + m),
					newFunc(STR), METHOD)
		}
		val str_methods_num = arrayOf("count", "isalnum", "isalpha", "isdigit", "islower", "isspace", "istitle", "isupper", "find", "startswith", "endswith")
		for (m in str_methods_num) {
			STR.table.insert(m, newLibUrl("stdtypes.html#str." + m),
					newFunc(INT), METHOD)
		}
		val str_methods_list = arrayOf("split", "splitlines")
		for (m in str_methods_list) {
			STR.table.insert(m, newLibUrl("stdtypes.html#str." + m),
					newFunc(newList(STR)), METHOD)
		}
		STR.table.insert("partition", newLibUrl("stdtypes"),
				newFunc(newTuple(STR)), METHOD)
	}

	private fun buildModuleType() {
		val attrs = arrayOf("__doc__", "__file__", "__name__", "__package__")
		for (m in attrs) {
			BaseModule.table.insert(m, newTutUrl("modules.html"), STR, ATTRIBUTE)
		}
		BaseModule.table.insert("__dict__", newLibUrl("stdtypes", "modules"),
				newDict(STR, UNKNOWN), ATTRIBUTE)
	}

	private fun buildDictType() {
		val url = "datastructures.html#dictionaries"
		val bt = BaseDict.table
		bt.insert("__getitem__", newTutUrl(url), newFunc(), METHOD)
		bt.insert("__iter__", newTutUrl(url), newFunc(), METHOD)
		bt.insert("get", newTutUrl(url), newFunc(), METHOD)
		bt.insert("items", newTutUrl(url),
				newFunc(newList(newTuple(UNKNOWN, UNKNOWN))), METHOD)
		bt.insert("keys", newTutUrl(url), newFunc(BaseList), METHOD)
		bt.insert("values", newTutUrl(url), newFunc(BaseList), METHOD)
		val dict_method_unknown = arrayOf("clear", "copy", "fromkeys", "get", "iteritems", "iterkeys", "itervalues", "pop", "popitem", "setdefault", "update")
		for (m in dict_method_unknown) {
			bt.insert(m, newTutUrl(url), newFunc(), METHOD)
		}
		val dict_method_num = arrayOf("has_key")
		for (m in dict_method_num) {
			bt.insert(m, newTutUrl(url), newFunc(INT), METHOD)
		}
	}

	private fun buildFileType() {
		val url = "stdtypes.html#bltin-file-objects"
		val table = BaseFile.table
		val methods_unknown = arrayOf("__enter__", "__exit__", "__iter__", "flush", "readinto", "truncate")
		for (m in methods_unknown) {
			table.insert(m, newLibUrl(url), newFunc(), METHOD)
		}
		val methods_str = arrayOf("next", "read", "readline")
		for (m in methods_str) {
			table.insert(m, newLibUrl(url), newFunc(STR), METHOD)
		}
		val num = arrayOf("fileno", "isatty", "tell")
		for (m in num) {
			table.insert(m, newLibUrl(url), newFunc(INT), METHOD)
		}
		val methods_none = arrayOf("close", "seek", "write", "writelines")
		for (m in methods_none) {
			table.insert(m, newLibUrl(url), newFunc(NONE), METHOD)
		}
		table.insert("readlines", newLibUrl(url), newFunc(newList(STR)), METHOD)
		table.insert("xreadlines", newLibUrl(url), newFunc(STR), METHOD)
		table.insert("closed", newLibUrl(url), INT, ATTRIBUTE)
		table.insert("encoding", newLibUrl(url), STR, ATTRIBUTE)
		table.insert("errors", newLibUrl(url), UNKNOWN, ATTRIBUTE)
		table.insert("mode", newLibUrl(url), INT, ATTRIBUTE)
		table.insert("name", newLibUrl(url), STR, ATTRIBUTE)
		table.insert("softspace", newLibUrl(url), INT, ATTRIBUTE)
		table.insert("newlines", newLibUrl(url), newUnion(STR, newTuple(STR)), ATTRIBUTE)
	}

	private fun buildFunctionType() {
		val t = BaseFunction.table
		for (s in list("func_doc", "__doc__", "func_name", "__name__", "__module__")) {
			t.insert(s, Url(DATAMODEL_URL), STR, ATTRIBUTE)
		}
		t.insert("func_closure", Url(DATAMODEL_URL), newTuple(), ATTRIBUTE)
		t.insert("func_code", Url(DATAMODEL_URL), UNKNOWN, ATTRIBUTE)
		t.insert("func_defaults", Url(DATAMODEL_URL), newTuple(), ATTRIBUTE)
		t.insert("func_globals", Url(DATAMODEL_URL), DictType(STR, UNKNOWN),
				ATTRIBUTE)
		t.insert("func_dict", Url(DATAMODEL_URL), DictType(STR, UNKNOWN), ATTRIBUTE)
		// Assume any function can become a method, for simplicity.
		for (s in list("__func__", "im_func")) {
			t.insert(s, Url(DATAMODEL_URL), FunType(), METHOD)
		}
	}

	// XXX:  finish wiring this up.  ClassType needs to inherit from it somehow,
	// so we can remove the per-instance attributes from NClassDef.
	private fun buildClassType() {
		val t = BaseClass.table
		for (s in list("__name__", "__doc__", "__module__")) {
			t.insert(s, Url(DATAMODEL_URL), STR, ATTRIBUTE)
		}
		t.insert("__dict__", Url(DATAMODEL_URL), DictType(STR, UNKNOWN), ATTRIBUTE)
	}

	internal abstract inner class NativeModule internal constructor(protected var name: String) {
		protected var module: ModuleType? = null
		protected var table: State? = null  // the module's symbol table

		init {
			modules.put(name, this)
		}

		/**
		 * Lazily load the module.
		 */
		internal fun getModule(): ModuleType? {
			if (module == null) {
				createModuleType()
				initBindings()
			}
			return module
		}

		abstract fun initBindings()

		private fun createModuleType() {
			newModule(name).let {
				module = it
				table = it.table
				Analyzer.self.moduleTable.insert(name, liburl(), it, MODULE)
			}
		}

		protected fun update(name: String, url: Url, type: Type, kind: Binding.Kind) {
			table?.insert(name, url, type, kind)
		}

		protected fun addClass(name: String, url: Url, type: Type) {
			table?.insert(name, url, type, CLASS)
		}

		protected fun addMethod(name: String, url: Url, type: Type) {
			table?.insert(name, url, type, METHOD)
		}

		protected fun addFunction(name: String, url: Url, type: Type?) {
			table?.insert(name, url, newFunc(type), FUNCTION)
		}

		// don't use this unless you're sure it's OK to share the type object
		protected fun addFunctions_beCareful(type: Type, vararg names: String) {
			for (name in names) {
				addFunction(name, liburl(), type)
			}
		}

		protected fun addNoneFuncs(vararg names: String) {
			addFunctions_beCareful(NONE, *names)
		}

		protected fun addNumFuncs(vararg names: String) {
			addFunctions_beCareful(INT, *names)
		}

		protected fun addStrFuncs(vararg names: String) {
			addFunctions_beCareful(STR, *names)
		}

		protected fun addUnknownFuncs(vararg names: String) {
			for (name in names) {
				addFunction(name, liburl(), UNKNOWN)
			}
		}

		protected fun addAttr(name: String, url: Url, type: Type) {
			table?.insert(name, url, type, ATTRIBUTE)
		}

		// don't use this unless you're sure it's OK to share the type object
		private fun addAttributes_beCareful(type: Type, vararg names: String) = names.forEach { name -> addAttr(name, liburl(), type) }

		protected fun addNumAttrs(vararg names: String) = addAttributes_beCareful(INT, *names)
		protected fun addStrAttrs(vararg names: String) = addAttributes_beCareful(STR, *names)
		protected fun addUnknownAttrs(vararg names: String) = names.forEach { name -> addAttr(name, liburl(), UNKNOWN) }
		protected open fun liburl() = newLibUrl(name)
		protected open fun liburl(anchor: String) = newLibUrl(name, anchor)
		override fun toString() = if (module == null) "<Non-loaded builtin module '$name'>" else "<NativeModule:$module>"
	}

	internal inner class BuiltinsModule : NativeModule("__builtin__") {
		init {
			newModule(name).let {
				module = it
				Builtin = it
				table = it.table
			}
		}

		override fun initBindings() {
			Analyzer.self.moduleTable.insert(name, liburl(), module!!, MODULE)
			table?.addSuper(BaseModule.table)
			addClass("None", newLibUrl("constants"), NONE)
			addFunction("bool", newLibUrl("functions", "bool"), Type.BOOL)
			addFunction("complex", newLibUrl("functions", "complex"), Type.COMPLEX)
			addClass("dict", newLibUrl("stdtypes", "typesmapping"), BaseDict)
			addFunction("file", newLibUrl("functions", "file"), BaseFileInst)
			addFunction("int", newLibUrl("functions", "int"), INT)
			addFunction("long", newLibUrl("functions", "long"), INT)
			addFunction("float", newLibUrl("functions", "float"), Type.FLOAT)
			addFunction("list", newLibUrl("functions", "list"), InstanceType(BaseList))
			addFunction("object", newLibUrl("functions", "object"), InstanceType(objectType))
			addFunction("str", newLibUrl("functions", "str"), STR)
			addFunction("tuple", newLibUrl("functions", "tuple"), InstanceType(BaseTuple))
			addFunction("type", newLibUrl("functions", "type"), InstanceType(BaseType))
			// XXX:  need to model the following as built-in class types:
			//   basestring, bool, buffer, frozenset, property, set, slice,
			//   staticmethod, super and unicode
			val builtin_func_unknown = arrayOf("apply", "basestring", "callable", "classmethod", "coerce", "compile", "copyright", "credits", "delattr", "enumerate", "eval", "execfile", "exit", "filter", "frozenset", "getattr", "help", "input", "intern", "iter", "license", "long", "property", "quit", "raw_input", "reduce", "reload", "reversed", "set", "setattr", "slice", "sorted", "staticmethod", "super", "type", "unichr", "unicode")
			for (f in builtin_func_unknown) {
				addFunction(f, newLibUrl("functions.html#" + f), UNKNOWN)
			}
			val builtin_func_num = arrayOf("abs", "all", "any", "cmp", "coerce", "divmod", "hasattr", "hash", "id", "isinstance", "issubclass", "len", "max", "min", "ord", "pow", "round", "sum")
			for (f in builtin_func_num) {
				addFunction(f, newLibUrl("functions.html#" + f), INT)
			}
			for (f in list("hex", "oct", "repr", "chr")) {
				addFunction(f, newLibUrl("functions.html#" + f), STR)
			}
			addFunction("dir", newLibUrl("functions", "dir"), newList(STR))
			addFunction("map", newLibUrl("functions", "map"), newList(UNKNOWN))
			addFunction("range", newLibUrl("functions", "range"), newList(INT))
			addFunction("xrange", newLibUrl("functions", "range"), newList(INT))
			addFunction("buffer", newLibUrl("functions", "buffer"), newList(UNKNOWN))
			addFunction("zip", newLibUrl("functions", "zip"), newList(newTuple(UNKNOWN)))

			for (f in list("globals", "vars", "locals")) {
				addFunction(f, newLibUrl("functions.html#" + f), newDict(STR, UNKNOWN))
			}
			for (f in builtin_exception_types) {
				addClass(f, newDataModelUrl("org/yinwang/pysonar/types"),
						newClass(f, Analyzer.self.globaltable, objectType))
			}
			BaseException = table?.lookupType("BaseException") as ClassType
			for (f in list("True", "False")) {
				addAttr(f, newDataModelUrl("org/yinwang/pysonar/types"), Type.BOOL)
			}
			addAttr("None", newDataModelUrl("org/yinwang/pysonar/types"), NONE)
			addFunction("open", newTutUrl("inputoutput.html#reading-and-writing-files"), BaseFileInst)
			addFunction("__import__", newLibUrl("functions"), newModule("<?>"))
			Analyzer.self.globaltable.insert("__builtins__", liburl(), module!!, ATTRIBUTE)
			Analyzer.self.globaltable.putAll(table!!)
		}
	}

	internal inner class ArrayModule : NativeModule("array") {
		override fun initBindings() {
			addClass("array", newLibUrl("array", "array"), BaseArray)
			addClass("ArrayType", newLibUrl("array", "ArrayType"), BaseArray)
		}
	}

	internal inner class AudioopModule : NativeModule("audioop") {
		override fun initBindings() {
			addClass("error", liburl(), newException("error", table))
			addStrFuncs("add", "adpcm2lin", "alaw2lin", "bias", "lin2alaw", "lin2lin",
					"lin2ulaw", "mul", "reverse", "tomono", "ulaw2lin")
			addNumFuncs("avg", "avgpp", "cross", "findfactor", "findmax",
					"getsample", "max", "maxpp", "rms")
			for (s in list("adpcm2lin", "findfit", "lin2adpcm", "minmax", "ratecv")) {
				addFunction(s, liburl(), newTuple())
			}
		}
	}

	internal inner class BinasciiModule : NativeModule("binascii") {
		override fun initBindings() {
			addStrFuncs(
					"a2b_uu", "b2a_uu", "a2b_base64", "b2a_base64", "a2b_qp",
					"b2a_qp", "a2b_hqx", "rledecode_hqx", "rlecode_hqx", "b2a_hqx",
					"b2a_hex", "hexlify", "a2b_hex", "unhexlify")
			addNumFuncs("crc_hqx", "crc32")
			addClass("Error", liburl(), newException("Error", table))
			addClass("Incomplete", liburl(), newException("Incomplete", table))
		}
	}

	internal inner class Bz2Module : NativeModule("bz2") {

		override fun initBindings() {
			val bz2 = newClass("BZ2File", table, BaseFile)  // close enough.
			addClass("BZ2File", liburl(), bz2)
			val bz2c = newClass("BZ2Compressor", table, objectType)
			bz2c.table.insert("compress", newLibUrl("bz2", "sequential-de-compression"),
					newFunc(STR), METHOD)
			bz2c.table.insert("flush", newLibUrl("bz2", "sequential-de-compression"),
					newFunc(NONE), METHOD)
			addClass("BZ2Compressor", newLibUrl("bz2", "sequential-de-compression"), bz2c)
			val bz2d = newClass("BZ2Decompressor", table, objectType)
			bz2d.table.insert("decompress", newLibUrl("bz2", "sequential-de-compression"),
					newFunc(STR), METHOD)
			addClass("BZ2Decompressor", newLibUrl("bz2", "sequential-de-compression"), bz2d)
			addFunction("compress", newLibUrl("bz2", "one-shot-de-compression"), STR)
			addFunction("decompress", newLibUrl("bz2", "one-shot-de-compression"), STR)
		}
	}

	internal inner class CPickleModule : NativeModule("cPickle") {

		override fun liburl(): Url = newLibUrl("pickle", "module-cPickle")

		override fun initBindings() {
			addUnknownFuncs("dump", "load", "dumps", "loads")
			addClass("PickleError", liburl(), newException("PickleError", table))
			val picklingError = newException("PicklingError", table)
			addClass("PicklingError", liburl(), picklingError)
			update("UnpickleableError", liburl(),
					newClass("UnpickleableError", table, picklingError), CLASS)
			val unpicklingError = newException("UnpicklingError", table)
			addClass("UnpicklingError", liburl(), unpicklingError)
			update("BadPickleGet", liburl(),
					newClass("BadPickleGet", table, unpicklingError), CLASS)
			val pickler = newClass("Pickler", table, objectType)
			pickler.table.insert("dump", liburl(), newFunc(), METHOD)
			pickler.table.insert("clear_memo", liburl(), newFunc(), METHOD)
			addClass("Pickler", liburl(), pickler)
			val unpickler = newClass("Unpickler", table, objectType)
			unpickler.table.insert("load", liburl(), newFunc(), METHOD)
			unpickler.table.insert("noload", liburl(), newFunc(), METHOD)
			addClass("Unpickler", liburl(), unpickler)
		}
	}

	internal inner class CStringIOModule : NativeModule("cStringIO") {

		override fun liburl(): Url = newLibUrl("stringio")

		override fun liburl(anchor: String): Url = newLibUrl("stringio", anchor)

		override fun initBindings() {
			val StringIO = newClass("StringIO", table, BaseFile)
			addFunction("StringIO", liburl(), InstanceType(StringIO))
			addAttr("InputType", liburl(), BaseType)
			addAttr("OutputType", liburl(), BaseType)
			addAttr("cStringIO_CAPI", liburl(), UNKNOWN)
		}
	}

	internal inner class CMathModule : NativeModule("cmath") {

		override fun initBindings() {
			addFunction("phase", liburl("conversions-to-and-from-polar-coordinates"), INT)
			addFunction("polar", liburl("conversions-to-and-from-polar-coordinates"),
					newTuple(INT, INT))
			addFunction("rect", liburl("conversions-to-and-from-polar-coordinates"),
					Type.COMPLEX)
			for (plf in list("exp", "log", "log10", "sqrt")) {
				addFunction(plf, liburl("power-and-logarithmic-functions"), INT)
			}
			for (tf in list("acos", "asin", "atan", "cos", "sin", "tan")) {
				addFunction(tf, liburl("trigonometric-functions"), INT)
			}
			for (hf in list("acosh", "asinh", "atanh", "cosh", "sinh", "tanh")) {
				addFunction(hf, liburl("hyperbolic-functions"), Type.COMPLEX)
			}
			for (cf in list("isinf", "isnan")) {
				addFunction(cf, liburl("classification-functions"), Type.BOOL)
			}
			for (c in list("pi", "e")) {
				addAttr(c, liburl("constants"), INT)
			}
		}
	}

	internal inner class CollectionsModule : NativeModule("collections") {

		private fun abcUrl(): Url = liburl("abcs-abstract-base-classes")

		private fun dequeUrl(): Url = liburl("deque-objects")

		override fun initBindings() {
			val callable = newClass("Callable", table, objectType)
			callable.table.insert("__call__", abcUrl(), newFunc(), METHOD)
			addClass("Callable", abcUrl(), callable)
			val iterableType = newClass("Iterable", table, objectType)
			iterableType.table.insert("__next__", abcUrl(), newFunc(), METHOD)
			iterableType.table.insert("__iter__", abcUrl(), newFunc(), METHOD)
			addClass("Iterable", abcUrl(), iterableType)
			val Hashable = newClass("Hashable", table, objectType)
			Hashable.table.insert("__hash__", abcUrl(), newFunc(INT), METHOD)
			addClass("Hashable", abcUrl(), Hashable)
			val Sized = newClass("Sized", table, objectType)
			Sized.table.insert("__len__", abcUrl(), newFunc(INT), METHOD)
			addClass("Sized", abcUrl(), Sized)
			val containerType = newClass("Container", table, objectType)
			containerType.table.insert("__contains__", abcUrl(), newFunc(INT), METHOD)
			addClass("Container", abcUrl(), containerType)
			val iteratorType = newClass("Iterator", table, iterableType)
			addClass("Iterator", abcUrl(), iteratorType)
			val sequenceType = newClass("Sequence", table, Sized, iterableType, containerType)
			sequenceType.table.insert("__getitem__", abcUrl(), newFunc(), METHOD)
			sequenceType.table.insert("reversed", abcUrl(), newFunc(sequenceType), METHOD)
			sequenceType.table.insert("index", abcUrl(), newFunc(INT), METHOD)
			sequenceType.table.insert("count", abcUrl(), newFunc(INT), METHOD)
			addClass("Sequence", abcUrl(), sequenceType)
			val mutableSequence = newClass("MutableSequence", table, sequenceType)
			mutableSequence.table.insert("__setitem__", abcUrl(), newFunc(), METHOD)
			mutableSequence.table.insert("__delitem__", abcUrl(), newFunc(), METHOD)
			addClass("MutableSequence", abcUrl(), mutableSequence)
			val setType = newClass("Set", table, Sized, iterableType, containerType)
			setType.table.insert("__getitem__", abcUrl(), newFunc(), METHOD)
			addClass("Set", abcUrl(), setType)
			val mutableSet = newClass("MutableSet", table, setType)
			mutableSet.table.insert("add", abcUrl(), newFunc(), METHOD)
			mutableSet.table.insert("discard", abcUrl(), newFunc(), METHOD)
			addClass("MutableSet", abcUrl(), mutableSet)
			val mapping = newClass("Mapping", table, Sized, iterableType, containerType)
			mapping.table.insert("__getitem__", abcUrl(), newFunc(), METHOD)
			addClass("Mapping", abcUrl(), mapping)
			val mutableMapping = newClass("MutableMapping", table, mapping)
			mutableMapping.table.insert("__setitem__", abcUrl(), newFunc(), METHOD)
			mutableMapping.table.insert("__delitem__", abcUrl(), newFunc(), METHOD)
			addClass("MutableMapping", abcUrl(), mutableMapping)
			val MappingView = newClass("MappingView", table, Sized)
			addClass("MappingView", abcUrl(), MappingView)
			val KeysView = newClass("KeysView", table, Sized)
			addClass("KeysView", abcUrl(), KeysView)
			val ItemsView = newClass("ItemsView", table, Sized)
			addClass("ItemsView", abcUrl(), ItemsView)
			val ValuesView = newClass("ValuesView", table, Sized)
			addClass("ValuesView", abcUrl(), ValuesView)
			val deque = newClass("deque", table, objectType)
			for (n in list("append", "appendLeft", "clear",
					"extend", "extendLeft", "rotate")) {
				deque.table.insert(n, dequeUrl(), newFunc(NONE), METHOD)
			}
			for (u in list("__getitem__", "__iter__",
					"pop", "popleft", "remove")) {
				deque.table.insert(u, dequeUrl(), newFunc(), METHOD)
			}
			addClass("deque", dequeUrl(), deque)
			val defaultdict = newClass("defaultdict", table, objectType)
			defaultdict.table.insert("__missing__", liburl("defaultdict-objects"),
					newFunc(), METHOD)
			defaultdict.table.insert("default_factory", liburl("defaultdict-objects"),
					newFunc(), METHOD)
			addClass("defaultdict", liburl("defaultdict-objects"), defaultdict)
			val argh = "namedtuple-factory-function-for-tuples-with-named-fields"
			val namedtuple = newClass("(namedtuple)", table, BaseTuple)
			namedtuple.table.insert("_fields", liburl(argh),
					ListType(STR), ATTRIBUTE)
			addFunction("namedtuple", liburl(argh), namedtuple)
		}
	}

	internal inner class CTypesModule : NativeModule("ctypes") {

		override fun initBindings() {
			val ctypes_attrs = arrayOf("ARRAY", "ArgumentError", "Array", "BigEndianStructure", "CDLL", "CFUNCTYPE", "DEFAULT_MODE", "DllCanUnloadNow", "DllGetClassObject", "FormatError", "GetLastError", "HRESULT", "LibraryLoader", "LittleEndianStructure", "OleDLL", "POINTER", "PYFUNCTYPE", "PyDLL", "RTLD_GLOBAL", "RTLD_LOCAL", "SetPointerType", "Structure", "Union", "WINFUNCTYPE", "WinDLL", "WinError", "_CFuncPtr", "_FUNCFLAG_CDECL", "_FUNCFLAG_PYTHONAPI", "_FUNCFLAG_STDCALL", "_FUNCFLAG_USE_ERRNO", "_FUNCFLAG_USE_LASTERROR", "_Pointer", "_SimpleCData", "_c_functype_cache", "_calcsize", "_cast", "_cast_addr", "_check_HRESULT", "_check_size", "_ctypes_version", "_dlopen", "_endian", "_memmove_addr", "_memset_addr", "_os", "_pointer_type_cache", "_string_at", "_string_at_addr", "_sys", "_win_functype_cache", "_wstring_at", "_wstring_at_addr", "addressof", "alignment", "byref", "c_bool", "c_buffer", "c_byte", "c_char", "c_char_p", "c_double", "c_float", "c_int", "c_int16", "c_int32", "c_int64", "c_int8", "c_long", "c_longdouble", "c_longlong", "c_short", "c_size_t", "c_ubyte", "c_uint", "c_uint16", "c_uint32", "c_uint64", "c_uint8", "c_ulong", "c_ulonglong", "c_ushort", "c_void_p", "c_voidp", "c_wchar", "c_wchar_p", "cast", "cdll", "create_string_buffer", "create_unicode_buffer", "get_errno", "get_last_error", "memmove", "memset", "oledll", "pointer", "py_object", "pydll", "pythonapi", "resize", "set_conversion_mode", "set_errno", "set_last_error", "sizeof", "string_at", "windll", "wstring_at")
			for (attr in ctypes_attrs) {
				addAttr(attr, liburl(attr), UNKNOWN)
			}
		}
	}

	internal inner class CryptModule : NativeModule("crypt") {

		override fun initBindings() {
			addStrFuncs("crypt")
		}
	}

	internal inner class DatetimeModule : NativeModule("datetime") {

		private fun dtUrl(anchor: String): Url = liburl("datetime." + anchor)

		override fun initBindings() {
			// XXX:  make datetime, time, date, timedelta and tzinfo Base* objects,
			// so built-in functions can return them.
			addNumAttrs("MINYEAR", "MAXYEAR")
			Datetime_timedelta = newClass("timedelta", table, objectType)
			val timedelta = Datetime_timedelta
			addClass("timedelta", dtUrl("timedelta"), timedelta)
			val tdtable = Datetime_timedelta.table
			tdtable.insert("min", dtUrl("timedelta"), timedelta, ATTRIBUTE)
			tdtable.insert("max", dtUrl("timedelta"), timedelta, ATTRIBUTE)
			tdtable.insert("resolution", dtUrl("timedelta"), timedelta, ATTRIBUTE)
			tdtable.insert("days", dtUrl("timedelta"), INT, ATTRIBUTE)
			tdtable.insert("seconds", dtUrl("timedelta"), INT, ATTRIBUTE)
			tdtable.insert("microseconds", dtUrl("timedelta"), INT, ATTRIBUTE)
			Datetime_tzinfo = newClass("tzinfo", table, objectType)
			val tzinfo = Datetime_tzinfo
			addClass("tzinfo", dtUrl("tzinfo"), tzinfo)
			val tztable = Datetime_tzinfo.table
			tztable.insert("utcoffset", dtUrl("tzinfo"), newFunc(timedelta), METHOD)
			tztable.insert("dst", dtUrl("tzinfo"), newFunc(timedelta), METHOD)
			tztable.insert("tzname", dtUrl("tzinfo"), newFunc(STR), METHOD)
			tztable.insert("fromutc", dtUrl("tzinfo"), newFunc(tzinfo), METHOD)
			Datetime_date = newClass("date", table, objectType)
			val date = Datetime_date
			addClass("date", dtUrl("date"), date)
			val dtable = Datetime_date.table
			dtable.insert("min", dtUrl("date"), date, ATTRIBUTE)
			dtable.insert("max", dtUrl("date"), date, ATTRIBUTE)
			dtable.insert("resolution", dtUrl("date"), timedelta, ATTRIBUTE)
			dtable.insert("today", dtUrl("date"), newFunc(date), METHOD)
			dtable.insert("fromtimestamp", dtUrl("date"), newFunc(date), METHOD)
			dtable.insert("fromordinal", dtUrl("date"), newFunc(date), METHOD)
			dtable.insert("year", dtUrl("date"), INT, ATTRIBUTE)
			dtable.insert("month", dtUrl("date"), INT, ATTRIBUTE)
			dtable.insert("day", dtUrl("date"), INT, ATTRIBUTE)
			dtable.insert("replace", dtUrl("date"), newFunc(date), METHOD)
			dtable.insert("timetuple", dtUrl("date"), newFunc(Time_struct_time), METHOD)
			for (n in list("toordinal", "weekday", "isoweekday")) {
				dtable.insert(n, dtUrl("date"), newFunc(INT), METHOD)
			}
			for (r in list("ctime", "strftime", "isoformat")) {
				dtable.insert(r, dtUrl("date"), newFunc(STR), METHOD)
			}
			dtable.insert("isocalendar", dtUrl("date"),
					newFunc(newTuple(INT, INT, INT)), METHOD)
			Datetime_time = newClass("time", table, objectType)
			val time = Datetime_time
			addClass("time", dtUrl("time"), time)
			val ttable = Datetime_time.table
			ttable.insert("min", dtUrl("time"), time, ATTRIBUTE)
			ttable.insert("max", dtUrl("time"), time, ATTRIBUTE)
			ttable.insert("resolution", dtUrl("time"), timedelta, ATTRIBUTE)
			ttable.insert("hour", dtUrl("time"), INT, ATTRIBUTE)
			ttable.insert("minute", dtUrl("time"), INT, ATTRIBUTE)
			ttable.insert("second", dtUrl("time"), INT, ATTRIBUTE)
			ttable.insert("microsecond", dtUrl("time"), INT, ATTRIBUTE)
			ttable.insert("tzinfo", dtUrl("time"), tzinfo, ATTRIBUTE)
			ttable.insert("replace", dtUrl("time"), newFunc(time), METHOD)
			for (l in list("isoformat", "strftime", "tzname")) {
				ttable.insert(l, dtUrl("time"), newFunc(STR), METHOD)
			}
			for (f in list("utcoffset", "dst")) {
				ttable.insert(f, dtUrl("time"), newFunc(timedelta), METHOD)
			}
			Datetime_datetime = newClass("datetime", table, date, time)
			val datetime = Datetime_datetime
			addClass("datetime", dtUrl("datetime"), datetime)
			val dttable = Datetime_datetime.table
			for (c in list("combine", "fromordinal", "fromtimestamp", "now",
					"strptime", "today", "utcfromtimestamp", "utcnow")) {
				dttable.insert(c, dtUrl("datetime"), newFunc(datetime), METHOD)
			}
			dttable.insert("min", dtUrl("datetime"), datetime, ATTRIBUTE)
			dttable.insert("max", dtUrl("datetime"), datetime, ATTRIBUTE)
			dttable.insert("resolution", dtUrl("datetime"), timedelta, ATTRIBUTE)
			dttable.insert("date", dtUrl("datetime"), newFunc(date), METHOD)
			for (x in list("time", "timetz")) {
				dttable.insert(x, dtUrl("datetime"), newFunc(time), METHOD)
			}
			for (y in list("replace", "astimezone")) {
				dttable.insert(y, dtUrl("datetime"), newFunc(datetime), METHOD)
			}
			dttable.insert("utctimetuple", dtUrl("datetime"), newFunc(Time_struct_time), METHOD)
		}
	}

	internal inner class DbmModule : NativeModule("dbm") {

		override fun initBindings() {
			val dbm = ClassType("dbm", table, BaseDict)
			addClass("dbm", liburl(), dbm)
			addClass("error", liburl(), newException("error", table))
			addStrAttrs("library")
			addFunction("open", liburl(), dbm)
		}
	}

	internal inner class ErrnoModule : NativeModule("errno") {

		override fun initBindings() {
			addNumAttrs(
					"E2BIG", "EACCES", "EADDRINUSE", "EADDRNOTAVAIL", "EAFNOSUPPORT",
					"EAGAIN", "EALREADY", "EBADF", "EBUSY", "ECHILD", "ECONNABORTED",
					"ECONNREFUSED", "ECONNRESET", "EDEADLK", "EDEADLOCK",
					"EDESTADDRREQ", "EDOM", "EDQUOT", "EEXIST", "EFAULT", "EFBIG",
					"EHOSTDOWN", "EHOSTUNREACH", "EILSEQ", "EINPROGRESS", "EINTR",
					"EINVAL", "EIO", "EISCONN", "EISDIR", "ELOOP", "EMFILE", "EMLINK",
					"EMSGSIZE", "ENAMETOOLONG", "ENETDOWN", "ENETRESET", "ENETUNREACH",
					"ENFILE", "ENOBUFS", "ENODEV", "ENOENT", "ENOEXEC", "ENOLCK",
					"ENOMEM", "ENOPROTOOPT", "ENOSPC", "ENOSYS", "ENOTCONN", "ENOTDIR",
					"ENOTEMPTY", "ENOTSOCK", "ENOTTY", "ENXIO", "EOPNOTSUPP", "EPERM",
					"EPFNOSUPPORT", "EPIPE", "EPROTONOSUPPORT", "EPROTOTYPE", "ERANGE",
					"EREMOTE", "EROFS", "ESHUTDOWN", "ESOCKTNOSUPPORT", "ESPIPE",
					"ESRCH", "ESTALE", "ETIMEDOUT", "ETOOMANYREFS", "EUSERS",
					"EWOULDBLOCK", "EXDEV", "WSABASEERR", "WSAEACCES", "WSAEADDRINUSE",
					"WSAEADDRNOTAVAIL", "WSAEAFNOSUPPORT", "WSAEALREADY", "WSAEBADF",
					"WSAECONNABORTED", "WSAECONNREFUSED", "WSAECONNRESET",
					"WSAEDESTADDRREQ", "WSAEDISCON", "WSAEDQUOT", "WSAEFAULT",
					"WSAEHOSTDOWN", "WSAEHOSTUNREACH", "WSAEINPROGRESS", "WSAEINTR",
					"WSAEINVAL", "WSAEISCONN", "WSAELOOP", "WSAEMFILE", "WSAEMSGSIZE",
					"WSAENAMETOOLONG", "WSAENETDOWN", "WSAENETRESET", "WSAENETUNREACH",
					"WSAENOBUFS", "WSAENOPROTOOPT", "WSAENOTCONN", "WSAENOTEMPTY",
					"WSAENOTSOCK", "WSAEOPNOTSUPP", "WSAEPFNOSUPPORT", "WSAEPROCLIM",
					"WSAEPROTONOSUPPORT", "WSAEPROTOTYPE", "WSAEREMOTE", "WSAESHUTDOWN",
					"WSAESOCKTNOSUPPORT", "WSAESTALE", "WSAETIMEDOUT",
					"WSAETOOMANYREFS", "WSAEUSERS", "WSAEWOULDBLOCK",
					"WSANOTINITIALISED", "WSASYSNOTREADY", "WSAVERNOTSUPPORTED")
			addAttr("errorcode", liburl("errorcode"), newDict(INT, STR))
		}
	}

	internal inner class ExceptionsModule : NativeModule("exceptions") {

		override fun initBindings() {
			val builtins = get("__builtin__")
			for (s in builtin_exception_types) {
				//                Binding b = builtins.table.lookup(s);
				//                table.update(b.getName(), b.getFirstNode(), b.getType(), b.getKind());
			}
		}
	}

	internal inner class FcntlModule : NativeModule("fcntl") {

		override fun initBindings() {
			for (s in list("fcntl", "ioctl")) {
				addFunction(s, liburl(), newUnion(INT, STR))
			}
			addNumFuncs("flock")
			addUnknownFuncs("lockf")
			addNumAttrs(
					"DN_ACCESS", "DN_ATTRIB", "DN_CREATE", "DN_DELETE", "DN_MODIFY",
					"DN_MULTISHOT", "DN_RENAME", "FASYNC", "FD_CLOEXEC", "F_DUPFD",
					"F_EXLCK", "F_GETFD", "F_GETFL", "F_GETLEASE", "F_GETLK", "F_GETLK64",
					"F_GETOWN", "F_GETSIG", "F_NOTIFY", "F_RDLCK", "F_SETFD", "F_SETFL",
					"F_SETLEASE", "F_SETLK", "F_SETLK64", "F_SETLKW", "F_SETLKW64",
					"F_SETOWN", "F_SETSIG", "F_SHLCK", "F_UNLCK", "F_WRLCK", "I_ATMARK",
					"I_CANPUT", "I_CKBAND", "I_FDINSERT", "I_FIND", "I_FLUSH",
					"I_FLUSHBAND", "I_GETBAND", "I_GETCLTIME", "I_GETSIG", "I_GRDOPT",
					"I_GWROPT", "I_LINK", "I_LIST", "I_LOOK", "I_NREAD", "I_PEEK",
					"I_PLINK", "I_POP", "I_PUNLINK", "I_PUSH", "I_RECVFD", "I_SENDFD",
					"I_SETCLTIME", "I_SETSIG", "I_SRDOPT", "I_STR", "I_SWROPT",
					"I_UNLINK", "LOCK_EX", "LOCK_MAND", "LOCK_NB", "LOCK_READ", "LOCK_RW",
					"LOCK_SH", "LOCK_UN", "LOCK_WRITE")
		}
	}

	internal inner class FpectlModule : NativeModule("fpectl") {

		override fun initBindings() {
			addNoneFuncs("turnon_sigfpe", "turnoff_sigfpe")
			addClass("FloatingPointError", liburl(), newException("FloatingPointError", table))
		}
	}

	internal inner class GcModule : NativeModule("gc") {

		override fun initBindings() {
			addNoneFuncs("enable", "disable", "set_debug", "set_threshold")
			addNumFuncs("isenabled", "collect", "get_debug", "get_count", "get_threshold")
			for (s in list("get_objects", "get_referrers", "get_referents")) {
				addFunction(s, liburl(), newList())
			}
			addAttr("garbage", liburl(), newList())
			addNumAttrs("DEBUG_STATS", "DEBUG_COLLECTABLE", "DEBUG_UNCOLLECTABLE",
					"DEBUG_INSTANCES", "DEBUG_OBJECTS", "DEBUG_SAVEALL", "DEBUG_LEAK")
		}
	}

	internal inner class GdbmModule : NativeModule("gdbm") {

		override fun initBindings() {
			addClass("error", liburl(), newException("error", table))
			val gdbm = ClassType("gdbm", table, BaseDict)
			gdbm.table.insert("firstkey", liburl(), newFunc(STR), METHOD)
			gdbm.table.insert("nextkey", liburl(), newFunc(STR), METHOD)
			gdbm.table.insert("reorganize", liburl(), newFunc(NONE), METHOD)
			gdbm.table.insert("sync", liburl(), newFunc(NONE), METHOD)
			addFunction("open", liburl(), gdbm)
		}
	}

	internal inner class GrpModule : NativeModule("grp") {

		override fun initBindings() {
			this@Builtins["struct"]
			val struct_group = newClass("struct_group", table, BaseStruct)
			struct_group.table.insert("gr_name", liburl(), STR, ATTRIBUTE)
			struct_group.table.insert("gr_passwd", liburl(), STR, ATTRIBUTE)
			struct_group.table.insert("gr_gid", liburl(), INT, ATTRIBUTE)
			struct_group.table.insert("gr_mem", liburl(), newList(STR), ATTRIBUTE)
			addClass("struct_group", liburl(), struct_group)
			for (s in list("getgrgid", "getgrnam")) {
				addFunction(s, liburl(), struct_group)
			}
			addFunction("getgrall", liburl(), ListType(struct_group))
		}
	}

	internal inner class ImpModule : NativeModule("imp") {

		override fun initBindings() {
			addStrFuncs("get_magic")
			addFunction("get_suffixes", liburl(), newList(newTuple(STR, STR, INT)))
			addFunction("find_module", liburl(), newTuple(STR, STR, INT))
			val module_methods = arrayOf("load_module", "new_module", "init_builtin", "init_frozen", "load_compiled", "load_dynamic", "load_source")
			for (mm in module_methods) {
				addFunction(mm, liburl(), newModule("<?>"))
			}
			addUnknownFuncs("acquire_lock", "release_lock")
			addNumAttrs("PY_SOURCE", "PY_COMPILED", "C_EXTENSION",
					"PKG_DIRECTORY", "C_BUILTIN", "PY_FROZEN", "SEARCH_ERROR")
			addNumFuncs("lock_held", "is_builtin", "is_frozen")
			val impNullImporter = newClass("NullImporter", table, objectType)
			impNullImporter.table.insert("find_module", liburl(), newFunc(NONE), FUNCTION)
			addClass("NullImporter", liburl(), impNullImporter)
		}
	}

	internal inner class ItertoolsModule : NativeModule("itertools") {

		override fun initBindings() {
			val iterator = newClass("iterator", table, objectType)
			iterator.table.insert("from_iterable", liburl("itertool-functions"),
					newFunc(iterator), METHOD)
			iterator.table.insert("next", liburl(), newFunc(), METHOD)
			for (s in list("chain", "combinations", "count", "cycle",
					"dropwhile", "groupby", "ifilter",
					"ifilterfalse", "imap", "islice", "izip",
					"izip_longest", "permutations", "product",
					"repeat", "starmap", "takewhile", "tee")) {
				addClass(s, liburl("itertool-functions"), iterator)
			}
		}
	}

	internal inner class MarshalModule : NativeModule("marshal") {

		override fun initBindings() {
			addNumAttrs("version")
			addStrFuncs("dumps")
			addUnknownFuncs("dump", "load", "loads")
		}
	}

	internal inner class MathModule : NativeModule("math") {

		override fun initBindings() {
			addNumFuncs(
					"acos", "acosh", "asin", "asinh", "atan", "atan2", "atanh", "ceil",
					"copysign", "cos", "cosh", "degrees", "exp", "fabs", "factorial",
					"floor", "fmod", "frexp", "fsum", "hypot", "isinf", "isnan",
					"ldexp", "log", "log10", "log1p", "modf", "pow", "radians", "sin",
					"sinh", "sqrt", "tan", "tanh", "trunc")
			addNumAttrs("pi", "e")
		}
	}

	internal inner class Md5Module : NativeModule("md5") {

		override fun initBindings() {
			addNumAttrs("blocksize", "digest_size")
			val md5 = newClass("md5", table, objectType)
			md5.table.insert("update", liburl(), newFunc(), METHOD)
			md5.table.insert("digest", liburl(), newFunc(STR), METHOD)
			md5.table.insert("hexdigest", liburl(), newFunc(STR), METHOD)
			md5.table.insert("copy", liburl(), newFunc(md5), METHOD)
			update("new", liburl(), newFunc(md5), CONSTRUCTOR)
			update("md5", liburl(), newFunc(md5), CONSTRUCTOR)
		}
	}

	internal inner class MmapModule : NativeModule("mmap") {

		override fun initBindings() {
			val mmap = newClass("mmap", table, objectType)
			for (s in list("ACCESS_COPY", "ACCESS_READ", "ACCESS_WRITE",
					"ALLOCATIONGRANULARITY", "MAP_ANON", "MAP_ANONYMOUS",
					"MAP_DENYWRITE", "MAP_EXECUTABLE", "MAP_PRIVATE",
					"MAP_SHARED", "PAGESIZE", "PROT_EXEC", "PROT_READ",
					"PROT_WRITE")) {
				mmap.table.insert(s, liburl(), INT, ATTRIBUTE)
			}
			for (fstr in list("read", "read_byte", "readline")) {
				mmap.table.insert(fstr, liburl(), newFunc(STR), METHOD)
			}
			for (fnum in list("find", "rfind", "tell")) {
				mmap.table.insert(fnum, liburl(), newFunc(INT), METHOD)
			}
			for (fnone in list("close", "flush", "move", "resize", "seek",
					"write", "write_byte")) {
				mmap.table.insert(fnone, liburl(), newFunc(NONE), METHOD)
			}
			addClass("mmap", liburl(), mmap)
		}
	}

	internal inner class NisModule : NativeModule("nis") {

		override fun initBindings() {
			addStrFuncs("match", "cat", "get_default_domain")
			addFunction("maps", liburl(), newList(STR))
			addClass("error", liburl(), newException("error", table))
		}
	}

	internal inner class OsModule : NativeModule("os") {

		override fun initBindings() {
			addAttr("name", liburl(), STR)
			addClass("error", liburl(), newException("error", table))  // XXX: OSError
			initProcBindings()
			initProcMgmtBindings()
			initFileBindings()
			initFileAndDirBindings()
			initMiscSystemInfo()
			initOsPathModule()
			addAttr("errno", liburl(), newModule("errno"))
			addFunction("urandom", liburl("miscellaneous-functions"), STR)
			addAttr("NGROUPS_MAX", liburl(), INT)
			for (s in list("_Environ", "_copy_reg", "_execvpe", "_exists",
					"_get_exports_list", "_make_stat_result",
					"_make_statvfs_result", "_pickle_stat_result",
					"_pickle_statvfs_result", "_spawnvef")) {
				addFunction(s, liburl(), UNKNOWN)
			}
		}

		private fun initProcBindings() {
			val a = "process-parameters"
			addAttr("environ", liburl(a), newDict(STR, STR))
			for (s in list("chdir", "fchdir", "putenv", "setegid", "seteuid",
					"setgid", "setgroups", "setpgrp", "setpgid",
					"setreuid", "setregid", "setuid", "unsetenv")) {
				addFunction(s, liburl(a), NONE)
			}
			for (s in list("getegid", "getgid", "getpgid", "getpgrp",
					"getppid", "getuid", "getsid", "umask")) {
				addFunction(s, liburl(a), INT)
			}
			for (s in list("getcwd", "ctermid", "getlogin", "getenv", "strerror")) {
				addFunction(s, liburl(a), STR)
			}
			addFunction("getgroups", liburl(a), newList(STR))
			addFunction("uname", liburl(a), newTuple(STR, STR, STR,
					STR, STR))
		}

		private fun initProcMgmtBindings() {
			val a = "process-management"
			for (s in list("EX_CANTCREAT", "EX_CONFIG", "EX_DATAERR",
					"EX_IOERR", "EX_NOHOST", "EX_NOINPUT",
					"EX_NOPERM", "EX_NOUSER", "EX_OK", "EX_OSERR",
					"EX_OSFILE", "EX_PROTOCOL", "EX_SOFTWARE",
					"EX_TEMPFAIL", "EX_UNAVAILABLE", "EX_USAGE",
					"P_NOWAIT", "P_NOWAITO", "P_WAIT", "P_DETACH",
					"P_OVERLAY", "WCONTINUED", "WCOREDUMP",
					"WEXITSTATUS", "WIFCONTINUED", "WIFEXITED",
					"WIFSIGNALED", "WIFSTOPPED", "WNOHANG", "WSTOPSIG",
					"WTERMSIG", "WUNTRACED")) {
				addAttr(s, liburl(a), INT)
			}
			for (s in list("abort", "execl", "execle", "execlp", "execlpe",
					"execv", "execve", "execvp", "execvpe", "_exit",
					"kill", "killpg", "plock", "startfile")) {
				addFunction(s, liburl(a), NONE)
			}
			for (s in list("nice", "spawnl", "spawnle", "spawnlp", "spawnlpe",
					"spawnv", "spawnve", "spawnvp", "spawnvpe", "system")) {
				addFunction(s, liburl(a), INT)
			}
			addFunction("fork", liburl(a), newUnion(BaseFileInst, INT))
			addFunction("times", liburl(a), newTuple(INT, INT, INT, INT, INT))
			for (s in list("forkpty", "wait", "waitpid")) {
				addFunction(s, liburl(a), newTuple(INT, INT))
			}
			for (s in list("wait3", "wait4")) {
				addFunction(s, liburl(a), newTuple(INT, INT, INT))
			}
		}

		private fun initFileBindings() {
			var a = "file-object-creation"
			for (s in list("fdopen", "popen", "tmpfile")) {
				addFunction(s, liburl(a), BaseFileInst)
			}
			addFunction("popen2", liburl(a), newTuple(BaseFileInst, BaseFileInst))
			addFunction("popen3", liburl(a), newTuple(BaseFileInst, BaseFileInst, BaseFileInst))
			addFunction("popen4", liburl(a), newTuple(BaseFileInst, BaseFileInst))
			a = "file-descriptor-operations"
			addFunction("open", liburl(a), BaseFileInst)
			for (s in list("close", "closerange", "dup2", "fchmod",
					"fchown", "fdatasync", "fsync", "ftruncate",
					"lseek", "tcsetpgrp", "write")) {
				addFunction(s, liburl(a), NONE)
			}
			for (s in list("dup2", "fpathconf", "fstat", "fstatvfs",
					"isatty", "tcgetpgrp")) {
				addFunction(s, liburl(a), INT)
			}
			for (s in list("read", "ttyname")) {
				addFunction(s, liburl(a), STR)
			}
			for (s in list("openpty", "pipe", "fstat", "fstatvfs",
					"isatty")) {
				addFunction(s, liburl(a), newTuple(INT, INT))
			}
			for (s in list("O_APPEND", "O_CREAT", "O_DIRECT", "O_DIRECTORY",
					"O_DSYNC", "O_EXCL", "O_LARGEFILE", "O_NDELAY",
					"O_NOCTTY", "O_NOFOLLOW", "O_NONBLOCK", "O_RDONLY",
					"O_RDWR", "O_RSYNC", "O_SYNC", "O_TRUNC", "O_WRONLY",
					"SEEK_CUR", "SEEK_END", "SEEK_SET")) {
				addAttr(s, liburl(a), INT)
			}
		}

		private fun initFileAndDirBindings() {
			val a = "files-and-directories"
			for (s in list("F_OK", "R_OK", "W_OK", "X_OK")) {
				addAttr(s, liburl(a), INT)
			}
			for (s in list("chflags", "chroot", "chmod", "chown", "lchflags",
					"lchmod", "lchown", "link", "mknod", "mkdir",
					"mkdirs", "remove", "removedirs", "rename", "renames",
					"rmdir", "symlink", "unlink", "utime")) {
				addAttr(s, liburl(a), NONE)
			}
			for (s in list("access", "lstat", "major", "minor",
					"makedev", "pathconf", "stat_float_times")) {
				addFunction(s, liburl(a), INT)
			}
			for (s in list("getcwdu", "readlink", "tempnam", "tmpnam")) {
				addFunction(s, liburl(a), STR)
			}
			for (s in list("listdir")) {
				addFunction(s, liburl(a), newList(STR))
			}
			addFunction("mkfifo", liburl(a), BaseFileInst)
			addFunction("stat", liburl(a), newList(INT))  // XXX: posix.stat_result
			addFunction("statvfs", liburl(a), newList(INT))  // XXX: pos.statvfs_result
			addAttr("pathconf_names", liburl(a), newDict(STR, INT))
			addAttr("TMP_MAX", liburl(a), INT)
			addFunction("walk", liburl(a), newList(newTuple(STR, STR, STR)))
		}

		private fun initMiscSystemInfo() {
			val a = "miscellaneous-system-information"
			addAttr("confstr_names", liburl(a), newDict(STR, INT))
			addAttr("sysconf_names", liburl(a), newDict(STR, INT))
			for (s in list("curdir", "pardir", "sep", "altsep", "extsep",
					"pathsep", "defpath", "linesep", "devnull")) {
				addAttr(s, liburl(a), STR)
			}
			for (s in list("getloadavg", "sysconf")) {
				addFunction(s, liburl(a), INT)
			}
			addFunction("confstr", liburl(a), STR)
		}

		private fun initOsPathModule() {
			val m = newModule("path")
			val ospath = m.table
			ospath.path = "os.path"  // make sure global qnames are correct
			update("path", newLibUrl("os.path.html#module-os.path"), m, MODULE)
			val str_funcs = arrayOf("_resolve_link", "abspath", "basename", "commonprefix", "dirname", "expanduser", "expandvars", "join", "normcase", "normpath", "realpath", "relpath")
			for (s in str_funcs) {
				ospath.insert(s, newLibUrl("os.path", s), newFunc(STR), FUNCTION)
			}
			val num_funcs = arrayOf("exists", "lexists", "getatime", "getctime", "getmtime", "getsize", "isabs", "isdir", "isfile", "islink", "ismount", "samefile", "sameopenfile", "samestat", "supports_unicode_filenames")
			for (s in num_funcs) {
				ospath.insert(s, newLibUrl("os.path", s), newFunc(INT), FUNCTION)
			}
			for (s in list("split", "splitdrive", "splitext", "splitunc")) {
				ospath.insert(s, newLibUrl("os.path", s),
						newFunc(newTuple(STR, STR)), FUNCTION)
			}
			ospath.insert("walk", newLibUrl("os.path"), newFunc(NONE), FUNCTION)
			val str_attrs = arrayOf("altsep", "curdir", "devnull", "defpath", "pardir", "pathsep", "sep")
			for (s in str_attrs) {
				ospath.insert(s, newLibUrl("os.path", s), STR, ATTRIBUTE)
			}
			ospath.insert("os", liburl(), this.module!!, ATTRIBUTE)
			ospath.insert("stat", newLibUrl("stat"),
					// moduleTable.lookupLocal("stat").getType(),
					newModule("<stat-fixme>"), ATTRIBUTE)
			// XXX:  this is an re object, I think
			ospath.insert("_varprog", newLibUrl("os.path"), UNKNOWN, ATTRIBUTE)
		}
	}

	internal inner class OperatorModule : NativeModule("operator") {

		override fun initBindings() {
			// XXX:  mark __getslice__, __setslice__ and __delslice__ as deprecated.
			addNumFuncs(
					"__abs__", "__add__", "__and__", "__concat__", "__contains__",
					"__div__", "__doc__", "__eq__", "__floordiv__", "__ge__",
					"__getitem__", "__getslice__", "__gt__", "__iadd__", "__iand__",
					"__iconcat__", "__idiv__", "__ifloordiv__", "__ilshift__",
					"__imod__", "__imul__", "__index__", "__inv__", "__invert__",
					"__ior__", "__ipow__", "__irepeat__", "__irshift__", "__isub__",
					"__itruediv__", "__ixor__", "__le__", "__lshift__", "__lt__",
					"__mod__", "__mul__", "__name__", "__ne__", "__neg__", "__not__",
					"__or__", "__package__", "__pos__", "__pow__", "__repeat__",
					"__rshift__", "__setitem__", "__setslice__", "__sub__",
					"__truediv__", "__xor__", "abs", "add", "and_", "concat",
					"contains", "countOf", "div", "eq", "floordiv", "ge", "getitem",
					"getslice", "gt", "iadd", "iand", "iconcat", "idiv", "ifloordiv",
					"ilshift", "imod", "imul", "index", "indexOf", "inv", "invert",
					"ior", "ipow", "irepeat", "irshift", "isCallable",
					"isMappingType", "isNumberType", "isSequenceType", "is_",
					"is_not", "isub", "itruediv", "ixor", "le", "lshift", "lt", "mod",
					"mul", "ne", "neg", "not_", "or_", "pos", "pow", "repeat",
					"rshift", "sequenceIncludes", "setitem", "setslice", "sub",
					"truediv", "truth", "xor")
			addUnknownFuncs("attrgetter", "itemgetter", "methodcaller")
			addNoneFuncs("__delitem__", "__delslice__", "delitem", "delclice")
		}
	}

	internal inner class ParserModule : NativeModule("parser") {

		override fun initBindings() {
			val st = newClass("st", table, objectType)
			st.table.insert("compile", newLibUrl("parser", "st-objects"),
					newFunc(), METHOD)
			st.table.insert("isexpr", newLibUrl("parser", "st-objects"),
					newFunc(INT), METHOD)
			st.table.insert("issuite", newLibUrl("parser", "st-objects"),
					newFunc(INT), METHOD)
			st.table.insert("tolist", newLibUrl("parser", "st-objects"),
					newFunc(newList()), METHOD)
			st.table.insert("totuple", newLibUrl("parser", "st-objects"),
					newFunc(newTuple()), METHOD)
			addAttr("STType", liburl("st-objects"), BaseType)
			for (s in list("expr", "suite", "sequence2st", "tuple2st")) {
				addFunction(s, liburl("creating-st-objects"), st)
			}
			addFunction("st2list", liburl("converting-st-objects"), newList())
			addFunction("st2tuple", liburl("converting-st-objects"), newTuple())
			addFunction("compilest", liburl("converting-st-objects"), UNKNOWN)
			addFunction("isexpr", liburl("queries-on-st-objects"), Type.BOOL)
			addFunction("issuite", liburl("queries-on-st-objects"), Type.BOOL)
			addClass("ParserError", liburl("exceptions-and-error-handling"),
					newException("ParserError", table))
		}
	}

	internal inner class PosixModule : NativeModule("posix") {

		override fun initBindings() {
			addAttr("environ", liburl(), newDict(STR, STR))
		}
	}

	internal inner class PwdModule : NativeModule("pwd") {

		override fun initBindings() {
			val struct_pwd = newClass("struct_pwd", table, objectType)
			for (s in list("pw_nam", "pw_passwd", "pw_uid", "pw_gid",
					"pw_gecos", "pw_dir", "pw_shell")) {
				struct_pwd.table.insert(s, liburl(), INT, ATTRIBUTE)
			}
			addAttr("struct_pwd", liburl(), struct_pwd)
			addFunction("getpwuid", liburl(), struct_pwd)
			addFunction("getpwnam", liburl(), struct_pwd)
			addFunction("getpwall", liburl(), newList(struct_pwd))
		}
	}

	internal inner class PyexpatModule : NativeModule("pyexpat") {

		override fun initBindings() {
			// XXX
		}
	}

	internal inner class ReadlineModule : NativeModule("readline") {

		override fun initBindings() {
			addNoneFuncs("parse_and_bind", "insert_text", "read_init_file",
					"read_history_file", "write_history_file",
					"clear_history", "set_history_length",
					"remove_history_item", "replace_history_item",
					"redisplay", "set_startup_hook", "set_pre_input_hook",
					"set_completer", "set_completer_delims",
					"set_completion_display_matches_hook", "add_history")
			addNumFuncs("get_history_length", "get_current_history_length",
					"get_begidx", "get_endidx")
			addStrFuncs("get_line_buffer", "get_history_item")
			addUnknownFuncs("get_completion_type")
			addFunction("get_completer", liburl(), newFunc())
			addFunction("get_completer_delims", liburl(), newList(STR))
		}
	}

	internal inner class ResourceModule : NativeModule("resource") {

		override fun initBindings() {
			addFunction("getrlimit", liburl(), newTuple(INT, INT))
			addFunction("getrlimit", liburl(), UNKNOWN)
			val constants = arrayOf("RLIMIT_CORE", "RLIMIT_CPU", "RLIMIT_FSIZE", "RLIMIT_DATA", "RLIMIT_STACK", "RLIMIT_RSS", "RLIMIT_NPROC", "RLIMIT_NOFILE", "RLIMIT_OFILE", "RLIMIT_MEMLOCK", "RLIMIT_VMEM", "RLIMIT_AS")
			for (c in constants) {
				addAttr(c, liburl("resource-limits"), INT)
			}
			val ru = newClass("struct_rusage", table, objectType)
			val ru_fields = arrayOf("ru_utime", "ru_stime", "ru_maxrss", "ru_ixrss", "ru_idrss", "ru_isrss", "ru_minflt", "ru_majflt", "ru_nswap", "ru_inblock", "ru_oublock", "ru_msgsnd", "ru_msgrcv", "ru_nsignals", "ru_nvcsw", "ru_nivcsw")
			for (ruf in ru_fields) {
				ru.table.insert(ruf, liburl("resource-usage"), INT, ATTRIBUTE)
			}
			addFunction("getrusage", liburl("resource-usage"), ru)
			addFunction("getpagesize", liburl("resource-usage"), INT)
			for (s in list("RUSAGE_SELF", "RUSAGE_CHILDREN", "RUSAGE_BOTH")) {
				addAttr(s, liburl("resource-usage"), INT)
			}
		}
	}

	internal inner class SelectModule : NativeModule("select") {

		override fun initBindings() {
			addClass("error", liburl(), newException("error", table))
			addFunction("select", liburl(), newTuple(newList(), newList(), newList()))
			var a = "edge-and-level-trigger-polling-epoll-objects"
			val epoll = newClass("epoll", table, objectType)
			epoll.table.insert("close", newLibUrl("select", a), newFunc(NONE), METHOD)
			epoll.table.insert("fileno", newLibUrl("select", a), newFunc(INT), METHOD)
			epoll.table.insert("fromfd", newLibUrl("select", a), newFunc(epoll), METHOD)
			for (s in list("register", "modify", "unregister", "poll")) {
				epoll.table.insert(s, newLibUrl("select", a), newFunc(), METHOD)
			}
			addClass("epoll", liburl(a), epoll)
			for (s in list("EPOLLERR", "EPOLLET", "EPOLLHUP", "EPOLLIN", "EPOLLMSG",
					"EPOLLONESHOT", "EPOLLOUT", "EPOLLPRI", "EPOLLRDBAND",
					"EPOLLRDNORM", "EPOLLWRBAND", "EPOLLWRNORM")) {
				addAttr(s, liburl(a), INT)
			}
			a = "polling-objects"
			val poll = newClass("poll", table, objectType)
			poll.table.insert("register", newLibUrl("select", a), newFunc(), METHOD)
			poll.table.insert("modify", newLibUrl("select", a), newFunc(), METHOD)
			poll.table.insert("unregister", newLibUrl("select", a), newFunc(), METHOD)
			poll.table.insert("poll", newLibUrl("select", a),
					newFunc(newList(newTuple(INT, INT))), METHOD)
			addClass("poll", liburl(a), poll)
			for (s in list("POLLERR", "POLLHUP", "POLLIN", "POLLMSG",
					"POLLNVAL", "POLLOUT", "POLLPRI", "POLLRDBAND",
					"POLLRDNORM", "POLLWRBAND", "POLLWRNORM")) {
				addAttr(s, liburl(a), INT)
			}
			a = "kqueue-objects"
			val kqueue = newClass("kqueue", table, objectType)
			kqueue.table.insert("close", newLibUrl("select", a), newFunc(NONE), METHOD)
			kqueue.table.insert("fileno", newLibUrl("select", a), newFunc(INT), METHOD)
			kqueue.table.insert("fromfd", newLibUrl("select", a), newFunc(kqueue), METHOD)
			kqueue.table.insert("control", newLibUrl("select", a),
					newFunc(newList(newTuple(INT, INT))), METHOD)
			addClass("kqueue", liburl(a), kqueue)
			a = "kevent-objects"
			val kevent = newClass("kevent", table, objectType)
			for (s in list("ident", "filter", "flags", "fflags", "data", "udata")) {
				kevent.table.insert(s, newLibUrl("select", a), UNKNOWN, ATTRIBUTE)
			}
			addClass("kevent", liburl(a), kevent)
		}
	}

	internal inner class SignalModule : NativeModule("signal") {

		override fun initBindings() {
			addNumAttrs(
					"NSIG", "SIGABRT", "SIGALRM", "SIGBUS", "SIGCHLD", "SIGCLD",
					"SIGCONT", "SIGFPE", "SIGHUP", "SIGILL", "SIGINT", "SIGIO",
					"SIGIOT", "SIGKILL", "SIGPIPE", "SIGPOLL", "SIGPROF", "SIGPWR",
					"SIGQUIT", "SIGRTMAX", "SIGRTMIN", "SIGSEGV", "SIGSTOP", "SIGSYS",
					"SIGTERM", "SIGTRAP", "SIGTSTP", "SIGTTIN", "SIGTTOU", "SIGURG",
					"SIGUSR1", "SIGUSR2", "SIGVTALRM", "SIGWINCH", "SIGXCPU", "SIGXFSZ",
					"SIG_DFL", "SIG_IGN")
			addUnknownFuncs("default_int_handler", "getsignal", "set_wakeup_fd", "signal")
		}
	}

	internal inner class ShaModule : NativeModule("sha") {

		override fun initBindings() {
			addNumAttrs("blocksize", "digest_size")
			val sha = newClass("sha", table, objectType)
			sha.table.insert("update", liburl(), newFunc(), METHOD)
			sha.table.insert("digest", liburl(), newFunc(STR), METHOD)
			sha.table.insert("hexdigest", liburl(), newFunc(STR), METHOD)
			sha.table.insert("copy", liburl(), newFunc(sha), METHOD)
			addClass("sha", liburl(), sha)
			update("new", liburl(), newFunc(sha), CONSTRUCTOR)
		}
	}

	internal inner class SpwdModule : NativeModule("spwd") {

		override fun initBindings() {
			val struct_spwd = newClass("struct_spwd", table, objectType)
			for (s in list("sp_nam", "sp_pwd", "sp_lstchg", "sp_min",
					"sp_max", "sp_warn", "sp_inact", "sp_expire",
					"sp_flag")) {
				struct_spwd.table.insert(s, liburl(), INT, ATTRIBUTE)
			}
			addAttr("struct_spwd", liburl(), struct_spwd)
			addFunction("getspnam", liburl(), struct_spwd)
			addFunction("getspall", liburl(), newList(struct_spwd))
		}
	}

	internal inner class StropModule : NativeModule("strop") {

		override fun initBindings() {
			table?.putAll(STR.table)
		}
	}

	internal inner class StructModule : NativeModule("struct") {

		override fun initBindings() {
			addClass("error", liburl(), newException("error", table))
			addStrFuncs("pack")
			addUnknownFuncs("pack_into")
			addNumFuncs("calcsize")
			addFunction("unpack", liburl(), newTuple())
			addFunction("unpack_from", liburl(), newTuple())
			BaseStruct = newClass("Struct", table, objectType)
			addClass("Struct", liburl("struct-objects"), BaseStruct)
			val t = BaseStruct.table
			t.insert("pack", liburl("struct-objects"), newFunc(STR), METHOD)
			t.insert("pack_into", liburl("struct-objects"), newFunc(), METHOD)
			t.insert("unpack", liburl("struct-objects"), newFunc(newTuple()), METHOD)
			t.insert("unpack_from", liburl("struct-objects"), newFunc(newTuple()), METHOD)
			t.insert("format", liburl("struct-objects"), STR, ATTRIBUTE)
			t.insert("size", liburl("struct-objects"), INT, ATTRIBUTE)
		}
	}

	internal inner class SysModule : NativeModule("sys") {

		override fun initBindings() {
			addUnknownFuncs(
					"_clear_type_cache", "call_tracing", "callstats", "_current_frames",
					"_getframe", "displayhook", "dont_write_bytecode", "exitfunc",
					"exc_clear", "exc_info", "excepthook", "exit",
					"last_traceback", "last_type", "last_value", "modules",
					"path_hooks", "path_importer_cache", "getprofile", "gettrace",
					"setcheckinterval", "setprofile", "setrecursionlimit", "settrace")
			addAttr("exc_type", liburl(), NONE)
			addUnknownAttrs("__stderr__", "__stdin__", "__stdout__",
					"stderr", "stdin", "stdout", "version_info")
			addNumAttrs("api_version", "hexversion", "winver", "maxint", "maxsize",
					"maxunicode", "py3kwarning", "dllhandle")
			addStrAttrs("platform", "byteorder", "copyright", "prefix", "version",
					"exec_prefix", "executable")
			addNumFuncs("getrecursionlimit", "getwindowsversion", "getrefcount",
					"getsizeof", "getcheckinterval")
			addStrFuncs("getdefaultencoding", "getfilesystemencoding")
			for (s in list("argv", "builtin_module_names", "path",
					"meta_path", "subversion")) {
				addAttr(s, liburl(), newList(STR))
			}
			for (s in list("flags", "warnoptions", "float_info")) {
				addAttr(s, liburl(), newDict(STR, INT))
			}
		}
	}

	internal inner class SyslogModule : NativeModule("syslog") {

		override fun initBindings() {
			addNoneFuncs("syslog", "openlog", "closelog", "setlogmask")
			addNumAttrs("LOG_ALERT", "LOG_AUTH", "LOG_CONS", "LOG_CRIT", "LOG_CRON",
					"LOG_DAEMON", "LOG_DEBUG", "LOG_EMERG", "LOG_ERR", "LOG_INFO",
					"LOG_KERN", "LOG_LOCAL0", "LOG_LOCAL1", "LOG_LOCAL2", "LOG_LOCAL3",
					"LOG_LOCAL4", "LOG_LOCAL5", "LOG_LOCAL6", "LOG_LOCAL7", "LOG_LPR",
					"LOG_MAIL", "LOG_MASK", "LOG_NDELAY", "LOG_NEWS", "LOG_NOTICE",
					"LOG_NOWAIT", "LOG_PERROR", "LOG_PID", "LOG_SYSLOG", "LOG_UPTO",
					"LOG_USER", "LOG_UUCP", "LOG_WARNING")
		}
	}

	internal inner class TermiosModule : NativeModule("termios") {

		override fun initBindings() {
			addFunction("tcgetattr", liburl(), newList())
			addUnknownFuncs("tcsetattr", "tcsendbreak", "tcdrain", "tcflush", "tcflow")
		}
	}

	internal inner class ThreadModule : NativeModule("thread") {

		override fun initBindings() {
			addClass("error", liburl(), newException("error", table))
			val lock = newClass("lock", table, objectType)
			lock.table.insert("acquire", liburl(), INT, METHOD)
			lock.table.insert("locked", liburl(), INT, METHOD)
			lock.table.insert("release", liburl(), NONE, METHOD)
			addAttr("LockType", liburl(), BaseType)
			addNoneFuncs("interrupt_main", "exit", "exit_thread")
			addNumFuncs("start_new", "start_new_thread", "get_ident", "stack_size")
			addFunction("allocate", liburl(), lock)
			addFunction("allocate_lock", liburl(), lock)  // synonym
			addAttr("_local", liburl(), BaseType)
		}
	}

	internal inner class TimeModule : NativeModule("time") {

		override fun initBindings() {
			Time_struct_time = InstanceType(newClass("datetime", table, objectType))
			val struct_time = Time_struct_time
			addAttr("struct_time", liburl(), struct_time)
			val struct_time_attrs = arrayOf("n_fields", "n_sequence_fields", "n_unnamed_fields", "tm_hour", "tm_isdst", "tm_mday", "tm_min", "tm_mon", "tm_wday", "tm_yday", "tm_year")
			for (s in struct_time_attrs) {
				struct_time.table.insert(s, liburl("struct_time"), INT, ATTRIBUTE)
			}
			addNumAttrs("accept2dyear", "altzone", "daylight", "timezone")
			addAttr("tzname", liburl(), newTuple(STR, STR))
			addNoneFuncs("sleep", "tzset")
			addNumFuncs("clock", "mktime", "time", "tzname")
			addStrFuncs("asctime", "ctime", "strftime")
			addFunctions_beCareful(struct_time, "gmtime", "localtime", "strptime")
		}
	}

	internal inner class UnicodedataModule : NativeModule("unicodedata") {

		override fun initBindings() {
			addNumFuncs("decimal", "digit", "numeric", "combining",
					"east_asian_width", "mirrored")
			addStrFuncs("lookup", "name", "category", "bidirectional",
					"decomposition", "normalize")
			addNumAttrs("unidata_version")
			addUnknownAttrs("ucd_3_2_0")
		}
	}

	internal inner class ZipimportModule : NativeModule("zipimport") {

		override fun initBindings() {
			addClass("ZipImportError", liburl(), newException("ZipImportError", table))
			val zipimporter = newClass("zipimporter", table, objectType)
			val t = zipimporter.table
			t.insert("find_module", liburl(), zipimporter, METHOD)
			t.insert("get_code", liburl(), UNKNOWN, METHOD)  // XXX:  code object
			t.insert("get_data", liburl(), UNKNOWN, METHOD)
			t.insert("get_source", liburl(), STR, METHOD)
			t.insert("is_package", liburl(), INT, METHOD)
			t.insert("load_module", liburl(), newModule("<?>"), METHOD)
			t.insert("archive", liburl(), STR, ATTRIBUTE)
			t.insert("prefix", liburl(), STR, ATTRIBUTE)
			addClass("zipimporter", liburl(), zipimporter)
			addAttr("_zip_directory_cache", liburl(), newDict(STR, UNKNOWN))
		}
	}

	internal inner class ZlibModule : NativeModule("zlib") {

		override fun initBindings() {
			val compress = newClass("Compress", table, objectType)
			for (s in list("compress", "flush")) {
				compress.table.insert(s, newLibUrl("zlib"), STR, METHOD)
			}
			compress.table.insert("copy", newLibUrl("zlib"), compress, METHOD)
			addClass("Compress", liburl(), compress)
			val decompress = newClass("Decompress", table, objectType)
			for (s in list("unused_data", "unconsumed_tail")) {
				decompress.table.insert(s, newLibUrl("zlib"), STR, ATTRIBUTE)
			}
			for (s in list("decompress", "flush")) {
				decompress.table.insert(s, newLibUrl("zlib"), STR, METHOD)
			}
			decompress.table.insert("copy", newLibUrl("zlib"), decompress, METHOD)
			addClass("Decompress", liburl(), decompress)
			addFunction("adler32", liburl(), INT)
			addFunction("compress", liburl(), STR)
			addFunction("compressobj", liburl(), compress)
			addFunction("crc32", liburl(), INT)
			addFunction("decompress", liburl(), STR)
			addFunction("decompressobj", liburl(), decompress)
		}
	}

	companion object {
		val LIBRARY_URL = "http://docs.python.org/library/"
		private val TUTORIAL_URL = "http://docs.python.org/tutorial/"
		private val REFERENCE_URL = "http://docs.python.org/reference/"
		val DATAMODEL_URL = "http://docs.python.org/reference/datamodel#"
		fun newLibUrl(module: String, name: String): Url = newLibUrl(module + ".html#" + name)
		fun newLibUrl(path: String): Url {
			var path = path
			if ("#" !in path && !path.endsWith(".html")) {
				path += ".html"
			}
			return Url(LIBRARY_URL + path)
		}

		fun newRefUrl(path: String): Url = Url(REFERENCE_URL + path)
		fun newDataModelUrl(path: String): Url = Url(DATAMODEL_URL + path)
		fun newTutUrl(path: String): Url = Url(TUTORIAL_URL + path)
	}
}
