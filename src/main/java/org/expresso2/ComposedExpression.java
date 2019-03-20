package org.expresso2;

import java.util.Collections;
import java.util.List;

import org.expresso.parse.BranchableStream;
import org.qommons.BiTuple;

public abstract class ComposedExpression<S extends BranchableStream<?, ?>> extends AbstractExpression<S> {
	private final List<? extends Expression<S>> theChildren;
	private final int theLength;
	private final Expression<S> theFirstError;
	private final int theErrorCount;
	private final BiTuple<Integer, String> theSelfError;

	public ComposedExpression(S stream, ExpressionComponent<? super S> type, List<? extends Expression<S>> children) {
		super(stream, type);
		theChildren = Collections.unmodifiableList(children);
		theLength = computeLength();
		Expression<S> firstError = null;
		int errorCount = 0;
		theSelfError = getSelfError();
		for (Expression<S> child : children) {
			errorCount += child.getErrorCount();
			if (errorCount > 0 && firstError == null)
				firstError = child.getFirstError();
		}
		if (theSelfError != null
			&& (firstError == null || firstError.getStream().getPosition() + firstError.length() > theSelfError.getValue1()))
			firstError = this;
		theFirstError = firstError;
		theErrorCount = errorCount;
	}

	@Override
	public List<? extends Expression<S>> getChildren() {
		return theChildren;
	}

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
}
