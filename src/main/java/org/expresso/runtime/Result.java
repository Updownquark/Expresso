package org.expresso.runtime;

import java.util.function.Supplier;

public interface Result<V> extends Supplier<V> {
	static Result<?> NO_RETURN = new Result<Object>() {
		@Override
		public Object get() {
			return null;
		}

		@Override
		public ControlFlowDirective getDirective() {
			return ControlFlowDirective.NORMAL;
		}

		@Override
		public String getDirectiveLabel() {
			throw new IllegalStateException();
		}
	};

	static <T> Result<T> noReturn() {
		return (Result<T>) NO_RETURN;
	}

	ControlFlowDirective getDirective();

	String getDirectiveLabel();
}
