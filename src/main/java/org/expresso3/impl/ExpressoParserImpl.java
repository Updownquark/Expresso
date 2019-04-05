package org.expresso3.impl;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.expresso.stream.BranchableStream;
import org.expresso3.Expression;
import org.expresso3.ExpressionType;
import org.expresso3.ExpressoParser;

/**
 * Default implementation of {@link ExpressoParser}
 *
 * @param <S> The type of the stream to parse
 */
public class ExpressoParserImpl<S extends BranchableStream<?, ?>> implements ExpressoParser<S> {
	private final ParseSession<S> theSession;
	private final S theStream;
	private final int[] theExcludedTypes;
	private final PersistentStack<CacheOverride<S>> theCacheOverride;
	private final Map<Integer, CachedExpression<S>> theCache;

	/**
	 * @param session The parsing session
	 * @param stream The stream (with position)
	 * @param excludedTypes IDs of types that this parser will not return expressions for
	 * @param cacheOverride Cache overrides for this parser
	 */
	public ExpressoParserImpl(ParseSession<S> session, S stream, int[] excludedTypes, PersistentStack<CacheOverride<S>> cacheOverride) {
		theSession = session;
		theStream = stream;
		theExcludedTypes = excludedTypes;
		theCacheOverride = cacheOverride;
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
	public ExpressoParser<S> advance(int spaces) throws IOException {
		if (spaces == 0)
			return this;
		return theSession.getParser(theStream, spaces, theExcludedTypes);
	}

	@Override
	public ExpressoParser<S> exclude(int... expressionIds) {
		int[] union = union(theExcludedTypes, expressionIds);
		if (union == theExcludedTypes)
			return this;
		try {
			// theSession.debug(() -> "Excluding " + Arrays.toString(expressionIds));
			return theSession.getParser(theStream, 0, union);
		} catch (IOException e) {
			throw new IllegalStateException("Should not happen--not advancing stream", e);
		}
	}

	@Override
	public ExpressoParser<S> useCache(ExpressionType<? super S> component, Expression<S> result, Runnable onHit) {
		return new ExpressoParserImpl<>(theSession, theStream, theExcludedTypes,
			new PersistentStack<>(theCacheOverride, new CacheOverride<>(component, result, onHit)));
	}

	@Override
	public Expression<S> parseWith(ExpressionType<? super S> component) throws IOException {
		int cacheId = component.getId();
		if (cacheId < 0) {
			return component.parse(this);
		} else if (theExcludedTypes != null && Arrays.binarySearch(theExcludedTypes, cacheId) >= 0) {
			theSession.debug(() -> "Excluded by type");
			return null;
		}
		CacheOverride<S> override = theCacheOverride == null ? null : theCacheOverride.search(tuple -> tuple.type == component);
		if (override != null) {
			override.hit();
			return override.result;
		}
		if (!component.isCacheable()) {
			return component.parse(this);
		}
		boolean[] newCache = new boolean[1];
		CachedExpression<S> cached = theCache.computeIfAbsent(cacheId, k -> {
			newCache[0] = true;
			return new CachedExpression<>(component);
		});
		if (newCache[0]) {
			theSession.debug(() -> component.toString());
			theSession.adjustDepth(1);
			cached.setPossibility(component.parse(this));
			theSession.adjustDepth(-1);
		} else {
			theSession.debug(() -> component + (cached.asPossibility() == null ? " (empty)" : ""));
		}
		return cached.asPossibility();
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

	static class CacheOverride<S extends BranchableStream<?, ?>> {
		final ExpressionType<? super S> type;
		final Expression<S> result;
		final Runnable onHit;

		public CacheOverride(ExpressionType<? super S> type, Expression<S> result, Runnable onHit) {
			this.type = type;
			this.result = result;
			this.onHit = onHit;
		}

		public void hit() {
			if (onHit != null)
				onHit.run();
		}
	}
}
