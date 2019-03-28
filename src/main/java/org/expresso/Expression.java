package org.expresso;

import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import org.expresso.stream.BranchableStream;

/**
 * The result of parsing an expression from a stream
 *
 * @param <S> The type of stream that this expression was parsed from
 */
public interface Expression<S extends BranchableStream<?, ?>> {
	/** @return The stream this expression was parsed from */
	S getStream();

	/** @return The expression type that parsed this expression */
	ExpressionType<? super S> getType();

	/** @return Any components that may make up this expression */
	List<? extends Expression<S>> getChildren();

	/** @return The first component of expression that has an error */
	Expression<S> getFirstError();

	/** @return The position in this expression where an error is recognized (or -1 if this expression itself does not have an error) */
	int getLocalErrorRelativePosition();

	/** @return The number of errors in this expression plus those in any of its children */
	int getErrorCount();

	/** @return The error message for this expression (or null if there is no error) */
	String getLocalErrorMessage();

	/** @return The number of spaces in the stream that this expression spans */
	int length();

	/** @return An expression with the same content as this expression but with less wrapping information */
	Expression<S> unwrap();

	/**
	 * @param fields The nested fields to get
	 * @return A deque with all expressions matching the given field path
	 */
	default Deque<ExpressionField<S>> getField(String... fields) {
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
				if (lfr instanceof ExpressionField) {
					for (Expression<S> child : lfr.getChildren())
						FieldSearcher.findFields(child, field, result);
				} else
					FieldSearcher.findFields(lfr, field, result);
			}
		}
		return (Deque<ExpressionField<S>>) (Deque<?>) result;
	}

	/**
	 * @param stream The stream to represent
	 * @param type The expression type to represent
	 * @return An empty expression
	 */
	public static <S extends BranchableStream<?, ?>> Expression<S> empty(S stream, ExpressionType<? super S> type) {
		return new EmptyExpression<>(stream, type);
	}

	/**
	 * An empty expression
	 *
	 * @param <S> the type of the expression's stream
	 */
	public static class EmptyExpression<S extends BranchableStream<?, ?>> extends AbstractExpression<S> {
		EmptyExpression(S stream, ExpressionType<? super S> type) {
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

	/** A worker class that searches an expression's tree structure for fields */
	public static class FieldSearcher {
		static <S extends BranchableStream<?, ?>> void findFields(Expression<S> expr, String field, Deque<Expression<S>> results) {
			if (expr instanceof ExpressionField) {
				if (((ExpressionField<S>) expr).getType().getFields().contains(field))
					results.add(expr);
				return;
			}
			for (Expression<S> child : expr.getChildren())
				findFields(child, field, results);
		}
	}
}
