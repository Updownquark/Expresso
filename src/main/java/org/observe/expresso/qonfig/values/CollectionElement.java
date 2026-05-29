package org.observe.expresso.qonfig.values;

import java.util.Collections;
import java.util.List;

import org.observe.SettableValue;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoBaseV0_1;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ModelValueElement;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.qommons.collect.BetterCollection;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.ErrorReporting;

/** ExElement definition for the Expresso &lt;element> element. */
@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
	qonfigType = "element",
	interpretation = CollectionElement.Interpreted.class,
	instance = CollectionElement.CollectionPopulator.class)
public class CollectionElement extends AbstractCompiledValue {
	/**
	 * @param parent The parent element of this value element
	 * @param qonfigType The Qonfig type of this value element
	 */
	public CollectionElement(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
		super(parent, qonfigType);
	}

	@Override
	@QonfigAttributeGetter
	public CompiledExpression getElementValue() {
		return super.getElementValue();
	}

	@Override
	protected void doPrepare(ExpressoQIS session) { // Nothing to do
	}

	@Override
	protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
		super.doUpdate(session);
	}

	@Override
	public CollectionElement.Interpreted<?> interpretValue(ExElement.Interpreted<?> parent) {
		return new CollectionElement.Interpreted<>(this, parent);
	}

	/**
	 * {@link CollectionElement} interpretation
	 *
	 * @param <T> The type of the element
	 */
	public static class Interpreted<T> extends AbstractCompiledValue.Interpreted<T> {
		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for this element
		 */
		protected Interpreted(CollectionElement definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public CollectionElement getDefinition() {
			return (CollectionElement) super.getDefinition();
		}

		@Override
		public ModelInstanceType<SettableValue<?>, SettableValue<T>> getType() {
			return getElementValue().getType();
		}

		@Override
		public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
			return Collections.singletonList(getElementValue());
		}

		@Override
		public CollectionElement.CollectionPopulator<T> create() throws ModelInstantiationException {
			return new CollectionPopulator.Default<>(this);
		}
	}

	/**
	 * {@link CollectionElement} instantiator
	 *
	 * @param <T> The type of the element
	 */
	public interface CollectionPopulator<T> extends ModelValueElement<SettableValue<T>> {
		/**
		 * Installs this populator's content into the collection
		 *
		 * @param collection The collection to populate
		 * @param models The model instance to get model values from
		 * @return Whether the collection's content was changed as a result of the call
		 * @throws ModelInstantiationException If any of this populator's content could not be instantiated
		 */
		boolean populateCollection(BetterCollection<? super T> collection, ModelSetInstance models) throws ModelInstantiationException;

		/**
		 * Default {@link CollectionPopulator} implementation for a single value
		 *
		 * @param <T> The type of the value
		 */
		static class Default<T> extends ModelValueElement.Abstract<SettableValue<T>> implements CollectionElement.CollectionPopulator<T> {
			private final ErrorReporting theReporting;

			Default(CollectionElement.Interpreted<T> interpreted) throws ModelInstantiationException {
				super(interpreted);
				theReporting = interpreted.reporting().at(interpreted.getDefinition().getElementValue().getFilePosition());
			}

			@Override
			public ModelValueInstantiator<SettableValue<T>> getElementValue() {
				return (ModelValueInstantiator<SettableValue<T>>) super.getElementValue();
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				super.instantiate();
			}

			@Override
			public SettableValue<T> evaluate(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				instantiate(models);
				return getElementValue().get(models);
			}

			@Override
			public SettableValue<T> forModelCopy(SettableValue<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				return getElementValue().forModelCopy(value, sourceModels, newModels);
			}

			@Override
			public boolean populateCollection(BetterCollection<? super T> collection, ModelSetInstance models)
				throws ModelInstantiationException {
				SettableValue<T> elValue = get(models);
				T value = elValue.get();
				String msg = collection.canAdd(value);
				if (msg != null) {
					theReporting.warn(msg);
					return false;
				} else if (!collection.add(value)) {
					theReporting.warn("Value not added for unspecified reason");
					return false;
				} else
					return true;
			}
		}
	}
}