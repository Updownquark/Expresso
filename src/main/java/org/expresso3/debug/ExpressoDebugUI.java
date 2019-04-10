package org.expresso3.debug;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JTree;
import javax.swing.border.Border;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;

import org.expresso.stream.BranchableStream;
import org.expresso3.BareContentExpressionType;
import org.expresso3.Expression;
import org.expresso3.ExpressionFieldType;
import org.expresso3.ExpressionType;
import org.expresso3.ExpressoGrammar;
import org.expresso3.GrammarExpressionType;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.SimpleSettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.observe.util.swing.ObservableSwingUtils;
import org.observe.util.swing.ObservableTreeModel;
import org.qommons.BreakpointHere;
import org.qommons.tree.BetterTreeList;

import com.google.common.reflect.TypeToken;

import net.miginfocom.swing.MigLayout;

public class ExpressoDebugUI extends JPanel implements ExpressoDebugger {
	static class ParsingState {
		static final TypeToken<ParsingState> TYPE = TypeTokens.get().of(ParsingState.class);

		final ParsingState parent;
		final ExpressionType<?> component;
		final ObservableCollection<ParsingState> children;
		final Iterator<? extends ExpressionType<?>> childIter;
		final SimpleObservable<Void> stateChange;
		BranchableStream<?, ?> theStream;
		Expression<?> theResult;
		DebugResultMethod theMethod;
		boolean isParsing;

		ParsingState(ParsingState parent, ExpressionType<?> component) {
			this.parent = parent;
			this.component = component;
			stateChange = new SimpleObservable<>();
			Iterable<? extends ExpressionType<?>> componentChildren = component.getComponents();
			children = ObservableCollection.create(TYPE, new BetterTreeList<>(false)).flow().refreshEach(state -> state.stateChange)
				.collect();
			childIter = componentChildren.iterator();
		}

		ParsingState getChild(int streamPos, ExpressionType<?> component) {
			int pos = theStream.getPosition();
			boolean useNext = pos == streamPos;
			for (ParsingState child : children) {
				if (useNext) {
					if (child.component == component)
						return child;
					else if (child.theResult != null && child.theResult.length() > 0)
						return null;
				} else if (child.theResult != null)
					pos += child.theResult.length();
				if (pos == streamPos)
					useNext = true;
				else if (pos > streamPos)
					return null;
			}
			if (!useNext)
				return null;
			if (childIter.hasNext())
				children.add(new ParsingState(this, childIter.next()));
			if (children.getLast().component == component)
				return children.getLast();
			else
				return null;
		}

		ParsingState into(BranchableStream<?, ?> stream) {
			theStream = stream;
			isParsing = true;
			stateChange.onNext(null);
			return this;
		}

		void out(Expression<?> result, DebugResultMethod methodUsed) {
			isParsing = false;
			theResult = result;
			theMethod = methodUsed;
			stateChange.onNext(null);
		}

		void reset(BranchableStream<?, ?> stream) {
			isParsing = false;
			theStream = stream;
			stateChange.onNext(null);
		}

		@Override
		public String toString() {
			if (component instanceof BareContentExpressionType || component instanceof GrammarExpressionType)
				return component.toString();
			else if (component instanceof ExpressionFieldType)
				return "Field " + ((ExpressionFieldType<?>) component).getFields();
			String name = component.getClass().getSimpleName();
			if (name.endsWith("Type")) {
				name = name.substring(0, name.length() - 4);
				if (name.endsWith("Expression"))
					name = name.substring(0, name.length() - 10);
				if (component instanceof ExpressionFieldType)
					name += " " + ((ExpressionFieldType<?>) component).getFields();
			}
			return name;
		}
	}

	static class ParseResult {
		final DebugResultMethod methodUsed;
		final Expression<?> result;

		ParseResult(DebugResultMethod methodUsed, Expression<?> result) {
			this.methodUsed = methodUsed;
			this.result = result;
		}
	}

	enum StepRequest {
		Over, Into, Out;
	}

	private final SettableValue<ParsingState> theRoot;
	private final SettableValue<ParsingState> theSelection;
	private final LinkedList<ParsingState> theStack;
	private final JTree theStateTree;
	private StepRequest theStepRequest;
	private int theStepRequestDepth;
	private final SettableValue<Boolean> isSuspended;
	private boolean isReallySuspended;
	private final SettableValue<Boolean> isDebugging;

	public ExpressoDebugUI() {
		super(new MigLayout("fill"));
		theRoot = new SimpleSettableValue<>(ParsingState.class, true);
		theSelection = new SimpleSettableValue<>(ParsingState.class, true);
		theStack = new LinkedList<>();
		isSuspended = new SimpleSettableValue<>(boolean.class, false);
		isSuspended.set(false, null);
		isDebugging = new SimpleSettableValue<>(boolean.class, false);
		isDebugging.set(false, null);

		theStateTree = new JTree(new ParseStackTreeModel(theRoot));
		initComponents();
	}

	@Override
	public void init(ExpressoGrammar<?> grammar, BranchableStream<?, ?> stream, ExpressionType<?> root) {
		if (EventQueue.isDispatchThread())
			throw new IllegalStateException("Cannot debug parsing on the EDT");
		if (theRoot.get() != null && theRoot.get().component == root)
			theRoot.get().reset(stream);
		else
			theRoot.set(new ParsingState(null, root), null);

		// Debug at root
		theStepRequest = StepRequest.Into;
	}

	@Override
	public DebugExpressionParsing begin(ExpressionType<?> component, BranchableStream<?, ?> stream, Expression<?> source) {
		debugPrint(() -> "Begin " + component, true);
		ParsingState state;
		if (theStack.isEmpty())
			state = theRoot.get();
		else
			state = theStack.getLast().getChild(stream.getPosition(), component);
		if (state == null)
			return DebugExpressionParsing.IDLE;
		else
			state.into(stream);
		while (!component.equals(state.component) && state.children.size() == 1) {
			state = state.children.get(0);
		}
		if (!component.equals(state.component)) {
			System.err.println("Bad State!!");
			suspend();
		}
		theStack.add(state);
		{
			boolean suspend = isSuspended.get();
			if (!suspend && theStepRequest != null) {
				switch (theStepRequest) {
				case Into:
					suspend = true;
					break;
				case Out:
				case Over:
					suspend = theStack.size() <= theStepRequestDepth;
					break;
				}
			}
			if (suspend) {
				theStepRequest = null;
				suspend();
			} else
				repaint();
		}
		ParsingState fState = state;
		return new DebugExpressionParsing() {
			@Override
			public void finished(Expression<?> expression, DebugResultMethod methodUsed) {
				debugPrint(() -> "End " + component, true);
				fState.out(expression, methodUsed);
				boolean suspend = isSuspended.get();
				if (!suspend && theStepRequest != null) {
					switch (theStepRequest) {
					case Into:
						theStepRequest = null;
						suspend();
						break;
					case Over:
					case Out:
						suspend = theStack.size() < theStepRequestDepth;
						break;
					}
				}
				if (suspend) {
					theStepRequest = null;
					suspend();
				} else
					repaint();
				theStack.removeLast();
			}
		};
	}

	private void debugPrint(Supplier<String> string, boolean newLine) {
		// StringBuilder str = new StringBuilder();
		// for (int i = 0; i < theStack.size(); i++)
		// str.append('\t');
		// str.append(string);
		// if (newLine)
		// System.out.println(str);
		// else
		// System.out.print(str);
	}

	@Override
	public void suspend() {
		isReallySuspended = true;
		isSuspended.set(true, null);
		if (!theStack.isEmpty()) {
			TreePath path = new TreePath(stackPath());
			EventQueue.invokeLater(() -> {
				theStateTree.setSelectionPath(path);
				theStateTree.scrollPathToVisible(path);
			});
		}
		while (isSuspended.get()) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
		isReallySuspended = false;
		if (isDebugging.get()) {
			isDebugging.set(false, null);
			BreakpointHere.breakpoint();
		}
	}

	private void resume() {
		isReallySuspended = false;
		isSuspended.set(false, null);
	}

	private Object[] stackPath() {
		Object[] stackPath = new Object[theStack.size()];
		int i = 0;
		for (ParsingState state : theStack) {
			if (i == 0)
				stackPath[i] = theRoot;
			else
				stackPath[i] = state;
			i++;
		}
		return stackPath;
	}

	private void initComponents() {
		JTextPane textArea = new JTextPane();
		JScrollPane textScroll = new JScrollPane(textArea);
		textScroll.setPreferredSize(new Dimension(500, 200));
		textArea.setEditable(false);
		textArea.setContentType("text/html");
		add(textScroll, "grow, wrap");

		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
		add(buttonPanel, "grow 0,wrap");

		JScrollPane treeScroll = new JScrollPane(theStateTree);
		treeScroll.getVerticalScrollBar().setUnitIncrement(15);
		treeScroll.getHorizontalScrollBar().setUnitIncrement(15);
		treeScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		add(treeScroll, "grow");
		theStateTree.setEditable(false);
		theStateTree.setExpandsSelectedPaths(true);
		theStateTree.setCellRenderer(new DefaultTreeCellRenderer() {
			private final Border selectedBorder;
			private Font normalFont;
			private Font matchFont;

			{
				selectedBorder = BorderFactory.createLineBorder(Color.black);
				setOpaque(true);
			}

			@Override
			public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row,
				boolean focused) {
				if (value instanceof ObservableValue)
					value = ((ObservableValue<?>) value).get();
				ParsingState state = (ParsingState) value;
				String text;
				if (state == null) {
					text = "";
				} else {
					text = state.toString();
					if (state.theMethod != null) {
						text += "(" + state.theMethod.toString();
						if (state.theResult != null)
							text += ", length=" + state.theResult.length() + ", " + state.theResult.getErrorCount() + " errors";
						text += ")";
					}
				}
				super.getTreeCellRendererComponent(tree, text, sel, expanded, leaf, row, focused);
				setOpaque(true);
				if (state == theStack.peekLast())
					setBackground(Color.green);
				else if (state.isParsing)
					setBackground(Color.cyan);
				else
					setBackground(Color.white);
				setBorder(selected ? selectedBorder : null);
				if (normalFont == null) {
					// I don't understand why, but getFont() returns null if called earlier
					normalFont = getFont();
					matchFont = normalFont.deriveFont(Font.BOLD);
				}
				setFont((state != null && state.theResult != null) ? matchFont : normalFont);

				return this;
			}
		});
		theStateTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
			@Override
			public void valueChanged(TreeSelectionEvent e) {
				TreePath[] selection = theStateTree.getSelectionPaths();
				if (selection == null || selection.length != 1)
					theSelection.set(null, e);
				else {
					Object lpc = selection[0].getLastPathComponent();
					if (lpc instanceof ObservableValue)
						theSelection.set(((ObservableValue<ParsingState>) lpc).get(), e);
					else
						theSelection.set((ParsingState) lpc, e);
				}
			}
		});
		
		//Debug buttons
		JButton stepOver = new JButton("Over");
		JButton stepInto = new JButton("Into");
		JButton stepOut = new JButton("Out");
		JButton resume = new JButton("Resume");
		JButton suspend = new JButton("Suspend");
		JButton debug = new JButton("Debug");
		buttonPanel.add(stepOver);
		buttonPanel.add(stepInto);
		buttonPanel.add(stepOut);
		buttonPanel.add(resume);
		buttonPanel.add(suspend);
		buttonPanel.add(debug);

		theSelection.changes().act(evt -> {
			ParsingState state = evt.getNewValue();
			boolean local = true;
			if (state != null) {
				while (state != null && state.theStream == null) {
					local = false;
					state = state.parent;
				}
			} else
				state = theRoot.get();
			if (state != null && state.theStream != null) {
				StringBuilder text = new StringBuilder("<html>");
				int len = text.length();
				if (!local || state.theResult == null || state.theResult.length() == 0) {
					text.append(state.theStream.toString());
					escapeHtml(text, len, text.length(), false);
				} else {
					theRoot.get().theStream.printContent(0, state.theResult.getStream().getPosition(), text);
					escapeHtml(text, len, text.length(), false);
					text.append("<b><font color=\"red\">");
					len = text.length();
					state.theStream.printContent(0, state.theResult.length(), text);
					escapeHtml(text, len, text.length(), false);
					text.append("</font></b>");
					len = text.length();
					state.theStream.printContent(state.theResult.length(), state.theStream.getDiscoveredLength(), text);
					escapeHtml(text, len, text.length(), false);
				}
				textArea.setText(text.toString());
			} else
				textArea.setText("");
		});
		stepOver.addActionListener(evt -> {
			theStepRequest = StepRequest.Over;
			theStepRequestDepth = theStack.size();
			resume();
		});
		stepInto.addActionListener(evt -> {
			theStepRequest = StepRequest.Into;
			resume();
		});
		stepOut.addActionListener(evt -> {
			theStepRequest = StepRequest.Out;
			theStepRequestDepth = theStack.size();
			if (theStack.getLast().isParsing)
				theStepRequestDepth--;
			resume();
		});
		resume.addActionListener(evt -> {
			resume();
		});
		suspend.addActionListener(evt -> {
			isSuspended.set(true, null);
		});
		debug.addActionListener(evt -> {
			isDebugging.set(true, null);
			resume();
		});

		isSuspended.changes().act(evt -> {
			boolean really = isReallySuspended;
			ObservableSwingUtils.onEQ(() -> {
				if (really || !evt.getNewValue()) {
					boolean parsing = evt.getNewValue() && !theStack.isEmpty() && theStack.getLast().isParsing;
					stepOver.setEnabled(evt.getNewValue());
					stepInto.setEnabled(parsing);
					stepOut.setEnabled(evt.getNewValue());
					debug.setEnabled(evt.getNewValue());
				}
				resume.setEnabled(evt.getNewValue());
				suspend.setEnabled(!evt.getNewValue());
			});
		});
	}

	private static void escapeHtml(StringBuilder text, int start, int end, boolean emphasized) {
		for (int i = start; i < end; i++) {
			char c = text.charAt(i);
			String replacement = null;
			switch (c) {
			case '<':
				replacement = "&lt;";
				break;
			case '\n':
				replacement = "\\n<br>\n";
				break;
			case '\t':
				replacement = "\\t&nbsp;&nbsp;&nbsp;";
				break;
			case ' ':
				if (emphasized)
					replacement = "_";
				break;
			}
			if (replacement != null) {
				text.replace(i, i + 1, replacement);
				i += replacement.length() - 1;
				end += replacement.length() - 1;
			}
		}
	}

	public JFrame buildFrame() {
		JFrame frame = new JFrame("Expresso Debugger UI");
		frame.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentHidden(ComponentEvent e) {
				resume();
			}
		});
		frame.getContentPane().add(this);
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
		return frame;
	}

	class ParseStackTreeModel extends ObservableTreeModel {
		ParseStackTreeModel(Object root) {
			super(root);
		}

		@Override
		public boolean isLeaf(Object node) {
			return false;
		}

		@Override
		public void valueForPathChanged(TreePath path, Object newValue) {}

		@Override
		protected ObservableCollection<?> getChildren(Object parent) {
			if (parent instanceof ObservableValue) {
				return ObservableCollection.flattenValue(((ObservableValue<ParsingState>) parent).map(ps -> {
					if (ps == null)
						return ObservableCollection.of(TypeTokens.get().of(ParsingState.class));
					else
						return ps.children;
				}));
			} else
				return ((ParsingState) parent).children;
		}
	}
}
