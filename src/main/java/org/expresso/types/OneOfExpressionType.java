package org.expresso.types;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import org.expresso.AbstractExpression;
import org.expresso.Expression;
import org.expresso.ExpressionPossibility;
import org.expresso.ExpressionType;
import org.expresso.ExpressoParser;
import org.expresso.stream.BranchableStream;
import org.qommons.tree.SortedTreeList;

public class OneOfExpressionType<S extends BranchableStream<?, ?>> extends AbstractExpressionType<S> {
	private final List<? extends ExpressionType<? super S>> theComponents;
	final boolean isPrioritized;

	public OneOfExpressionType(int id, List<? extends ExpressionType<? super S>> components) {
		this(id, components, false);
	}

	protected OneOfExpressionType(int id, List<? extends ExpressionType<? super S>> components, boolean prioritized) {
		super(id);
		theComponents = components;
		isPrioritized = prioritized;
	}

	public List<? extends ExpressionType<? super S>> getComponents() {
		return theComponents;
	}

	@Override
	public <S2 extends S> ExpressionPossibility<S2> parse(ExpressoParser<S2> parser) throws IOException {
		Queue<ExpressionPossibility<S2>> possibilities = possibilities(//
			parser.useCache(this, null), new HashSet<>());
		if (possibilities.isEmpty())
			return null;
		Set<ExpressionPossibility<S2>> allVisited = new HashSet<>(possibilities);
		ExpressionPossibility<S2> first = possibilities.poll();
		return new OneOfPossibility<>(this, parser, first, possibilities, allVisited);
	}

	@Override
	public String toString() {
		return "OneOf" + theComponents;
	}

	<S2 extends S> Queue<ExpressionPossibility<S2>> possibilities(ExpressoParser<S2> parser, Set<ExpressionPossibility<S2>> allVisited)
		throws IOException {
		SortedTreeList<ExpressionPossibility<S2>> possibilities = new SortedTreeList<>(false, ExpressionPossibility::compareTo);
		for (ExpressionType<? super S> component : theComponents) {
			ExpressionPossibility<S2> possibility = parser.parseWith(component);
			if (possibility == null) {
			} else if (!parser.tolerateErrors() && possibility.getErrorCount() > 0) {
			} else if (allVisited.add(possibility))
				possibilities.add(possibility);
		}
		return possibilities;
	}

	private static class OneOfPossibility<S extends BranchableStream<?, ?>> implements ExpressionPossibility<S> {
		private final OneOfExpressionType<? super S> theType;
		private final ExpressoParser<S> theParser;
		private final ExpressionPossibility<S> theComponent;
		private final Collection<ExpressionPossibility<S>> otherPossibilities;
		private final Set<ExpressionPossibility<S>> allVisited;

		OneOfPossibility(OneOfExpressionType<? super S> type, ExpressoParser<S> parser, ExpressionPossibility<S> component,
			Collection<ExpressionPossibility<S>> otherPossibilities, Set<ExpressionPossibility<S>> allVisited) {
			theType = type;
			theParser = parser;
			theComponent = component;
			this.otherPossibilities = otherPossibilities;
			this.allVisited = allVisited;
		}

		@Override
		public OneOfExpressionType<? super S> getType() {
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
			CompositeCollection<ExpressionPossibility<S>> forked = new CompositeCollection<>();
			if (!otherPossibilities.isEmpty()) {
				forked.addComponent(otherPossibilities.stream().map(this::singletonPossibility)
					.collect(Collectors.toCollection(() -> new ArrayList<>(otherPossibilities.size()))));
			}
			Collection<? extends ExpressionPossibility<S>> componentForks = theComponent.fork();
			if (!componentForks.isEmpty()) {
				forked.addComponent(componentForks.stream().filter(allVisited::add).map(this::singletonPossibility)
					.collect(Collectors.toCollection(() -> new ArrayList<>(componentForks.size()))));
			}
			Queue<ExpressionPossibility<S>> dive = theType.possibilities(theParser.useCache(theType, this), allVisited);
			if (!dive.isEmpty())
				forked.addComponent(Arrays.asList(new OneOfPossibility<>(theType, theParser, dive.poll(), dive, allVisited)));
			return forked;
		}

		OneOfPossibility<S> singletonPossibility(ExpressionPossibility<S> possibility) {
			return new OneOfPossibility<>(theType, theParser, possibility, Collections.emptyList(), allVisited);
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
		public StringBuilder print(StringBuilder str, int indent, String metadata) {
			for (int i = 0; i < indent; i++)
				str.append('\t');
			str.append(theType).append(metadata).append('\n');
			theComponent.print(str, indent + 1, "");
			return str;
		}

		@Override
		public String toString() {
			return print(new StringBuilder(), 0, "").toString();
		}
	}

	public static class OneOfExpr<S extends BranchableStream<?, ?>> extends AbstractExpression<S> {
		private final Expression<S> theComponent;

		public OneOfExpr(OneOfExpressionType<? super S> type, Expression<S> component) {
			super(component.getStream(), type);
			theComponent = component;
		}

		@Override
		public OneOfExpressionType<? super S> getType() {
			return (OneOfExpressionType<? super S>) super.getType();
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