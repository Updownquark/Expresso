package org.expresso2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.expresso.parse.BranchableStream;

public class OneOfExpression<S extends BranchableStream<?, ?>> extends AbstractExpressionComponent<S> {
	private final List<? extends ExpressionComponent<? super S>> theComponents;
	final boolean isPrioritized;

	public OneOfExpression(int id, List<? extends ExpressionComponent<? super S>> components) {
		this(id, components, false);
	}

	protected OneOfExpression(int id, List<? extends ExpressionComponent<? super S>> components, boolean prioritized) {
		super(id);
		theComponents = components;
		isPrioritized = prioritized;
	}

	public List<? extends ExpressionComponent<? super S>> getComponents() {
		return theComponents;
	}

	@Override
	public <S2 extends S> ExpressionPossibility<S2> parse(ExpressoParser<S2> parser) throws IOException {
		for (int i = 0; i < theComponents.size(); i++) {
			ExpressionComponent<? super S> first = theComponents.get(i);
			ExpressionPossibility<S2> firstPossibility = parser.parseWith(first);
			if (firstPossibility != null)
				return new OneOfPossibility<>(this, parser, firstPossibility, theComponents, i, new HashSet<>());
		}
		return null;
	}

	@Override
	public String toString() {
		return "OneOf" + theComponents;
	}

	private static class OneOfPossibility<S extends BranchableStream<?, ?>> implements ExpressionPossibility<S> {
		private final OneOfExpression<? super S> theType;
		private final ExpressoParser<S> theParser;
		private final ExpressionPossibility<S> theComponent;
		private final List<? extends ExpressionComponent<? super S>> allowedComponents;
		private final int theIndex;
		private final Set<ExpressionPossibility<S>> allFoundPossibilities;

		OneOfPossibility(OneOfExpression<? super S> type, ExpressoParser<S> parser, ExpressionPossibility<S> component,
			List<? extends ExpressionComponent<? super S>> components, int index, Set<ExpressionPossibility<S>> allFound) {
			theType = type;
			theParser = parser;
			theComponent = component;
			allowedComponents = components;
			theIndex = index;
			allFoundPossibilities = allFound;
		}

		@Override
		public OneOfExpression<? super S> getType() {
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
		public Collection<? extends ExpressionPossibility<S>> fork() throws IOException {
			Collection<? extends ExpressionPossibility<S>> componentForks = theComponent.fork();
			Collection<? extends ExpressionPossibility<S>> mappedComponentForks;
			if (componentForks.isEmpty())
				mappedComponentForks = componentForks;
			else
				mappedComponentForks = componentForks.stream()
					.map(fork -> new OneOfPossibility<>(theType, theParser, fork, allowedComponents, theIndex, allFoundPossibilities))
					.collect(Collectors.toCollection(() -> new ArrayList<>(componentForks.size())));

			ExpressionPossibility<S> nextPossibility = null;
			for (int index = theIndex + 1; nextPossibility == null && index < allowedComponents.size(); index++) {
				ExpressionPossibility<S> nextComponent = theParser.parseWith(allowedComponents.get(index));
				if (nextComponent != null)
					nextPossibility = new OneOfPossibility<>(theType, theParser, nextComponent, allowedComponents, index,
						allFoundPossibilities);
			}

			ExpressionPossibility<S> recursed = null;
			int limit = theType.isPrioritized ? theIndex - 1 : allowedComponents.size();
			for (int i = 0; i < limit && recursed == null; i++) {
				ExpressionComponent<? super S> first = allowedComponents.get(i);
				ExpressionPossibility<S> firstPossibility = theParser.parseWith(first, false);
				if (firstPossibility != null && allFoundPossibilities.add(firstPossibility))
					recursed = new OneOfPossibility<>(theType, theParser, firstPossibility, allowedComponents.subList(0, limit), i,
						allFoundPossibilities);
			}

			List<ExpressionPossibility<S>> localPossibilities;
			if (nextPossibility != null && recursed == null)
				localPossibilities = Arrays.asList(nextPossibility, recursed);
			else if (nextPossibility != null)
				localPossibilities = Arrays.asList(nextPossibility);
			else if (recursed != null)
				localPossibilities = Arrays.asList(recursed);
			else
				localPossibilities = null;

			if (localPossibilities == null)
				return mappedComponentForks;
			else if (mappedComponentForks.isEmpty())
				return localPossibilities;
			else
				return new CompositeCollection<ExpressionPossibility<S>>().addComponent(mappedComponentForks)
					.addComponent(localPossibilities);
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
		public boolean equals(Object o) {
			if (this == o)
				return true;
			else if (!(o instanceof OneOfPossibility))
				return false;
			OneOfPossibility<S> other = (OneOfPossibility<S>) o;
			return getType().equals(other.getType()) && getStream().getPosition() == other.getStream().getPosition()
				&& theComponent.equals(other.theComponent);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theType, getStream().getPosition(), theComponent);
		}

		@Override
		public Expression<S> getExpression() {
			return new OneOfExpr<>(theType, theComponent.getExpression());
		}

		@Override
		public String toString() {
			return "*(" + theComponent + ")";
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
