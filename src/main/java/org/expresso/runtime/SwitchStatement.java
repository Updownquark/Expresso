package org.expresso.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.expresso.Expression;

public class SwitchStatement<E extends ExpressoEnvironment<E>, R, C> implements Statement<E, R> {
	public static class CaseStatement<E extends ExpressoEnvironment<E>, R, C> extends Block<E, R> {
		private final List<? extends Statement<E, ? extends C>> theCaseValues;

		public CaseStatement(Expression expression, List<? extends Statement<E, ? extends C>> caseValues,
			List<? extends Statement<E, ? extends R>> statements) {
			super(expression, statements);
			theCaseValues = caseValues;
		}

		public List<? extends Statement<E, ? extends C>> getCaseValues() {
			return theCaseValues;
		}
	}

	public static class DefaultCaseLabel<E extends ExpressoEnvironment<E>, C> implements Statement<E, C> {
		private final Expression theExpression;

		public DefaultCaseLabel(Expression expression) {
			theExpression = expression;
		}

		@Override
		public Expression getExpression() {
			return theExpression;
		}

		@Override
		public Result<? extends C> execute(E env) {
			throw new IllegalStateException();
		}

		@Override
		public String toString() {
			return "default";
		}
	}

	private final Expression theExpression;
	private final Statement<E, C> theCondition;
	private final Map<C, CaseStatement<E, ? extends R, ? extends C>> theCaseStatements;
	private final CaseStatement<E, ? extends R, ? extends C> theDefaultStatement;

	public SwitchStatement(Expression expression, Statement<E, C> condition,
		List<? extends CaseStatement<E, ? extends R, ? extends C>> caseStatements, E env) throws CompilationException {
		theExpression = expression;
		theCondition = condition;
		theCaseStatements = new LinkedHashMap<>();
		CaseStatement<E, ? extends R, ? extends C> defStmt = null;
		for (CaseStatement<E, ? extends R, ? extends C> caseStmt : caseStatements) {
			for (Statement<E, ? extends C> caseValue : caseStmt.getCaseValues()) {
				if (caseValue instanceof DefaultCaseLabel) {
					if (defStmt != null)
						throw new CompilationException(caseStmt.getExpression(), "Duplicate default cases");
					defStmt = caseStmt;
				}
				C cv;
				try {
					cv = caseValue.execute(env).get();
				} catch (EvaluationTargetException e) {
					throw new CompilationException(caseValue.getExpression(), "Constant expression should not have thrown an exception",
						e.getCause());
				}
				if (theCaseStatements.containsKey(cv))
					throw new CompilationException(caseValue.getExpression(), "Duplicate case value " + cv);
				theCaseStatements.put(cv, caseStmt);
			}
		}
		theDefaultStatement = defStmt;
	}

	@Override
	public Expression getExpression() {
		return theExpression;
	}

	@Override
	public Result<? extends R> execute(E env) throws EvaluationTargetException {
		C caseValue = theCondition.execute(env).get();
		CaseStatement<E, ? extends R, ? extends C> statement = theCaseStatements.get(caseValue);
		if (statement == null)
			statement = theDefaultStatement;
		if (statement != null) {
			Result<? extends R> result = statement.execute(env);
			switch (result.getDirective()) {
			case RETURN:
			case THROW:
			case CONTINUE:
				return result;
			case BREAK:
				if (result.getDirectiveLabel() != null)
					return result;
				//$FALL-THROUGH$
			case NORMAL:
				break;
			}
		}
		return getDefaultResult();
	}

	public Result<? extends R> getDefaultResult() {
		return Result.noReturn();
	}
}
