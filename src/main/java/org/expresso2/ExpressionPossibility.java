package org.expresso2;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import org.expresso.parse.BranchableStream;

public interface ExpressionPossibility<S extends BranchableStream<?, ?>> extends Comparable<ExpressionPossibility<S>> {
	ExpressionComponent<? super S> getType();

	S getStream();

	int length();

	Collection<? extends ExpressionPossibility<S>> fork() throws IOException;

	int getErrorCount();

	int getFirstErrorPosition();

	Expression<S> getExpression();

	@Override
	default int compareTo(ExpressionPossibility<S> p2) {
		int fep1 = getFirstErrorPosition();
		int fep2 = p2.getFirstErrorPosition();
		int len1 = length();
		int len2 = p2.length();

		// The possibility that understands the most content without error is the best
		int understood1 = fep1 >= 0 ? fep1 : len1;
		int understood2 = fep2 >= 0 ? fep2 : len2;
		if (understood1 != understood2)
			return understood2 - understood1;

		// If both understand the same but one is complete, it is the best
		if ((fep1 < 0) != (fep2 < 0)) {
			if (fep1 < 0)
				return -1;
			else if (fep2 < 0)
				return 1;
		}

		// If both are incomplete but one thinks it might understand more, give it a chance
		if (len1 != len2)
			return len2 - len1;

		// Otherwise just differentiate on the number of errors
		int ec1 = getErrorCount();
		int ec2 = p2.getErrorCount();
		if (ec1 != ec2)
			return ec1 - ec2;

		return 0;
	}

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
