package org.expresso.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.expresso.Expression;
import org.expresso.ExpressionType;
import org.expresso.stream.BranchableStream;

class CachedExpression<S extends BranchableStream<?, ?>> implements Expression<S> {
	private final ExpressionType<? super S> theType;
	private Expression<S> thePossibility;
	private List<CachedExpression<S>> theForks;
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
	public Collection<? extends Expression<S>> fork() throws IOException {
		if (theForks == null) {
			Collection<? extends Expression<S>> forks = thePossibility.fork();
			if (forks.isEmpty())
				theForks = Collections.emptyList();
			else
				theForks = forks.stream().map(fork -> new CachedExpression<S>(theType).setPossibility(fork))
					.collect(Collectors.toCollection(() -> new ArrayList<>(forks.size())));
		}
		return theForks;
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
	public int getComplexity() {
		return thePossibility.getComplexity();
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
