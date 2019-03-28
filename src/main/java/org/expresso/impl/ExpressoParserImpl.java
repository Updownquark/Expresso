package org.expresso.impl;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.expresso.ExpressionPossibility;
import org.expresso.ExpressionType;
import org.expresso.ExpressoParser;
import org.expresso.stream.BranchableStream;
import org.qommons.BiTuple;

public class ExpressoParserImpl<S extends BranchableStream<?, ?>> implements ExpressoParser<S> {
	private final ParseSession<S> theSession;
	private final S theStream;
	private final boolean isErrorTolerated;
	private final int[] theExcludedTypes;
	private final PersistentStack<BiTuple<ExpressionType<? super S>, ExpressionPossibility<S>>> theCacheOverride;
	private final Map<Integer, CachedExpressionPossibility<S>> theCache;

	public ExpressoParserImpl(ParseSession<S> session, S stream, boolean tolerateError, int[] excludedTypes,
		PersistentStack<BiTuple<ExpressionType<? super S>, ExpressionPossibility<S>>> cacheOverride) {
		theSession = session;
		theStream = stream;
		isErrorTolerated = tolerateError;
		theExcludedTypes = excludedTypes;
		theCacheOverride = cacheOverride;
		theCache = new HashMap<>();
	}

	@Override
	public S getStream() {
		return theStream;
	}

	@Override
	public boolean tolerateErrors() {
		return isErrorTolerated;
	}

	public int[] getExcludedTypes() {
		return theExcludedTypes;
	}

	@Override
	public ExpressoParser<S> advance(int spaces) throws IOException {
		if (spaces == 0)
			return this;
		return theSession.getParser(theStream, spaces, isErrorTolerated, theExcludedTypes);
	}

	@Override
	public ExpressoParser<S> exclude(int... expressionIds) {
		int[] union = union(theExcludedTypes, expressionIds);
		if (union == theExcludedTypes)
			return this;
		try {
			theSession.debug(() -> "Excluding " + Arrays.toString(expressionIds));
			return theSession.getParser(theStream, 0, isErrorTolerated, union);
		} catch (IOException e) {
			throw new IllegalStateException("Should not happen--not advancing stream", e);
		}
	}

	@Override
	public ExpressoParser<S> useCache(ExpressionType<? super S> component, ExpressionPossibility<S> possibility) {
		return new ExpressoParserImpl<>(theSession, theStream, isErrorTolerated, theExcludedTypes,
			new PersistentStack<>(theCacheOverride, new BiTuple<>(component, possibility)));
	}

	@Override
	public ExpressionPossibility<S> parseWith(ExpressionType<? super S> component) throws IOException {
		int cacheId = component.getCacheId();
		if (cacheId < 0)
			return component.parse(this);
		if (theExcludedTypes != null && Arrays.binarySearch(theExcludedTypes, cacheId) >= 0) {
			theSession.debug(() -> "Excluded by type");
			return null;
		}
		BiTuple<ExpressionType<? super S>, ExpressionPossibility<S>> override = theCacheOverride == null ? null
			: theCacheOverride.search(tuple -> tuple.getValue1() == component);
		if (override != null)
			return override.getValue2();
		boolean[] newCache = new boolean[1];
		CachedExpressionPossibility<S> cached = theCache.computeIfAbsent(cacheId, k -> {
			newCache[0] = true;
			return new CachedExpressionPossibility<>(component);
		});
		if (newCache[0]) {
			theSession.debug(() -> component.toString() + ": cache " + System.identityHashCode(cached));
			theSession.adjustDepth(1);
			cached.setPossibility(component.parse(this));
			theSession.adjustDepth(-1);
		} else
			theSession.debug(() -> component + ": Used cache " + System.identityHashCode(cached)//
				+ (cached.asPossibility() == null ? " (empty)" : ""));
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
}
