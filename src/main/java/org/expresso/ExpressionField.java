package org.expresso;

import java.util.Arrays;
import java.util.List;

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
		public String toString() {
			return getStream().printContent(0, length(), null).toString();
		}
	}
}
