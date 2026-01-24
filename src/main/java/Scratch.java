import org.observe.Observable;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.JavaExpressoParser;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.qommons.ex.ExceptionHandler;

/**
 * <p>
 * This file was never supposed to be committed, but it turns out it's easy to miss in big commits.
 * </p>
 *
 * <p>
 * This is a simple main class that I created to do tiny little one-off tests that are not worth saving. The content of this file at any
 * point in history is irrelevant, but I'm keeping it in here just for utility.
 * </p>
 */
public class Scratch {
	/**
	 * Main method. What it does at any point in time, who knows.
	 *
	 * @param args Command-line arguments, typically ignored
	 * @throws Throwable Hey, could happen
	 */
	public static void main(String... args) throws Throwable {
		// ObservableExpression exp = new JavaExpressoParser()
		// .parse("`Are you sure you want to delete `+(app.toDelete.size()==1" + " ? (`Call Group '`+app.toDelete.peekFirst()+`'`)"//
		// + ": (`These `+app.toDelete.size()+` Call Groups`)"//
		// + ")+`?`");

		ObservableExpression exp = new JavaExpressoParser()
			.parse("(app.toDelete.size()==1" + " ? (`Call Group '`+app.toDelete.peekFirst()+`'`)"//
				+ ": (`These `+app.toDelete.size())"//
				+ ")");

		// ObservableExpression exp = new JavaExpressoParser().parse("`These `+app.toDelete.size()");

		ObservableCollection<String> toDelete = ObservableCollection.create();
		toDelete.alias("toDelete");
		ObservableModelSet.Built oms = ObservableModelSet.build("models", ObservableModelSet.JAVA_NAME_CHECKER)//
			.withSubModel("app", null, sub -> sub//
				.with("toDelete", ModelTypes.Collection.forType(String.class), ModelValueInstantiator.literal(toDelete, "toDelete"), null)//
				)//
			.build();
		CompiledExpressoEnv compileEnv = CompiledExpressoEnv.STANDARD_JAVA.with(oms);
		InterpretedExpressoEnv env = compileEnv.interpret(null, null);
		env.getModels().interpret(env);
		env.getModels().instantiate();

		ModelSetInstance models = env.getModels().createInstance(Observable.empty()).build();
		SettableValue<String> message = exp.evaluate(ModelTypes.Value.STRING, env, 0, ExceptionHandler.thrower2())//
			.instantiate()//
			.get(models);
		message.changes().act(evt -> System.out.println("Event: " + evt.getNewValue()));
		System.out.println("Add 'Value 1'");
		toDelete.add("Value 1");
		System.out.println("Get: " + message.get());
		message.toString();
		System.out.println("Add 'Value 2'");
		toDelete.add("Value 2");
		System.out.println("Get: " + message.get());
		System.out.println("Remove 'Value 1'");
		toDelete.remove(0);
		System.out.println("Get: " + message.get());
		System.out.println("Clear");
		toDelete.clear();
		System.out.println("Get: " + message.get());
		// ObservableCollection<Integer> rows = ObservableCollection.<Integer> create()//
		// .with(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
		// ObservableCollection<Integer> selection = ObservableCollection.create();
		// int[] next = new int[] { 11 };
		// EventQueue.invokeLater(() -> {
		// ObservableSwingUtils.systemLandF();
		// WindowPopulation.populateWindow(null, null, true, true)//
		// .withTitle("ObServe Scratch")//
		// .withVContent(p -> p.fill()//
		// .addTable(rows, table -> table.fill()//
		// .withColumn("Value", int.class, i -> i, null)//
		// .withSelection(selection)//
		// .withAdd(() -> next[0]++, null)//
		// .withRemove(vs -> rows.removeAll(vs), null)//
		// .dragSourceRow(d -> d.draggable(true))//
		// .dragAcceptRow(d -> d.draggable(true))//
		// )//
		// .addTable(selection, table -> table.fill()//
		// .withColumn("Value", int.class, i -> i, null)//
		// )//
		// )//
		// .run(null);
		// });
		// selection.onChange(evt -> System.out.println(evt));
	}
}
