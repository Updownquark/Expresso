package org.expresso2.impl;

import java.io.IOException;
import java.util.Collection;
import java.util.function.Supplier;

import org.expresso.parse.BranchableStream;
import org.expresso2.Expression;
import org.expresso2.ExpressionComponent;
import org.expresso2.ExpressionPossibility;
import org.expresso2.ExpressoGrammar;
import org.expresso2.ExpressoParser;
import org.qommons.collect.BetterSortedSet;
import org.qommons.tree.BetterTreeSet;

public class ParseSession<S extends BranchableStream<?, ?>> {
	private final ExpressoGrammar<? super S> theGrammar;
	private final BetterTreeSet<ExpressoParserImpl<S>> theParsers;
	private boolean isDebugging;
	private int theDebugIndent;

	public ParseSession(ExpressoGrammar<? super S> grammar) {
		theGrammar = grammar;
		theParsers = new BetterTreeSet<>(false, ParseSession::compareParseStates);
		isDebugging = true;
	}

	public Expression<S> parse(S stream, ExpressionComponent<? super S> component, boolean bestError)
		throws IOException {
		BetterSortedSet<ExpressionPossibility<S>> possibilities = new BetterTreeSet<>(false, ExpressionPossibility::compareTo);
		ExpressionPossibility<S> nextBest = getParser(stream, 0, new int[0]).parseWith(component);
		ExpressionPossibility<S> bestComplete = null;
		ExpressionPossibility<S> best = null;
		int round = 1;
		int roundCount = 1;
		int nextRoundCount = 0;
		while (nextBest != null) {
			ExpressionPossibility<S> fNextBest = nextBest;
			if (best == null || nextBest.compareTo(best) < 0) {
				debug(() -> "Replaced best with " + fNextBest);
				best = nextBest;
			}
			if (nextBest.isComplete() && nextBest.getErrorCount() == 0 && (bestComplete == null || nextBest.compareTo(bestComplete) < 0)) {
				debug(() -> "Replaced best complete with " + fNextBest);
				bestComplete = nextBest;
			}

			roundCount--;
			if (roundCount == 0) {
				if (stream.isFullyDiscovered() && best.length() == stream.getDiscoveredLength())
					break;
				round++;
				int fRound = round;
				debug(() -> "\nRound " + fRound);
				roundCount = nextRoundCount;
				nextRoundCount = 0;
			}

			debug(() -> "Forking " + fNextBest.getType() + ": " + fNextBest + " @" + fNextBest.getStream().getPosition());
			adjustDepth(1);
			Collection<? extends ExpressionPossibility<S>> forks = nextBest.fork();
			nextRoundCount += forks.size();
			possibilities.addAll(forks);
			adjustDepth(-1);

			nextBest = possibilities.pollFirst();
		}
		if (bestComplete != null)
			return bestComplete.getExpression();
		else if (best == null)
			throw new IllegalStateException("No possibilities?!");
		else if (bestError)
			return best.getExpression();
		else
			return null; // Perhaps throw an exception using the best to give specific information about what might be wrong in the stream
	}

	public void adjustDepth(int adjust) {
		if (isDebugging)
			theDebugIndent += adjust;
	}

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
