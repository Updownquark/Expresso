package org.expresso2;

import java.util.List;
import java.util.Set;

import org.expresso.parse.BranchableStream;
import org.qommons.collect.ParameterSet.ParameterMap;

public class ExpressionType<S extends BranchableStream<?, ?>> extends SequenceExpression<S> {
	public final int id;

	private final String theName;
	private final Set<String> theClass;
	private final ParameterMap<ExpressionComponent<? super S>> theFields;

	public ExpressionType(int id, String name, Set<String> clazz, List<ExpressionComponent<? super S>> components,
		ParameterMap<ExpressionComponent<? super S>> fields) {
		super(components);
		this.id = id;
		theName = name;
		theClass = clazz;
		theFields = fields;
	}
}
