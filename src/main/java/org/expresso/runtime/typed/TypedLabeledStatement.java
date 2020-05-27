package org.expresso.runtime.typed;

import org.expresso.Expression;
import org.expresso.runtime.EvaluationTargetException;
import org.expresso.runtime.LabeledStatement;

import com.google.common.reflect.TypeToken;

public class TypedLabeledStatement<E extends TypedExpressoEnvironment<E>, R> extends LabeledStatement<E, R>
	implements TypedStatement<E, R> {
	private final TypeToken<R> theType;

	public TypedLabeledStatement(Expression expression, TypeToken<R> type, String label, TypedStatement<E, R> statement) {
		super(expression, label, statement);
		theType = type;
	}

	@Override
	public TypeToken<R> getReturnType() {
		return theType;
	}

	@Override
	public TypedStatement<E, R> getStatement() {
		return (TypedStatement<E, R>) super.getStatement();
	}

	@Override
	public TypedResult<? extends R> execute(E env) throws EvaluationTargetException {
		return (TypedResult<? extends R>) super.execute(env);
	}
}
