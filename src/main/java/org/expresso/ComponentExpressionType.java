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
public class ComponentExpressionType<S extends BranchableStream<?, ?>> implements ExpressionType<S> {
	private final ExpressionType<S> theWrapped;
	private final NavigableSet<String> theFields;
	private final boolean isEnclosed;

	/**
	 * @param wrapped The expression type marked as a field by a composing expression
	 * @param fields The set of fields this type is marked with
	 * @param enclosed Whether this expression type is {@link #isEnclosed() enclosed} by its owner
	 */
	public ComponentExpressionType(ExpressionType<S> wrapped, NavigableSet<String> fields, boolean enclosed) {
		theWrapped = wrapped;
		theFields = fields;
		isEnclosed = enclosed;
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

	@Override
	public boolean isEnclosed() {
		return isEnclosed;
	}

	/** @return The fields that are declared on this expression type */
	public NavigableSet<String> getFields() {
		return theFields;
	}

	@Override
	public <S2 extends S> ComponentExpression<S2> parse(ExpressoParser<S2> parser, Expression<S2> lowBound, Expression<S2> highBound)
		throws IOException {
		return ComponentExpressionType.wrap(this, parser.parseWith(theWrapped, //
			unwrap(lowBound), unwrap(highBound)));
	}

	private <S2 extends S> Expression<S2> unwrap(Expression<S2> ex) {
		return ex == null ? null : ((ComponentExpression<S2>) ex).getWrapped();
	}

	@Override
	public int compare(Expression<? extends S> o1, Expression<? extends S> o2) {
		return theWrapped.compare(//
			((ComponentExpression<? extends S>) o1).getWrapped(), ((ComponentExpression<? extends S>) o2).getWrapped());
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
	static <S extends BranchableStream<?, ?>> ComponentExpression<S> wrap(ComponentExpressionType<? super S> type,
		Expression<S> possibility) {
		return possibility == null ? null : new ComponentExpression<>(type, possibility);
	}
}
