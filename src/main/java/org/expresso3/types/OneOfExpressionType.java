package org.expresso3.types;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import org.expresso.stream.BranchableStream;
import org.expresso3.ConfiguredExpressionType;
import org.expresso3.Expression;
import org.expresso3.ExpressionType;
import org.expresso3.ExpressoParser;
import org.expresso3.impl.PersistentStack;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.tree.SortedTreeList;

/**
 * An expression that may be satisfied of any one of several components
 *
 * @param <S> The type of the stream
 */
public class OneOfExpressionType<S extends BranchableStream<?, ?>> extends AbstractExpressionType<S> {
	private final BetterList<? extends ExpressionType<? super S>> theComponents;
	final boolean isPrioritized;
	private int theSpecificity;

	/**
	 * @param id The cache ID of the expression type
	 * @param components The components, any of which may satisfy this expression for a stream
	 */
	public OneOfExpressionType(int id, BetterList<? extends ExpressionType<? super S>> components) {
		this(id, components, false);
	}

	/**
	 * @param id The cache ID of the expression type
	 * @param components The components, any of which may satisfy this expression for a stream
	 * @param prioritized Whether the components are ordered by priority (typically {@link ConfiguredExpressionType#getPriority()})
	 */
	protected OneOfExpressionType(int id, BetterList<? extends ExpressionType<? super S>> components, boolean prioritized) {
		super(id);
		theComponents = components;
		isPrioritized = prioritized;
		// Can't calculate the specificity here,
		// because the component list may be populated by the grammar parser after this constructor is called
		theSpecificity = -1;
	}

	/** @return The components, any of which may satisfy this expression for a stream */
	public BetterList<? extends ExpressionType<? super S>> getComponents() {
		return theComponents;
	}

	@Override
	public <S2 extends S> Expression<S2> parse(ExpressoParser<S2> parser) throws IOException {
		boolean[] recursive = new boolean[1];
		ExpressoParser<S2> stopRecurse = parser.useCache(this, null, () -> recursive[0] = true);
		PersistentStack<ElementId> recursiveComponents = null;
		for (CollectionElement<? extends ExpressionType<? super S>> component : theComponents.elements()) {
			Expression<S2> possibility = stopRecurse.parseWith(component.get());
			if (recursive[0]) {
				recursive[0] = false;
				recursiveComponents = new PersistentStack<>(recursiveComponents, component.getElementId());
			}
			if (possibility != null)
				return new OneOfPossibility<>(this, parser, possibility, component.getElementId(), recursiveComponents);
		}
		return null;
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

	<S2 extends S> Queue<Expression<S2>> possibilities(ExpressoParser<S2> parser, Set<Expression<S2>> allVisited) throws IOException {
		SortedTreeList<Expression<S2>> possibilities = new SortedTreeList<>(false, Expression::compareTo);
		for (ExpressionType<? super S> component : theComponents) {
			Expression<S2> possibility = parser.parseWith(component);
			if (possibility != null)
				possibilities.add(possibility);
		}
		return possibilities;
	}

	private static class OneOfPossibility<S extends BranchableStream<?, ?>> implements Expression<S> {
		private final OneOfExpressionType<? super S> theType;
		private final ExpressoParser<S> theParser;
		private final Expression<S> theComponent;
		private final ElementId theComponentId;
		private final PersistentStack<ElementId> theRecursiveComponents;

		OneOfPossibility(OneOfExpressionType<? super S> type, ExpressoParser<S> parser, Expression<S> component, ElementId componentId,
			PersistentStack<ElementId> recursiveComponents) {
			theType = type;
			theParser = parser;
			theComponent = component;
			theComponentId = componentId;
			theRecursiveComponents = recursiveComponents;
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
		public Expression<S> nextMatch() throws IOException {
			final Expression<S>[] next = new Expression[1];
			boolean[] recursive = new boolean[1];
			ExpressoParser<S> stopRecurse = theParser.useCache(theType, this, () -> recursive[0] = true);
			// First, try to use a higher-priority recursive component with this expression as the recursive result
			IOException[] ex = new IOException[1];
			if (theRecursiveComponents != null && theRecursiveComponents.searchStacks(stack -> {
				ExpressionType<? super S> component = theType.getComponents().getElement(stack.get()).get();
				try {
					next[0] = stopRecurse.parseWith(component);
				} catch (IOException e) {
					ex[0] = e;
					return true;
				}
				if (recursive[0] && next[0] != null) {
					next[0] = new OneOfPossibility<>(theType, theParser, next[0], theComponentId, stack);
					return true;
				} else
					return false;
			})) {
				if (ex[0] != null)
					throw ex[0];
				return next[0];
			}
			// Next, try to branch the component itself
			next[0] = theComponent.nextMatch();
			if (next[0] != null)
				return new OneOfPossibility<>(theType, theParser, next[0], theComponentId, theRecursiveComponents);
			// Last, see if there are other options in the one-of
			CollectionElement<? extends ExpressionType<? super S>> nextComponent = theType.getComponents()
				.getAdjacentElement(theComponentId, true);
			if (nextComponent != null) {
				PersistentStack<ElementId> recursiveComponents = null;
				while (nextComponent != null) {
					next[0] = stopRecurse.parseWith(nextComponent.get());
					if (recursive[0]) {
						recursive[0] = false;
						recursiveComponents = new PersistentStack<>(recursiveComponents, nextComponent.getElementId());
					}
					if (next[0] != null)
						return new OneOfPossibility<>(theType, theParser, next[0], nextComponent.getElementId(), recursiveComponents);
					nextComponent = theType.getComponents().getAdjacentElement(theComponentId, true);
				}
			}
			return null;
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
			str.append(theType).append(metadata);
			if (length() == 0)
				str.append("(empty)");
			else
				str.append(": ").append(printContent());
			str.append('\n');
			theComponent.print(str, indent + 1, "");
			return str;
		}

		@Override
		public String toString() {
			return print(new StringBuilder(), 0, "").toString();
		}
	}
}
