package org.observe.expresso.qonfig.values;

import java.util.Arrays;
import java.util.List;

import org.observe.Observable;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ModelValueElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/** A simple event */
public class Event extends ModelValueElement.Def.SingleTyped<Observable<?>, ModelValueElement<?>>
implements ModelValueElement.CompiledSynth<Observable<?>, ModelValueElement<?>> {
	/**
	 * @param parent The parent element of this value element
	 * @param qonfigType The Qonfig type of this value element
	 */
	public Event(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
		super(parent, qonfigType, ModelTypes.Event);
	}

	@Override
	protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException { // Nothing to do
	}

	@Override
	public Event.Interpreted<?> interpretValue(ExElement.Interpreted<?> parent) {
		return new Event.Interpreted<>(this, parent);
	}

	/**
	 * {@link Event} interpretation
	 *
	 * @param <T> The type of the event
	 */
	public static class Interpreted<T>
	extends ModelValueElement.Def.SingleTyped.Interpreted<Observable<?>, Observable<T>, ModelValueElement<Observable<T>>>
	implements ModelValueElement.InterpretedSynth<Observable<?>, Observable<T>, ModelValueElement<Observable<T>>> {
		Interpreted(Event definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
			return Arrays.asList(getElementValue());
		}

		@Override
		public ModelValueElement<Observable<T>> create() throws ModelInstantiationException {
			return new Event.Instantiator<>(this);
		}
	}

	/**
	 * {@link Event} instantiator
	 *
	 * @param <T> The type of the event
	 */
	public static class Instantiator<T> extends ModelValueElement.Abstract<Observable<T>> {
		Instantiator(Event.Interpreted<T> interpreted) throws ModelInstantiationException {
			super(interpreted);
		}

		@Override
		public ModelValueInstantiator<Observable<T>> getElementValue() {
			return (ModelValueInstantiator<Observable<T>>) super.getElementValue();
		}

		@Override
		public Observable<T> evaluate(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
			instantiate(models);
			return getElementValue().get(models);
		}

		@Override
		public Observable<T> forModelCopy(Observable<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
			throws ModelInstantiationException {
			return getElementValue().forModelCopy(value, sourceModels, newModels);
		}
	}
}