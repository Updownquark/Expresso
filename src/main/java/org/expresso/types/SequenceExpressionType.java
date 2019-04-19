package org.expresso.types;

import java.util.List;

import org.expresso.Expression;
import org.expresso.ExpressionType;
import org.expresso.stream.BranchableStream;
import org.expresso.util.ExpressoUtils;

/**
 * An expression type that is composed of one or more other expressions that must be present in the stream in order
 *
 * @param <S> The type of the stream
 */
public class SequenceExpressionType<S extends BranchableStream<?, ?>> extends AbstractSequencedExpressionType<S> {
	/**
	 * @param id The cache ID for this expression type
	 * @param components The components that make up this sequence
	 */
	public SequenceExpressionType(int id, List<? extends ExpressionType<? super S>> components) {
		super(id, components);
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
	protected CompositionError getErrorForComponents(List<? extends Expression<? extends S>> components, int minQuality) {
		if (components.size() < getComponents().size()) {
			// TrackNode seqNode = TRACKER.start("sequence");
			int quality = 0;
			ExpressionType<S> missingComponent = getComponents().get(components.size());
			for (int i = components.size(); i < getComponents().size(); i++) {
				int cq = getComponents().get(i).getEmptyQuality(minQuality-quality);
				if (cq < 0) {
					quality += cq;
					if(quality<minQuality)
						break;
				}
			}
			// Even if all components are optional, they have to be there
			quality = Math.min(quality, -(getComponents().size() - components.size()));
			// TrackNode posNode = TRACKER.start("length");
			int pos = ExpressoUtils.getLength(0, components);
			// posNode.end();
			ExpressionType<S> fMissing = missingComponent;
			// seqNode.end();
			return new CompositionError(pos, () -> fMissing.toString() + " expected", -quality);
		}
		return null;
	}

	@Override
	public String toString() {
		return getComponents().toString();
	}
}
