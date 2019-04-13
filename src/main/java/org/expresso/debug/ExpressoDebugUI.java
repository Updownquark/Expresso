package org.expresso.debug;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.Instant;
import java.util.Collection;
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

import org.expresso.*;
import org.expresso.stream.BranchableStream;
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

/** A graphical debugger */
public class ExpressoDebugUI extends JPanel implements ExpressoDebugger {
	static class ParsingState {
		static final TypeToken<ParsingState> TYPE = TypeTokens.get().of(ParsingState.class);

		final ParsingState theParent;
		final ExpressionType<?> theExpressionType;
		final ObservableCollection<ParsingState> theChildren;
		final boolean variableChildren;
		final Iterator<? extends ExpressionType<?>> theChildIter;
		final SimpleObservable<Void> theStateChange;
		BranchableStream<?, ?> theStream;
		Expression<?> theResult;
		DebugResultMethod theMethod;
		Instant theLastParseTime;
		boolean isParsing;

		boolean isBreakpoint;

		ParsingState(ParsingState parent, ExpressionType<?> component) {
			this.theParent = parent;
			this.theExpressionType = component;
			theStateChange = new SimpleObservable<>();
			Iterable<? extends ExpressionType<?>> componentChildren = component.getComponents();
			theChildren = ObservableCollection.create(TYPE, new BetterTreeList<>(false)).flow().refreshEach(state -> state.theStateChange)
				.collect();
			variableChildren = !(componentChildren instanceof Collection);
			theChildIter = componentChildren.iterator();
		}

		ParsingState getChild(int streamPos, ExpressionType<?> component) {
			// This might be a little hacky, but we know that "variable children" means a repeat operation.
			// Otherwise, we can assume all the child component types will be different
			if (!variableChildren) {
				while (theChildIter.hasNext())
					theChildren.add(new ParsingState(this, theChildIter.next()));
				for (ParsingState child : theChildren) {
					if (child.theExpressionType == component)
						return child;
					else if (child.theResult != null && child.theStream.getPosition() == streamPos) {
						// The parser didn't use this match for some reason
						child.theResult = null;
						if (child.theLastParseTime.compareTo(theLastParseTime) < 0)
							child.theMethod = null;
						else
							child.theMethod = DebugResultMethod.Excluded;
						child.theStateChange.onNext(null);
					}
				}
			} else {
				int pos = theStream.getPosition();
				boolean useNext = pos == streamPos;
				for (ParsingState child : theChildren) {
					if (useNext) {
						if (child.theExpressionType == component)
							return child;
						else if (child.theResult != null && child.theResult.length() > 0)
							return null;
					} else if (child.theResult != null)
						pos += child.theResult.length();
					if (pos == streamPos)
						useNext = true;
					else if (pos > streamPos)
						return child;
				}
				if (!useNext)
					return null;
				if (theChildIter.hasNext())
					theChildren.add(new ParsingState(this, theChildIter.next()));
				if (theChildren.getLast().theExpressionType == component)
					return theChildren.getLast();
			}
			return null;
		}

		ParsingState into(BranchableStream<?, ?> stream) {
			theLastParseTime = Instant.now();
			theStream = stream;
			isParsing = true;
			theStateChange.onNext(null);
			return this;
		}

		void out(Expression<?> result, DebugResultMethod methodUsed) {
			isParsing = false;
			theResult = result;
			theMethod = methodUsed;
			theStateChange.onNext(null);
			for (ParsingState child : theChildren.reverse())
				if (child.theLastParseTime != null && child.theLastParseTime.compareTo(theLastParseTime) < 0)
					child.reset(null);
		}

		void reset(BranchableStream<?, ?> stream) {
			isParsing = false;
			theStream = stream;
			theResult = null;
			theMethod = null;
			theLastParseTime = null;
			theStateChange.onNext(null);
		}

		@Override
		public String toString() {
			if (theExpressionType instanceof BareContentExpressionType || theExpressionType instanceof GrammarExpressionType)
				return theExpressionType.toString();
			else if (theExpressionType instanceof ExpressionFieldType)
				return "Field " + ((ExpressionFieldType<?>) theExpressionType).getFields();
			String name = theExpressionType.getClass().getSimpleName();
			if (name.endsWith("Type")) {
				name = name.substring(0, name.length() - 4);
				if (name.endsWith("Expression"))
					name = name.substring(0, name.length() - 10);
				if (theExpressionType instanceof ExpressionFieldType)
					name += " " + ((ExpressionFieldType<?>) theExpressionType).getFields();
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

	/** Creates the debugger */
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
		if (theRoot.get() != null && theRoot.get().theExpressionType == root)
			theRoot.get().reset(stream);
		else
			theRoot.set(new ParsingState(null, root), null);
		((ParseStackTreeModel) theStateTree.getModel()).rootChanged();

		theStepRequest = StepRequest.Into; // Debug at root
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
		while (!component.equals(state.theExpressionType) && state.theChildren.size() == 1) {
			state = state.theChildren.get(0);
		}
		if (!component.equals(state.theExpressionType)) {
			System.err.println("Bad State!!");
			suspend();
		}
		theStack.add(state);
		{
			boolean suspend = isSuspended.get() || state.isBreakpoint;
			state.isBreakpoint = false;
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
				boolean suspend = isSuspended.get() || fState.isBreakpoint;
				fState.isBreakpoint = false;
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
			if (theSelection.get() == theStack.getLast()) {
				// Refresh the selection, since it may have changed
				theSelection.set(theStack.getLast(), null);
			}
			TreePath path = new TreePath(stackPath());
			EventQueue.invokeLater(() -> {
				theStateTree.setSelectionPath(path);
				theStateTree.scrollPathToVisible(path);
			});
		}
		while (isSuspended.get()) {
			// This loop is to capture the case where the user clicks "Debug" and then "Suspend" again right after
			// The debugger will catch in the same place without progressing in the parsing
			isReallySuspended = true;
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
					if (state.isBreakpoint)
						text += "*";
					DebugResultMethod method = state.theMethod;
					if (method != null) {
						text += "(" + method.toString();
						Expression<?> result = state.theResult;
						if (result != null)
							text += ", length=" + result.length() + ", " + result.getErrorCount() + " errors";
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

		// Debug buttons
		JButton stepOver = new JButton("Over");
		JButton stepInto = new JButton("Into");
		JButton stepOut = new JButton("Out");
		JButton resume = new JButton("Resume");
		JButton suspend = new JButton("Suspend");
		JButton debug = new JButton("Debug");
		JButton breakpoint = new JButton("BP");
		buttonPanel.add(stepOver);
		buttonPanel.add(stepInto);
		buttonPanel.add(stepOut);
		buttonPanel.add(resume);
		buttonPanel.add(suspend);
		buttonPanel.add(debug);
		buttonPanel.add(breakpoint);

		theSelection.changes().act(evt -> {
			ParsingState state = evt.getNewValue();
			breakpoint.setEnabled(state != null);
			if (state != null) {
				breakpoint.setText(state.isBreakpoint ? "BP Off" : "BP On");
			} else
				breakpoint.setText("BP");
			boolean local = true;
			if (state != null) {
				while (state != null && state.theStream == null) {
					local = false;
					state = state.theParent;
				}
			} else
				state = theRoot.get();
			BranchableStream<?, ?> stream = state == null ? null : state.theStream;
			if (stream != null) {
				Expression<?> result = state.theResult;
				StringBuilder text = new StringBuilder("<html>");
				int len = text.length();
				if (!local || result == null || result.length() == 0) {
					text.append(state.theStream.toString());
					escapeHtml(text, len, text.length(), false);
				} else {
					theRoot.get().theStream.printContent(0, result.getStream().getPosition(), text);
					escapeHtml(text, len, text.length(), false);
					text.append("<b><font color=\"red\">");
					len = text.length();
					result.getStream().printContent(0, result.length(), text);
					escapeHtml(text, len, text.length(), false);
					text.append("</font></b>");
					len = text.length();
					result.getStream().printContent(result.length(), state.theStream.getDiscoveredLength(), text);
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
		breakpoint.addActionListener(evt -> {
			ParsingState state = theSelection.get();
			if (state != null) {
				state.isBreakpoint = !state.isBreakpoint;
				theSelection.set(state, evt);
			}
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

	/**
	 * Builds (and displays) a JFrame with this debugger panel as its content
	 * 
	 * @return The frame
	 */
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
						return ps.theChildren;
				}));
			} else
				return ((ParsingState) parent).theChildren;
		}
	}
}
