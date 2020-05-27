package org.expresso.grammars.java8;

import java.util.List;

import org.expresso.Expression;
import org.expresso.runtime.EvaluationTargetException;
import org.expresso.runtime.typed.TypedBlock;
import org.expresso.runtime.typed.TypedResult;
import org.expresso.runtime.typed.TypedStatement;

import com.google.common.reflect.TypeToken;

public class SynchronizedStatement<E extends JavaEnvironment<E>, R> extends TypedBlock<E, R> {
	private final TypedStatement<E, ?> theSyncValue;

	public SynchronizedStatement(Expression expression, TypeToken<R> type, TypedStatement<E, ?> syncValue,
		List<? extends TypedStatement<E, ? extends R>> statements) {
		super(expression, type, statements);
		theSyncValue = syncValue;
	}

	@Override
	public TypedResult<? extends R> execute(E env) throws EvaluationTargetException {
		Object syncValue = theSyncValue.execute(env).get();
		synchronized (syncValue) {
			return super.execute(env);
		}
	}
}
