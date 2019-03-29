package org.expresso;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.stream.Collectors;

import org.expresso.stream.BranchableStream;

/**
 * A wrapper around an expression type that is marked as a field by a {@link ConfiguredExpressionType} ancestor
 *
 * @param <S> The stream super-type of the expression type
 */
public interface ExpressionFieldType<S extends BranchableStream<?, ?>> extends ExpressionType<S> {
	/** @return The actual content expression type */
	ExpressionType<S> getWrapped();

	/** @return The fields that are declared on this expression type */
	NavigableSet<String> getFields();

	@Override
	default int getCacheId() {
		return -1;
	}

	/**
	 * @param type The field type
	 * @param possibility The possibility to wrap
	 * @return The wrapped field possibility
	 */
	static <S extends BranchableStream<?, ?>> FieldExpressionPossibility<S> wrap(ExpressionFieldType<? super S> type,
		ExpressionPossibility<S> possibility) {
		return possibility == null ? null : new FieldExpressionPossibility<>(type, possibility);
	}

	/**
	 * A simple field possibility
	 *
	 * @param <S> The stream type of the possibility
	 */
	public static class FieldExpressionPossibility<S extends BranchableStream<?, ?>> implements ExpressionPossibility<S> {
		private final ExpressionFieldType<? super S> theType;
		private final ExpressionPossibility<S> theWrapped;

		/**
		 * @param type The field type
		 * @param wrapped The wrapped possibility
		 */
		public FieldExpressionPossibility(ExpressionFieldType<? super S> type, ExpressionPossibility<S> wrapped) {
			theType = type;
			theWrapped = wrapped;
		}

		@Override
		public ExpressionType<? super S> getType() {
			return theType;
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
		public Collection<? extends ExpressionPossibility<S>> fork() throws IOException {
			Collection<? extends ExpressionPossibility<S>> wrappedForks = theWrapped.fork();
			if (wrappedForks.isEmpty())
				return wrappedForks;
			else
				return wrappedForks.stream().map(fork -> wrap(theType, fork))
					.collect(Collectors.toCollection(() -> new ArrayList<>(wrappedForks.size())));
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
		public int getComplexity() {
			return theWrapped.getComplexity();
		}

		@Override
		public boolean equals(Object o) {
			if (o == this)
				return true;
			else if (!(o instanceof FieldExpressionPossibility))
				return false;
			FieldExpressionPossibility<S> other = (FieldExpressionPossibility<S>) o;
			return theType.equals(other.getType()) && theWrapped.equals(other.theWrapped);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theType, theWrapped);
		}

		@Override
		public ExpressionField<S> getExpression() {
			return new ExpressionField.SimpleExpressionField<>(theType, theWrapped.getExpression());
		}

		@Override
		public StringBuilder print(StringBuilder str, int indent, String metadata) {
			return theWrapped.print(str, indent, metadata + theType.getFields());
		}

		@Override
		public String toString() {
			return print(new StringBuilder(), 0, "").toString();
		}
	}
}
