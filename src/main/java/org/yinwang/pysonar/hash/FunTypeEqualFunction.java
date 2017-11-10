package org.yinwang.pysonar.hash;

import org.yinwang.pysonar.types.FunType;


public class FunTypeEqualFunction extends EqualFunction {

	public boolean equals(Object x, Object y) {
		if (x instanceof FunType && y instanceof FunType) {
			FunType xx = (FunType) x;
			FunType yy = (FunType) y;
			return xx == yy ||
					xx.getTable().getPath().equals(yy.getTable().getPath());
		} else {
			return x.equals(y);
		}
	}
}
