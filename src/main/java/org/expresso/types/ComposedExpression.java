package org.expresso.types;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.expresso.Expression;
import org.expresso.ExpressionType;
import org.expresso.ExpressoParser;
import org.expresso.stream.BranchableStream;

/**
 * An (abstract) expression composed of zero or more components
 *
 * @param <S> The type of the stream
 */
public abstract class ComposedExpression<S extends BranchableStream<?, ?>> implements Expression<S> {
	/** Represents an error in this expression that is not due directly to an error in a component */
	protected static class CompositionError {
		/** The position of the error in the composite expression */
		public final int position;
		/** The error message */
		public final String error;
		/** The weight of the error, applied (negatively) to the expression's {@link Expression#getMatchQuality() quality} */
		public final int errorWeight;

		/**
		 * @param position The position of the error in the composite expression
		 * @param error The error message
		 * @param errorWeight The weight of the error, applied (negatively) to the expression's {@link Expression#getMatchQuality() quality}
		 */
		public CompositionError(int position, String error, int errorWeight) {
			this.position = position;
			this.error = error;
			this.errorWeight = errorWeight;
		}
	}

	private final ExpressionType<? super S> theType;
	private final ExpressoParser<S> theParser;
	private final List<? extends Expression<S>> theChildren;
	private final int theLength;
	private final Expression<S> theFirstError;
	private final int theErrorCount;
	private final CompositionError theSelfError;
	private final int theComplexity;
	private final int theQuality;

	/**
	 * @param type The type of the expression
	 * @param parser The parser this expression was parsed at
	 * @param children The components
	 */
	public ComposedExpression(ExpressionType<? super S> type, ExpressoParser<S> parser, List<? extends Expression<S>> children) {
		theType = type;
		theParser = parser;
		theChildren = Collections.unmodifiableList(children);
		theLength = computeLength();
		Expression<S> firstError = null;
		int errorCount = 0;
		int complexity = getSelfComplexity();
		int quality = 0;
		for (Expression<S> child : children) {
			errorCount += child.getErrorCount();
			complexity += child.getComplexity();
			quality += child.getMatchQuality();
			if (errorCount > 0 && firstError == null)
				firstError = child.getFirstError();
		}
		theErrorCount = errorCount;
		theComplexity = complexity;
		theSelfError = getSelfError();
		if (theSelfError != null) {
			errorCount++;
			quality -= theSelfError.errorWeight;
		}
		if (theSelfError != null
			&& (firstError == null || firstError.getStream().getPosition() + firstError.length() > theSelfError.position))
			firstError = this;
		theFirstError = firstError;
		theQuality = quality;
	}

	@Override
	public ExpressionType<? super S> getType() {
		return theType;
	}

	/** @return The parser this expression was parsed at */
	protected ExpressoParser<S> getParser() {
		return theParser;
	}

	@Override
	public S getStream() {
		return theParser.getStream();
	}

	@Override
	public List<? extends Expression<S>> getChildren() {
		return theChildren;
	}

	/** @return The complexity that this type imparts apart from the complexity of the components */
	protected abstract int getSelfComplexity();

	/**
	 * @return
	 *         <p>
	 *         The length of this expression.
	 *         </p>
	 *         <p>
	 *         By default, this is the difference between the stream position and the stream position of the last component plus the last
	 *         component's length.
	 *         </p>
	 *         <p>
	 *         Subclasses may override this value if they have content beyond the last component.
	 *         </p>
	 */
	protected int computeLength() {
		if (getChildren().isEmpty())
			return 0;
		Expression<S> last = getChildren().get(getChildren().size() - 1);
		return last.length() + last.getStream().getPosition() - getStream().getPosition();
	}

	/**
	 * @return Computes any errors in this component that are not directly due to an error in a component. Should be overridden by
	 *         subclasses when this is possible.
	 */
	protected CompositionError getSelfError() {
		return null;
	}

	@Override
	public int length() {
		return theLength;
	}

	@Override
	public Expression<S> getFirstError() {
		return theFirstError;
	}

	@Override
	public int getErrorCount() {
		return theErrorCount;
	}

	@Override
	public int getLocalErrorRelativePosition() {
		return theSelfError == null ? -1 : theSelfError.position;
	}

	@Override
	public String getLocalErrorMessage() {
		return theSelfError == null ? null : theSelfError.error;
	}

	@Override
	public int getComplexity() {
		return theComplexity;
	}

	@Override
	public int getMatchQuality() {
		return theQuality;
	}

	@Override
	public Expression<S> unwrap() {
		return this;
	}

	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		else if (o == null || o.getClass() != getClass())
			return false;
		ComposedExpression<S> other = (ComposedExpression<S>) o;
		if (!getType().equals(other.getType()))
			return false;
		if (getStream().getPosition() != other.getStream().getPosition())
			return false;
		if (length() != other.length())
			return false;
		if (theChildren.size() != other.theChildren.size())
			return false;
		for (int i = 0; i < theChildren.size(); i++)
			if (!theChildren.get(i).equals(other.theChildren.get(i)))
				return false;
		return true;
	}

	@Override
	public int hashCode() {
		return Objects.hash(getClass(), theType, getStream().getPosition(), length(), theChildren);
	}

	/** @return Whether this expression should print error information in its {@link #toString()} */
	protected boolean shouldPrintErrorInfo() {
		return false;
	}

	/** @return Whether this expression should print its stream content in its {@link #toString()} */
	protected boolean shouldPrintContent() {
		return false;
	}

	@Override
	public StringBuilder print(StringBuilder str, int indent, String metadata) {
		for (int i = 0; i < indent; i++)
			str.append('\t');
		str.append(theType);
		if (shouldPrintErrorInfo()) {
			str.append(metadata).append(" (").append(getErrorCount()).append(", ");
			if (theFirstError != null)
				str.append(theFirstError.getLocalErrorRelativePosition());
			else
				str.append("-1");
			str.append(", ").append(getComplexity()).append("): ");
		}
		if (shouldPrintContent())
			getStream().printContent(0, length(), str);
		for (Expression<S> child : theChildren) {
			str.append('\n');
			child.print(str, indent + 1, "");
		}
		return str;
	}

	@Override
	public String toString() {
		return print(new StringBuilder(), 0, "").toString();
	}
}
