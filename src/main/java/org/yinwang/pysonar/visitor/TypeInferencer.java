package org.yinwang.pysonar.visitor;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yinwang.pysonar.*;
import org.yinwang.pysonar.ast.*;
import org.yinwang.pysonar.types.*;

import java.util.*;

import static org.yinwang.pysonar.Binding.Kind.ATTRIBUTE;
import static org.yinwang.pysonar.Binding.Kind.CLASS;

public class TypeInferencer implements Visitor1<Type, State> {

	static void bindMethodAttrs(@NotNull FunType cl) {
		if (cl.getTable().getParent() != null) {
			Type cls = cl.getTable().getParent().getType();
			if (cls != null && cls instanceof ClassType) {
				addReadOnlyAttr(cl, "im_class", cls, CLASS);
				addReadOnlyAttr(cl, "__class__", cls, CLASS);
				addReadOnlyAttr(cl, "im_self", cls, ATTRIBUTE);
				addReadOnlyAttr(cl, "__self__", cls, ATTRIBUTE);
			}
		}
	}

	static void addReadOnlyAttr(
			@NotNull FunType fun,
			String name,
			@NotNull Type type,
			Binding.Kind kind) {
		Node loc = Builtins.Companion.newDataModelUrl("the-standard-type-hierarchy");
		Binding b = new Binding(name, loc, type, kind);
		fun.getTable().update(name, b);
		b.markSynthetic();
		b.markStatic();
	}

	@Contract(pure = true)
	static boolean missingReturn(@NotNull Type toType) {
		boolean hasNone = false;
		boolean hasOther = false;

		if (toType instanceof UnionType) {
			for (Type t : ((UnionType) toType).getTypes()) {
				if (t == Type.NONE || t == Type.CONT) {
					hasNone = true;
				} else {
					hasOther = true;
				}
			}
		}

		return hasNone && hasOther;
	}

	public static void bind(@NotNull State s, @NotNull Name name, @NotNull Type rvalue, Binding.Kind kind) {
		if (s.isGlobalName(name.getId())) {
			Set<Binding> bs = s.lookup(name.getId());
			if (bs != null) {
				for (Binding b : bs) {
					b.addType(rvalue);
					Analyzer.self.putRef(name, b);
				}
			}
		} else {
			s.insert(name.getId(), name, rvalue, kind);
		}
	}

	private static void reportUnpackMismatch(@NotNull List<Node> xs, int vsize) {
		int xsize = xs.size();
		int beg = xs.get(0).start;
		int end = xs.get(xs.size() - 1).end;
		int diff = xsize - vsize;
		String msg;
		if (diff > 0) {
			msg = "ValueError: need more than " + vsize + " values to unpack";
		} else {
			msg = "ValueError: too many values to unpack";
		}
		Analyzer.self.putProblem(xs.get(0).file, beg, end, msg);
	}

	@NotNull
	@Override
	public Type visit(Alias node, State s) {
		return Type.UNKNOWN;
	}

	@NotNull
	@Override
	public Type visit(Assert node, State s) {
		if (node.getTest() != null) {
			visit(node.getTest(), s);
		}
		if (node.getMsg() != null) {
			visit(node.getMsg(), s);
		}
		return Type.CONT;
	}

	@NotNull
	@Override
	public Type visit(Assign node, State s) {
		Type valueType = visit(node.getValue(), s);
		bind(s, node.getTarget(), valueType);
		return Type.CONT;
	}

	@NotNull
	@Override
	public Type visit(Attribute node, State s) {
		Type targetType = visit(node.getTarget(), s);
		if (targetType instanceof UnionType) {
			Set<Type> types = ((UnionType) targetType).getTypes();
			Type retType = Type.UNKNOWN;
			for (Type tt : types) {
				retType = UnionType.Companion.union(retType, getAttrType(node, tt));
			}
			return retType;
		} else {
			return getAttrType(node, targetType);
		}
	}

	@NotNull
	@Override
	public Type visit(Await node, State s) {
		if (node.getValue() == null) {
			return Type.NONE;
		} else {
			return visit(node.getValue(), s);
		}
	}

	@NotNull
	@Override
	public Type visit(BinOp node, State s) {
		Type ltype = visit(node.getLeft(), s);
		Type rtype = visit(node.getRight(), s);
		if (operatorOverridden(ltype, node.getOp().getMethod())) {
			Type result = applyOp(node.getOp(), ltype, rtype, node.getOp().getMethod(), node, node.getLeft());
			if (result != null) {
				return result;
			}
		} else if (Op.isBoolean(node.getOp())) {
			return Type.BOOL;
		} else if (ltype == Type.UNKNOWN) {
			return rtype;
		} else if (rtype == Type.UNKNOWN) {
			return ltype;
		} else if (ltype.typeEquals(rtype)) {
			return ltype;
		}

		Analyzer.self.putProblem(node, "Cannot apply binary operator " + node.getOp().getRep() +
				" to type " + ltype + " and " + rtype);
		return Type.UNKNOWN;
	}

	private boolean operatorOverridden(Type type, String method) {
		if (type instanceof InstanceType) {
			Type opType = type.getTable().lookupAttrType(method);
			if (opType != null) {
				return true;
			}
		}
		return false;
	}

	@Nullable
	private Type applyOp(Op op, Type ltype, Type rtype, String method, Node node, Node left) {
		Type opType = ltype.getTable().lookupAttrType(method);
		if (opType instanceof FunType) {
			((FunType) opType).setSelfType(ltype);
			return apply((FunType) opType, Collections.singletonList(rtype), null, null, null, node);
		} else {
			Analyzer.self.putProblem(left, "Operator method " + method + " is not a function");
			return null;
		}
	}

	@NotNull
	@Override
	public Type visit(Block node, State s) {
		// first pass: mark global names
		for (Node n : node.getSeq()) {
			if (n instanceof Global) {
				for (Name name : ((Global) n).names) {
					s.addGlobalName(name.getId());
					Set<Binding> nb = s.lookup(name.getId());
					if (nb != null) {
						Analyzer.self.putRef(name, nb);
					}
				}
			}
		}

		boolean returned = false;
		Type retType = Type.UNKNOWN;

		for (Node n : node.getSeq()) {
			Type t = visit(n, s);
			if (!returned) {
				retType = UnionType.Companion.union(retType, t);
				if (!UnionType.Companion.contains(t, Type.CONT)) {
					returned = true;
					retType = UnionType.Companion.remove(retType, Type.CONT);
				}
			}
		}

		return retType;
	}

	@NotNull
	@Override
	public Type visit(Break node, State s) {
		return Type.NONE;
	}

	@NotNull
	@Override
	public Type visit(Bytes node, State s) {
		return Type.STR;
	}

	@NotNull
	@Override
	public Type visit(Call node, State s) {
		Type fun = visit(node.getFunc(), s);
		List<Type> pos = visit(node.getArgs(), s);
		Map<String, Type> hash = new HashMap<>();

		if (node.getKeywords() != null) {
			for (Keyword kw : node.getKeywords()) {
				hash.put(kw.arg, visit(kw.value, s));
			}
		}

		Type kw = node.getKwargs() == null ? null : visit(node.getKwargs(), s);
		Type star = node.getStarargs() == null ? null : visit(node.getStarargs(), s);

		if (fun instanceof UnionType) {
			Set<Type> types = ((UnionType) fun).getTypes();
			Type retType = Type.UNKNOWN;
			for (Type ft : types) {
				Type t = resolveCall(node, ft, pos, hash, kw, star);
				retType = UnionType.Companion.union(retType, t);
			}
			return retType;
		} else {
			return resolveCall(node, fun, pos, hash, kw, star);
		}
	}

	@NotNull
	@Override
	public Type visit(ClassDef node, State s) {
		ClassType classType = new ClassType(node.name.id, s);
		List<Type> baseTypes = new ArrayList<>();
		for (Node base : node.getBases()) {
			Type baseType = visit(base, s);
			if (baseType instanceof ClassType) {
				classType.addSuper(baseType);
			} else if (baseType instanceof UnionType) {
				for (Type parent : ((UnionType) baseType).getTypes()) {
					classType.addSuper(parent);
				}
			} else {
				Analyzer.self.putProblem(base, base + " is not a class");
			}
			baseTypes.add(baseType);
		}

		// XXX: Not sure if we should add "bases", "name" and "dict" here. They
		// must be added _somewhere_ but I'm just not sure if it should be HERE.
		node.addSpecialAttribute(classType.getTable(), "__bases__", new TupleType(baseTypes));
		node.addSpecialAttribute(classType.getTable(), "__name__", Type.STR);
		node.addSpecialAttribute(classType.getTable(), "__dict__",
				new DictType(Type.STR, Type.UNKNOWN));
		node.addSpecialAttribute(classType.getTable(), "__module__", Type.STR);
		node.addSpecialAttribute(classType.getTable(), "__doc__", Type.STR);

		// Bind ClassType to name here before resolving the body because the
		// methods need node type as self.
		bind(s, node.name, classType, Binding.Kind.CLASS);
		if (node.getBody() != null) {
			visit(node.getBody(), classType.getTable());
		}
		return Type.CONT;
	}

	@NotNull
	@Override
	public Type visit(Comprehension node, State s) {
		bindIter(s, node.getTarget(), node.getIter(), Binding.Kind.SCOPE);
		visit(node.getIfs(), s);
		return visit(node.getTarget(), s);
	}

	@NotNull
	@Override
	public Type visit(Continue node, State s) {
		return Type.CONT;
	}

	@NotNull
	@Override
	public Type visit(Delete node, State s) {
		for (Node n : node.getTargets()) {
			visit(n, s);
			if (n instanceof Name) {
				s.remove(((Name) n).getId());
			}
		}
		return Type.CONT;
	}

	@NotNull
	@Override
	public Type visit(Dict node, State s) {
		Type keyType = resolveUnion(node.getKeys(), s);
		Type valType = resolveUnion(node.getValues(), s);
		return new DictType(keyType, valType);
	}

	@NotNull
	@Override
	public Type visit(DictComp node, State s) {
		visit(node.getGenerators(), s);
		Type keyType = visit(node.getKey(), s);
		Type valueType = visit(node.getValue(), s);
		return new DictType(keyType, valueType);
	}

	@NotNull
	@Override
	public Type visit(Dummy node, State s) {
		return Type.UNKNOWN;
	}

	@NotNull
	@Override
	public Type visit(Ellipsis node, State s) {
		return Type.NONE;
	}

	@NotNull
	@Override
	public Type visit(Exec node, State s) {
		if (node.getBody() != null) {
			visit(node.getBody(), s);
		}
		if (node.getGlobals() != null) {
			visit(node.getGlobals(), s);
		}
		if (node.getLocals() != null) {
			visit(node.getLocals(), s);
		}
		return Type.CONT;
	}

	@NotNull
	@Override
	public Type visit(Expr node, State s) {
		if (node.getValue() != null) {
			visit(node.getValue(), s);
		}
		return Type.CONT;

	}

	@NotNull
	@Override
	public Type visit(ExtSlice node, State s) {
		for (Node d : node.getDims()) {
			visit(d, s);
		}
		return new ListType();
	}

	@NotNull
	@Override
	public Type visit(For node, State s) {
		bindIter(s, node.getTarget(), node.getIter(), Binding.Kind.SCOPE);

		Type ret;
		if (node.getBody() == null) {
			ret = Type.UNKNOWN;
		} else {
			ret = visit(node.getBody(), s);
		}
		if (node.getOrelse() != null) {
			ret = UnionType.Companion.union(ret, visit(node.getOrelse(), s));
		}
		return ret;
	}

	@NotNull
	@Override
	public Type visit(FunctionDef node, State s) {
		State env = s.getForwarding();
		FunType fun = new FunType(node, env);
		fun.getTable().setParent(s);
		fun.getTable().setPath(s.extendPath(node.getName().getId()));
		fun.setDefaultTypes(visit(node.getDefaults(), s));
		Analyzer.self.addUncalled(fun);
		Binding.Kind funkind;

		if (node.getIsLamba()) {
			return fun;
		} else {
			if (s.getStateType() == State.StateType.CLASS) {
				if ("__init__".equals(node.getName().getId())) {
					funkind = Binding.Kind.CONSTRUCTOR;
				} else {
					funkind = Binding.Kind.METHOD;
				}
			} else {
				funkind = Binding.Kind.FUNCTION;
			}

			Type outType = s.getType();
			if (outType instanceof ClassType) {
				fun.setCls((ClassType) outType);
			}

			bind(s, node.getName(), fun, funkind);
			return Type.CONT;
		}
	}

	@NotNull
	@Override
	public Type visit(GeneratorExp node, State s) {
		visit(node.generators, s);
		return new ListType(visit(node.elt, s));
	}

	@NotNull
	@Override
	public Type visit(Global node, State s) {
		return Type.CONT;
	}

	@NotNull
	@Override
	public Type visit(Handler node, State s) {
		Type typeval = Type.UNKNOWN;
		if (node.exceptions != null) {
			typeval = resolveUnion(node.exceptions, s);
		}
		if (node.binder != null) {
			bind(s, node.binder, typeval);
		}
		if (node.body != null) {
			return visit(node.body, s);
		} else {
			return Type.UNKNOWN;
		}
	}

	@NotNull
	@Override
	public Type visit(If node, State s) {
		Type type1, type2;
		State s1 = s.copy();
		State s2 = s.copy();

		// ignore condition for now
		visit(node.test, s);

		if (node.body != null) {
			type1 = visit(node.body, s1);
		} else {
			type1 = Type.CONT;
		}

		if (node.orelse != null) {
			type2 = visit(node.orelse, s2);
		} else {
			type2 = Type.CONT;
		}

		boolean cont1 = UnionType.Companion.contains(type1, Type.CONT);
		boolean cont2 = UnionType.Companion.contains(type2, Type.CONT);

		// decide which branch affects the downstream state
		if (cont1 && cont2) {
			s1.merge(s2);
			s.overwrite(s1);
		} else if (cont1) {
			s.overwrite(s1);
		} else if (cont2) {
			s.overwrite(s2);
		}

		return UnionType.Companion.union(type1, type2);
	}

	@NotNull
	@Override
	public Type visit(IfExp node, State s) {
		Type type1, type2;
		visit(node.test, s);

		if (node.body != null) {
			type1 = visit(node.body, s);
		} else {
			type1 = Type.CONT;
		}
		if (node.orelse != null) {
			type2 = visit(node.orelse, s);
		} else {
			type2 = Type.CONT;
		}
		return UnionType.Companion.union(type1, type2);
	}

	@NotNull
	@Override
	public Type visit(Import node, State s) {
		for (Alias a : node.names) {
			Type mod = Analyzer.self.loadModule(a.name, s);
			if (mod == null) {
				Analyzer.self.putProblem(node, "Cannot load module");
			} else if (a.getAsname() != null) {
				s.insert(a.getAsname().getId(), a.getAsname(), mod, Binding.Kind.VARIABLE);
			}
		}
		return Type.CONT;
	}

	@NotNull
	@Override
	public Type visit(ImportFrom node, State s) {
		if (node.module == null) {
			return Type.CONT;
		}

		Type mod = Analyzer.self.loadModule(node.module, s);

		if (mod == null) {
			Analyzer.self.putProblem(node, "Cannot load module");
		} else if (node.isImportStar()) {
			node.importStar(s, mod);
		} else {
			for (Alias a : node.names) {
				Name first = a.name.get(0);
				Set<Binding> bs = mod.getTable().lookup(first.getId());
				if (bs != null) {
					if (a.getAsname() != null) {
						s.update(a.getAsname().getId(), bs);
						Analyzer.self.putRef(a.getAsname(), bs);
					} else {
						s.update(first.getId(), bs);
						Analyzer.self.putRef(first, bs);
					}
				} else {
					List<Name> ext = new ArrayList<>(node.module);
					ext.add(first);
					Type mod2 = Analyzer.self.loadModule(ext, s);
					if (mod2 != null) {
						if (a.getAsname() != null) {
							s.insert(a.getAsname().getId(), a.getAsname(), mod2, Binding.Kind.VARIABLE);
						} else {
							s.insert(first.getId(), first, mod2, Binding.Kind.VARIABLE);
						}
					}
				}
			}
		}

		return Type.CONT;
	}

	@NotNull
	@Override
	public Type visit(Index node, State s) {
		return visit(node.value, s);
	}

	@NotNull
	@Override
	public Type visit(Keyword node, State s) {
		return visit(node.value, s);
	}

	@NotNull
	@Override
	public Type visit(ListComp node, State s) {
		visit(node.generators, s);
		return new ListType(visit(node.elt, s));
	}

	@NotNull
	@Override
	public Type visit(Module node, State s) {
		ModuleType mt = new ModuleType(node.name, node.file, Analyzer.self.globaltable);
		s.insert($.moduleQname(node.file), node, mt, Binding.Kind.MODULE);
		if (node.getBody() != null) {
			visit(node.getBody(), mt.getTable());
		}
		return mt;
	}

	@NotNull
	@Override
	public Type visit(Name node, State s) {
		Set<Binding> b = s.lookup(node.getId());
		if (b != null) {
			Analyzer.self.putRef(node, b);
			Analyzer.self.resolved.add(node);
			Analyzer.self.unresolved.remove(node);
			return State.Companion.makeUnion(b);
		} else if (node.getId().equals("True") || node.getId().equals("False")) {
			return Type.BOOL;
		} else {
			Analyzer.self.putProblem(node, "unbound variable " + node.getId());
			Analyzer.self.unresolved.add(node);
			Type t = Type.UNKNOWN;
			t.getTable().setPath(s.extendPath(node.getId()));
			return t;
		}
	}

	@NotNull
	@Override
	public Type visit(Pass node, State s) {
		return Type.CONT;
	}

	@NotNull
	@Override
	public Type visit(Print node, State s) {
		if (node.dest != null) {
			visit(node.dest, s);
		}
		if (node.values != null) {
			visit(node.values, s);
		}
		return Type.CONT;
	}

	@NotNull
	@Override
	public Type visit(PyComplex node, State s) {
		return Type.COMPLEX;
	}

	@NotNull
	@Override
	public Type visit(PyFloat node, State s) {
		return Type.FLOAT;
	}

	@NotNull
	@Override
	public Type visit(PyInt node, State s) {
		return Type.INT;
	}

	@NotNull
	@Override
	public Type visit(PyList node, State s) {
		if (node.elts.size() == 0) {
			return new ListType();  // list<unknown>
		}

		ListType listType = new ListType();
		for (Node elt : node.elts) {
			listType.add(visit(elt, s));
			if (elt instanceof Str) {
				listType.addValue(((Str) elt).value);
			}
		}

		return listType;
	}

	@NotNull
	@Override
	public Type visit(PySet node, State s) {
		if (node.elts.size() == 0) {
			return new ListType();
		}

		ListType listType = null;
		for (Node elt : node.elts) {
			if (listType == null) {
				listType = new ListType(visit(elt, s));
			} else {
				listType.add(visit(elt, s));
			}
		}

		return listType;
	}

	@NotNull
	@Override
	public Type visit(Raise node, State s) {
		if (node.exceptionType != null) {
			visit(node.exceptionType, s);
		}
		if (node.inst != null) {
			visit(node.inst, s);
		}
		if (node.traceback != null) {
			visit(node.traceback, s);
		}
		return Type.CONT;
	}

	@NotNull
	@Override
	public Type visit(Repr node, State s) {
		if (node.value != null) {
			visit(node.value, s);
		}
		return Type.STR;
	}

	@NotNull
	@Override
	public Type visit(Return node, State s) {
		if (node.value == null) {
			return Type.NONE;
		} else {
			return visit(node.value, s);
		}
	}

	@NotNull
	@Override
	public Type visit(SetComp node, State s) {
		visit(node.generators, s);
		return new ListType(visit(node.elt, s));
	}

	@NotNull
	@Override
	public Type visit(Slice node, State s) {
		if (node.lower != null) {
			visit(node.lower, s);
		}
		if (node.step != null) {
			visit(node.step, s);
		}
		if (node.upper != null) {
			visit(node.upper, s);
		}
		return new ListType();
	}

	@NotNull
	@Override
	public Type visit(Starred node, State s) {
		return visit(node.value, s);
	}

	@NotNull
	@Override
	public Type visit(Str node, State s) {
		return Type.STR;
	}

	@NotNull
	@Override
	public Type visit(Subscript node, State s) {
		Type vt = visit(node.value, s);
		Type st = node.slice == null ? null : visit(node.slice, s);

		if (vt instanceof UnionType) {
			Type retType = Type.UNKNOWN;
			for (Type t : ((UnionType) vt).getTypes()) {
				retType = UnionType.Companion.union(retType, getSubscript(node, t, st, s));
			}
			return retType;
		} else {
			return getSubscript(node, vt, st, s);
		}
	}

	@NotNull
	@Override
	public Type visit(Try node, State s) {
		Type tp1 = Type.UNKNOWN;
		Type tp2 = Type.UNKNOWN;
		Type tph = Type.UNKNOWN;
		Type tpFinal = Type.UNKNOWN;

		if (node.handlers != null) {
			for (Handler h : node.handlers) {
				tph = UnionType.Companion.union(tph, visit(h, s));
			}
		}

		if (node.body != null) {
			tp1 = visit(node.body, s);
		}

		if (node.orelse != null) {
			tp2 = visit(node.orelse, s);
		}

		if (node.finalbody != null) {
			tpFinal = visit(node.finalbody, s);
		}

		return new UnionType(tp1, tp2, tph, tpFinal);
	}

	@NotNull
	@Override
	public Type visit(Tuple node, State s) {
		TupleType t = new TupleType();
		for (Node e : node.elts) {
			t.add(visit(e, s));
		}
		return t;
	}

	@NotNull
	@Override
	public Type visit(UnaryOp node, State s) {
		return visit(node.operand, s);
	}

	@NotNull
	@Override
	public Type visit(Unsupported node, State s) {
		return Type.NONE;
	}

	@NotNull
	@Override
	public Type visit(Url node, State s) {
		return Type.STR;
	}

	@NotNull
	@Override
	public Type visit(While node, State s) {
		visit(node.test, s);
		Type t = Type.UNKNOWN;

		if (node.body != null) {
			t = visit(node.body, s);
		}

		if (node.orelse != null) {
			t = UnionType.Companion.union(t, visit(node.orelse, s));
		}

		return t;
	}

	@NotNull
	@Override
	public Type visit(With node, State s) {
		for (Withitem item : node.items) {
			Type val = visit(item.context_expr, s);
			if (item.optional_vars != null) {
				bind(s, item.optional_vars, val);
			}
		}
		return visit(node.body, s);
	}

	@NotNull
	@Override
	public Type visit(Withitem node, State s) {
		return Type.UNKNOWN;
	}

	@NotNull
	@Override
	public Type visit(Yield node, State s) {
		if (node.value != null) {
			return new ListType(visit(node.value, s));
		} else {
			return Type.NONE;
		}
	}

	@NotNull
	@Override
	public Type visit(YieldFrom node, State s) {
		if (node.value != null) {
			return new ListType(visit(node.value, s));
		} else {
			return Type.NONE;
		}
	}

	@NotNull
	private Type resolveUnion(@NotNull Collection<? extends Node> nodes, State s) {
		Type result = Type.UNKNOWN;
		for (Node node : nodes) {
			Type nodeType = visit(node, s);
			result = UnionType.Companion.union(result, nodeType);
		}
		return result;
	}

	public void setAttr(Attribute node, State s, @NotNull Type v) {
		Type targetType = visit(node.getTarget(), s);
		if (targetType instanceof UnionType) {
			Set<Type> types = ((UnionType) targetType).getTypes();
			for (Type tp : types) {
				setAttrType(node, tp, v);
			}
		} else {
			setAttrType(node, targetType, v);
		}
	}

	private void addRef(Attribute node, @NotNull Type targetType, @NotNull Set<Binding> bs) {
		for (Binding b : bs) {
			Analyzer.self.putRef(node.getAttr(), b);
			if (node.parent != null && node.parent instanceof Call &&
					b.type instanceof FunType && targetType instanceof InstanceType) {  // method call
				((FunType) b.type).setSelfType(targetType);
			}
		}
	}

	private void setAttrType(Attribute node, @NotNull Type targetType, @NotNull Type v) {
		if (targetType.isUnknownType()) {
			Analyzer.self.putProblem(node, "Can't set attribute for UnknownType");
			return;
		}
		Set<Binding> bs = targetType.getTable().lookupAttr(node.getAttr().getId());
		if (bs != null) {
			addRef(node, targetType, bs);
		}

		targetType.getTable().insert(node.getAttr().getId(), node.getAttr(), v, ATTRIBUTE);
	}

	public Type getAttrType(Attribute node, @NotNull Type targetType) {
		Set<Binding> bs = targetType.getTable().lookupAttr(node.getAttr().getId());
		if (bs == null) {
			Analyzer.self.putProblem(node.getAttr(), "attribute not found in type: " + targetType);
			Type t = Type.UNKNOWN;
			t.getTable().setPath(targetType.getTable().extendPath(node.getAttr().getId()));
			return t;
		} else {
			addRef(node, targetType, bs);
			return State.Companion.makeUnion(bs);
		}
	}

	@NotNull
	public Type resolveCall(Call node, @NotNull Type fun,
	                        List<Type> pos,
	                        Map<String, Type> hash,
	                        Type kw,
	                        Type star) {
		if (fun instanceof FunType) {
			FunType ft = (FunType) fun;
			return apply(ft, pos, hash, kw, star, node);
		} else if (fun instanceof ClassType) {
			return new InstanceType(fun, node, pos, this);
		} else {
			addWarning(node, "calling non-function and non-class: " + fun);
			return Type.UNKNOWN;
		}
	}

	@NotNull
	public Type apply(@NotNull FunType func,
	                  @Nullable List<Type> pos,
	                  Map<String, Type> hash,
	                  Type kw,
	                  Type star,
	                  @Nullable Node call) {
		Analyzer.self.removeUncalled(func);

		if (func.func != null && !func.func.getCalled()) {
			Analyzer.self.nCalled++;
			func.func.setCalled(true);
		}

		if (func.func == null) {
			// func without definition (possibly builtins)
			return func.getReturnType();
		}

		List<Type> pTypes = new ArrayList<>();

		if (func.selfType != null) {
			pTypes.add(func.selfType);
		} else {
			if (func.cls != null) {
				pTypes.add(func.cls.getCanon());
			}
		}

		if (pos != null) {
			pTypes.addAll(pos);
		}

		bindMethodAttrs(func);

		State funcTable = new State(func.env, State.StateType.FUNCTION);

		if (func.getTable().getParent() != null) {
			funcTable.setPath(func.getTable().getParent().extendPath(func.func.getName().getId()));
		} else {
			funcTable.setPath(func.func.getName().getId());
		}

		Type fromType = bindParams(call, func.func, funcTable, func.func.getArgs(),
				func.func.getVararg(), func.func.getKwarg(),
				pTypes, func.defaultTypes, hash, kw, star);

		Type cachedTo = func.getMapping(fromType);

		if (cachedTo != null) {
			func.setSelfType(null);
			return cachedTo;
		} else if (func.oversized()) {
			func.setSelfType(null);
			return Type.UNKNOWN;
		} else {
			func.addMapping(fromType, Type.UNKNOWN);
			Type toType = visit(func.func.getBody(), funcTable);
			if (missingReturn(toType)) {
				Analyzer.self.putProblem(func.func.getName(), "Function not always return a value");

				if (call != null) {
					Analyzer.self.putProblem(call, "Call not always return a value");
				}
			}

			toType = UnionType.Companion.remove(toType, Type.CONT);
			func.addMapping(fromType, toType);
			func.setSelfType(null);
			return toType;
		}
	}

	@NotNull
	private Type bindParams(
			@Nullable Node call,
			@NotNull FunctionDef func,
			@NotNull State funcTable,
			@Nullable List<Node> args,
			@Nullable Name rest,
			@Nullable Name restKw,
			@Nullable List<Type> pTypes,
			@Nullable List<Type> dTypes,
			@Nullable Map<String, Type> hash,
			@Nullable Type kw,
			@Nullable Type star) {
		TupleType fromType = new TupleType();
		int pSize = args == null ? 0 : args.size();
		int aSize = pTypes == null ? 0 : pTypes.size();
		int dSize = dTypes == null ? 0 : dTypes.size();
		int nPos = pSize - dSize;

		if (star != null && star instanceof ListType) {
			star = ((ListType) star).toTupleType();
		}

		for (int i = 0, j = 0; i < pSize; i++) {
			Node arg = args.get(i);
			Type aType;
			if (i < aSize) {
				aType = pTypes.get(i);
			} else if (i - nPos >= 0 && i - nPos < dSize) {
				aType = dTypes.get(i - nPos);
			} else {
				if (hash != null && args.get(i) instanceof Name &&
						hash.containsKey(((Name) args.get(i)).getId())) {
					aType = hash.get(((Name) args.get(i)).getId());
					hash.remove(((Name) args.get(i)).getId());
				} else {
					if (star != null && star instanceof TupleType &&
							j < ((TupleType) star).eltTypes.size()) {
						aType = ((TupleType) star).get(j++);
					} else {
						aType = Type.UNKNOWN;
						if (call != null) {
							Analyzer.self.putProblem(args.get(i),
									"unable to bind argument:" + args.get(i));
						}
					}
				}
			}
			bind(funcTable, arg, aType, Binding.Kind.PARAMETER);
			fromType.add(aType);
		}

		if (restKw != null) {
			if (hash != null && !hash.isEmpty()) {
				Type hashType = UnionType.Companion.newUnion(hash.values());
				bind(
						funcTable,
						restKw,
						new DictType(Type.STR, hashType),
						Binding.Kind.PARAMETER);
			} else {
				bind(funcTable,
						restKw,
						Type.UNKNOWN,
						Binding.Kind.PARAMETER);
			}
		}

		if (rest != null) {
			if (pTypes.size() > pSize) {
				if (func.getAfterRest() != null) {
					int nAfter = func.getAfterRest().size();
					for (int i = 0; i < nAfter; i++) {
						bind(funcTable, func.getAfterRest().get(i),
								pTypes.get(pTypes.size() - nAfter + i),
								Binding.Kind.PARAMETER);
					}
					if (pTypes.size() - nAfter > 0) {
						Type restType = new TupleType(pTypes.subList(pSize, pTypes.size() - nAfter));
						bind(funcTable, rest, restType, Binding.Kind.PARAMETER);
					}
				} else {
					Type restType = new TupleType(pTypes.subList(pSize, pTypes.size()));
					bind(funcTable, rest, restType, Binding.Kind.PARAMETER);
				}
			} else {
				bind(funcTable,
						rest,
						Type.UNKNOWN,
						Binding.Kind.PARAMETER);
			}
		}

		return fromType;
	}

	@NotNull
	public Type getSubscript(Node node, @NotNull Type vt, @Nullable Type st, State s) {
		if (vt.isUnknownType()) {
			return Type.UNKNOWN;
		} else {
			if (vt instanceof ListType) {
				return getListSubscript(node, vt, st, s);
			} else if (vt instanceof TupleType) {
				return getListSubscript(node, ((TupleType) vt).toListType(), st, s);
			} else if (vt instanceof DictType) {
				DictType dt = (DictType) vt;
				if (!dt.getKeyType().equals(st)) {
					addWarning(node, "Possible KeyError (wrong type for subscript)");
				}
				return ((DictType) vt).getValueType();
			} else if (vt == Type.STR) {
				if (st != null && (st instanceof ListType || st.isNumType())) {
					return vt;
				} else {
					addWarning(node, "Possible KeyError (wrong type for subscript)");
					return Type.UNKNOWN;
				}
			} else {
				return Type.UNKNOWN;
			}
		}
	}

	@NotNull
	private Type getListSubscript(Node node, @NotNull Type vt, @Nullable Type st, State s) {
		if (vt instanceof ListType) {
			if (st != null && st instanceof ListType) {
				return vt;
			} else if (st == null || st.isNumType()) {
				return ((ListType) vt).eltType;
			} else {
				Type sliceFunc = vt.getTable().lookupAttrType("__getslice__");
				if (sliceFunc == null) {
					addError(node, "The type can't be sliced: " + vt);
					return Type.UNKNOWN;
				} else if (sliceFunc instanceof FunType) {
					return apply((FunType) sliceFunc, null, null, null, null, node);
				} else {
					addError(node, "The type's __getslice__ method is not a function: " + sliceFunc);
					return Type.UNKNOWN;
				}
			}
		} else {
			return Type.UNKNOWN;
		}
	}

	public void bind(@NotNull State s, Node target, @NotNull Type rvalue, Binding.Kind kind) {
		if (target instanceof Name) {
			bind(s, (Name) target, rvalue, kind);
		} else if (target instanceof Tuple) {
			bind(s, ((Tuple) target).elts, rvalue, kind);
		} else if (target instanceof PyList) {
			bind(s, ((PyList) target).elts, rvalue, kind);
		} else if (target instanceof Attribute) {
			setAttr(((Attribute) target), s, rvalue);
		} else if (target instanceof Subscript) {
			Subscript sub = (Subscript) target;
			Type valueType = visit(sub.value, s);
			visit(sub.slice, s);
			if (valueType instanceof ListType) {
				ListType t = (ListType) valueType;
				t.setElementType(UnionType.Companion.union(t.eltType, rvalue));
			}
		} else if (target != null) {
			Analyzer.self.putProblem(target, "invalid location for assignment");
		}
	}

	/**
	 * Without specifying a kind, bind determines the kind according to the type
	 * of the scope.
	 */
	public void bind(@NotNull State s, Node target, @NotNull Type rvalue) {
		Binding.Kind kind;
		if (s.getStateType() == State.StateType.FUNCTION) {
			kind = Binding.Kind.VARIABLE;
		} else if (s.getStateType() == State.StateType.CLASS ||
				s.getStateType() == State.StateType.INSTANCE) {
			kind = Binding.Kind.ATTRIBUTE;
		} else {
			kind = Binding.Kind.SCOPE;
		}
		bind(s, target, rvalue, kind);
	}

	public void bind(@NotNull State s, @NotNull List<Node> xs, @NotNull Type rvalue, Binding.Kind kind) {
		if (rvalue instanceof TupleType) {
			List<Type> vs = ((TupleType) rvalue).eltTypes;
			if (xs.size() != vs.size()) {
				reportUnpackMismatch(xs, vs.size());
			} else {
				for (int i = 0; i < xs.size(); i++) {
					bind(s, xs.get(i), vs.get(i), kind);
				}
			}
		} else {
			if (rvalue instanceof ListType) {
				bind(s, xs, ((ListType) rvalue).toTupleType(xs.size()), kind);
			} else if (rvalue instanceof DictType) {
				bind(s, xs, ((DictType) rvalue).toTupleType(xs.size()), kind);
			} else if (rvalue.isUnknownType()) {
				for (Node x : xs) {
					bind(s, x, Type.UNKNOWN, kind);
				}
			} else if (xs.size() > 0) {
				Analyzer.self.putProblem(xs.get(0).file,
						xs.get(0).start,
						xs.get(xs.size() - 1).end,
						"unpacking non-iterable: " + rvalue);
			}
		}
	}

	// iterator
	public void bindIter(@NotNull State s, Node target, @NotNull Node iter, Binding.Kind kind) {
		Type iterType = visit(iter, s);

		if (iterType instanceof ListType) {
			bind(s, target, ((ListType) iterType).eltType, kind);
		} else if (iterType instanceof TupleType) {
			bind(s, target, ((TupleType) iterType).toListType().eltType, kind);
		} else {
			Set<Binding> ents = iterType.getTable().lookupAttr("__iter__");
			if (ents != null) {
				for (Binding ent : ents) {
					if (ent == null || !(ent.type instanceof FunType)) {
						if (!iterType.isUnknownType()) {
							Analyzer.self.putProblem(iter, "not an iterable type: " + iterType);
						}
						bind(s, target, Type.UNKNOWN, kind);
					} else {
						bind(s, target, ((FunType) ent.type).getReturnType(), kind);
					}
				}
			} else {
				bind(s, target, Type.UNKNOWN, kind);
			}
		}
	}

	public void addWarning(Node node, String msg) {
		Analyzer.self.putProblem(node, msg);
	}

	public void addError(Node node, String msg) {
		Analyzer.self.putProblem(node, msg);
	}
}
