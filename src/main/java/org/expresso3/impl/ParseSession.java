package org.expresso3.impl;

import java.io.IOException;
import java.util.function.Supplier;

import org.expresso.stream.BranchableStream;
import org.expresso3.Expression;
import org.expresso3.ExpressionType;
import org.expresso3.ExpressoGrammar;
import org.expresso3.ExpressoParser;
import org.qommons.collect.BetterSortedSet;
import org.qommons.tree.BetterTreeSet;

/**
 * Does the work of parsing a stream
 *
 * @param <S> The type of the stream
 */
public class ParseSession<S extends BranchableStream<?, ?>> {
	@SuppressWarnings("unused") // Not used currently, but seems like it belongs here
	private final ExpressoGrammar<? super S> theGrammar;
	private final BetterTreeSet<ExpressoParserImpl<S>> theParsers;
	private boolean isDebugging;
	private int theDebugIndent;
	private int theQualityLevel;

	/** @param grammar The grammar to use to parse expressions */
	public ParseSession(ExpressoGrammar<? super S> grammar) {
		theGrammar = grammar;
		theParsers = new BetterTreeSet<>(false, ParseSession::compareParseStates);
		isDebugging = true;
	}

	public int getQualityLevel() {
		return theQualityLevel;
	}

	/**
	 * @param stream The stream whose content to parse
	 * @param component The root component with which to parse the stream
	 * @return The best interpretation of the stream content
	 * @throws IOException If an error occurs reading the stream data
	 */
	public Expression<S> parse(S stream, ExpressionType<? super S> component) throws IOException {
		theQualityLevel = 0;
		Expression<S> best = null;
		for (int round = 1; round < 10; round++) {
			Expression<S> match = getParser(stream, 0, new int[0])//
				.parseWith(component);
			for (; match != null; match = match.nextMatch()) {
				if (best == null || match.compareTo(best) < 0) {
					best = match;
					if (isSatisfied(best, stream))
						return best;
				}
			}
			theParsers.clear(); // Clear the cache between rounds
			theQualityLevel--;
		}
		return best;
	}

	private static boolean isSatisfied(Expression<?> best, BranchableStream<?, ?> stream) {
		return stream.isFullyDiscovered() && best.length() == stream.getDiscoveredLength();
	}

	/**
	 * Debug operation
	 * 
	 * @param adjust The amount by which to adjust the session depth
	 */
	public void adjustDepth(int adjust) {
		if (isDebugging)
			theDebugIndent += adjust;
	}

	/** @param debug Provides a message to print during debugging */
	public void debug(Supplier<String> debug) {
		if (!isDebugging)
			return;
		StringBuilder str = new StringBuilder();
		for (int i = 0; i < theDebugIndent; i++)
			str.append('\t');
		str.append(debug.get());
		System.out.println(str.toString());
	}

	ExpressoParser<S> getParser(S stream, int advance, int[] excludedTypes) throws IOException {
		if (!stream.hasMoreData(advance))
			return null;
		int streamPosition = stream.getPosition() + advance;
		ExpressoParserImpl<S> parser = theParsers.searchValue(
			p -> compareParseStates(p.getStream().getPosition(), p.getExcludedTypes(), streamPosition, excludedTypes),
			BetterSortedSet.SortedSearchFilter.OnlyMatch);
		if (parser == null) {
			parser = new ExpressoParserImpl<>(this, (S) stream.advance(advance), excludedTypes, null);
			theParsers.add(parser);
		}
		return parser;
	}

	private static int compareParseStates(ExpressoParserImpl<?> p1, ExpressoParserImpl<?> p2) {
		if (p1 == p2)
			return 0;
		return compareParseStates(p1.getStream().getPosition(), p1.getExcludedTypes(), p2.getStream().getPosition(), p2.getExcludedTypes());
	}

	private static int compareParseStates(int position1, int[] excludedTypes1, int position2, int[] excludedTypes2) {
		int compare = Integer.compare(position1, position2);
		if (compare == 0) {
			int i;
			for (i = 0; i < excludedTypes1.length && i < excludedTypes2.length; i++) {
				if (excludedTypes1[i] != excludedTypes2[i] || excludedTypes1[i] == -1) {
					compare = Integer.compare(excludedTypes1[i], excludedTypes2[i]);
					break;
				}
			}
			if (i == excludedTypes1.length)
				compare = 1;
			else if (i == excludedTypes2.length)
				compare = -1;
		}
		return compare;
	}
}
