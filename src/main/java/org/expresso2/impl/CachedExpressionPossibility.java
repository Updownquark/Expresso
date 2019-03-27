package org.expresso2.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.expresso.parse.BranchableStream;
import org.expresso2.Expression;
import org.expresso2.ExpressionComponent;
import org.expresso2.ExpressionPossibility;

class CachedExpressionPossibility<S extends BranchableStream<?, ?>> implements ExpressionPossibility<S> {
	private final ExpressionComponent<? super S> theType;
	private ExpressionPossibility<S> thePossibility;
	private List<CachedExpressionPossibility<S>> theForks;
	private int cachedHash = -1;

	CachedExpressionPossibility(ExpressionComponent<? super S> type) {
		theType = type;
	}

	CachedExpressionPossibility<S> setPossibility(ExpressionPossibility<S> possibility) {
		thePossibility = possibility;
		return this;
	}

	ExpressionPossibility<S> asPossibility() {
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
	public Collection<? extends ExpressionPossibility<S>> fork() throws IOException {
		if (theForks == null) {
			Collection<? extends ExpressionPossibility<S>> forks = thePossibility.fork();
			if (forks.isEmpty())
				theForks = Collections.emptyList();
			else
				theForks = forks.stream().map(fork -> new CachedExpressionPossibility<S>(theType).setPossibility(fork))
					.collect(Collectors.toCollection(() -> new ArrayList<>(forks.size())));
		}
		return theForks;
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
	public boolean equals(Object o) {
		if (this == o)
			return true;
		else if (thePossibility == null)
			return false;
		else if (!(o instanceof CachedExpressionPossibility))
			return false;
		CachedExpressionPossibility<S> other = (CachedExpressionPossibility<S>) o;
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
	public Expression<S> getExpression() {
		return thePossibility.getExpression();
	}

	@Override
	public String toString() {
		return thePossibility == null ? "N/A" : thePossibility.toString();
	}
}
