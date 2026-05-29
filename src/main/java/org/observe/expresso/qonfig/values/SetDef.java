package org.observe.expresso.qonfig.values;

import java.util.List;

import org.observe.collect.ObservableCollectionBuilder;
import org.observe.collect.ObservableSet;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElement.Def;
import org.observe.expresso.qonfig.ModelValueElement;
import org.observe.expresso.qonfig.values.CollectionElement.CollectionPopulator;
import org.qommons.config.QonfigElementOrAddOn;

/** ExElement definition for the Expresso &lt;set> element. */
public class SetDef extends AbstractCollectionDef<ObservableSet<?>> {
	/**
	 * @param parent The parent element of this value element
	 * @param qonfigType The Qonfig type of this value element
	 */
	public SetDef(Def<?> parent, QonfigElementOrAddOn qonfigType) {
		super(parent, qonfigType, ModelTypes.Set);
	}

	@Override
	protected SetDef.Interpreted<?> interpret2(ExElement.Interpreted<?> parent) {
		return new SetDef.Interpreted<>(this, parent);
	}

	/**
	 * {@link SetDef} interpretation
	 *
	 * @param <T> The type of values in the collection
	 */
	public static class Interpreted<T> extends AbstractCollectionDef.Interpreted<T, ObservableSet<T>> {
		Interpreted(SetDef definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public ModelValueElement<ObservableSet<T>> create() throws ModelInstantiationException {
			return new SetDef.SetInstantiator<>(this, instantiateElements());
		}
	}

	/**
	 * {@link SetDef} instantiator
	 *
	 * @param <T> The type of values in the collection
	 */
	public static class SetInstantiator<T> extends Instantiator<T, ObservableSet<T>> {
		SetInstantiator(SetDef.Interpreted<T> interpreted, List<CollectionPopulator<T>> elements) throws ModelInstantiationException {
			super(interpreted, elements);
		}

		@Override
		protected ObservableCollectionBuilder<T, ?> create(ModelSetInstance models) throws ModelInstantiationException {
			return ObservableSet.build();
		}
	}
}