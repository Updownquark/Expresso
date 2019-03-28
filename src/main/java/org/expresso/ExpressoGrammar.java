package org.expresso;

import java.io.IOException;
import java.util.List;

import org.expresso.impl.ParseSession;
import org.expresso.stream.BranchableStream;
import org.qommons.collect.BetterSortedMap;

/**
 * Represents a set of {@link ConfiguredExpressionType}s and {@link ExpressionClass}es that can be used to parse expressions from some type
 * of stream
 *
 * @param <S> The type of stream that this grammar can parse
 */
public class ExpressoGrammar<S extends BranchableStream<?, ?>> {
	private final String theName;
	private final List<ConfiguredExpressionType<S>> theExpressions;
	private final BetterSortedMap<String, ConfiguredExpressionType<S>> theExpressionsByName;
	private final BetterSortedMap<String, ExpressionClass<S>> theExpressionClasses;

	/**
	 * @param name The name for the grammar
	 * @param expressions All expressions in the grammar, sorted by priority
	 * @param expressionsByName All expressions by name
	 * @param expressionClasses All classes by name
	 */
	public ExpressoGrammar(String name, List<ConfiguredExpressionType<S>> expressions,
		BetterSortedMap<String, ConfiguredExpressionType<S>> expressionsByName,
		BetterSortedMap<String, ExpressionClass<S>> expressionClasses) {
		theName = name;
		theExpressions = expressions;
		theExpressionsByName = expressionsByName;
		theExpressionClasses = expressionClasses;
	}

	/** @return The name of this grammar. This value is not used internally. */
	public String getName() {
		return theName;
	}

	/**
	 * @return All {@link ConfiguredExpressionType}s configured in this grammar, sorted by {@link ConfiguredExpressionType#getPriority()
	 *         priority}, then by order of occurrence in the grammar file
	 */
	public List<ConfiguredExpressionType<S>> getExpressions() {
		return theExpressions;
	}

	/** @return This grammar's {@link ConfiguredExpressionType}s by name */
	public BetterSortedMap<String, ConfiguredExpressionType<S>> getExpressionsByName() {
		return theExpressionsByName;
	}

	/** @return This grammar's {@link ExpressionClass}es by name */
	public BetterSortedMap<String, ExpressionClass<S>> getExpressionClasses() {
		return theExpressionClasses;
	}

	/**
	 * Parses an expression from a stream
	 * 
	 * @param stream The stream to parse
	 * @param type The type (in this grammar) to parse from
	 * @param bestError If true, the parsing will attempt to parse the stream even with errors and return the best guess (may be slower)
	 * @return The parsed expression
	 * @throws IOException If an error occurs parsing the stream
	 */
	public <SS extends S> Expression<SS> parse(SS stream, ExpressionType<S> type, boolean bestError) throws IOException {
		return new ParseSession<SS>(this).parse(stream, type, bestError);
	}
}