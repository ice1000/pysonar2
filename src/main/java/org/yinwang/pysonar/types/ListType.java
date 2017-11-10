package org.yinwang.pysonar.types;

import org.jetbrains.annotations.NotNull;
import org.yinwang.pysonar.Analyzer;

import java.util.ArrayList;
import java.util.List;


public class ListType extends Type {

	public Type eltType;
	@NotNull
	public List<Type> positional = new ArrayList<>();
	@NotNull
	public List<Object> values = new ArrayList<>();


	public ListType() {
		this(Type.UNKNOWN);
	}


	public ListType(Type elt0) {
		eltType = elt0;
		getTable().addSuper(Analyzer.self.builtins.BaseList.getTable());
		getTable().setPath(Analyzer.self.builtins.BaseList.getTable().getPath());
	}


	public void setElementType(Type eltType) {
		this.eltType = eltType;
	}


	public void add(@NotNull Type another) {
		eltType = UnionType.union(eltType, another);
		positional.add(another);
	}


	public void addValue(Object v) {
		values.add(v);
	}


	public Type get(int i) {
		return positional.get(i);
	}


	@NotNull
	public TupleType toTupleType(int n) {
		TupleType ret = new TupleType();
		for (int i = 0; i < n; i++) {
			ret.add(eltType);
		}
		return ret;
	}


	@NotNull
	public TupleType toTupleType() {
		return new TupleType(positional);
	}


	@Override
	public boolean typeEquals(Object other) {
		if (Companion.getTypeStack().contains(this, other)) {
			return true;
		} else if (other instanceof ListType) {
			ListType co = (ListType) other;
			Companion.getTypeStack().push(this, other);
			boolean result = co.eltType.typeEquals(eltType);
			Companion.getTypeStack().pop(this, other);
			return result;
		} else {
			return false;
		}
	}


	@Override
	public int hashCode() {
		return "ListType".hashCode();
	}


	@Override
	public String printType(@NotNull CyclicTypeRecorder ctr) {
		StringBuilder sb = new StringBuilder();

		Integer num = ctr.visit(this);
		if (num != null) {
			sb.append("#").append(num);
		} else {
			ctr.push(this);
			sb.append("[");
			sb.append(eltType.printType(ctr));
			sb.append("]");
			ctr.pop(this);
		}

		return sb.toString();
	}

}
