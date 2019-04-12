package org.expresso3.impl;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.expresso.stream.BranchableStream;
import org.expresso3.Expression;
import org.expresso3.ExpressionType;
import org.expresso3.ExpressoParser;
import org.expresso3.debug.ExpressoDebugger.DebugExpressionParsing;
import org.expresso3.debug.ExpressoDebugger.DebugResultMethod;

/**
 * Default implementation of {@link ExpressoParser}
 *
 * @param <S> The type of the stream to parse
 */
public class ExpressoParserImpl<S extends BranchableStream<?, ?>> implements ExpressoParser<S> {
	public static class Template {
		final int position;
		final int[] excludedTypes;

		public Template(int position, int[] excludedTypes) {
			this.position = position;
			this.excludedTypes = excludedTypes;
		}

		@Override
		public int hashCode() {
			int hash = position;
			if (excludedTypes != null)
				for (int et : excludedTypes)
					hash = hash ^ et;
			return hash;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof Template))
				return false;
			Template other = (Template) obj;
			return position == other.position && Arrays.equals(excludedTypes, other.excludedTypes);
		}
	}

	private final ParseSession<S> theSession;
	private final S theStream;
	private final int[] theExcludedTypes;
	private final PersistentStack<ComponentStateInfo<S>> theStates;
	private final Map<Integer, CachedExpression<S>> theCache;

	/**
	 * @param session The parsing session
	 * @param stream The stream (with position)
	 * @param excludedTypes IDs of types that this parser will not return expressions for
	 * @param cacheOverride Cache overrides for this parser
	 */
	public ExpressoParserImpl(ParseSession<S> session, S stream, int[] excludedTypes,
		PersistentStack<ComponentStateInfo<S>> cacheOverride) {
		theSession = session;
		theStream = stream;
		theExcludedTypes = excludedTypes;
		theStates = cacheOverride;
		theCache = new HashMap<>();
	}

	@Override
	public S getStream() {
		return theStream;
	}

	/** @return The IDs of the types that this parser will not return expressions for */
	public int[] getExcludedTypes() {
		return theExcludedTypes;
	}

	@Override
	public int getQualityLevel() {
		return theSession.getQualityLevel();
	}

	@Override
	public int getDepth() {
		return theSession.getDepth();
	}

	@Override
	public ExpressoParser<S> advance(int spaces) throws IOException {
		if (spaces == 0)
			return this;
		else
			return theSession.getParser(theStream, spaces, theExcludedTypes);
	}

	@Override
	public ExpressoParser<S> exclude(int... expressionIds) {
		int[] union = union(theExcludedTypes, expressionIds);
		if (union == theExcludedTypes)
			return this;
		else if (theStates == null) {
			try {
				// theSession.debug(() -> "Excluding " + Arrays.toString(expressionIds));
				return theSession.getParser(theStream, 0, union);
			} catch (IOException e) {
				throw new IllegalStateException("Should not happen--not advancing stream", e);
			}
		} else
			return new ExpressoParserImpl<>(theSession, theStream, union, theStates);
	}

	@Override
	public ExpressoParser<S> withState(ExpressionType<? super S> type, Object datum) {
		return new ExpressoParserImpl<>(theSession, theStream, theExcludedTypes,
			new PersistentStack<>(theStates, new ComponentStateInfo<>(type, datum)));
	}

	@Override
	public Object getState(ExpressionType<? super S> type) {
		ComponentStateInfo<S> override = theStates == null ? null : theStates.search(tuple -> tuple.type == type);
		if (override != null)
			return override.value;
		else
			return null;
	}

	// @Override
	// public ExpressoParser<S> useCache(ExpressionType<? super S> component, Expression<S> result, Consumer<ExpressoParser<S>> onHit) {
	// return new ExpressoParserImpl<>(theSession, theStream, theExcludedTypes,
	// new PersistentStack<>(theCacheOverride, new CacheOverride<>(component, result, onHit)));
	// }

	@Override
	public Expression<S> parseWith(ExpressionType<? super S> component) throws IOException {
		theSession.descend();
		DebugExpressionParsing debug = theSession.getDebugger().begin(component, theStream, null);
		try {
			int cacheId = component.getId();
			if (cacheId < 0) {
				Expression<S> result = component.parse(this);
				debug.finished(result, DebugResultMethod.Parsed);
				return result;
			} else if (theExcludedTypes != null && Arrays.binarySearch(theExcludedTypes, cacheId) >= 0) {
				debug.finished(null, DebugResultMethod.Excluded);
				return null;
			}
			if (!component.isCacheable()) {
				Expression<S> result = component.parse(this);
				debug.finished(result, DebugResultMethod.Parsed);
				return result;
			}
			boolean[] newCache = new boolean[1];
			CachedExpression<S> cached = theCache.computeIfAbsent(cacheId, k -> {
				newCache[0] = true;
				return new CachedExpression<>(component);
			});
			if (newCache[0]) {
				Expression<S> match = component.parse(this);
				cached.setPossibility(match);
				debug.finished(match, DebugResultMethod.Parsed);
			} else {
				debug.finished(cached.asPossibility(), DebugResultMethod.UsedCache);
			}
			return cached.asPossibility();
		} catch (RuntimeException e) {
			theSession.getDebugger().suspend();
			throw e;
		} finally {
			theSession.ascend();
		}
	}

	@Override
	public Expression<S> nextMatch(Expression<S> expression) throws IOException {
		theSession.descend();
		DebugExpressionParsing debug = theSession.getDebugger().begin(expression.getType(), theStream, expression);
		try {
			int cacheId = expression.getType().getId();
			if (cacheId < 0) {
				Expression<S> result = expression.nextMatch(this);
				debug.finished(result, DebugResultMethod.Parsed);
				return result;
			} else if (theExcludedTypes != null && Arrays.binarySearch(theExcludedTypes, cacheId) >= 0) {
				debug.finished(null, DebugResultMethod.Excluded);
				return null;
			}
			Expression<S> match = expression.nextMatch(this);
			debug.finished(match, DebugResultMethod.Parsed);
			return match;
		} catch (RuntimeException e) {
			theSession.getDebugger().suspend();
			throw e;
		} finally {
			theSession.ascend();
		}
	}

	private static int[] union(int[] i1, int[] i2) {
		int[] newIs = null;
		int i = 0, j = 0, r = 0;
		while (i < i1.length && j < i2.length) {
			if (i1[i] == i2[j]) {
				if (newIs != null)
					newIs[r] = i1[i];
				i++;
				j++;
				r++;
			} else if (i1[i] < i2[j]) {
				if (newIs == null) {
					// Assume all the remaining elements are different
					newIs = new int[i1.length + i2.length - 1];
					System.arraycopy(i1, 0, newIs, 0, i);
					Arrays.fill(newIs, i, newIs.length, -1);// Fill the rest of the array with -1s
				}
				newIs[r++] = i1[i++];
			} else {
				if (newIs == null) {
					// Assume all the remaining elements are different
					newIs = new int[i1.length + i2.length - 1];
					System.arraycopy(i1, 0, newIs, 0, i);
					Arrays.fill(newIs, i, newIs.length, -1);// Fill the rest of the array with -1s
				}
				newIs[r++] = i2[j++];
			}
		}
		if (i < i1.length) {
			if (newIs == null)
				return i1;
			System.arraycopy(i1, i, newIs, r, i1.length - i);
		} else if (j < i2.length) {
			if (newIs == null)
				return i2;
			System.arraycopy(i2, j, newIs, r, i2.length - j);
		}
		if (newIs != null)
			return newIs;
		else
			return i1; // Arrays were the same
	}

	@Override
	public String toString() {
		return theStream.toString();
	}

	static class ComponentStateInfo<S extends BranchableStream<?, ?>> {
		final ExpressionType<? super S> type;
		final Object value;

		public ComponentStateInfo(ExpressionType<? super S> type, Object value) {
			this.type = type;
			this.value = value;
		}

		@Override
		public String toString() {
			return type + "->" + value;
		}
	}
}
