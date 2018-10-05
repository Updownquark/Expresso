package org.expresso2;

import java.io.IOException;
import java.util.List;

import org.expresso.parse.BranchableStream;
import org.expresso2.impl.ParseSession;
import org.qommons.collect.ParameterSet.ParameterMap;

public class ExpressoGrammar<S extends BranchableStream<?, ?>> {
	private final String theName;
	private final List<ExpressionType<S>> theExpressions;
	private final ParameterMap<ExpressionType<S>> theExpressionsByName;
	private final ParameterMap<ExpressionClass<S>> theExpressionClasses;
	
	public ExpressoGrammar(String name, List<ExpressionType<S>> expressions, ParameterMap<ExpressionType<S>> expressionsByName,
		ParameterMap<ExpressionClass<S>> expressionClasses) {
		theName = name;
		theExpressions = expressions;
		theExpressionsByName = expressionsByName;
		theExpressionClasses = expressionClasses;
	}

	public List<ExpressionType<S>> getExpressions() {
		return theExpressions;
	}

	public ParameterMap<ExpressionType<S>> getExpressionsByName() {
		return theExpressionsByName;
	}

	public ParameterMap<ExpressionClass<S>> getExpressionClasses() {
		return theExpressionClasses;
	}

	public <SS extends S> Expression<SS> parse(SS stream, ExpressionComponent<S> type, boolean bestError) throws IOException {
		return new ParseSession<SS>(this).parse(stream, type, bestError);
	}
}
