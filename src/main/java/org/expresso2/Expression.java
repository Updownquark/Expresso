package org.expresso2;

import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import org.expresso.parse.BranchableStream;

public interface Expression<S extends BranchableStream<?, ?>> {
	S getStream();

	ExpressionComponent<? super S> getType();

	List<? extends Expression<S>> getChildren();

	Expression<S> getFirstError();

	int getLocalErrorRelativePosition();

	int getErrorCount();

	String getLocalErrorMessage();

	int length();

	Expression<S> unwrap();

	default Deque<Expression<S>> getField(String... fields) {
		if (fields.length == 0)
			throw new IllegalArgumentException("Fields expected");
		Deque<Expression<S>> result = new LinkedList<>();
		Deque<Expression<S>> lastFieldResult = new LinkedList<>();
		result.add(this);
		for (String field : fields) {
			// We could clear out lastFieldResult and add all the results to it, then clear the results, but this is more efficient
			Deque<Expression<S>> temp = result;
			result = lastFieldResult;
			lastFieldResult = temp;
			result.clear();
			for (Expression<S> lfr : lastFieldResult) {
				FieldSearcher.findFields(lfr, field, result);
			}
		}
		return result;
	}

	public static <S extends BranchableStream<?, ?>> Expression<S> empty(S stream, ExpressionComponent<? super S> type) {
		return new EmptyExpression<>(stream, type);
	}

	public static class EmptyExpression<S extends BranchableStream<?, ?>> extends AbstractExpression<S> {
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

		@Override
		public Expression<S> unwrap() {
			return null;
		}
	}

	public static class FieldSearcher {
		static <S extends BranchableStream<?, ?>> void findFields(Expression<S> expr, String field, Deque<Expression<S>> results) {
			if (expr.getType() instanceof ConfiguredExpressionType
				&& ((ConfiguredExpressionType<? super S>) expr.getType()).getFields().contains(field)) {
				results.add(expr);
				return;
			}
			for (Expression<S> child : expr.getChildren())
				findFields(child, field, results);
		}
	}
}
