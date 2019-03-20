package org.expresso2;

import java.io.IOException;

import org.expresso.parse.BranchableStream;

public interface ExpressionPossibility<S extends BranchableStream<?, ?>> {
	ExpressionComponent<? super S> getType();

	S getStream();

	int length();

	ExpressionPossibility<S> advance() throws IOException;

	ExpressionPossibility<S> leftFork() throws IOException;

	ExpressionPossibility<S> rightFork() throws IOException;

	int getErrorCount();

	int getFirstErrorPosition();

	boolean isComplete();

	boolean isEquivalent(ExpressionPossibility<S> o);

	Expression<S> getExpression();

	static <S extends BranchableStream<?, ?>> ExpressionPossibility<S> empty(S stream, ExpressionComponent<? super S> type) {
		return new ExpressionPossibility<S>() {
			@Override
			public ExpressionComponent<? super S> getType() {
				return type;
			}

			@Override
			public S getStream() {
				return stream;
			}

			@Override
			public int length() {
				return 0;
			}

			@Override
			public ExpressionPossibility<S> advance() throws IOException {
				return null;
			}

			@Override
			public ExpressionPossibility<S> leftFork() throws IOException {
				return null;
			}

			@Override
			public ExpressionPossibility<S> rightFork() throws IOException {
				return null;
			}

			@Override
			public int getErrorCount() {
				return 0;
			}

			@Override
			public int getFirstErrorPosition() {
				return -1;
			}

			@Override
			public boolean isComplete() {
				return true;
			}

			@Override
			public boolean isEquivalent(ExpressionPossibility<S> o) {
				return o.getClass() == getClass() && getType().equals(o.getType());
			}

			@Override
			public Expression<S> getExpression() {
				return Expression.empty(stream, type);
			}

			@Override
			public String toString() {
				return "(empty)";
			}
		};
	}
}
