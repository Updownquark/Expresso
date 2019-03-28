package org.expresso;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import org.expresso.stream.BranchableStream;

/**
 * Represents a possible interpretation of content at a particular place in a stream
 *
 * @param <S> The type of the stream
 */
public interface ExpressionPossibility<S extends BranchableStream<?, ?>> extends Comparable<ExpressionPossibility<S>> {
	/** @return The expression type whose interpretation this is */
	ExpressionType<? super S> getType();

	/** @return The stream whose content this is an interpretation of */
	S getStream();

	/** @return The number of places in the stream that this interpretation thinks it understands */
	int length();

	/**
	 * @return A collection of different interpretations that are variations of this one
	 * @throws IOException If an error occurs reading the stream
	 */
	Collection<? extends ExpressionPossibility<S>> fork() throws IOException;

	/** @return The number of errors in this possibility */
	int getErrorCount();

	/** @return The location of the first error in this possibility (or -1 if there is no error) */
	int getFirstErrorPosition();

	/**
	 * Creates an {@link Expression} as a concrete representation of this possibility
	 * 
	 * @return The expression representing this possibility
	 */
	Expression<S> getExpression();

	/**
	 * Prints a multi-line text representation of this possibility to a string builder
	 * 
	 * @param str The string builder to print to
	 * @param indent The number of tabs to insert after each newline in the text representation
	 * @param metadata Metadata that will be appended to the first line
	 * @return The same string builder
	 */
	StringBuilder print(StringBuilder str, int indent, String metadata);

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

	/**
	 * @param stream The stream
	 * @param type The expression type
	 * @return An empty expression possibility for the given type and stream
	 */
	static <S extends BranchableStream<?, ?>> ExpressionPossibility<S> empty(S stream, ExpressionType<? super S> type) {
		return new ExpressionPossibility<S>() {
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
}
