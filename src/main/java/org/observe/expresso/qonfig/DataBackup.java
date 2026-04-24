package org.observe.expresso.qonfig;

import java.time.Duration;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.TemporalBackupScheme;

public class DataBackup extends ExElement.Abstract {
	public static final String DATA_BACKUP = "data-backup";

	@ExElementTraceable(toolkit = ExpressoConfigV0_1.CONFIG,
		qonfigType = DATA_BACKUP,
		interpretation = Interpreted.class,
		instance = DataBackup.class)
	public static class Def<B extends DataBackup> extends ExElement.Def.Abstract<B> {
		private CompiledExpression theBackupAges;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("ages")
		public CompiledExpression getBackupAges() {
			return theBackupAges;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theBackupAges = getAttributeExpression("ages", session);
		}

		public Interpreted<? extends B> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<B extends DataBackup> extends ExElement.Interpreted.Abstract<B> {
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<Duration>> theBackupAges;

		Interpreted(Def<? super B> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super B> getDefinition() {
			return (Def<? super B>) super.getDefinition();
		}

		public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<Duration>> getBackupAges() {
			return theBackupAges;
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();
			theBackupAges = interpret(getDefinition().getBackupAges(), ModelTypes.Collection.forType(Duration.class));
		}

		public DataBackup create() {
			return new DataBackup(getIdentity());
		}
	}

	private ModelValueInstantiator<ObservableCollection<Duration>> theBackupAgesInstantiator;
	private SettableValue<ObservableCollection<Duration>> theBackupAgesValue;
	private ObservableCollection<Duration> theBackupAges;

	DataBackup(Object id) {
		super(id);
		theBackupAgesValue = SettableValue.create();
		theBackupAges = ObservableCollection.flattenValue(
			theBackupAgesValue.map(ages -> ages == null ? getDefaultBackupAges() : ages));
	}

	protected ObservableCollection<Duration> getDefaultBackupAges() {
		return ObservableCollection.of(TemporalBackupScheme.DEFAULT_BACKUP_AGES);
	}

	public ObservableCollection<Duration> getBackupAges() {
		return theBackupAges;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted<?> myInterpreted = (Interpreted<?>) interpreted;
		theBackupAgesInstantiator = myInterpreted.getBackupAges() == null ? null : myInterpreted.getBackupAges().instantiate();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		theBackupAgesValue.set(theBackupAgesInstantiator == null ? null : theBackupAgesInstantiator.get(myModels));

		return myModels;
	}

	@Override
	public Abstract copy(ExElement parent) {
		DataBackup copy = (DataBackup) super.copy(parent);

		copy.theBackupAgesValue = SettableValue.create();
		copy.theBackupAges = ObservableCollection.flattenValue(
			copy.theBackupAgesValue.map(ages -> ages == null ? ObservableCollection.of(TemporalBackupScheme.DEFAULT_BACKUP_AGES) : ages));

		return copy;
	}

	public static class NoBackup extends DataBackup {
		public static final String NO_BACKUP = "no-backup";

		@ExElementTraceable(toolkit = ExpressoConfigV0_1.CONFIG,
			qonfigType = NO_BACKUP,
			interpretation = Interpreted.class,
			instance = NoBackup.class)
		public static class Def<B extends NoBackup> extends DataBackup.Def<B> {
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
			}

			@Override
			public Interpreted<? extends B> interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted<>(this, parent);
			}
		}

		public static class Interpreted<B extends NoBackup> extends DataBackup.Interpreted<B> {
			public Interpreted(Def<? super B> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def<? super B> getDefinition() {
				return (Def<? super B>) super.getDefinition();
			}

			@Override
			public NoBackup create() {
				return new NoBackup(getIdentity());
			}
		}

		NoBackup(Object id) {
			super(id);
		}

		@Override
		protected ObservableCollection<Duration> getDefaultBackupAges() {
			return null;
		}
	}
}
