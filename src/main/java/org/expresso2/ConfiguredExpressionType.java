package org.expresso2;

import java.io.IOException;
import java.util.NavigableSet;

import org.expresso.parse.BranchableStream;

public interface ConfiguredExpressionType<S extends BranchableStream<?, ?>> extends ExpressionComponent<S> {
	NavigableSet<String> getFields();

	@Override
	<S2 extends S> ConfiguredExpressionPossibility<S2> parse(ExpressoParser<S2> parser) throws IOException;

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
		public ConfiguredExpression<S> getExpression() {
			return new ConfiguredExpression.SimpleConfiguredExpression<>(theType, theWrapped.getExpression());
		}
	}
}
