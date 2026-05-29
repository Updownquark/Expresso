package org.observe.expresso.qonfig.values;

import org.observe.SettableValue;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ModelValueElement;
import org.qommons.config.QonfigElementOrAddOn;

/** Abstract scalar model value definition */
public abstract class AbstractCompiledValue extends ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<?>>
implements ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<?>> {
	/**
	 * @param parent The parent element of this model value
	 * @param qonfigType The Qonfig type of this model value
	 */
	protected AbstractCompiledValue(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
		super(parent, qonfigType, ModelTypes.Value);
	}

	/**
	 * Interpretation of a {@link AbstractCompiledValue}
	 *
	 * @param <T> The type of the model value
	 */
	public static abstract class Interpreted<T>
	extends ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<T>, ModelValueElement<SettableValue<T>>>
	implements ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<T>, ModelValueElement<SettableValue<T>>> {
		/**
		 * @param definition The definition to interpret
		 * @param parent The parent for this element
		 */
		protected Interpreted(AbstractCompiledValue definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}
	}
}