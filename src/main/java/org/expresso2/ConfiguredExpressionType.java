package org.expresso2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.stream.Collectors;

import org.expresso.parse.BranchableStream;

public interface ConfiguredExpressionType<S extends BranchableStream<?, ?>> extends ExpressionComponent<S> {
	ExpressionComponent<S> getWrapped();

	NavigableSet<String> getFields();

	@Override
	default int getCacheId() {
		return -1;
	}

	@Override
	default <S2 extends S> ExpressionPossibility<S2> parse(ExpressoParser<S2> parser) throws IOException {
		throw new IllegalStateException("The one-argument parse method of an uncachable component should not be called");
	}

	<S2 extends S> ExpressionPossibility<S2> parse(ExpressoParser<S2> parser, boolean useCache) throws IOException;

	static <S extends BranchableStream<?, ?>> ConfiguredExpressionPossibility<S> wrap(ConfiguredExpressionType<? super S> type,
		ExpressionPossibility<S> possibility) {
		return possibility == null ? null : new ConfiguredExpressionPossibility<>(type, possibility);
	}

	public static class ConfiguredExpressionPossibility<S extends BranchableStream<?, ?>> implements ExpressionPossibility<S> {
		private final ConfiguredExpressionType<? super S> theType;
		private final ExpressionPossibility<S> theWrapped;

		public ConfiguredExpressionPossibility(ConfiguredExpressionType<? super S> type, ExpressionPossibility<S> wrapped) {
			theType = type;
			theWrapped = wrapped;
		}

		@Override
		public ExpressionComponent<? super S> getType() {
			return theType;
		}

		@Override
		public S getStream() {
			return theWrapped.getStream();
		}

		@Override
		public int length() {
			return theWrapped.length();
		}

		@Override
		public Collection<? extends ExpressionPossibility<S>> fork() throws IOException {
			Collection<? extends ExpressionPossibility<S>> wrappedForks = theWrapped.fork();
			if (wrappedForks.isEmpty())
				return wrappedForks;
			else
				return wrappedForks.stream().map(fork -> wrap(theType, fork))
					.collect(Collectors.toCollection(() -> new ArrayList<>(wrappedForks.size())));
		}

		@Override
		public int getErrorCount() {
			return theWrapped.getErrorCount();
		}

		@Override
		public int getFirstErrorPosition() {
			return theWrapped.getFirstErrorPosition();
		}

		@Override
		public boolean isComplete() {
			return theWrapped.isComplete();
		}

		@Override
		public boolean equals(Object o) {
			if (o == this)
				return true;
			else if (!(o instanceof ConfiguredExpressionPossibility))
				return false;
			ConfiguredExpressionPossibility<S> other = (ConfiguredExpressionPossibility<S>) o;
			return theType.equals(other.getType()) && theWrapped.equals(other.theWrapped);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theType, theWrapped);
		}

		@Override
		public ConfiguredExpression<S> getExpression() {
			return new ConfiguredExpression.SimpleConfiguredExpression<>(theType, theWrapped.getExpression());
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}
}
