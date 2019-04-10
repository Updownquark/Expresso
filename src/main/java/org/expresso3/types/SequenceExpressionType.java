package org.expresso3.types;

import java.util.List;

import org.expresso.stream.BranchableStream;
import org.expresso.util.ExpressoUtils;
import org.expresso3.Expression;
import org.expresso3.ExpressionType;

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
	@Override
	public List<ExpressionType<S>> getComponents() {
		return (List<ExpressionType<S>>) super.getComponents();
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
	protected CompositionError getErrorForComponents(List<? extends Expression<? extends S>> components) {
		if (components.size() < getComponents().size()) {
			// TrackNode seqNode = TRACKER.start("sequence");
			int missingWeight = getComponents().size() - components.size();
			ExpressionType<S> missingComponent = getComponents().get(components.size());
			// for (int i = components.size(); i < getComponents().size(); i++) {
			// int spec = getComponents().get(i).getSpecificity();
			// if (spec > 0) {
			// if (missingComponent == null)
			// missingComponent = getComponents().get(i);
			// missingWeight += spec;
			// }
			// }
			if (missingComponent == null) {
				// seqNode.end();
				return null;
			}
			// TrackNode posNode = TRACKER.start("length");
			int pos = ExpressoUtils.getLength(0, components);
			// posNode.end();
			ExpressionType<S> fMissing = missingComponent;
			// seqNode.end();
			return new CompositionError(pos, () -> fMissing.toString() + " expected", missingWeight);
		}
		return null;
	}

	@Override
	public String toString() {
		return getComponents().toString();
	}
}
