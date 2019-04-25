package org.expresso.types;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.expresso.Expression;
import org.expresso.ExpressionType;
import org.expresso.ExpressoParser;
import org.expresso.stream.BranchableStream;
import org.qommons.IntList;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;

/**
 * An expression that may be satisfied of any one of several components
 *
 * @param <S> The type of the stream
 */
public class OneOfExpressionType<S extends BranchableStream<?, ?>> extends AbstractExpressionType<S> {
	private final BetterList<? extends ExpressionType<? super S>> theComponents;
	private IntList theEmptyQuality;

	/**
	 * @param id The cache ID of the expression type
	 * @param components The components, any of which may satisfy this expression for a stream
	 */
	public OneOfExpressionType(int id, BetterList<? extends ExpressionType<? super S>> components) {
		super(id);
		theComponents = components;
	}

	/** @return The components, any of which may satisfy this expression for a stream */
	@Override
	public BetterList<? extends ExpressionType<? super S>> getComponents() {
		return theComponents;
	}

	@Override
	public int getEmptyQuality(int minQuality) {
		if (theEmptyQuality == null)
			theEmptyQuality = new IntList();
		int eqIndex = -minQuality;
		while (theEmptyQuality.size() <= eqIndex)
			theEmptyQuality.add(1);
		if (theEmptyQuality.get(eqIndex) > 0) {
			theEmptyQuality.set(eqIndex, 0); // To prevent infinite recursion
			int quality = Integer.MIN_VALUE;
			for (ExpressionType<?> component : theComponents) {
				quality = Math.max(quality, component.getEmptyQuality(minQuality));
				if (quality == 0)
					break;
			}
			theEmptyQuality.set(eqIndex, quality);
		}
		return theEmptyQuality.get(eqIndex);
	}

	@Override
	public <S2 extends S> Expression<S2> parse(ExpressoParser<S2> parser) throws IOException {
		for (CollectionElement<? extends ExpressionType<? super S>> component : theComponents.elements()) {
			Expression<S2> possibility = parser.parseWith(//
				component.get());
			if (possibility != null)
				return new OneOfPossibility<>(this, possibility, component.getElementId());
		}
		return null;
	}

	@Override
	public String toString() {
		return "OneOf" + theComponents;
	}

	<S2 extends S> Expression<S2> recurse(Expression<S2> expression, ElementId componentId, ExpressoParser<S2> parser) throws IOException {
		CollectionElement<? extends ExpressionType<? super S>> component = theComponents.getAdjacentElement(componentId, true);
		while (component != null) {
			ElementId compId = component.getElementId();

			Expression<S2> recursed = parser.parseWith(component.get());
			if (recursed != null)
				return new OneOfPossibility<>(this, recursed, component.getElementId());
			component = theComponents.getAdjacentElement(compId, true);
		}
		return null;
	}

	private static class OneOfPossibility<S extends BranchableStream<?, ?>> implements Expression<S> {
		private final OneOfExpressionType<? super S> theType;
		private final Expression<S> theComponent;
		private final ElementId theComponentId;

		OneOfPossibility(OneOfExpressionType<? super S> type, Expression<S> component, ElementId componentId) {
			theType = type;
			theComponent = component;
			theComponentId = componentId;
		}

		@Override
		public OneOfExpressionType<? super S> getType() {
			return theType;
		}

		@Override
		public S getStream() {
			return theComponent.getStream();
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
			Expression<S> next = parser.nextMatch(theComponent);
			if (next != null)
				return new OneOfPossibility<>(theType, next, theComponentId);
			return null;
		}

		@Override
		public Expression<S> nextMatchLowPriority(ExpressoParser<S> parser) throws IOException {
			CollectionElement<? extends ExpressionType<? super S>> component = theType.getComponents().getAdjacentElement(theComponentId,
				true);
			while (component != null) {
				ElementId compId = component.getElementId();

				Expression<S> recursed = parser.parseWith(component.get());
				if (recursed != null)
					return new OneOfPossibility<>(theType, recursed, component.getElementId());
				component = theType.getComponents().getAdjacentElement(compId, true);
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
