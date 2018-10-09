package org.expresso2;

import java.io.IOException;

import org.expresso.parse.BranchableStream;

public interface ExpressionPossibility<S extends BranchableStream<?, ?>> {
	int length();

	int advanceInStream() throws IOException;

	int getErrorCount();

	boolean isComplete();

	Expression<S> getExpression();

	static <S extends BranchableStream<?, ?>> ExpressionPossibility<S> empty() {
		return new ExpressionPossibility<S>() {
			@Override
			public int length() {
				return 0;
			}

			@Override
			public int advanceInStream() throws IOException {
				return 0;
			}

			@Override
			public int getErrorCount() {
				return 0;
			}

			@Override
			public boolean isComplete() {}

			@Override
			public Expression<S> getExpression() {}
		};
	}
}
