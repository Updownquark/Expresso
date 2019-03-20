package org.expresso2.impl;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.expresso.parse.BranchableStream;
import org.expresso2.ExpressionComponent;
import org.expresso2.ExpressionPossibility;
import org.expresso2.ExpressoParser;

public class ExpressoParserImpl<S extends BranchableStream<?, ?>> implements ExpressoParser<S> {
	private final ParseSession<S> theSession;
	private final S theStream;
	private final int[] theExcludedTypes;
	private final Map<Integer, CachedExpressionPossibility<S>> theCache;

	public ExpressoParserImpl(ParseSession<S> session, S stream, int[] excludedTypes) {
		theSession = session;
		theStream = stream;
		theExcludedTypes = excludedTypes;
		theCache = new HashMap<>();
	}

	@Override
	public S getStream() {
		return theStream;
	}

	public int[] getExcludedTypes() {
		return theExcludedTypes;
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
			return theSession.getParser(theStream, 0, union);
		} catch (IOException e) {
			throw new IllegalStateException("Should not happen--not advancing stream", e);
		}
	}

	@Override
	public ExpressionPossibility<S> parseWith(ExpressionComponent<? super S> component, boolean useCache) throws IOException {
		int cacheId = component.getCacheId();
		if (cacheId < 0)
			return component.parse(this, useCache);
		if (theExcludedTypes != null && Arrays.binarySearch(theExcludedTypes, cacheId) >= 0)
			return null;
		boolean[] newCache = new boolean[1];
		CachedExpressionPossibility<S> cached = theCache.computeIfAbsent(cacheId, k -> {
			newCache[0] = true;
			return new CachedExpressionPossibility<>(component);
		});
		if (newCache[0] || !useCache)
			cached.setPossibility(component.parse(this, true));
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
}
