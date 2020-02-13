package org.expresso;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.expresso.stream.BranchableStream;

/**
 * A wrapper around an expression that is marked as a field by a {@link ConfiguredExpressionType}-typed ancestor
 *
 * @param <S> The type of this expression's stream
 */
public class ComponentExpression<S extends BranchableStream<?, ?>> implements Expression<S> {
	private final ComponentExpressionType<? super S> theType;
	private final Expression<S> theWrapped;

	/**
	 * @param type The field type
	 * @param wrapped The wrapped expression
	 */
	public ComponentExpression(ComponentExpressionType<? super S> type, Expression<S> wrapped) {
		theType = type;
		theWrapped = wrapped;
	}

	@Override
	public ComponentExpressionType<? super S> getType() {
		return theType;
	}

	/** @return The actual content of the expression */
	public Expression<S> getWrapped() {
		return theWrapped;
	}

	@Override
	public S getStream() {
		return getWrapped().getStream();
	}

	@Override
	public List<? extends Expression<S>> getChildren() {
		return Arrays.asList(getWrapped());
	}

	@Override
	public Expression<S> getFirstError() {
		return getWrapped().getFirstError();
	}

	@Override
	public int getLocalErrorRelativePosition() {
		return getWrapped().getLocalErrorRelativePosition();
	}

	@Override
	public int getErrorCount() {
		return getWrapped().getErrorCount();
	}

	@Override
	public String getLocalErrorMessage() {
		return getWrapped().getLocalErrorMessage();
	}

	@Override
	public int length() {
		return getWrapped().length();
	}

	// @Override
	// public Expression<S> nextMatch(ExpressoParser<S> parser) throws IOException {
	// Expression<S> wrapFork = parser.nextMatch(getWrapped());
	// if (wrapFork == null)
	// return null;
	// else
	// return new ExpressionField<>(getType(), wrapFork);
	// }

	// @Override
	// public Expression<S> nextMatchHighPriority(ExpressoParser<S> parser) throws IOException {
	// Expression<S> wrapFork = parser.nextMatchHighPriority(getWrapped());
	// if (wrapFork == null)
	// return null;
	// else
	// return new ExpressionField<>(getType(), wrapFork);
	// }
	//
	// @Override
	// public Expression<S> nextMatchLowPriority(ExpressoParser<S> parser, Expression<S> limit) throws IOException {
	// Expression<S> wrapFork = parser.nextMatchLowPriority(getWrapped(), limit);
	// if (wrapFork == null)
	// return null;
	// else
	// return new ExpressionField<>(getType(), wrapFork);
	// }

	@Override
	public int getMatchQuality() {
		return getWrapped().getMatchQuality();
	}

	@Override
	public StringBuilder print(StringBuilder str, int indent, String metadata) {
		return getWrapped().print(str, indent, metadata + getType().getFields());
	}

	@Override
	public Expression<S> unwrap() {
		Expression<S> wrappedUnwrapped = getWrapped().unwrap();
		if (wrappedUnwrapped != null && wrappedUnwrapped != getWrapped())
			return new ComponentExpression<>(getType(), wrappedUnwrapped);
		else
			return this;
	}

	public Expression<S> unwrapFully() {
		Expression<S> wrappedUnwrapped = getWrapped().unwrap();
		if (wrappedUnwrapped instanceof ComponentExpression)
			wrappedUnwrapped = ((ComponentExpression<S>) wrappedUnwrapped).unwrapFully();
		return wrappedUnwrapped;
	}

	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		else if (!(o instanceof ComponentExpression))
			return false;
		ComponentExpression<S> other = (ComponentExpression<S>) o;
		return theType.equals(other.getType()) && theWrapped.equals(other.getWrapped());
	}

	@Override
	public int hashCode() {
		return Objects.hash(theType, theWrapped);
	}

	@Override
	public String toString() {
		return print(new StringBuilder(), 0, "").toString();
	}
}
