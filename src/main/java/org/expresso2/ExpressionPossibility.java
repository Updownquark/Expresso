package org.expresso2;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import org.expresso.parse.BranchableStream;

public interface ExpressionPossibility<S extends BranchableStream<?, ?>> {
	ExpressionComponent<? super S> getType();

	S getStream();

	int length();

	Collection<? extends ExpressionPossibility<S>> fork() throws IOException;

	int getErrorCount();

	int getFirstErrorPosition();

	boolean isComplete();

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
			public Collection<? extends ExpressionPossibility<S>> fork() throws IOException {
				return Collections.emptyList();
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
			public boolean equals(Object o) {
				return o.getClass() == getClass() && getType().equals(((ExpressionPossibility<S>) o).getType());
			}

			@Override
			public int hashCode() {
				return Objects.hash(type, stream.getPosition());
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
