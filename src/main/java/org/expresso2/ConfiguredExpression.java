package org.expresso2;

import java.util.Arrays;
import java.util.List;

import org.expresso.parse.BranchableStream;

public interface ConfiguredExpression<S extends BranchableStream<?, ?>> extends Expression<S> {
	@Override
	ConfiguredExpressionType<? super S> getType();

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
			return new SimpleConfiguredExpression<>(getType(), wrappedUnwrapped);
		return this;
	}

	public static class SimpleConfiguredExpression<S extends BranchableStream<?, ?>> implements ConfiguredExpression<S> {
		private final ConfiguredExpressionType<? super S> theType;
		private final Expression<S> theWrapped;

		public SimpleConfiguredExpression(ConfiguredExpressionType<? super S> type, Expression<S> wrapped) {
			theType = type;
			theWrapped = wrapped;
		}

		@Override
		public ConfiguredExpressionType<? super S> getType() {
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
