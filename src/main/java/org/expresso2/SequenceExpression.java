package org.expresso2;

import java.util.List;

import org.expresso.parse.BranchableStream;

public class SequenceExpression<S extends BranchableStream<?, ?>> extends AbstractSequencedExpressionType<S> {
	public SequenceExpression(int id, List<ExpressionComponent<S>> components) {
		super(id, components);
	}

	public List<ExpressionComponent<S>> getComponents() {
		return (List<ExpressionComponent<S>>) super.getSequence();
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
}
