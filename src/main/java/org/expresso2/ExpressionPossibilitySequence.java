package org.expresso2;

import java.io.IOException;

import org.expresso.parse.BranchableStream;

public interface ExpressionPossibilitySequence<S extends BranchableStream<?, ?>> {
	ExpressionPossibility<S> getNextPossibility() throws IOException;

	static <S extends BranchableStream<?, ?>> ExpressionPossibilitySequence<S> empty() {
		return new ExpressionPossibilitySequence<S>() {
			@Override
			public ExpressionPossibility<S> getNextPossibility() throws IOException {
				return null;
			}
		};
	}
}
