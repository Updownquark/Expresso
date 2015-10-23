package org.expresso.parse.debug;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.TreePath;

import org.expresso.parse.*;
import org.expresso.parse.impl.CharSequenceStream;
import org.expresso.parse.impl.ReferenceMatcher;
import org.expresso.parse.impl.WhitespaceMatcher;
import org.qommons.config.MutableConfig;
import org.qommons.config.QommonsConfig;

/**
 * A graphical debugger for the {@link ExpressoParser}
 *
 * @param <S> The sub-type of stream to parse
 */
public class ExpressoParserDebugGUI<S extends CharSequenceStream> extends JPanel
implements org.expresso.parse.debug.ExpressoParsingDebugger<S> {
	enum StepTargetType {
		EXIT, CHILD
	}

	private static Color SELECTED = new Color(100, 100, 255);

	private static ImageIcon RESUME_ICON = getIcon("play.png", 24, 24);

	private static ImageIcon PAUSE_ICON = getIcon("pause.png", 24, 24);

	private JTextPane theMainText;

	private JScrollPane theMainTextScroll;

	private ParsingExpressionTreeModel theTreeModel;

	private JTree theParseTree;

	private JButton theOverButton;
	private JButton theIntoButton;
	private JButton theOutButton;
	private JButton theResumeButton;

	private JToggleButton theDebugButton;

	private JTable theBreakpointList;

	private JLabel theAddBreakpointLabel;

	private JList<MatcherObject> theDebugPane;

	private JSplitPane theMainSplit;
	private JSplitPane theRightSplit;

	private File theConfigFile;

	private List<String> theOpNames;

	private List<ExpressoParserBreakpoint> theBreakpoints;

	private boolean isPopupWhenHit = true;

	private volatile boolean isSuspended;
	private volatile boolean isHolding;
	private S theStream;
	private int thePosition;
	private int inIgnorable;
	private ParseNode theStepTarget;
	private StepTargetType theStepTargetType;
	private boolean isDebugging;
	private int theLastBreakIndex;

	private java.util.Set<ExpressoParserBreakpoint> theIndexBreakpoints;

	private boolean hasWarnedAwtEventThread;

	/** Creates a debug GUI using the config file in the standard location */
	public ExpressoParserDebugGUI() {
		this(new File("PrismsParserDebug.config"));
	}

	/**
	 * Creates a debug GUI
	 *
	 * @param configFile The config file to use
	 */
	public ExpressoParserDebugGUI(File configFile) {
		super(new BorderLayout());

		theConfigFile = configFile;
		theMainText = new JTextPane();
		theMainText.setContentType("text/html");
		theMainText.setEditable(false);
		theMainText.setBackground(java.awt.Color.white);
		theTreeModel = new ParsingExpressionTreeModel();
		theParseTree = new JTree(theTreeModel);
		theParseTree.getSelectionModel().setSelectionMode(javax.swing.tree.TreeSelectionModel.SINGLE_TREE_SELECTION);
		theParseTree.getSelectionModel().addTreeSelectionListener(new javax.swing.event.TreeSelectionListener() {
			@Override
			public void valueChanged(TreeSelectionEvent e) {
				if(e.getPath() == null)
					setDebugOperator(null);
				else
					setDebugOperator((ParseNode) e.getPath().getLastPathComponent());
				setGuiEnabled(isSuspended);
			}
		});
		theOverButton = new JButton(getIcon("arrow180.png", 24, 16));
		theIntoButton = new JButton(getIcon("arrow90down.png", 24, 24));
		theOutButton = new JButton(getIcon("arrow90right.png", 24, 24));
		theResumeButton = new JButton(RESUME_ICON);
		theDebugButton = new JToggleButton(getIcon("bug.png", 24, 24));
		theAddBreakpointLabel = new JLabel(getIcon("bluePlus.png", 16, 16));
		theDebugPane = new JList<>(new DefaultListModel<MatcherObject>());
		theDebugPane.setCellRenderer(new MatcherRenderer());
		theBreakpointList = new JTable(0, 4);
		theBreakpointList.getTableHeader().setReorderingAllowed(false);
		theBreakpointList.getColumnModel().getColumn(0).setHeaderValue("");
		theBreakpointList.getColumnModel().getColumn(0).setCellRenderer(new BreakpointEnabledRenderer());
		theBreakpointList.getColumnModel().getColumn(0).setCellEditor(new BreakpointEnabledEditor());
		int w = new BreakpointEnabledRenderer().getPreferredSize().width;
		theBreakpointList.getColumnModel().getColumn(0).setMinWidth(w);
		theBreakpointList.getColumnModel().getColumn(0).setPreferredWidth(w);
		theBreakpointList.getColumnModel().getColumn(0).setMaxWidth(w);
		theBreakpointList.getColumnModel().getColumn(0).setResizable(false);
		theBreakpointList.getColumnModel().getColumn(1).setHeaderValue("Text");
		theBreakpointList.getColumnModel().getColumn(1).setCellRenderer(new BreakpointTextRenderer());
		theBreakpointList.getColumnModel().getColumn(1).setCellEditor(new BreakpointTextEditor());
		theBreakpointList.getColumnModel().getColumn(2).setHeaderValue("Operator");
		theBreakpointList.getColumnModel().getColumn(2).setCellRenderer(new BreakpointOpRenderer());
		theBreakpointList.getColumnModel().getColumn(2).setCellEditor(new BreakpointOpEditor());
		theBreakpointList.getColumnModel().getColumn(2).setResizable(false);
		theBreakpointList.getColumnModel().getColumn(3).setHeaderValue("");
		theBreakpointList.getColumnModel().getColumn(3).setCellRenderer(new BreakpointDeleteRenderer());
		theBreakpointList.getColumnModel().getColumn(3).setCellEditor(new BreakpointDeleteEditor());
		w = BreakpointDeleteRenderer.ICON.getIconWidth() + 4;
		theBreakpointList.getColumnModel().getColumn(3).setMinWidth(w);
		theBreakpointList.getColumnModel().getColumn(3).setPreferredWidth(w);
		theBreakpointList.getColumnModel().getColumn(3).setMaxWidth(w);
		theBreakpointList.getColumnModel().getColumn(3).setResizable(false);
		theMainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		theRightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

		theBreakpoints = new java.util.concurrent.CopyOnWriteArrayList<>();

		theMainTextScroll = new JScrollPane(theMainText);
		theMainTextScroll.setPreferredSize(new Dimension(100, 200));
		add(theMainTextScroll, BorderLayout.NORTH);
		add(theMainSplit);
		JScrollPane treeScroll = new JScrollPane(theParseTree);
		treeScroll.setPreferredSize(new Dimension(450, 400));
		theMainSplit.setLeftComponent(treeScroll);
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
		buttonPanel.add(theOverButton);
		buttonPanel.add(theIntoButton);
		buttonPanel.add(theOutButton);
		buttonPanel.add(theResumeButton);
		buttonPanel.add(theDebugButton);
		JPanel rightPanel = new JPanel(new BorderLayout());
		theMainSplit.setRightComponent(rightPanel);
		rightPanel.add(buttonPanel, BorderLayout.NORTH);
		rightPanel.add(theRightSplit);
		JPanel breakpointPanel = new JPanel(new BorderLayout());
		JPanel breakpointTop = new JPanel(new BorderLayout());
		breakpointTop.add(new JLabel("Breakpoints"), BorderLayout.WEST);
		breakpointPanel.add(breakpointTop, BorderLayout.NORTH);
		breakpointTop.add(theAddBreakpointLabel, BorderLayout.EAST);
		JScrollPane breakpointScroll = new JScrollPane(theBreakpointList);
		breakpointPanel.add(breakpointScroll);
		theRightSplit.setBottomComponent(breakpointPanel);
		JScrollPane debugScroll = new JScrollPane(theDebugPane);
		theRightSplit.setTopComponent(debugScroll);

		theOpNames = new ArrayList<>();

		theMainSplit.addPropertyChangeListener(JSplitPane.LAST_DIVIDER_LOCATION_PROPERTY, e -> writeConfig());
		theRightSplit.addPropertyChangeListener(JSplitPane.LAST_DIVIDER_LOCATION_PROPERTY, e -> writeConfig());

		theAddBreakpointLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				addBreakpoint();
			}
		});
		theOverButton.addActionListener(e -> stepOver());
		theIntoButton.addActionListener(e -> stepInto());
		theOutButton.addActionListener(e -> stepOut());
		theResumeButton.addActionListener(e -> resume());
		theDebugButton.addActionListener(e -> debug());

		theLastBreakIndex = -1;
		theIndexBreakpoints = new java.util.HashSet<>();

		reset();
		readConfig();
	}

	/** @param popup Whether this debugger should become visible when a breakpoint is hit */
	public void setPopupWhenHit(boolean popup) {
		isPopupWhenHit = popup;
	}

	/** @return The parsed config file */
	protected MutableConfig getConfig() {
		if(theConfigFile.exists()) {
			try {
				return new MutableConfig(null, QommonsConfig.fromXml(theConfigFile.toURI().toString()));
			} catch(java.io.IOException e) {
				e.printStackTrace();
				return new MutableConfig("config");
			}
		} else {
			System.out.println("Configuration does not exist yet.  Creating at " + theConfigFile.getAbsolutePath());
			MutableConfig ret = new MutableConfig("config");
			try {
				MutableConfig.writeAsXml(ret, new java.io.FileOutputStream(theConfigFile));
			} catch(IOException e) {
				System.err.println("Could not save config file for the debugger");
				e.printStackTrace();
				return new MutableConfig("config");
			}
			return ret;
		}
	}

	/** @param config The config data to save to the config file */
	protected void writeConfig(MutableConfig config) {
		try (java.io.BufferedOutputStream os = new java.io.BufferedOutputStream(new java.io.FileOutputStream(theConfigFile))) {
			MutableConfig.writeAsXml(config, os);
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	private static ImageIcon getIcon(String location, int w, int h) {
		ImageIcon icon = new ImageIcon(ExpressoParserDebugGUI.class.getResource(location));
		Image img = icon.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH);
		return new ImageIcon(img);
	}

	private void readSizeConfig() {
		MutableConfig config = getConfig();
		Window window = getWindow();
		if(window != null) {
			window.setBounds(config.getInt("x", window.getX()), config.getInt("y", window.getY()), config.getInt("w", window.getWidth()),
				config.getInt("h", window.getHeight()));
		}
		if(getWidth() > 0) {
			theMainSplit.setDividerLocation(config.getFloat("main-split", .5f));
			theRightSplit.setDividerLocation(config.getFloat("right-split", .5f));
		}
	}

	private void readConfig() {
		MutableConfig config = getConfig();
		if(config.subConfig("breakpoints") != null) {
			for(MutableConfig breakpointConfig : config.subConfig("breakpoints").subConfigs("breakpoint")) {
				ExpressoParserBreakpoint breakpoint = new ExpressoParserBreakpoint();
				breakpoint.setPreCursorText(
					breakpointConfig.get("pre") == null ? null : Pattern.compile(".*" + breakpointConfig.get("pre"), Pattern.DOTALL));
				breakpoint.setPostCursorText(
					breakpointConfig.get("post") == null ? null : Pattern.compile(breakpointConfig.get("post") + ".*", Pattern.DOTALL));
				breakpoint.setMatcherName(breakpointConfig.get("operator"));
				breakpoint.setEnabled(breakpointConfig.is("enabled", true));
				theBreakpoints.add(breakpoint);
				((javax.swing.table.DefaultTableModel) theBreakpointList.getModel())
				.addRow(new Object[] {breakpoint, breakpoint, breakpoint, breakpoint});
			}
		}
	}

	private void writeConfig() {
		MutableConfig config = getConfig();
		Window window = getWindow();
		if(window != null) {
			config.set("x", "" + window.getX());
			config.set("y", "" + window.getY());
			config.set("w", "" + window.getWidth());
			config.set("h", "" + window.getHeight());
		}
		if(getWidth() > 0) {
			config.set("main-split", "" + (theMainSplit.getDividerLocation() * 1.0f / theMainSplit.getWidth()));
			config.set("right-split", "" + (theRightSplit.getDividerLocation() * 1.0f / theRightSplit.getHeight()));
		}
		MutableConfig breakpoints = config.subConfig("breakpoints");
		if(breakpoints == null) {
			breakpoints = new MutableConfig("breakpoints");
			config.addSubConfig(breakpoints);
		} else {
			breakpoints.setSubConfigs(new MutableConfig[0]);
		}
		for(ExpressoParserBreakpoint bp : theBreakpoints) {
			MutableConfig bpConfig = breakpoints.addSubConfig(new MutableConfig("breakpoint")
				.set("pre", bp.getPreCursorText() == null ? null : bp.getPreCursorText().pattern().substring(2))
				.set("post",
					bp.getPostCursorText() == null ? null
						: bp.getPostCursorText().pattern().substring(0, bp.getPostCursorText().pattern().length() - 2))
				.set("enabled", "" + bp.isEnabled()));
			if(bp.getMatcherName() != null)
				bpConfig.set("operator", bp.getMatcherName());
		}
		writeConfig(config);
	}

	private Window getWindow() {
		Component c = this;
		while(c != null && !(c instanceof Window))
			c = c.getParent();
		return (Window) c;
	}

	private void breakpointChanged(ExpressoParserBreakpoint breakpoint) {
		theIndexBreakpoints.remove(breakpoint);
		writeConfig();
	}

	private void breakpointDeleted(ExpressoParserBreakpoint breakpoint) {
		theBreakpoints.remove(breakpoint);
		javax.swing.table.DefaultTableModel model = (javax.swing.table.DefaultTableModel) theBreakpointList.getModel();
		for(int i = 0; i < model.getRowCount(); i++)
			if(model.getValueAt(i, 0) == breakpoint) {
				model.removeRow(i);
				break;
			}
		writeConfig();
	}

	private void addBreakpoint() {
		ExpressoParserBreakpoint breakpoint = new ExpressoParserBreakpoint();
		theBreakpoints.add(breakpoint);
		((javax.swing.table.DefaultTableModel) theBreakpointList.getModel())
		.addRow(new Object[] {breakpoint, breakpoint, breakpoint, breakpoint});
		writeConfig();
	}

	@Override
	public void init(ExpressoParser<?> parser) {
		theOpNames.clear();
		for(ParseMatcher<?> op : parser.getComposed())
			theOpNames.add(op.getName());
		reset();
	}

	@Override
	public void start(S text) {
		theStream = text;
	}

	@Override
	public void end(ParseMatch<? extends S>... matches) {
		theStream = null;
	}

	@Override
	public void fail(S stream, ParseMatch<? extends S> match) {
		theStream = null;
	}

	@Override
	public void preParse(S stream, final ParseMatcher<?> matcher) {
		if(EventQueue.isDispatchThread()) {
			if(!hasWarnedAwtEventThread) {
				hasWarnedAwtEventThread = true;
				System.err.println("Prisms parser debugging on the AWT event thread is not allowed");
			}
			return;
		} else if(hasWarnedAwtEventThread)
			hasWarnedAwtEventThread = false;
		if(theLastBreakIndex != stream.getPosition()) {
			theLastBreakIndex = -1;
			theIndexBreakpoints.clear();
		}
		if(matcher.getTags().contains(ExpressoParser.IGNORABLE)) {
			inIgnorable++;
			return;
		} else if(inIgnorable > 0)
			return;
		boolean isOnStepTarget = false;
		if(theStepTarget != null && theStepTargetType == StepTargetType.CHILD && theStepTarget == theTreeModel.getCursor())
			isOnStepTarget = true;

		safe(() -> theTreeModel.startNew(matcher));
		int position = stream.getPosition();
		update(position, matcher, null);

		if(!isDebugging) {
			if(isSuspended || isOnStepTarget)
				suspend(null);
			else if(theStepTarget == null) {
				CharSequence pre = theStream.subSequence(0, position);
				CharSequence post = theStream.subSequence(position, theStream.length());
				for(ExpressoParserBreakpoint breakpoint : theBreakpoints) {
					if(!breakpoint.isEnabled() || theIndexBreakpoints.contains(breakpoint))
						continue;
					if(breakpoint.getPreCursorText() == null && breakpoint.getPostCursorText() == null
						&& breakpoint.getMatcherName() == null)
						continue;
					if(breakpoint.getPreCursorText() != null && !breakpoint.getPreCursorText().matcher(pre).matches())
						continue;
					if(breakpoint.getPostCursorText() != null && !breakpoint.getPostCursorText().matcher(post).matches())
						continue;
					if(breakpoint.getMatcherName() != null && !breakpoint.getMatcherName().equals(matcher.getName()))
						continue;
					theLastBreakIndex = position;
					theIndexBreakpoints.add(breakpoint);
					suspend(breakpoint);
					break;
				}
			}
		}
	}

	private void safe(Runnable run) {
		try {
			EventQueue.invokeAndWait(run);
		} catch(InvocationTargetException | InterruptedException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public void postParse(S stream, ParseMatcher<?> matcher, final ParseMatch<? extends S> match) {
		if(EventQueue.isDispatchThread())
			return;
		if(matcher.getTags().contains(ExpressoParser.IGNORABLE)) {
			inIgnorable--;
			return;
		} else if(inIgnorable > 0)
			return;
		if(matcher != theTreeModel.getCursor().theMatcher) {
			System.out.println("Post-parse for an operator that was not pre-parsed or has already been post-parsed!");
			return;
		}

		boolean isOnStepTarget = false;
		if(theStepTarget == theTreeModel.getCursor())
			isOnStepTarget = true;

		safe(() -> theTreeModel.finish(match));
		update(stream.getPosition(), matcher, match);

		if(isOnStepTarget && isDebugging)
			suspend(null);
		else if(!isSuspended && theStepTarget == null)
			render();
	}

	@Override
	public void matchDiscarded(final ParseMatch<? extends S> match) {
		if(EventQueue.isDispatchThread())
			return;
		safe(() -> theTreeModel.matchDiscarded(match));
	}

	@Override
	public void usedCache(final ParseMatch<? extends S> match) {
		if(match == null)
			return;
		if(EventQueue.isDispatchThread())
			return;
		safe(() -> theTreeModel.add(match));
	}

	private void reset() {
		theStream = null;
		thePosition = 0;
	}

	private void update(int position, ParseMatcher<?> op, ParseMatch<? extends S> match) {
		thePosition = position;
	}

	private void render() {
		theTreeModel.display();
		if(isPopupWhenHit && isSuspended) {
			Window window = getWindow();
			if(window != null)
				window.setVisible(true);
		}

		EventQueue.invokeLater(() -> {
			if(theStream == null)
				theMainText.setText("");
			else {
				StringBuilder sb = new StringBuilder("<html>");
				for(int c = 0; c < thePosition; c++) {
					char ch = theStream.charAt(c);
					if(ch == '<')
						sb.append("&lt;");
					else if(ch == '\n')
						sb.append("<br>");
					else if(ch == '\r') {} else if(ch == '\t')
						sb.append("&nbsp;&nbsp;&nbsp;&nbsp;");
					else
						sb.append(ch);
				}
				sb.append("<b><font color=\"red\" size=\"4\">|</font></b>");
				for(int c = thePosition; c < theStream.length(); c++) {
					char ch = theStream.charAt(c);
					if(ch == '<')
						sb.append("&lt;");
					else if(ch == '\n')
						sb.append("<br>");
					else if(ch == '\r') {} else if(ch == '\t')
						sb.append("&nbsp;&nbsp;&nbsp;&nbsp;");
					else
						sb.append(ch);
				}
				sb.append("</html>");
				int vScroll = theMainTextScroll.getVerticalScrollBar().getValue();
				int hScroll = theMainTextScroll.getHorizontalScrollBar().getValue();
				theMainText.setText(sb.toString());
				EventQueue.invokeLater(() -> {
					theMainTextScroll.getVerticalScrollBar().setValue(vScroll);
					theMainTextScroll.getHorizontalScrollBar().setValue(hScroll);
				});
			}
		});
	}

	private void setDebugOperator(ParseNode op) {
		DefaultListModel<MatcherObject> model = (DefaultListModel<MatcherObject>) theDebugPane.getModel();
		model.removeAllElements();
		while(op != null && op.theParent != null && !(op.theParent.theMatcher instanceof ReferenceMatcher))
			op = op.theParent;
		if(op != null)
			addToModel(model, op.theMatcher, 0);
	}

	private void addToModel(DefaultListModel<MatcherObject> model, ParseMatcher<?> op, int indent) {
		model.addElement(new MatcherObject(op, indent, false));
		boolean needsEnd = false;
		for(ParseMatcher<?> sub : op.getComposed()) {
			needsEnd = true;
			addToModel(model, sub, indent + 1);
		}
		if(needsEnd)
			model.addElement(new MatcherObject(op, indent, true));
	}

	private void suspend(ExpressoParserBreakpoint breakpoint) {
		theStepTarget = null;
		isSuspended = true;
		setGuiEnabled(true);
		isHolding = true;
		try {
			EventQueue.invokeLater(() -> render());
			while(isSuspended) {
				if(isDebugging) {
					resume();
					org.qommons.BreakpointHere.breakpoint();
				} else {
					try {
						Thread.sleep(50);
					} catch(InterruptedException e) {}
				}
			}
		} finally {
			isHolding = false; // Here is where to put a breakpoint in order to start java debugging from a suspended debug session
		}
	}

	private void stepOver() {
		ParseNode target = theParseTree.getSelectionPath() == null ? theTreeModel.getCursor()
			: (ParseNode) theParseTree.getSelectionPath().getLastPathComponent();
		if(target == null)
			throw new IllegalStateException("No selection or cursor!");
		theStepTarget = target.theParent;
		theStepTargetType = StepTargetType.CHILD;
		setGuiEnabled(false);
		isSuspended = false;
	}

	private void stepInto() {
		ParseNode target = theParseTree.getSelectionPath() == null ? theTreeModel.getCursor()
			: (ParseNode) theParseTree.getSelectionPath().getLastPathComponent();
		if(target == null)
			throw new IllegalStateException("No selection or cursor!");
		theStepTarget = target;
		theStepTargetType = StepTargetType.CHILD;
		setGuiEnabled(false);
		isSuspended = false;
	}

	private void stepOut() {
		ParseNode target = theParseTree.getSelectionPath() == null ? theTreeModel.getCursor()
			: (ParseNode) theParseTree.getSelectionPath().getLastPathComponent();
		if(target == null)
			throw new IllegalStateException("No selection or cursor!");
		theStepTarget = target;
		theStepTargetType = StepTargetType.EXIT;
		setGuiEnabled(false);
		isSuspended = false;
	}

	private void resume() {
		if(isSuspended) {
			theStepTarget = null;
			setGuiEnabled(false);
			isSuspended = false;
		} else {
			isSuspended = true;
			theResumeButton.setIcon(RESUME_ICON);
			if(isHolding)
				setGuiEnabled(true);
		}
	}

	private void debug() {
		isDebugging = theDebugButton.isSelected();
	}

	private void setGuiEnabled(boolean enabled) {
		if(enabled) {
			ParseNode cursor = theTreeModel.getCursor();
			theOverButton.setEnabled(cursor != null && cursor.theParent != null);
			theIntoButton.setEnabled(cursor != null && theParseTree.getSelectionPath() != null
				&& theParseTree.getSelectionPath().getLastPathComponent() == theTreeModel.getCursor() && isDescendable(cursor.theMatcher));
			theOutButton.setEnabled(cursor != null && cursor.theParent != null);
			theResumeButton.setIcon(RESUME_ICON);
		} else {
			theOverButton.setEnabled(false);
			theIntoButton.setEnabled(false);
			theOutButton.setEnabled(false);
			theResumeButton.setIcon(PAUSE_ICON);
		}
	}

	private boolean isDescendable(ParseMatcher<?> op) {
		if(op instanceof ReferenceMatcher)
			return true;
		return !op.getComposed().isEmpty();
	}

	/**
	 * Creates a frame containing the given debugger panel
	 *
	 * @param debugger The debugger to frame
	 * @return The frame containing the given debugger
	 */
	public static JFrame getDebuggerFrame(final ExpressoParserDebugGUI<?> debugger) {
		JFrame ret = new JFrame("Prisms Parser Debug");
		ret.setContentPane(debugger);
		ret.pack();
		ret.setLocationRelativeTo(null);
		debugger.readSizeConfig();
		ret.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				debugger.writeConfig();
			}

			@Override
			public void componentMoved(ComponentEvent e) {
				debugger.writeConfig();
			}
		});
		ret.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				if(debugger.isSuspended)
					debugger.resume();
			}
		});
		return ret;
	}

	/**
	 * Pops up the GUI, allowing the user to create breakpoints and configure settings outside of a parsing environment
	 *
	 * @param args If none, the default config file is used. Otherwise, the first argument is used as the location of the config file to
	 *            use.
	 */
	public static void main(String [] args) {
		try {
			javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
		} catch(Exception e) {
			System.err.println("Could not install system L&F");
			e.printStackTrace();
		}
		ExpressoParserDebugGUI<CharSequenceStream> debugger;
		if(args.length > 0)
			debugger = new ExpressoParserDebugGUI<>(new File(args[0]));
		else
			debugger = new ExpressoParserDebugGUI<>();
		JFrame frame = getDebuggerFrame(debugger);
		frame.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			}
		});

		frame.setVisible(true);
	}

	private class ParsingExpressionTreeModel implements javax.swing.tree.TreeModel {
		private ParseNode theRoot;

		private ParseNode theCursor;

		private List<TreeModelListener> theListeners = new ArrayList<>();

		private boolean isDirty;

		ParseNode getCursor() {
			return theCursor;
		}

		/** NOTE: This is only safe to call when parsing is suspended */
		void display() {
			if(isDirty)
				refresh();
		}

		void startNew(ParseMatcher<?> op) {
			ParseNode newNode = new ParseNode(theCursor, op);
			boolean newRoot = theCursor == null;
			if(newRoot)
				theRoot = newNode;
			else
				theCursor.theChildren.add(newNode);
			theCursor = newNode;

			isDirty = true;
		}

		void finish(ParseMatch<? extends S> match) {
			if(theCursor == null)
				return;
			theCursor.theMatch = match;
			ParseNode changed = theCursor;
			theCursor = theCursor.theParent;
			int childIdx = theCursor == null ? 0 : theCursor.theChildren.indexOf(changed);
			if(match == null && theCursor != null)
				theCursor.theChildren.remove(childIdx);

			isDirty = true;
		}

		void matchDiscarded(ParseMatch<? extends S> match) {
			if(theCursor == null)
				return;
			ParseNode removed = null;
			for(ParseNode child : theCursor.theChildren)
				if(child.theMatch == match) {
					removed = child;
					break;
				}
			if(removed == null)
				return;
			int childIdx = theCursor == null ? 0 : theCursor.theChildren.indexOf(removed);
			theCursor.theChildren.remove(childIdx);
			isDirty = true;
		}

		void add(ParseMatch<? extends S> match) {
			ParseNode newNode = new ParseNode(theCursor, match);
			boolean newRoot = theCursor == null;
			if(newRoot)
				theRoot = newNode;
			else
				theCursor.theChildren.add(newNode);

			isDirty = true;
		}

		void refresh() {
			TreeModelEvent evt = new TreeModelEvent(this, new Object[] {getRoot()});
			for(TreeModelListener listener : theListeners)
				listener.treeStructureChanged(evt);
			isDirty = false;
			if(theCursor != null) {
				theParseTree.setSelectionPath(new TreePath(getPath(theCursor)));
				// TODO Trying to expand the tree to the cursor. Not working yet.
				if(theCursor.theParent != null)
					theParseTree.expandPath(new TreePath(getPath(theCursor.theParent)));
			}
		}

		Object [] getPath(ParseNode node) {
			ArrayList<Object> ret = new ArrayList<>();
			do {
				ret.add(0, node);
				node = node.theParent;
			} while(node != null);
			return ret.toArray();
		}

		@Override
		public Object getRoot() {
			if(theRoot == null)
				return new ParseNode(null, new EmptyMatcher());
			return theRoot;
		}

		@Override
		public Object getChild(Object parent, int index) {
			return ((ParseNode) parent).theChildren.get(index);
		}

		@Override
		public int getChildCount(Object parent) {
			return ((ParseNode) parent).theChildren.size();
		}

		@Override
		public boolean isLeaf(Object node) {
			return getChildCount(node) == 0;
		}

		@Override
		public int getIndexOfChild(Object parent, Object child) {
			return ((ParseNode) parent).theChildren.indexOf(child);
		}

		@Override
		public void valueForPathChanged(TreePath path, Object newValue) {
		}

		@Override
		public void addTreeModelListener(TreeModelListener l) {
			theListeners.add(l);
		}

		@Override
		public void removeTreeModelListener(TreeModelListener l) {
			theListeners.remove(l);
		}
	}

	private static class EmptyMatcher implements ParseMatcher<BranchableStream<?, ?>> {
		@Override
		public String getName() {
			return "(empty)";
		}

		@Override
		public String getTypeName() {
			return "empty";
		}

		@Override
		public Map<String, String> getAttributes() {
			return java.util.Collections.EMPTY_MAP;
		}

		@Override
		public Set<String> getTags() {
			return java.util.Collections.EMPTY_SET;
		}

		@Override
		public List<ParseMatcher<? super BranchableStream<?, ?>>> getComposed() {
			return java.util.Collections.EMPTY_LIST;
		}

		@Override
		public <SS extends BranchableStream<?, ?>> ParseMatch<SS> match(SS stream, ExpressoParser<? super SS> parser, ParseSession session)
			throws IOException {
			throw new IllegalStateException("This placeholder does not do any parsing");
		}
	}

	private class ParseNode {
		final ParseNode theParent;

		ParseMatcher<?> theMatcher;

		ParseMatch<? extends S> theMatch;

		final List<ParseNode> theChildren = new ArrayList<>();

		private final String theString;

		ParseNode(ParseNode parent, ParseMatcher<?> matcher) {
			theParent = parent;
			theMatcher = matcher;
			StringBuilder str = new StringBuilder();
			str.append("<").append(matcher.getTypeName());
			if(!(matcher instanceof WhitespaceMatcher) && matcher.getName() != null)
				str.append(" name=\"").append(matcher.getName()).append('"');
			for(Map.Entry<String, String> attr : matcher.getAttributes().entrySet())
				str.append(' ').append(attr.getKey()).append("=\"").append(attr.getValue()).append('"');
			str.append(">");
			theString = str.toString();
		}

		ParseNode(ParseNode parent, ParseMatch<? extends S> match) {
			this(parent, match.getMatcher());
			theMatch = match;
		}

		@Override
		public String toString() {
			if(theMatch == null)
				return theString;
			else
				return theString + " - \"" + theMatch.flatText() + "\"";
		}
	}

	private static class MatcherObject {
		final ParseMatcher<?> theMatcher;

		final int theIndent;

		final boolean isTerminal;

		MatcherObject(ParseMatcher<?> op, int indent, boolean terminal) {
			theMatcher = op;
			theIndent = indent;
			isTerminal = terminal;
		}
	}

	private static class BreakpointEnabledRenderer extends JCheckBox implements TableCellRenderer {
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row,
			int column) {
			if(value instanceof ExpressoParserBreakpoint)
				setSelected(((ExpressoParserBreakpoint) value).isEnabled());
			else if(value instanceof Boolean)
				setSelected((Boolean) value);
			return this;
		}
	}

	private class BreakpointEnabledEditor extends javax.swing.DefaultCellEditor {
		private ExpressoParserBreakpoint theEditingBreakpoint;

		BreakpointEnabledEditor() {
			super(new JCheckBox());
		}

		@Override
		public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
			theEditingBreakpoint = (ExpressoParserBreakpoint) value;
			return super.getTableCellEditorComponent(table, theEditingBreakpoint.isEnabled(), isSelected, row, column);
		}

		@Override
		public boolean stopCellEditing() {
			boolean ret = super.stopCellEditing();
			theEditingBreakpoint.setEnabled(((JCheckBox) getComponent()).isSelected());
			breakpointChanged(theEditingBreakpoint);
			return ret;
		}

		@Override
		public Object getCellEditorValue() {
			return theEditingBreakpoint;
		}
	}

	private static class BreakpointTextRenderer extends JLabel implements TableCellRenderer {
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row,
			int column) {
			ExpressoParserBreakpoint breakpoint = (ExpressoParserBreakpoint) value;
			String pre = breakpoint.getPreCursorText() == null ? null : breakpoint.getPreCursorText().pattern();
			String post = breakpoint.getPostCursorText() == null ? null : breakpoint.getPostCursorText().pattern();
			if(pre == null && post == null) {
				setText("");
			} else {
				String blue = "<font color=\"blue\" size=\"3\">";
				StringBuilder text = new StringBuilder("<html>");
				if(pre != null)
					text.append(blue).append("(</font>").append(pre.substring(2).replaceAll("<", "&lt;")).append(blue).append(")</font>");
				text.append("<font color=\"red\" size=\"3\">.</font>");
				if(post != null)
					text.append(blue).append("(</font>").append(post.substring(0, post.length() - 2).replaceAll("<", "&lt;")).append(blue)
					.append(")</font>");
				text.append("</html>");
				setText(text.toString());
			}
			return this;
		}
	}

	private class BreakpointTextEditor extends JPanel implements TableCellEditor {
		private JTextField thePreField;

		private JTextField thePostField;

		private ExpressoParserBreakpoint theEditingBreakpoint;

		private ArrayList<CellEditorListener> theListeners;

		BreakpointTextEditor() {
			setLayout(null);
			thePreField = new JTextField();
			thePostField = new JTextField();
			add(thePreField);
			add(thePostField);
			theListeners = new ArrayList<>();
			thePreField.addKeyListener(new java.awt.event.KeyAdapter() {
				@Override
				public void keyTyped(KeyEvent e) {
					super.keyTyped(e);
					EventQueue.invokeLater(new Runnable() {
						@Override
						public void run() {
							checkPattern(thePreField);
						}
					});
				}
			});
			thePostField.addKeyListener(new java.awt.event.KeyAdapter() {
				@Override
				public void keyTyped(KeyEvent e) {
					super.keyTyped(e);
					EventQueue.invokeLater(new Runnable() {
						@Override
						public void run() {
							checkPattern(thePostField);
						}
					});
				}
			});
		}

		private void checkPattern(JTextField field) {
			try {
				Pattern.compile(field.getText());
				field.setBackground(java.awt.Color.white);
				field.setToolTipText(null);
			} catch(PatternSyntaxException e) {
				field.setBackground(java.awt.Color.red);
				field.setToolTipText(e.getMessage());
			}
		}

		@Override
		public void doLayout() {
			thePreField.setBounds(0, 0, getWidth() / 2, getHeight());
			thePostField.setBounds(getWidth() / 2 + 1, 0, getWidth() / 2, getHeight());
		}

		@Override
		public Dimension getPreferredSize() {
			Dimension ret = new Dimension(thePreField.getPreferredSize());
			ret.width *= 2;
			return ret;
		}

		@Override
		public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
			theEditingBreakpoint = (ExpressoParserBreakpoint) value;
			// Lop off the .*
			String text;
			if(theEditingBreakpoint.getPreCursorText() == null)
				text = "";
			else
				text = theEditingBreakpoint.getPreCursorText().pattern().substring(2);
			thePreField.setText(text);
			checkPattern(thePreField);
			if(theEditingBreakpoint.getPostCursorText() == null)
				text = "";
			else {
				text = theEditingBreakpoint.getPostCursorText().pattern();
				text = text.substring(0, text.length() - 2);
			}
			thePostField.setText(text);
			checkPattern(thePostField);
			return this;
		}

		@Override
		public Object getCellEditorValue() {
			return theEditingBreakpoint;
		}

		@Override
		public boolean isCellEditable(EventObject anEvent) {
			return true;
		}

		@Override
		public boolean shouldSelectCell(EventObject anEvent) {
			return true;
		}

		@Override
		public boolean stopCellEditing() {
			Pattern prePattern;
			Pattern postPattern;
			try {
				prePattern = thePreField.getText().length() == 0 ? null : Pattern.compile(".*" + thePreField.getText(), Pattern.DOTALL);
				postPattern = thePostField.getText().length() == 0 ? null : Pattern.compile(thePostField.getText() + ".*", Pattern.DOTALL);
			} catch(PatternSyntaxException e) {
				return false;
			}
			theEditingBreakpoint.setPreCursorText(prePattern);
			theEditingBreakpoint.setPostCursorText(postPattern);
			breakpointChanged(theEditingBreakpoint);

			ChangeEvent evt = new ChangeEvent(this);
			for(CellEditorListener listener : theListeners.toArray(new CellEditorListener[0]))
				listener.editingStopped(evt);
			return true;
		}

		@Override
		public void cancelCellEditing() {
			ChangeEvent evt = new ChangeEvent(this);
			for(CellEditorListener listener : theListeners.toArray(new CellEditorListener[0]))
				listener.editingCanceled(evt);
		}

		@Override
		public void addCellEditorListener(CellEditorListener l) {
			theListeners.add(l);
		}

		@Override
		public void removeCellEditorListener(CellEditorListener l) {
			theListeners.remove(l);
		}
	}

	private static class BreakpointOpRenderer extends javax.swing.table.DefaultTableCellRenderer {
		private final String NONE = "(any)";

		private Color theBG = getBackground();

		private Color theFG = getForeground();

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row,
			int column) {
			ExpressoParserBreakpoint breakpoint = (ExpressoParserBreakpoint) value;
			super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			if(breakpoint.getMatcherName() == null)
				setText(NONE);
			else
				setText(breakpoint.getMatcherName());
			setBackground(theBG);
			setForeground(theFG);
			return this;
		}
	}

	private class BreakpointOpEditor extends javax.swing.DefaultCellEditor {
		private final String NONE = "(any)";

		private ExpressoParserBreakpoint theEditingBreakpoint;

		BreakpointOpEditor() {
			super(new JComboBox<String>());
		}

		@Override
		public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
			Component ret = super.getTableCellEditorComponent(table, value, isSelected, row, column);
			theEditingBreakpoint = (ExpressoParserBreakpoint) value;
			javax.swing.DefaultComboBoxModel<String> model = (javax.swing.DefaultComboBoxModel<String>) ((JComboBox<String>) getComponent())
				.getModel();
			model.removeAllElements();
			model.addElement(NONE);
			for(String opName : theOpNames)
				model.addElement(opName);
			model.setSelectedItem(theEditingBreakpoint.getMatcherName() == null ? NONE : theEditingBreakpoint.getMatcherName());
			return ret;
		}

		@Override
		public boolean stopCellEditing() {
			String opName = (String) ((JComboBox<String>) getComponent()).getSelectedItem();
			if(opName == null || opName.equals(NONE))
				theEditingBreakpoint.setMatcherName(null);
			else
				theEditingBreakpoint.setMatcherName(opName);
			breakpointChanged(theEditingBreakpoint);
			return super.stopCellEditing();
		}

		@Override
		public Object getCellEditorValue() {
			return theEditingBreakpoint;
		}
	}

	private static class BreakpointDeleteRenderer extends javax.swing.table.DefaultTableCellRenderer {
		static ImageIcon ICON = ExpressoParserDebugGUI.getIcon("delete.png", 16, 16);

		private Color theBG;

		BreakpointDeleteRenderer() {
			setText("");
			setIcon(ICON);
			theBG = getBackground();
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row,
			int column) {
			super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			setText("");
			setIcon(ICON);
			setBackground(theBG);
			return this;
		}
	}

	private class BreakpointDeleteEditor extends JPanel implements TableCellEditor {
		private ArrayList<CellEditorListener> theListeners = new ArrayList<>();

		@Override
		public Object getCellEditorValue() {
			return null;
		}

		@Override
		public boolean isCellEditable(EventObject anEvent) {
			return true;
		}

		@Override
		public boolean shouldSelectCell(EventObject anEvent) {
			return true;
		}

		@Override
		public boolean stopCellEditing() {
			ChangeEvent evt = new ChangeEvent(this);
			for(CellEditorListener listener : theListeners.toArray(new CellEditorListener[0]))
				listener.editingStopped(evt);
			return true;
		}

		@Override
		public void cancelCellEditing() {
			ChangeEvent evt = new ChangeEvent(this);
			for(CellEditorListener listener : theListeners.toArray(new CellEditorListener[0]))
				listener.editingCanceled(evt);
		}

		@Override
		public void addCellEditorListener(CellEditorListener l) {
			theListeners.add(l);
		}

		@Override
		public void removeCellEditorListener(CellEditorListener l) {
			theListeners.remove(l);
		}

		@Override
		public Component getTableCellEditorComponent(JTable table, final Object value, boolean isSelected, int row, int column) {
			EventQueue.invokeLater(new Runnable() {
				@Override
				public void run() {
					stopCellEditing();
					breakpointDeleted((ExpressoParserBreakpoint) value);
				}
			});
			return this;
		}
	}

	private class MatcherRenderer extends JPanel implements javax.swing.ListCellRenderer<MatcherObject> {
		private JLabel theLabel;

		MatcherRenderer() {
			setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
			theLabel = new JLabel();
			add(theLabel);
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends MatcherObject> list, MatcherObject value, int index,
			boolean isSelected, boolean cellHasFocus) {
			StringBuilder text = new StringBuilder("<html>");
			for(int i = 0; i < value.theIndent; i++)
				text.append("&nbsp;&nbsp;&nbsp;&nbsp;");
			text.append("<font color=\"blue\">&lt;</font><font color=\"red\">");
			if(value.isTerminal)
				text.append('/');
			text.append(value.theMatcher.getTypeName()).append("</font>");
			if(!value.isTerminal) {
				if(!(value.theMatcher instanceof WhitespaceMatcher) && value.theMatcher.getName() != null)
					text.append(" name=\"").append(value.theMatcher.getName()).append('"');
				for(Map.Entry<String, String> attr : value.theMatcher.getAttributes().entrySet())
					text.append(' ').append(attr.getKey()).append("=\"").append(attr.getValue()).append('"');
				boolean needsEnding = value.theMatcher.getComposed().isEmpty();
				if(value.theMatcher instanceof org.expresso.parse.impl.SimpleValueMatcher) {
					text.append("<font color=\"blue\">&gt;</font>");
					text.append(((org.expresso.parse.impl.SimpleValueMatcher<?>) value.theMatcher).getValueString()
						.replaceAll("<", "&amp;lt;").replaceAll(">", "&amp;gt;"));
					if(needsEnding) {
						text.append("<font color=\"blue\">&lt;/</font><font color=\"red\">");
						text.append(value.theMatcher.getTypeName()).append("</font>");
					}
				} else if(needsEnding)
					text.append(" <font color=\"blue\">/</font>");
			}
			text.append("<font color=\"blue\">&gt;</font>");
			text.append("</html>");
			theLabel.setText(text.toString());

			Color bg = Color.white;
			boolean cursor = theTreeModel.getCursor() != null && theTreeModel.getCursor().theMatcher == value.theMatcher;
			if(cursor)
				cursor = (theTreeModel.getCursor().theMatch != null) == value.isTerminal;
			boolean selected = !value.isTerminal && theParseTree.getSelectionPath() != null
				&& ((ParseNode) theParseTree.getSelectionPath().getLastPathComponent()).theMatcher == value.theMatcher;
			if(cursor && selected)
				bg = new Color(0, 255, 255);
			else if(cursor)
				bg = Color.green;
			else if(selected)
				bg = SELECTED;
			setBackground(bg);
			return this;
		}
	}
}
