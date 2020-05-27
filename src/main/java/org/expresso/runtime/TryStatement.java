package org.expresso.runtime;

import java.util.ArrayList;
import java.util.List;

import org.expresso.Expression;

public class TryStatement<E extends ExpressoEnvironment<E>, R> implements Statement<E, R> {
	public static class Catch<E extends ExpressoEnvironment<E>, R, X extends Throwable> {
		private final List<Class<? extends X>> theExceptionTypes;
		private final VariableDeclaration.DeclaredVariable<E, X> theVariable;
		private final Block<E, R> theBlock;
		private final boolean isFinal;

		public Catch(List<Class<? extends X>> exceptionTypes, VariableDeclaration.DeclaredVariable<E, X> variable, Block<E, R> block,
			boolean isFinal) {
			theExceptionTypes = exceptionTypes;
			theVariable = variable;
			theBlock = block;
			this.isFinal = isFinal;
		}

		public List<Class<? extends X>> getExceptionTypes() {
			return theExceptionTypes;
		}

		public VariableDeclaration.DeclaredVariable<E, X> getVariable() {
			return theVariable;
		}

		public Block<E, R> getBlock() {
			return theBlock;
		}
	}

	private final Expression theExpression;
	private final List<? extends Statement<E, ? extends AutoCloseable>> theResources;
	private final Block<E, R> theBody;
	private final List<? extends Catch<E, R, ?>> theCatches;
	private final Block<E, R> theFinally;

	public TryStatement(Expression expression, List<? extends Statement<E, ? extends AutoCloseable>> resources, Block<E, R> body,
		List<? extends Catch<E, R, ?>> catches, Block<E, R> _finally) {
		theExpression = expression;
		theResources = resources;
		theBody = body;
		theCatches = catches;
		theFinally = _finally;
	}

	@Override
	public Expression getExpression() {
		return theExpression;
	}

	@Override
	public Result<? extends R> execute(E env) throws EvaluationTargetException {
		List<AutoCloseable> resources = new ArrayList<>(theResources.size());
		try {
			try {
				E scoped = env.scope(null);
				for (Statement<E, ? extends AutoCloseable> resStmt : theResources)
					resources.add(resStmt.execute(scoped).get());

				return theBody.execute(scoped);
			} finally {
				for (int i = resources.size() - 1; i >= 0; i--)
					resources.get(i).close();
			}
		} catch (Throwable e) {
			if (e instanceof EvaluationTargetException)
				e = e.getCause();
			for (Catch<E, R, ?> ctch : theCatches) {
				boolean matches = false;
				for (Class<? extends Throwable> exType : ctch.getExceptionTypes()) {
					if (exType.isInstance(e)) {
						matches = true;
						break;
					}
				}
				if (matches) {
					E scoped = env.scope(null);
					((Catch<E, R, Throwable>) ctch).getVariable().declare(scoped, ctch.isFinal, e);
					return ctch.getBlock().execute(scoped);
				}
			}
			throw new EvaluationTargetException(e);
		} finally {
			if (theFinally != null) {
				Result<? extends R> result = theFinally.execute(env.scope(null));
				if (result.getDirective() == ControlFlowDirective.RETURN)
					return result;
			}
		}
	}
}
