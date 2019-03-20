package org.expresso2;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.expresso.parse.BranchableStream;

public class OneOfExpression<S extends BranchableStream<?, ?>> extends AbstractExpressionComponent<S> {
	private final List<? extends ExpressionComponent<? super S>> theComponents;

	public OneOfExpression(int id, List<? extends ExpressionComponent<? super S>> components) {
		super(id);
		theComponents = components;
	}

	public List<? extends ExpressionComponent<? super S>> getComponents() {
		return theComponents;
	}

	@Override
	public <S2 extends S> ExpressionPossibility<S2> parse(ExpressoParser<S2> session) throws IOException {
		ExpressionComponent<? super S> first = theComponents.get(0);
		List<? extends ExpressionComponent<? super S>> remaining = theComponents.subList(1, theComponents.size());
		ExpressionPossibility<S2> firstPossibility = session.parseWith(first);
		return firstPossibility == null ? null : new OneOfPossibility<>(this, session, firstPossibility, remaining);
	}

	@Override
	public String toString() {
		return "OneOf" + theComponents;
	}

	private static class OneOfPossibility<S extends BranchableStream<?, ?>> implements ExpressionPossibility<S> {
		private final OneOfExpression<? super S> theType;
		private final ExpressoParser<S> theParser;
		private final ExpressionPossibility<S> theComponent;
		private final List<? extends ExpressionComponent<? super S>> theRemaining;

		OneOfPossibility(OneOfExpression<? super S> type, ExpressoParser<S> parser, ExpressionPossibility<S> component,
			List<? extends ExpressionComponent<? super S>> remaining) {
			theType = type;
			theParser = parser;
			theComponent = component;
			theRemaining = remaining;
		}

		@Override
		public S getStream() {
			return theParser.getStream();
		}

		@Override
		public int length() {
			return theComponent.length();
		}

		@Override
		public ExpressionPossibility<S> advance() throws IOException {
			ExpressionPossibility<S> componentAdv = theComponent.advance();
			return componentAdv == null ? null : new OneOfPossibility<>(theType, theParser, componentAdv, theRemaining);
		}

		@Override
		public ExpressionPossibility<S> leftFork() throws IOException {
			return null;
		}

		@Override
		public ExpressionPossibility<S> rightFork() throws IOException {
			if (theRemaining.isEmpty())
				return null;
			ExpressionComponent<? super S> next = theRemaining.get(0);
			List<? extends ExpressionComponent<? super S>> remaining = theRemaining.subList(1, theRemaining.size());
			ExpressionPossibility<S> nextPossibility = theParser.parseWith(next);
			return nextPossibility == null ? null : new OneOfPossibility<>(theType, theParser, nextPossibility, remaining);
		}

		@Override
		public int getErrorCount() {
			return theComponent.getErrorCount();
		}

		@Override
		public int getFirstErrorPosition() {
			return theComponent.getFirstErrorPosition();
		}

		@Override
		public boolean isComplete() {
			return theComponent.isComplete();
		}

		@Override
		public Expression<S> getExpression() {
			return new OneOfExpr<>(theType, theComponent.getExpression());
		}
	}

	public static class OneOfExpr<S extends BranchableStream<?, ?>> extends AbstractExpression<S> {
		private final Expression<S> theComponent;

		public OneOfExpr(OneOfExpression<? super S> type, Expression<S> component) {
			super(component.getStream(), type);
			theComponent = component;
		}

		@Override
		public OneOfExpression<? super S> getType() {
			return (OneOfExpression<? super S>) super.getType();
		}

		@Override
		public List<? extends Expression<S>> getChildren() {
			return Arrays.asList(theComponent);
		}

		@Override
		public Expression<S> getFirstError() {
			return theComponent.getFirstError();
		}

		@Override
		public int getErrorCount() {
			return theComponent.getErrorCount();
		}

		@Override
		public int getLocalErrorRelativePosition() {
			return -1;
		}

		@Override
		public String getLocalErrorMessage() {
			return null;
		}

		@Override
		public int length() {
			return theComponent.length();
		}

		@Override
		public Expression<S> unwrap() {
			return theComponent.unwrap();
		}
	}
}
