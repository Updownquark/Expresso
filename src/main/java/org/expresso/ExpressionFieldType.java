package org.expresso;

import java.util.NavigableSet;

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
	static <S extends BranchableStream<?, ?>> ExpressionField<S> wrap(ExpressionFieldType<? super S> type,
		Expression<S> possibility) {
		return possibility == null ? null : new ExpressionField.SimpleExpressionField<>(type, possibility);
	}
}
