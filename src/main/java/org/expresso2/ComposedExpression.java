package org.expresso2;

import java.util.Collections;
import java.util.List;

import org.expresso.parse.BranchableStream;

public class ComposedExpression<S extends BranchableStream<?, ?>> extends Expression<S> {
	private final List<? extends Expression<S>> theChildren;
	private final int theLength;
	private final ErrorExpression<S> theFirstError;
	private final int theErrorCount;

	public ComposedExpression(S stream, ExpressionComponent<? super S> type, List<? extends Expression<S>> children) {
		super(stream, type);
		theChildren = Collections.unmodifiableList(children);
		ErrorExpression<S> firstError = null;
		int errorCount = 0;
		int length = 0;
		for (Expression<S> child : children) {
			length += child.length();
			errorCount += child.getErrorCount();
			if (firstError == null)
				firstError = child.getFirstError();
		}
		theFirstError = firstError;
		theLength = length;
		theErrorCount = errorCount;
	}

	@Override
	public List<? extends Expression<S>> getChildren() {
		return theChildren;
	}

	@Override
	public int length() {
		return theLength;
	}

	@Override
	public ErrorExpression<S> getFirstError() {
		return theFirstError;
	}

	@Override
	public int getErrorCount() {
		return theErrorCount;
	}

	@Override
	public boolean isComplete() {
		return theChildren.isEmpty() || theChildren.get(theChildren.size() - 1).isComplete();
	}
}
