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

import org.expresso.ConfiguredExpressionType;
import org.expresso.Expression;
import org.expresso.ExpressionType;
import org.expresso.ExpressoParser;
import org.expresso.stream.BranchableStream;
import org.qommons.tree.SortedTreeList;

/**
 * An expression that may be satisfied of any one of several components
 *
 * @param <S> The type of the stream
 */
public class OneOfExpressionType<S extends BranchableStream<?, ?>> extends AbstractExpressionType<S> {
	private final List<? extends ExpressionType<? super S>> theComponents;
	final boolean isPrioritized;
	private int theSpecificity;

	/**
	 * @param id The cache ID of the expression type
	 * @param components The components, any of which may satisfy this expression for a stream
	 */
	public OneOfExpressionType(int id, List<? extends ExpressionType<? super S>> components) {
		this(id, components, false);
	}

	/**
	 * @param id The cache ID of the expression type
	 * @param components The components, any of which may satisfy this expression for a stream
	 * @param prioritized Whether the components are ordered by priority (typically {@link ConfiguredExpressionType#getPriority()})
	 */
	protected OneOfExpressionType(int id, List<? extends ExpressionType<? super S>> components, boolean prioritized) {
		super(id);
		theComponents = components;
		isPrioritized = prioritized;
		// Can't calculate the specificity here,
		// because the component list may be populated by the grammar parser after this constructor is called
		theSpecificity = -1;
	}

	/** @return The components, any of which may satisfy this expression for a stream */
	public List<? extends ExpressionType<? super S>> getComponents() {
		return theComponents;
	}

	@Override
	public <S2 extends S> Expression<S2> parse(ExpressoParser<S2> parser) throws IOException {
		Queue<Expression<S2>> possibilities = possibilities(//
			parser.useCache(this, null), new HashSet<>());
		if (possibilities.isEmpty())
			return null;
		Set<Expression<S2>> allVisited = new HashSet<>(possibilities);
		Expression<S2> first = possibilities.poll();
		return new OneOfPossibility<>(this, parser, first, possibilities, allVisited);
	}

	@Override
	public int getSpecificity() {
		if (theSpecificity == -1) {
			theSpecificity = getComponents().stream().mapToInt(ExpressionType::getSpecificity).min().orElse(0);
		}
		return theSpecificity;
	}

	@Override
	public String toString() {
		return "OneOf" + theComponents;
	}

	<S2 extends S> Queue<Expression<S2>> possibilities(ExpressoParser<S2> parser, Set<Expression<S2>> allVisited)
		throws IOException {
		SortedTreeList<Expression<S2>> possibilities = new SortedTreeList<>(false, Expression::compareTo);
		for (ExpressionType<? super S> component : theComponents) {
			Expression<S2> possibility = parser.parseWith(component);
			if (possibility == null) {
			} else if (!parser.tolerateErrors() && possibility.getErrorCount() > 0) {
			} else if (allVisited.add(possibility))
				possibilities.add(possibility);
		}
		return possibilities;
	}

	private static class OneOfPossibility<S extends BranchableStream<?, ?>> implements Expression<S> {
		private final OneOfExpressionType<? super S> theType;
		private final ExpressoParser<S> theParser;
		private final Expression<S> theComponent;
		private final Collection<Expression<S>> otherPossibilities;
		private final Set<Expression<S>> allVisited;

		OneOfPossibility(OneOfExpressionType<? super S> type, ExpressoParser<S> parser, Expression<S> component,
			Collection<Expression<S>> otherPossibilities, Set<Expression<S>> allVisited) {
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
		public List<? extends Expression<S>> getChildren() {
			return Arrays.asList(theComponent);
		}

		@Override
		public Collection<? extends Expression<S>> fork() throws IOException {
			CompositeCollection<Expression<S>> forked = new CompositeCollection<>();
			if (!otherPossibilities.isEmpty()) {
				forked.addComponent(otherPossibilities.stream().map(this::singletonPossibility)
					.collect(Collectors.toCollection(() -> new ArrayList<>(otherPossibilities.size()))));
			}
			Collection<? extends Expression<S>> componentForks = theComponent.fork();
			if (!componentForks.isEmpty()) {
				forked.addComponent(componentForks.stream().filter(allVisited::add).map(this::singletonPossibility)
					.collect(Collectors.toCollection(() -> new ArrayList<>(componentForks.size()))));
			}
			Queue<Expression<S>> dive = theType.possibilities(theParser.useCache(theType, this), allVisited);
			if (!dive.isEmpty())
				forked.addComponent(Arrays.asList(new OneOfPossibility<>(theType, theParser, dive.poll(), dive, allVisited)));
			return forked;
		}

		OneOfPossibility<S> singletonPossibility(Expression<S> possibility) {
			return new OneOfPossibility<>(theType, theParser, possibility, Collections.emptyList(), allVisited);
		}

		@Override
		public int getErrorCount() {
			return theComponent.getErrorCount();
		}

		@Override
		public Expression<S> getFirstError() {
			return theComponent.getFirstError();
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
		public int getComplexity() {
			return theComponent.getComplexity() + 1;
		}

		@Override
		public int getMatchQuality() {
			return theComponent.getMatchQuality();
		}

		@Override
		public Expression<S> unwrap() {
			return theComponent.unwrap();
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
}
