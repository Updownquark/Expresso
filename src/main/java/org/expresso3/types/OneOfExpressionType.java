package org.expresso3.types;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
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
	@Override
	public BetterList<? extends ExpressionType<? super S>> getComponents() {
		return theComponents;
	}

	@Override
	public <S2 extends S> Expression<S2> parse(ExpressoParser<S2> parser) throws IOException {
		boolean[] recursive = new boolean[1];
		ExpressoParser<S2> stopRecurse = parser.useCache(this, null, () -> recursive[0] = true);
		PersistentStack<List<ElementId>> recursiveComponents = null;
		List<ElementId> adjRecComps = new LinkedList<>();
		for (CollectionElement<? extends ExpressionType<? super S>> component : theComponents.elements()) {
			Expression<S2> possibility = stopRecurse.parseWith(//
				component.get());
			if (recursive[0]) {
				recursive[0] = false;
				adjRecComps.add(component.getElementId());
			}
			if (possibility != null) {
				if (!adjRecComps.isEmpty()) {
					recursiveComponents = new PersistentStack<>(recursiveComponents, adjRecComps);
					adjRecComps = new LinkedList<>();
				}
				return new OneOfPossibility<>(this, parser.getStream(), possibility, component.getElementId(), recursiveComponents);
			}
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
		private final S theStream;
		private final Expression<S> theComponent;
		private final ElementId theComponentId;
		private final PersistentStack<List<ElementId>> theRecursiveComponents;

		OneOfPossibility(OneOfExpressionType<? super S> type, S stream, Expression<S> component, ElementId componentId,
			PersistentStack<List<ElementId>> recursiveComponents) {
			theType = type;
			theStream = stream;
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
			return theStream;
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
		public Expression<S> nextMatch(ExpressoParser<S> parser) throws IOException {
			final Expression<S>[] next = new Expression[1];
			boolean[] recursive = new boolean[1];
			// First, try to use a higher-priority recursive component with this expression as the recursive result
			if (theRecursiveComponents != null) {
				ExpressoParser<S> stopRecurse = parser.useCache(theType, this, () -> recursive[0] = true);
				IOException[] ex = new IOException[1];
				if (theRecursiveComponents.searchStacks(stack -> {
					List<ElementId> adjRecComps = new LinkedList<>();
					for (ElementId compId : stack.get()) {
						ExpressionType<? super S> component = theType.getComponents().getElement(compId).get();
						try {
							next[0] = stopRecurse.parseWith(component);
						} catch (IOException e) {
							ex[0] = e;
							return true;
						}
						if (recursive[0] && next[0] != null) {
							if (compId.equals(theComponentId) && theComponent.equals(next[0]))
								next[0] = null;
							else {
								next[0] = new OneOfPossibility<>(theType, parser.getStream(), next[0], compId,
									adjRecComps.isEmpty() ? stack.getParent() : new PersistentStack<>(stack.getParent(), adjRecComps));
								return true;
							}
						} else if (recursive[0])
							adjRecComps.add(compId);
					}
					return false;
				})) {
					if (ex[0] != null)
						throw ex[0];
					return next[0];
				}
			}
			// Next, try to branch the component itself
			ExpressoParser<S> stopRecurse = parser.useCache(theType, null, () -> recursive[0] = true);
			next[0] = stopRecurse.nextMatch(theComponent);
			if (next[0] != null)
				return new OneOfPossibility<>(theType, parser.getStream(), next[0], theComponentId, theRecursiveComponents);
			// Last, see if there are other options in the one-of
			CollectionElement<? extends ExpressionType<? super S>> nextComponent = theType.getComponents()
				.getAdjacentElement(theComponentId, true);
			if (nextComponent != null) {
				PersistentStack<List<ElementId>> recursiveComponents = theRecursiveComponents;
				List<ElementId> adjRecComps = new LinkedList<>();
				while (nextComponent != null) {
					next[0] = stopRecurse.parseWith(nextComponent.get());
					if (recursive[0]) {
						recursive[0] = false;
						adjRecComps.add(nextComponent.getElementId());
					}
					if (next[0] != null) {
						if (!adjRecComps.isEmpty()) {
							recursiveComponents = new PersistentStack<>(recursiveComponents, adjRecComps);
						}
						return new OneOfPossibility<>(theType, parser.getStream(), next[0], nextComponent.getElementId(),
							recursiveComponents);
					}
					nextComponent = theType.getComponents().getAdjacentElement(nextComponent.getElementId(), true);
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
				str.append(": ").append(printContent(true));
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
