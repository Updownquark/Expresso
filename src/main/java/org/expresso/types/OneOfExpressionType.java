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
	public <S2 extends S> Expression<S2> parse(ExpressoParser<S2> parser, Expression<S2> lowBound, Expression<S2> highBound)
		throws IOException {
		OneOfPossibility<S2> low = (OneOfPossibility<S2>) lowBound;
		OneOfPossibility<S2> high = (OneOfPossibility<S2>) highBound;
		CollectionElement<? extends ExpressionType<? super S>> component;
		if (low != null) {
			Expression<S2> lowNext = parser.parseWith(//
				low.theComponent.getType(), low.theComponent, high == null ? null : high.theComponent);
			if (lowNext != null)
				return new OneOfPossibility<>(this, lowNext, low.theComponentId);
			component = theComponents.getAdjacentElement(low.theComponentId, true);
		} else
			component = theComponents.getTerminalElement(true);
		while (component != null) {
			boolean isHighBound = high == null ? false : component.getElementId().equals(high.theComponentId);
			Expression<S2> result = parser.parseWith(//
				component.get(), null, isHighBound ? high.theComponent : null);
			if (result != null)
				return new OneOfPossibility<>(this, result, component.getElementId());
			if (isHighBound)
				break;
			else
				component = theComponents.getAdjacentElement(component.getElementId(), true);
		}
		return null;
	}

	@Override
	public int compare(Expression<? extends S> o1, Expression<? extends S> o2) {
		OneOfPossibility<? extends S> oo1 = (OneOfPossibility<? extends S>) o1;
		OneOfPossibility<? extends S> oo2 = (OneOfPossibility<? extends S>) o2;
		int comp = oo1.theComponentId.compareTo(oo2.theComponentId);
		if (comp == 0)
			comp = theComponents.getElement(oo1.theComponentId).get().compare(oo1.theComponent, oo2.theComponent);
		return comp;
	}

	@Override
	public String toString() {
		return "OneOf" + theComponents;
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
		public Expression<S> nextMatchHighPriority(ExpressoParser<S> parser) throws IOException {
			CollectionElement<? extends ExpressionType<? super S>> component = theType.getComponents().getTerminalElement(true);
			while (true) {
				Expression<S> result = parser.parseWith(component.get());
				if (result != null)
					return new OneOfPossibility<>(theType, result, component.getElementId());
				if (component.getElementId().equals(theComponentId))
					break;
				else
					component = theType.getComponents().getAdjacentElement(component.getElementId(), true);
			}
			return null;
		}

		@Override
		public Expression<S> nextMatchLowPriority(ExpressoParser<S> parser, Expression<S> limit) throws IOException {
			CollectionElement<? extends ExpressionType<? super S>> component = theType.getComponents().getAdjacentElement(theComponentId,
				true);
			while (component != null) {
				Expression<S> result = parser.parseWith(component.get());
				if (result != null)
					return new OneOfPossibility<>(theType, result, component.getElementId());
				if (limit != null && component.getElementId().equals(((OneOfPossibility<S>) limit).theComponentId))
					break;
				else
					component = theType.getComponents().getAdjacentElement(component.getElementId(), true);
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
