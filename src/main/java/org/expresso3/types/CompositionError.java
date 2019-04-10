package org.expresso3.types;

import java.util.function.Supplier;

import org.expresso3.Expression;

/** Represents an error in this expression that is not due directly to an error in a component */
public class CompositionError {
	/** The position of the error in the composite expression */
	public final int position;
	/** The error message */
	public final Supplier<String> error;
	/** The weight of the error, applied (negatively) to the expression's {@link Expression#getMatchQuality() quality} */
	public final int errorWeight;

	/**
	 * @param position The position of the error in the composite expression
	 * @param error The error message
	 * @param errorWeight The weight of the error, applied (negatively) to the expression's {@link Expression#getMatchQuality() quality}
	 */
	public CompositionError(int position, Supplier<String> error, int errorWeight) {
		this.position = position;
		this.error = error;
		this.errorWeight = errorWeight;
	}
}