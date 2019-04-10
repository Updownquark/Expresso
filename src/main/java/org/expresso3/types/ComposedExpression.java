package org.expresso3.types;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.expresso.stream.BranchableStream;
import org.expresso.util.ExpressoUtils;
import org.expresso3.Expression;
import org.expresso3.ExpressionType;

/**
 * An (abstract) expression composed of zero or more components
 *
 * @param <S> The type of the stream
 */
public abstract class ComposedExpression<S extends BranchableStream<?, ?>> implements Expression<S> {
	private final ExpressionType<? super S> theType;
	private final S theStream;
	private final List<? extends Expression<S>> theChildren;
	private int theLength;

	private boolean isQualityComputed;
	private Expression<S> theFirstError;
	private int theErrorCount;
	private CompositionError theSelfError;
	private int theQuality;

	/**
	 * @param type The type of the expression
	 * @param stream The stream this expression was parsed at
	 * @param children The components
	 */
	public ComposedExpression(ExpressionType<? super S> type, S stream, List<? extends Expression<S>> children) {
		theType = type;
		theStream = stream;
		theChildren = Collections.unmodifiableList(children);
		theLength = -1;
	}

	@Override
	public ExpressionType<? super S> getType() {
		return theType;
	}

	@Override
	public S getStream() {
		return theStream;
	}

	@Override
	public List<? extends Expression<S>> getChildren() {
		return theChildren;
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
		if (theLength < 0) {
			theLength = computeLength();
			if (theLength < 0) {
				computeLength();
				throw new IllegalStateException("Negative length: " + theLength);
			}
		}
		return theLength;
	}

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
		return ExpressoUtils.getLength(theStream.getPosition(), theChildren);
	}

	@Override
	public Expression<S> getFirstError() {
		computeQuality();
		return theFirstError;
	}

	@Override
	public int getErrorCount() {
		computeQuality();
		return theErrorCount;
	}

	@Override
	public int getLocalErrorRelativePosition() {
		computeQuality();
		return theSelfError == null ? -1 : theSelfError.position;
	}

	@Override
	public String getLocalErrorMessage() {
		computeQuality();
		return theSelfError == null ? null : theSelfError.error.get();
	}

	@Override
	public int getMatchQuality() {
		computeQuality();
		return theQuality;
	}

	private void computeQuality() {
		if (isQualityComputed)
			return;
		isQualityComputed = true;
		Expression<S> firstError = null;
		int errorCount = 0;
		int quality = 0;
		for (Expression<S> child : theChildren) {
			errorCount += child.getErrorCount();
			quality += child.getMatchQuality();
			if (errorCount > 0 && firstError == null)
				firstError = child.getFirstError();
		}
		theSelfError = getSelfError();
		if (theSelfError != null) {
			errorCount++;
			quality -= theSelfError.errorWeight;
		}
		theErrorCount = errorCount;
		if (theSelfError != null
			&& (firstError == null || firstError.getStream().getPosition() + firstError.length() > theSelfError.position))
			firstError = this;
		theFirstError = firstError;
		theQuality = quality;
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
			str.append(", ").append(getMatchQuality()).append("): ");
		}
		if (shouldPrintContent()) {
			if (length() > 0)
				getStream().printContent(0, length(), str);
			else
				str.append("(empty)");
		}
		for (Expression<S> child : theChildren) {
			if (child.length() == 0 && child.getType().getSpecificity() == 0)
				continue; // Optional and not present, ignore
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
