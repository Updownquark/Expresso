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
import org.qommons.tree.SortedTreeList;

public class ParseSession<S extends BranchableStream<?, ?>> {
	private final ExpressoGrammar<? super S> theGrammar;
	private final BetterTreeSet<ExpressoParserImpl<S>> theParsers;
	private boolean isDebugging;
	private int theDebugIndent;

	public ParseSession(ExpressoGrammar<? super S> grammar) {
		theGrammar = grammar;
		theParsers = new BetterTreeSet<>(false, ParseSession::compareParseStates);
		isDebugging = false;
	}

	public Expression<S> parse(S stream, ExpressionComponent<? super S> component, boolean bestError)
		throws IOException {
		SortedTreeList<ExpressionPossibility<S>> toEvaluate = new SortedTreeList<>(false, ExpressionPossibility::compareTo);
		// SortedTreeList<ExpressionPossibility<S>> forks = new SortedTreeList<>(false, ExpressionPossibility::compareTo);
		toEvaluate.add(getParser(stream, 0, new int[0]).parseWith(component));
		ExpressionPossibility<S> bestComplete = null;
		ExpressionPossibility<S> best = null;
		while (!toEvaluate.isEmpty()) {
			ExpressionPossibility<S> nextBest = toEvaluate.poll();
			debug(() -> "Checking " + nextBest.getType() + ": " + nextBest);
			if (best == null || nextBest.compareTo(best) < 0) {
				debug(() -> "Replaced best");
				best = nextBest;
			}
			if (nextBest.getErrorCount() == 0 && (bestComplete == null || nextBest.compareTo(bestComplete) < 0)) {
				debug(() -> "Replaced best complete");
				bestComplete = nextBest;
				if (isSatisfied(bestComplete, stream))
					break;
			}
			debug(() -> "Forking " + nextBest.getType() + ": " + nextBest);
			adjustDepth(1);
			Collection<? extends ExpressionPossibility<S>> pForks = nextBest.fork();
			adjustDepth(-1);
			toEvaluate.addAll(pForks);
		}
		/*for (int round = 1; !toEvaluate.isEmpty(); round++) {
			int fRound = round;
			debug(() -> "Round " + fRound);
			for (ExpressionPossibility<S> nextBest : toEvaluate) {
				debug(() -> "Checking " + nextBest.getType() + ": " + nextBest);
				if (best == null || nextBest.compareTo(best) < 0) {
					debug(() -> "Replaced best");
					best = nextBest;
				}
				if (nextBest.getErrorCount() == 0 && (bestComplete == null || nextBest.compareTo(bestComplete) < 0)) {
					debug(() -> "Replaced best complete");
					bestComplete = nextBest;
				}
			}
			if (isSatisfied(best, stream))
				break;
			for (ExpressionPossibility<S> p : toEvaluate) {
				debug(() -> "Forking " + p.getType() + ": " + p);
				adjustDepth(1);
				Collection<? extends ExpressionPossibility<S>> pForks = p.fork();
				adjustDepth(-1);
				forks.addAll(pForks);
			}
			SortedTreeList<ExpressionPossibility<S>> temp = toEvaluate;
			toEvaluate = forks;
			forks = temp;
			forks.clear();
		}*/

		if (bestComplete != null)
			return bestComplete.getExpression();
		else if (best == null)
			throw new IllegalStateException("No possibilities?!");
		else if (bestError)
			return best.getExpression();
		else
			return null; // Perhaps throw an exception using the best to give specific information about what might be wrong in the stream
	}

	private static boolean isSatisfied(ExpressionPossibility<?> best, BranchableStream<?, ?> stream) {
		return best.getErrorCount() == 0 && stream.isFullyDiscovered() && best.length() == stream.getDiscoveredLength();
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
