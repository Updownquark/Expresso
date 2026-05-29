package org.observe.expresso.qonfig.values;

import java.util.List;

import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionBuilder;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ModelValueElement;
import org.observe.expresso.qonfig.values.CollectionElement.CollectionPopulator;
import org.qommons.config.QonfigElementOrAddOn;

/** ExElement definition for the Expresso &lt;list> element. */
public class PlainCollectionDef extends AbstractCollectionDef<ObservableCollection<?>> {
	/**
	 * @param parent The parent element of this value element
	 * @param qonfigType The Qonfig type of this value element
	 */
	public PlainCollectionDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
		super(parent, qonfigType, ModelTypes.Collection);
	}

	@Override
	protected PlainCollectionDef.Interpreted<?> interpret2(ExElement.Interpreted<?> parent) {
		return new PlainCollectionDef.Interpreted<>(this, parent);
	}

	/**
	 * {@link PlainCollectionDef} interpretation
	 *
	 * @param <T> The type of values in the collection
	 */
	public static class Interpreted<T> extends AbstractCollectionDef.Interpreted<T, ObservableCollection<T>> {
		Interpreted(PlainCollectionDef definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public ModelValueElement<ObservableCollection<T>> create() throws ModelInstantiationException {
			return new PlainCollectionDef.PlainInstantiator<>(this, instantiateElements());
		}
	}

	/**
	 * {@link PlainCollectionDef} instantiator
	 *
	 * @param <T> The type of values in the collection
	 */
	public static class PlainInstantiator<T> extends Instantiator<T, ObservableCollection<T>> {
		PlainInstantiator(PlainCollectionDef.Interpreted<T> interpreted, List<CollectionPopulator<T>> elements)
			throws ModelInstantiationException {
			super(interpreted, elements);
		}

		@Override
		protected ObservableCollectionBuilder<T, ?> create(ModelSetInstance models) throws ModelInstantiationException {
			return ObservableCollection.build();
		}
	}
}