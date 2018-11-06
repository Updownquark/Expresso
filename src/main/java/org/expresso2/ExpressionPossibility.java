package org.expresso2;

import java.io.IOException;

import org.expresso.parse.BranchableStream;

public interface ExpressionPossibility<S extends BranchableStream<?, ?>> {
	S getStream();

	int length();

	ExpressionPossibility<S> advance() throws IOException;

	boolean hasFork();

	ExpressionPossibility<S> fork() throws IOException;

	int getErrorCount();

	boolean isComplete();

	Expression<S> getExpression();

	static <S extends BranchableStream<?, ?>> ExpressionPossibility<S> empty(S stream, ExpressionComponent<? super S> type) {
		return new ExpressionPossibility<S>() {
			@Override
			public S getStream() {
				return stream;
			}

			@Override
			public int length() {
				return 0;
			}

			@Override
			public ExpressionPossibility<S> next() throws IOException {
				return null;
			}

			@Override
			public int getErrorCount() {
				return 0;
			}

			@Override
			public boolean isComplete() {
				return true;
			}

			@Override
			public Expression<S> getExpression() {
				return Expression.empty(stream, type);
			}
		};
	}
}
