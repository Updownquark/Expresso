package org.expresso2;

import java.util.List;

import org.expresso.parse.BranchableStream;
import org.qommons.collect.BetterList;
import org.qommons.collect.ParameterSet.ParameterMap;

public class ExpressionType<S extends BranchableStream<?, ?>> extends SequenceExpression<S> {
	public final int id;
	public final int priority;

	private final String theName;
	private final List<ExpressionClass<S>> theClasses;
	private final ParameterMap<BetterList<ExpressionComponent<S>>> theFields;

	public ExpressionType(int id, int priority, String name, List<ExpressionClass<S>> classes, List<ExpressionComponent<S>> components,
		ParameterMap<BetterList<ExpressionComponent<S>>> fields) {
		super(id, components);
		this.id = id;
		this.priority = priority;
		theName = name;
		theClasses = classes;
		theFields = fields;
	}
}
