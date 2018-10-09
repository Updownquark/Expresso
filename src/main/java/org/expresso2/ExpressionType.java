package org.expresso2;

import java.util.List;

import org.expresso.parse.BranchableStream;
import org.qommons.collect.ParameterSet.ParameterMap;

public class ExpressionType<S extends BranchableStream<?, ?>> extends SequenceExpression<S> {
	public final int id;

	private final String theName;
	private final List<ExpressionClass<S>> theClasses;
	private final ParameterMap<ExpressionComponent<S>> theFields;

	public ExpressionType(int id, String name, List<ExpressionClass<S>> classes, List<ExpressionComponent<S>> components,
		ParameterMap<ExpressionComponent<S>> fields) {
		super(id, components);
		this.id = id;
		theName = name;
		theClasses = classes;
		theFields = fields;
	}
}
