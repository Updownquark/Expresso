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
	public <S2 extends S> ExpressionPossibility<S2> parse(ExpressoParser<S2> session, boolean useCache) throws IOException {
		return findNextPossibility(theComponents, session);
	}

	@Override
	public String toString() {
		return "OneOf" + theComponents;
	}

	<S2 extends S> ExpressionPossibility<S2> findNextPossibility(List<? extends ExpressionComponent<? super S>> components,
		ExpressoParser<S2> parser) throws IOException {
		// Repeat until you get the best (unchanging) result
		int i;
		ExpressionPossibility<S2> firstPossibility = null;
		for (i = 0; i < components.size(); i++) {
			ExpressionComponent<? super S> first = components.get(i);
			firstPossibility = parser.parseWith(first, true);
			if (firstPossibility != null)
				break;
		}
		if (components.size() > 1 && firstPossibility != null) {
			int j = i;
			ExpressionPossibility<S2> firstPossibility2 = firstPossibility;
			do {
				i = j;
				firstPossibility = firstPossibility2;
				firstPossibility2 = null;
				for (j = 0; j < i; j++) {
					ExpressionComponent<? super S> first = components.get(j);
					firstPossibility2 = parser.parseWith(first, false);
					if (firstPossibility2 != null)
						break;
				}
			} while (firstPossibility2 != null && !firstPossibility.isEquivalent(firstPossibility2));
		}
		if (firstPossibility != null)
			return new OneOfPossibility<>(this, parser, firstPossibility, components.subList(i + 1, components.size()));
		else
			return null;
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
		public ExpressionComponent<? super S> getType() {
			return theType;
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
			return ((OneOfExpression<S>) theType).findNextPossibility(theRemaining, theParser);
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
		public boolean isEquivalent(ExpressionPossibility<S> o) {
			if (this == o)
				return true;
			else if (!(o instanceof OneOfPossibility))
				return false;
			OneOfPossibility<S> other = (OneOfPossibility<S>) o;
			return getType().equals(other.getType()) && getStream().getPosition() == other.getStream().getPosition()
				&& theComponent.isEquivalent(other.theComponent);
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
