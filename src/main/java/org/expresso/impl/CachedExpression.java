package org.expresso.impl;

import java.io.IOException;
import java.util.List;

import org.expresso.Expression;
import org.expresso.ExpressionType;
import org.expresso.ExpressoParser;
import org.expresso.stream.BranchableStream;

class CachedExpression<S extends BranchableStream<?, ?>> implements Expression<S> {
	private final ExpressionType<? super S> theType;
	private Expression<S> thePossibility;
	private CachedExpression<S> theNextMatch;
	private int cachedHash = -1;

	CachedExpression(ExpressionType<? super S> type) {
		theType = type;
	}

	CachedExpression<S> setPossibility(Expression<S> possibility) {
		thePossibility = possibility;
		return this;
	}

	Expression<S> asPossibility() {
		return thePossibility == null ? null : this;
	}

	@Override
	public ExpressionType<? super S> getType() {
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
	public Expression<S> nextMatch(ExpressoParser<S> parser) throws IOException {
		if (theNextMatch == null) {
			Expression<S> next = thePossibility.nextMatch(parser);
			theNextMatch = new CachedExpression<S>(theType).setPossibility(next);
		}
		return theNextMatch.asPossibility();
	}

	@Override
	public int getErrorCount() {
		return thePossibility.getErrorCount();
	}

	@Override
	public List<? extends Expression<S>> getChildren() {
		return thePossibility.getChildren();
	}

	@Override
	public Expression<S> getFirstError() {
		return thePossibility.getFirstError();
	}

	@Override
	public int getLocalErrorRelativePosition() {
		return thePossibility.getLocalErrorRelativePosition();
	}

	@Override
	public String getLocalErrorMessage() {
		return thePossibility.getLocalErrorMessage();
	}

	@Override
	public Expression<S> unwrap() {
		return thePossibility.unwrap();
	}

	@Override
	public int getMatchQuality() {
		return thePossibility.getMatchQuality();
	}

	@Override
	public boolean isInvariant() {
		return thePossibility.isInvariant();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		else if (thePossibility == null)
			return false;
		else if (!(o instanceof CachedExpression))
			return false;
		CachedExpression<S> other = (CachedExpression<S>) o;
		if (other.thePossibility == null)
			return false;
		return thePossibility.equals(other.thePossibility);
	}

	@Override
	public int hashCode() {
		if (thePossibility == null)
			return 0;
		else if (cachedHash != -1)
			return cachedHash;
		else {
			cachedHash = thePossibility.hashCode();
			return cachedHash;
		}
	}

	@Override
	public StringBuilder print(StringBuilder str, int indent, String metadata) {
		if (thePossibility == null)
			str.append("N/A");
		else
			thePossibility.print(str, indent, metadata);
		return str;
	}

	@Override
	public String toString() {
		return print(new StringBuilder(), 0, "").toString();
	}
}
