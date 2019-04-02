package org.expresso.types;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.expresso.Expression;
import org.expresso.ExpressionType;
import org.expresso.ExpressoParser;
import org.expresso.stream.BranchableStream;
import org.qommons.BiTuple;

public abstract class ComposedExpression<S extends BranchableStream<?, ?>> implements Expression<S> {
	private final ExpressionType<? super S> theType;
	private final ExpressoParser<S> theParser;
	private final List<? extends Expression<S>> theChildren;
	private final int theLength;
	private final Expression<S> theFirstError;
	private final int theErrorCount;
	private final BiTuple<Integer, String> theSelfError;
	private final int theComplexity;

	public ComposedExpression(ExpressionType<? super S> type, ExpressoParser<S> parser, List<? extends Expression<S>> children) {
		theType = type;
		theParser = parser;
		theChildren = Collections.unmodifiableList(children);
		theLength = computeLength();
		Expression<S> firstError = null;
		int errorCount = 0;
		int complexity = getSelfComplexity();
		for (Expression<S> child : children) {
			errorCount += child.getErrorCount();
			complexity += child.getComplexity();
			if (errorCount > 0 && firstError == null)
				firstError = child.getFirstError();
		}
		theErrorCount = errorCount;
		theComplexity = complexity;
		theSelfError = getSelfError();
		if (theSelfError != null)
			errorCount++;
		if (theSelfError != null
			&& (firstError == null || firstError.getStream().getPosition() + firstError.length() > theSelfError.getValue1()))
			firstError = this;
		theFirstError = firstError;
	}

	@Override
	public ExpressionType<? super S> getType() {
		return theType;
	}

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

	protected abstract int getSelfComplexity();

	protected int computeLength() {
		if (getChildren().isEmpty())
			return 0;
		Expression<S> last = getChildren().get(getChildren().size() - 1);
		return last.length() + last.getStream().getPosition() - getStream().getPosition();
	}

	protected BiTuple<Integer, String> getSelfError() {
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
		return theSelfError == null ? -1 : theSelfError.getValue1();
	}

	@Override
	public String getLocalErrorMessage() {
		return theSelfError == null ? null : theSelfError.getValue2();
	}

	@Override
	public int getComplexity() {
		return theComplexity;
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

	protected boolean shouldPrintErrorInfo() {
		return false;
	}

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
