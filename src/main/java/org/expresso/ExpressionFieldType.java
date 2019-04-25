package org.expresso;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.NavigableSet;

import org.expresso.stream.BranchableStream;

/**
 * A wrapper around an expression type that is marked as a field by a {@link ConfiguredExpressionType} ancestor
 *
 * @param <S> The stream super-type of the expression type
 */
public class ExpressionFieldType<S extends BranchableStream<?, ?>> implements ExpressionType<S> {
	private final ExpressionType<S> theWrapped;
	private final NavigableSet<String> theFields;

	/**
	 * @param wrapped The expression type marked as a field by a composing expression
	 * @param fields The set of fields this type is marked with
	 */
	public ExpressionFieldType(ExpressionType<S> wrapped, NavigableSet<String> fields) {
		theWrapped = wrapped;
		theFields = fields;
	}

	/** @return The actual content expression type */
	public ExpressionType<S> getWrapped() {
		return theWrapped;
	}

	@Override
	public int getId() {
		return -1;
	}

	@Override
	public boolean isCacheable() {
		return theWrapped.isCacheable();
	}

	@Override
	public int getEmptyQuality(int minQuality) {
		return theWrapped.getEmptyQuality(minQuality);
	}

	/** @return The fields that are declared on this expression type */
	public NavigableSet<String> getFields() {
		return theFields;
	}

	@Override
	public <S2 extends S> ExpressionField<S2> parse(ExpressoParser<S2> parser) throws IOException {
		return ExpressionFieldType.wrap(this, parser.parseWith(theWrapped));
	}

	@Override
	public Iterable<? extends ExpressionType<? super S>> getComponents() {
		return Collections.unmodifiableList(Arrays.asList(theWrapped));
	}

	@Override
	public String toString() {
		return theWrapped.toString() + " field=" + theFields;
	}

	/**
	 * @param <S> The type of the stream being parsed
	 * @param type The field type
	 * @param possibility The possibility to wrap
	 * @return The wrapped field possibility
	 */
	static <S extends BranchableStream<?, ?>> ExpressionField<S> wrap(ExpressionFieldType<? super S> type,
		Expression<S> possibility) {
		return possibility == null ? null : new ExpressionField.SimpleExpressionField<>(type, possibility);
	}
}
