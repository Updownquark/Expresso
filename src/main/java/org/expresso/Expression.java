package org.expresso;

import java.io.IOException;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import org.expresso.stream.BranchableStream;
import org.qommons.collect.BetterList;
import org.qommons.tree.BetterTreeList;

/**
 * Represents a possible interpretation of content at a particular place in a stream
 *
 * @param <S> The type of the stream
 */
public interface Expression<S extends BranchableStream<?, ?>> extends Comparable<Expression<?>> {
	/** @return The expression type whose interpretation this is */
	ExpressionType<? super S> getType();

	/** @return The stream whose content this is an interpretation of */
	S getStream();

	/** @return The number of places in the stream that this interpretation thinks it understands */
	int length();

	/** @return Any components that may make up this expression */
	List<? extends Expression<S>> getChildren();

	/**
	 * @param parser The parser parsing the expression
	 * @return Another interpretation of the stream by this expresssion's type
	 * @throws IOException If an error occurs reading the stream
	 */
	Expression<S> nextMatch(ExpressoParser<S> parser) throws IOException;

	/**
	 * Allows another branch of matches
	 * 
	 * @param parser The parser parsing the expression
	 * @return Another interpretation of the stream by this expresssion's type
	 * @throws IOException If an error occurs reading the stream
	 */
	Expression<S> nextMatchLowPriority(ExpressoParser<S> parser) throws IOException;

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

	/**
	 * @return A measure of how certain this expression's {@link #getType() type} is that this expression accurately represents the intent
	 *         of the stream content
	 */
	int getMatchQuality();

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
	 * @return A list with all expressions matching the given field path
	 */
	default BetterList<ExpressionField<S>> getField(String... fields) {
		if (fields.length == 0)
			throw new IllegalArgumentException("Fields expected");
		BetterList<Expression<S>> result = new BetterTreeList<>(false);
		BetterList<Expression<S>> lastFieldResult = new BetterTreeList<>(false);
		result.add(this);
		for (String field : fields) {
			boolean optional = field.length() > 1 && field.charAt(0) == '?';
			if (optional)
				field = field.substring(1);

			// We could clear out lastFieldResult and add all the results to it, then clear the results, but this is more efficient
			BetterList<Expression<S>> temp = result;
			result = lastFieldResult;
			lastFieldResult = temp;
			result.clear();
			for (Expression<S> lfr : lastFieldResult) {
				if (lfr instanceof ExpressionField) {
					for (Expression<S> child : lfr.getChildren()) {
						if (!FieldSearcher.findFields(child, field, result) && optional)
							result.add(lfr);
					}
				} else {
					if (!FieldSearcher.findFields(lfr, field, result) && optional)
						result.add(lfr);
				}
			}
		}
		return (BetterList<ExpressionField<S>>) (Deque<?>) result;
	}

	/**
	 * @param inline If true, the resulting string will have all newlines and tabs replaced with "\n" and "\t", respectively
	 * @return The stream content that this expression represents
	 */
	default String printContent(boolean inline) {
		StringBuilder str = getStream().printContent(0, length(), null);
		if (inline) {
			for (int i = 0; i < str.length(); i++) {
				char c = str.charAt(i);
				switch (c) {
				case '\n':
					str.setCharAt(i, '\\');
					str.insert(i + 1, 'n');
					i++;
					break;
				case '\t':
					str.setCharAt(i, '\\');
					str.insert(i + 1, 't');
					i++;
					break;
				}
			}
		}
		return str.toString();
	}

	/**
	 * Compares this expression to another for likelihood that each expression matches the true intent of the stream content.
	 * 
	 * @param p2 The expression to compare this to
	 * @return
	 *         <ul>
	 *         <li>&lt;0 if this expression is more likely to be the true intent of the stream content than <code>p2</code></li>
	 *         <li>&gt;0 if this expression is less likely to be the true intent of the stream content than <code>p2</code></li>
	 *         <li>0 if this expression and <code>p2</code> are equally likely to be the true intent of the stream content</li>
	 *         </ul>
	 */
	@Override
	default int compareTo(Expression<?> p2) {
		int mq1 = getMatchQuality();
		int mq2 = p2.getMatchQuality();
		if (mq1 != mq2)
			return mq2 - mq1;

		Expression<?> firstErr1 = getFirstError();
		Expression<?> firstErr2 = p2.getFirstError();
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

		// If both are incomplete but one thinks it might understand more, give it a chance
		if (len1 != len2)
			return len2 - len1;

		return 0;
	}

	/**
	 * @param <S> The type of the stream being parsed
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
			public Expression<S> nextMatch(ExpressoParser<S> parser) throws IOException {
				return null;
			}

			@Override
			public Expression<S> nextMatchLowPriority(ExpressoParser<S> parser) throws IOException {
				return null;
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
			public int getMatchQuality() {
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
		static <S extends BranchableStream<?, ?>> boolean findFields(Expression<S> expr, String field, Deque<Expression<S>> results) {
			boolean found;
			if (expr instanceof ExpressionField) {
				found = ((ExpressionField<S>) expr).getType().getFields().contains(field);
				if (found)
					results.add(expr);
				return found;
			}
			found = false;
			for (Expression<S> child : expr.getChildren())
				found |= findFields(child, field, results);
			return found;
		}
	}
}
