package org.expresso;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.expresso.stream.BranchableStream;

/**
 * A wrapper around an expression that is marked as a field by a {@link ConfiguredExpressionType}-typed ancestor
 *
 * @param <S> The type of this expression's stream
 */
public interface ExpressionField<S extends BranchableStream<?, ?>> extends Expression<S> {
	@Override
	ExpressionFieldType<? super S> getType();

	/** @return The actual content of the expression */
	public Expression<S> getWrapped();

	@Override
	default S getStream() {
		return getWrapped().getStream();
	}

	@Override
	default List<? extends Expression<S>> getChildren() {
		return Arrays.asList(getWrapped());
	}

	@Override
	default Expression<S> getFirstError() {
		return getWrapped().getFirstError();
	}

	@Override
	default int getLocalErrorRelativePosition() {
		return getWrapped().getLocalErrorRelativePosition();
	}

	@Override
	default int getErrorCount() {
		return getWrapped().getErrorCount();
	}

	@Override
	default String getLocalErrorMessage() {
		return getWrapped().getLocalErrorMessage();
	}

	@Override
	default int length() {
		return getWrapped().length();
	}

	@Override
	default Collection<? extends Expression<S>> fork() throws IOException {
		Collection<? extends Expression<S>> wrapFork = getWrapped().fork();
		if (wrapFork.isEmpty())
			return wrapFork;
		else
			return wrapFork.stream().map(f -> new SimpleExpressionField<>(getType(), f))
				.collect(Collectors.toCollection(() -> new ArrayList<>(wrapFork.size())));
	}

	@Override
	default int getComplexity() {
		return getWrapped().getComplexity();
	}

	@Override
	default StringBuilder print(StringBuilder str, int indent, String metadata) {
		return getWrapped().print(str, indent, metadata + getType().getFields());
	}

	@Override
	default Expression<S> unwrap() {
		Expression<S> wrappedUnwrapped = getWrapped().unwrap();
		if (wrappedUnwrapped != null && wrappedUnwrapped != getWrapped())
			return new SimpleExpressionField<>(getType(), wrappedUnwrapped);
		return this;
	}

	/**
	 * A simple expression field
	 *
	 * @param <S> The stream type of the expression
	 */
	public static class SimpleExpressionField<S extends BranchableStream<?, ?>> implements ExpressionField<S> {
		private final ExpressionFieldType<? super S> theType;
		private final Expression<S> theWrapped;

		/**
		 * @param type The field type
		 * @param wrapped The wrapped expression
		 */
		public SimpleExpressionField(ExpressionFieldType<? super S> type, Expression<S> wrapped) {
			theType = type;
			theWrapped = wrapped;
		}

		@Override
		public ExpressionFieldType<? super S> getType() {
			return theType;
		}

		@Override
		public Expression<S> getWrapped() {
			return theWrapped;
		}

		@Override
		public boolean equals(Object o) {
			if (o == this)
				return true;
			else if (!(o instanceof ExpressionField))
				return false;
			ExpressionField<S> other = (ExpressionField<S>) o;
			return theType.equals(other.getType()) && theWrapped.equals(other.getWrapped());
		}

		@Override
		public int hashCode() {
			return Objects.hash(theType, theWrapped);
		}

		@Override
		public String toString() {
			return getStream().printContent(0, length(), null).toString();
		}
	}
}
