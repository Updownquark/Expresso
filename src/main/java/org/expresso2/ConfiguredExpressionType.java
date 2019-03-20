package org.expresso2;

import java.io.IOException;
import java.util.NavigableSet;

import org.expresso.parse.BranchableStream;

public interface ConfiguredExpressionType<S extends BranchableStream<?, ?>> extends ExpressionComponent<S> {
	ExpressionComponent<S> getWrapped();

	NavigableSet<String> getFields();

	@Override
	<S2 extends S> ConfiguredExpressionPossibility<S2> parse(ExpressoParser<S2> parser, boolean useCache) throws IOException;

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
		public ExpressionPossibility<S> advance() throws IOException {
			return wrap(theType, theWrapped.advance());
		}

		@Override
		public ExpressionPossibility<S> leftFork() throws IOException {
			return wrap(theType, theWrapped.leftFork());
		}

		@Override
		public ExpressionPossibility<S> rightFork() throws IOException {
			return wrap(theType, theWrapped.rightFork());
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
		public boolean isEquivalent(ExpressionPossibility<S> o) {
			if (o == this)
				return true;
			else if (!(o instanceof ConfiguredExpressionPossibility))
				return false;
			ConfiguredExpressionPossibility<S> other = (ConfiguredExpressionPossibility<S>) o;
			return theType.equals(other.getType()) && theWrapped.isEquivalent(other.theWrapped);
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
