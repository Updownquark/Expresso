package org.expresso.types;

import java.util.List;

import org.expresso.ExpressionType;
import org.expresso.stream.BranchableStream;

public class SequenceExpressionType<S extends BranchableStream<?, ?>> extends AbstractSequencedExpressionType<S> {
	public SequenceExpressionType(int id, List<ExpressionType<S>> components) {
		super(id, components);
	}

	public List<ExpressionType<S>> getComponents() {
		return (List<ExpressionType<S>>) super.getSequence();
	}

	@Override
	protected int getInitComponentCount() {
		return getComponents().size();
	}

	@Override
	protected boolean isComplete(int componentCount) {
		return componentCount == getComponents().size();
	}

	@Override
	protected String getErrorForComponentCount(int componentCount) {
		if (componentCount < getComponents().size())
			return getComponents().get(componentCount) + " expected";
		return null;
	}

	@Override
	public String toString() {
		if (getComponents().size() == 1)
			return getComponents().get(0).toString();
		else
			return getComponents().toString();
	}
}
