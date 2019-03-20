package org.expresso2.impl;

import java.io.IOException;

import org.expresso.parse.BranchableStream;
import org.expresso2.Expression;
import org.expresso2.ExpressionComponent;
import org.expresso2.ExpressionPossibility;

class CachedExpressionPossibility<S extends BranchableStream<?, ?>> implements ExpressionPossibility<S> {
	private final ExpressionComponent<? super S> theType;
	private ExpressionPossibility<S> thePossibility;
	private CachedExpressionPossibility<S> theLeftFork;
	private CachedExpressionPossibility<S> theRightFork;
	private CachedExpressionPossibility<S> theAdvanced;

	CachedExpressionPossibility(ExpressionComponent<? super S> type) {
		theType = type;
	}

	CachedExpressionPossibility<S> setPossibility(ExpressionPossibility<S> possibility) {
		thePossibility = possibility;
		return this;
	}

	public ExpressionPossibility<S> asPossibility() {
		return thePossibility == null ? null : this;
	}

	@Override
	public ExpressionComponent<? super S> getType() {
		return theType;
	}

	@Override
	public S getStream() {
		return thePossibility.getStream();
	}

	@Override
	public int length() {
		return thePossibility.length();
	}

	@Override
	public ExpressionPossibility<S> advance() throws IOException {
		if (theAdvanced == null)
			theAdvanced = new CachedExpressionPossibility<S>(theType).setPossibility(thePossibility.advance());
		return theAdvanced.asPossibility();
	}

	@Override
	public ExpressionPossibility<S> leftFork() throws IOException {
		if (theLeftFork == null)
			theLeftFork = new CachedExpressionPossibility<S>(theType).setPossibility(thePossibility.leftFork());
		return theLeftFork.asPossibility();
	}

	@Override
	public ExpressionPossibility<S> rightFork() throws IOException {
		if (theRightFork == null)
			theRightFork = new CachedExpressionPossibility<S>(theType).setPossibility(thePossibility.rightFork());
		return theRightFork.asPossibility();
	}

	@Override
	public int getErrorCount() {
		return thePossibility.getErrorCount();
	}

	@Override
	public int getFirstErrorPosition() {
		return thePossibility.getFirstErrorPosition();
	}

	@Override
	public boolean isComplete() {
		return thePossibility.isComplete();
	}

	@Override
	public boolean isEquivalent(ExpressionPossibility<S> o) {
		if (this == o)
			return true;
		else if (thePossibility == null)
			return false;
		else if (!(o instanceof CachedExpressionPossibility))
			return false;
		CachedExpressionPossibility<S> other = (CachedExpressionPossibility<S>) o;
		if (other.thePossibility == null)
			return false;
		return thePossibility.isEquivalent(other.thePossibility);
	}

	@Override
	public Expression<S> getExpression() {
		return thePossibility.getExpression();
	}
}
