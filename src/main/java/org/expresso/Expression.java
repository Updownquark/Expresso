package org.expresso;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import org.expresso.stream.BranchableStream;

/**
 * Represents a possible interpretation of content at a particular place in a stream
 *
 * @param <S> The type of the stream
 */
public interface Expression<S extends BranchableStream<?, ?>> extends Comparable<Expression<S>> {
	/** @return The expression type whose interpretation this is */
	ExpressionType<? super S> getType();

	/** @return The stream whose content this is an interpretation of */
	S getStream();

	/** @return The number of places in the stream that this interpretation thinks it understands */
	int length();

	/** @return Any components that may make up this expression */
	List<? extends Expression<S>> getChildren();

	/**
	 * @return A collection of different interpretations that are variations of this one
	 * @throws IOException If an error occurs reading the stream
	 */
	Collection<? extends Expression<S>> fork() throws IOException;

	/** @return The number of errors in this possibility */
	int getErrorCount();

	/** @return The expression in this structure containing the first error in this possibility (or null if there is no error) */
	Expression<S> getFirstError();

	/** @return The position in this expression where an error is recognized (or -1 if this expression itself does not have an error) */
	int getLocalErrorRelativePosition();

	/** @return The error message in this expression */
	String getLocalErrorMessage();

	/** @return An expression equivalent to this with possibly condensed structure */
	Expression<S> unwrap();

	/** @return The complexity of this expression */
	int getComplexity();

	/**
	 * Prints a multi-line text representation of this possibility to a string builder
	 * 
	 * @param str The string builder to print to
	 * @param indent The number of tabs to insert after each newline in the text representation
	 * @param metadata Metadata that will be appended to the first line
	 * @return The same string builder
	 */
	StringBuilder print(StringBuilder str, int indent, String metadata);

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

	/** @return The stream content that this expression represents */
	default String printContent() {
		return getStream().printContent(0, length(), null).toString();
	}

	@Override
	default int compareTo(Expression<S> p2) {
		// If one is more consistent (has fewer errors than the other), then obviously it is better
		int ec1 = getErrorCount();
		int ec2 = p2.getErrorCount();
		if (ec1 != ec2)
			return ec1 - ec2;

		Expression<S> firstErr1 = getFirstError();
		Expression<S> firstErr2 = p2.getFirstError();
		int len1 = length();
		int len2 = p2.length();

		// The possibility that understands the most content without error is the best
		int understood1 = firstErr1 != null ? firstErr1.getLocalErrorRelativePosition() : len1;
		int understood2 = firstErr2 != null ? firstErr2.getLocalErrorRelativePosition() : len2;
		if (understood1 != understood2)
			return understood2 - understood1;

		// If both understand the same but one is complete, it is the best
		if ((firstErr1 == null) != (firstErr2 == null)) {
			if (firstErr1 == null)
				return -1;
			else if (firstErr2 == null)
				return 1;
		}

		// Use Occam's razor
		int c1 = getComplexity();
		int c2 = p2.getComplexity();
		if (c1 != c2)
			return c1 - c2;

		// If both are incomplete but one thinks it might understand more, give it a chance
		if (len1 != len2)
			return len2 - len1;

		return 0;
	}

	/**
	 * @param stream The stream
	 * @param type The expression type
	 * @return An empty expression possibility for the given type and stream
	 */
	static <S extends BranchableStream<?, ?>> Expression<S> empty(S stream, ExpressionType<? super S> type) {
		return new Expression<S>() {
			@Override
			public ExpressionType<? super S> getType() {
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
			public List<? extends Expression<S>> getChildren() {
				return Collections.emptyList();
			}

			@Override
			public Collection<? extends Expression<S>> fork() throws IOException {
				return Collections.emptyList();
			}

			@Override
			public int getErrorCount() {
				return 0;
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
			public String getLocalErrorMessage() {
				return null;
			}

			@Override
			public Expression<S> unwrap() {
				return this;
			}

			@Override
			public int getComplexity() {
				return 0;
			}

			@Override
			public boolean equals(Object o) {
				return o.getClass() == getClass() && getType().equals(((Expression<S>) o).getType());
			}

			@Override
			public int hashCode() {
				return Objects.hash(type, stream.getPosition());
			}

			@Override
			public StringBuilder print(StringBuilder str, int indent, String metadata) {
				for (int i = 0; i < indent; i++)
					str.append('\t');
				str.append(type).append("(empty)").append(metadata);
				return str;
			}

			@Override
			public String toString() {
				return "(empty)";
			}
		};
	}

	/** A worker class that searches an expression's tree structure for fields */
	public static class FieldSearcher {
		static <S extends BranchableStream<?, ?>> void findFields(Expression<S> expr, String field,
			Deque<Expression<S>> results) {
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
