package org.observe.expresso.qonfig.values;

import java.util.Collections;
import java.util.List;

import org.observe.config.ObservableConfig;
import org.observe.config.ObservableValueSet;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ModelValueElement;
import org.observe.expresso.qonfig.ModelValueElement.InterpretedSynth;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.config.QonfigElementOrAddOn;

import com.google.common.reflect.TypeToken;

/** A list of values containing additional capabilities for creating new elements */
public class ValueSet extends ModelValueElement.Def.SingleTyped<ObservableValueSet<?>, ModelValueElement<?>>
implements ModelValueElement.CompiledSynth<ObservableValueSet<?>, ModelValueElement<?>> {
	/**
	 * @param parent The parent element of this value element
	 * @param qonfigType The Qonfig type of this value element
	 */
	public ValueSet(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
		super(parent, qonfigType, ModelTypes.ValueSet);
	}

	@Override
	public void doPrepare(ExpressoQIS session) { // Nothing to do
	}

	@Override
	public InterpretedSynth<ObservableValueSet<?>, ?, ? extends ModelValueElement<?>> interpretValue(ExElement.Interpreted<?> parent) {
		return new ValueSet.Interpreted<>(this, parent);
	}

	/**
	 * {@link ValueSet} interpretation
	 *
	 * @param <T> The type of values in the collection
	 */
	public static class Interpreted<T> extends
	ModelValueElement.Def.SingleTyped.Interpreted<ObservableValueSet<?>, ObservableValueSet<T>, ModelValueElement<ObservableValueSet<T>>>
	implements
	ModelValueElement.InterpretedSynth<ObservableValueSet<?>, ObservableValueSet<T>, ModelValueElement<ObservableValueSet<T>>> {
		Interpreted(ValueSet definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public ValueSet getDefinition() {
			return (ValueSet) super.getDefinition();
		}

		@Override
		public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
			return Collections.emptyList();
		}

		@Override
		public ModelValueElement<ObservableValueSet<T>> create() throws ModelInstantiationException {
			return new ValueSet.Instantiator<>(this);
		}
	}

	/**
	 * {@link ValueSet} instantiator
	 *
	 * @param <T> The type of values in the collection
	 */
	public static class Instantiator<T> extends ModelValueElement.Abstract<ObservableValueSet<T>> {
		private final TypeToken<T> theType;

		Instantiator(ValueSet.Interpreted<T> interpreted) throws ModelInstantiationException {
			super(interpreted);
			theType = (TypeToken<T>) interpreted.getType().getType(0);
		}

		@Override
		public void instantiate() {
		}

		@Override
		public ObservableValueSet<T> evaluate(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
			instantiate(models);
			// Although a purely in-memory value set would be more efficient, I have yet to implement one.
			// Easiest path forward for this right now is to make an unpersisted ObservableConfig and use it to back the value set.
			// TODO At some point I should come back and make an in-memory implementation and use it here.
			ObservableConfig config = ObservableConfig.createRoot("root", null,
				__ -> new FastFailLockingStrategy());
			return config.asValue(theType).buildEntitySet(null);
		}

		@Override
		public ObservableValueSet<T> forModelCopy(ObservableValueSet<T> value, ModelSetInstance sourceModels,
			ModelSetInstance newModels) throws ModelInstantiationException {
			return value;
		}
	}
}