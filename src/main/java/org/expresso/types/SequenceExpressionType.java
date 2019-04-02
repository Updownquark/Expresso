package org.expresso.types;

import java.util.List;

import org.expresso.Expression;
import org.expresso.ExpressionType;
import org.expresso.stream.BranchableStream;
import org.qommons.BiTuple;

/**
 * An expression type that is composed of one or more other expressions that must be present in the stream in order
 *
 * @param <S> The type of the stream
 */
public class SequenceExpressionType<S extends BranchableStream<?, ?>> extends AbstractSequencedExpressionType<S> {
	private boolean hasSpecificity;
	private int theSpecificity;

	/**
	 * @param id The cache ID for this expression type
	 * @param components The components that make up this sequence
	 */
	public SequenceExpressionType(int id, List<ExpressionType<S>> components) {
		super(id, components);
		// Can't calculate the specificity here,
		// because the component list may be populated by the grammar parser after this constructor is called
	}

	/** @return The components that make up this sequence */
	public List<ExpressionType<S>> getComponents() {
		return (List<ExpressionType<S>>) super.getSequence();
	}

	@Override
	protected boolean isComplete(int componentCount) {
		return componentCount == getComponents().size();
	}

	@Override
	public int getSpecificity() {
		if (!hasSpecificity) {
			hasSpecificity = true;
			theSpecificity = getComponents().stream().mapToInt(ExpressionType::getSpecificity).sum();
		}
		return theSpecificity;
	}

	@Override
	protected BiTuple<String, Integer> getErrorForComponents(List<? extends Expression<? extends S>> components) {
		if (components.size() < getComponents().size()) {
			int missingWeight = 0;
			ExpressionType<S> missingComponent = null;
			for (int i = components.size(); i < getComponents().size(); i++) {
				int spec = getComponents().get(i).getSpecificity();
				if (spec > 0) {
					if (missingComponent == null)
						missingComponent = getComponents().get(i);
					missingWeight += spec;
				}
			}
			if (missingComponent == null)
				return null;
			return new BiTuple<>(missingComponent + " expected", missingWeight);
		}
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
