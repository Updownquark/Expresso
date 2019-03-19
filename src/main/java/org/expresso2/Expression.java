package org.expresso2;

import java.util.Collections;
import java.util.List;

import org.expresso.parse.BranchableStream;

public abstract class Expression<S extends BranchableStream<?, ?>> {
	private final S theStream;
	private final ExpressionComponent<? super S> theType;

	public Expression(S stream, ExpressionComponent<? super S> type) {
		theStream = stream;
		theType = type;
	}

	public S getStream() {
		return theStream;
	}

	public ExpressionComponent<? super S> getType() {
		return theType;
	}

	public abstract List<? extends Expression<S>> getChildren();

	public abstract Expression<S> getFirstError();

	public abstract int getLocalErrorRelativePosition();

	public abstract int getErrorCount();

	public abstract String getLocalErrorMessage();

	public abstract int length();

	public static <S extends BranchableStream<?, ?>> Expression<S> empty(S stream, ExpressionComponent<? super S> type) {
		return new EmptyExpression<>(stream, type);
	}

	private static class EmptyExpression<S extends BranchableStream<?, ?>> extends Expression<S> {
		EmptyExpression(S stream, ExpressionComponent<? super S> type) {
			super(stream, type);
		}

		@Override
		public List<? extends Expression<S>> getChildren() {
			return Collections.emptyList();
		}

		@Override
		public Expression<S> getFirstError() {
			return null;
		}

		@Override
		public int getLocalErrorRelativePosition() {
			return -1;
		}

		@Override
		public int getErrorCount() {
			return 0;
		}

		@Override
		public String getLocalErrorMessage() {
			return null;
		}

		@Override
		public int length() {
			return 0;
		}
	}
}
