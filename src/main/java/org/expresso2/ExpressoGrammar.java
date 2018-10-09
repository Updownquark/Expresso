package org.expresso2;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.expresso.parse.BranchableStream;
import org.expresso2.impl.ParseSession;

public class ExpressoGrammar<S extends BranchableStream<?, ?>> {
	private final String theName;
	private final List<ExpressionType<S>> theExpressions;
	private final Map<String, ExpressionType<S>> theExpressionsByName;
	private final Map<String, ExpressionClass<S>> theExpressionClasses;
	
	public ExpressoGrammar(String name, List<ExpressionType<S>> expressions, Map<String, ExpressionType<S>> expressionsByName,
		Map<String, ExpressionClass<S>> expressionClasses) {
		theName = name;
		theExpressions = expressions;
		theExpressionsByName = expressionsByName;
		theExpressionClasses = expressionClasses;
	}

	public List<ExpressionType<S>> getExpressions() {
		return theExpressions;
	}

	public Map<String, ExpressionType<S>> getExpressionsByName() {
		return theExpressionsByName;
	}

	public Map<String, ExpressionClass<S>> getExpressionClasses() {
		return theExpressionClasses;
	}

	public <SS extends S> Expression<SS> parse(SS stream, ExpressionComponent<S> type, boolean bestError) throws IOException {
		return new ParseSession<SS>(this).parse(stream, type, bestError);
	}
}
