package org.expresso.runtime.typed;

import org.expresso.runtime.ControlFlowDirective;
import org.expresso.runtime.Result;

import com.google.common.reflect.TypeToken;

public interface TypedResult<V> extends Result<V> {
	static <T> TypedResult<T> noReturn(TypeToken<T> type) {
		return new TypedResult<T>() {
			@Override
			public ControlFlowDirective getDirective() {
				return ControlFlowDirective.NORMAL;
			}

			@Override
			public String getDirectiveLabel() {
				throw new IllegalStateException();
			}

			@Override
			public T get() {
				return null;
			}

			@Override
			public TypeToken<T> getType() {
				return type;
			}
		};
	}

	TypeToken<V> getType();
}
