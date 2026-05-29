package org.observe.expresso.qonfig.values;

import java.util.List;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet;
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
import org.observe.expresso.qonfig.ObservableModelElement;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMap;
import org.qommons.collect.BetterMultiMap;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.ErrorReporting;

import com.google.common.reflect.TypeToken;

/** ExElement definition for the Expresso &lt;entry>. */
@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
	qonfigType = "entry",
	interpretation = MapEntry.Interpreted.class,
	instance = MapEntry.MapPopulator.class)
public class MapEntry extends ModelValueElement.Def.Abstract<SettableValue<?>, ModelValueElement<?>> {
	private CompiledExpression theKey;
	private CompiledExpression theValue;

	/**
	 * @param parent The parent element for this map entry
	 * @param qonfigType The Qonfig type of this map entry
	 */
	public MapEntry(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
		super(parent, qonfigType, ModelTypes.Value);
	}

	/** @return The key of the entry */
	@QonfigAttributeGetter("key")
	public CompiledExpression getKey() {
		return theKey;
	}

	/** @return The value of the entry */
	@Override
	@QonfigAttributeGetter
	public CompiledExpression getElementValue() {
		return theValue;
	}

	@Override
	public void populate(ObservableModelSet.Builder builder, ExpressoQIS session) throws QonfigInterpretationException {
		throw new QonfigInterpretationException("<entry> cannot be used this way", reporting().getFileLocation());
	}

	@Override
	protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
	}

	@Override
	protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
		super.doUpdate(session);
	}

	/**
	 * @param parent The parent element for the interpreted entry
	 * @return The interpreted entry
	 */
	public MapEntry.Interpreted<?, ?> interpret(ModelValueElement.Interpreted<?, ?, ?> parent) {
		return new MapEntry.Interpreted<>(this, parent);
	}

	/**
	 * {@link MapEntry} interpretation
	 *
	 * @param <K> The key type for the entry
	 * @param <V> The value type for the entry
	 */
	public static class Interpreted<K, V>
	extends ModelValueElement.Interpreted.Abstract<SettableValue<?>, SettableValue<V>, ModelValueElement<SettableValue<V>>> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<K>> theKey;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<V>> theValue;
		private ObservableModelElement.Interpreted<?> theInterpretedModel;

		Interpreted(MapEntry definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public MapEntry getDefinition() {
			return (MapEntry) super.getDefinition();
		}

		@Override
		public ObservableModelElement.Interpreted<?> getInterpretedModel() {
			return theInterpretedModel;
		}

		@Override
		public void setInterpretedModel(ObservableModelElement.Interpreted<?> interpretedModel) {
			theInterpretedModel = interpretedModel;
		}

		@Override
		protected ModelInstanceType<SettableValue<?>, SettableValue<V>> getTargetType() {
			AbstractMapDef.Interpreted<K, V, ?> parent = (AbstractMapDef.Interpreted<K, V, ?>) getParentElement();
			return ModelTypes.Value.forType(parent.getValueType());
		}

		/** @return The key of the entry */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<K>> getKey() {
			return theKey;
		}

		/** @return The value of the entry */
		@Override
		public InterpretedValueSynth<SettableValue<?>, SettableValue<V>> getElementValue() {
			return theValue;
		}

		/**
		 * Initializes or updates this entry
		 *
		 * @param keyType The key type for the entry
		 * @param valueType The value type for the entry
		 * @throws ExpressoInterpretationException If this entry cannot be interpreted
		 */
		public void update(TypeToken<K> keyType, TypeToken<V> valueType)
			throws ExpressoInterpretationException {
			super.update();
			theKey = interpret(getDefinition().getKey(), ModelTypes.Value.forType(keyType));
			theValue = interpret(getDefinition().getElementValue(), ModelTypes.Value.forType(valueType));
		}

		@Override
		public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
			return BetterList.of(theKey, theValue);
		}

		@Override
		public ModelValueInstantiator<SettableValue<V>> instantiate() throws ModelInstantiationException {
			return create();
		}

		@Override
		public MapEntry.MapPopulator<K, V> create() throws ModelInstantiationException {
			return new MapPopulator.Default<>(this);
		}
	}

	/**
	 * {@link MapEntry} instantiator
	 *
	 * @param <K> The key type of the entry
	 * @param <V> The value type of the entry
	 */
	public interface MapPopulator<K, V> extends ModelValueElement<SettableValue<V>> {
		@Override
		void instantiate() throws ModelInstantiationException;

		/**
		 * Installs this entry's content into a map
		 *
		 * @param map The map to populate
		 * @param models The model instance to use to instantiate this entry's content
		 * @return If the map was changed as a result of this call
		 * @throws ModelInstantiationException If this entry's content cannot be instantiated
		 */
		boolean populateMap(BetterMap<? super K, ? super V> map, ModelSetInstance models) throws ModelInstantiationException;

		/**
		 * Installs this entry's content into a multi-map
		 *
		 * @param map The map to populate
		 * @param models The model instance to use to instantiate this entry's content
		 * @return If the map was changed as a result of this call
		 * @throws ModelInstantiationException If this entry's content cannot be instantiated
		 */
		boolean populateMultiMap(BetterMultiMap<? super K, ? super V> map, ModelSetInstance models) throws ModelInstantiationException;

		/**
		 * Default {@link MapEntry} implementation for the &lt;entry> element
		 *
		 * @param <K> The key type of the entry
		 * @param <V> The value type of the entry
		 */
		static class Default<K, V> extends ModelValueElement.Abstract<SettableValue<V>> implements MapEntry.MapPopulator<K, V> {
			private final ModelValueInstantiator<SettableValue<K>> theKey;
			private final ErrorReporting theReporting;

			public Default(MapEntry.Interpreted<K, V> interpreted) throws ModelInstantiationException {
				super(interpreted);
				theKey = interpreted.getKey().instantiate();
				theReporting = interpreted.reporting().at(interpreted.getDefinition().getElementValue().getFilePosition());
			}

			public ModelValueInstantiator<SettableValue<K>> getKey() {
				return theKey;
			}

			@Override
			public ModelValueInstantiator<SettableValue<V>> getElementValue() {
				return (ModelValueInstantiator<SettableValue<V>>) super.getElementValue();
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				super.instantiate();
				theKey.instantiate();
			}

			@Override
			public SettableValue<V> evaluate(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				instantiate(models);
				return getElementValue().get(models);
			}

			@Override
			public SettableValue<V> forModelCopy(SettableValue<V> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				return getElementValue().forModelCopy(value, sourceModels, newModels);
			}

			@Override
			public boolean populateMap(BetterMap<? super K, ? super V> map, ModelSetInstance models)
				throws ModelInstantiationException {
				SettableValue<K> keyValue = theKey.get(models);
				SettableValue<V> vValue = getElementValue().get(models);
				K key = keyValue.get();
				V value = vValue.get();
				String msg = map.canPut(key, value);
				if (msg != null) {
					theReporting.warn(msg);
					return false;
				}
				map.put(key, value);
				return true;
			}

			@Override
			public boolean populateMultiMap(BetterMultiMap<? super K, ? super V> map, ModelSetInstance models)
				throws ModelInstantiationException {
				SettableValue<K> keyValue = theKey.get(models);
				SettableValue<V> vValue = getElementValue().get(models);
				K key = keyValue.get();
				V value = vValue.get();
				BetterCollection<? super V> values = map.get(key);
				String msg = values.canAdd(value);
				if (msg != null) {
					theReporting.warn(msg);
					return false;
				} else if (!values.add(value)) {
					theReporting.warn("Entry not added for unspecified reason");
					return false;
				} else
					return true;
			}
		}
	}
}