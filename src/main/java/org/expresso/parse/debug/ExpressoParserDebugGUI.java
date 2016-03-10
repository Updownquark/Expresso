package org.expresso.parse.debug;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EventObject;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.swing.BoundedRangeModel;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.BadLocationException;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;

import org.expresso.parse.BranchableStream;
import org.expresso.parse.ExpressoParser;
import org.expresso.parse.ParseMatch;
import org.expresso.parse.ParseMatcher;
import org.expresso.parse.ParseSession;
import org.expresso.parse.impl.CharSequenceStream;
import org.expresso.parse.matchers.ReferenceMatcher;
import org.expresso.parse.matchers.SimpleValueMatcher;
import org.expresso.parse.matchers.WhitespaceMatcher;
import org.qommons.ArrayUtils;
import org.qommons.config.MutableConfig;
import org.qommons.config.QommonsConfig;
import org.qommons.ex.ExIterable;

/**
 * A graphical debugger for the {@link ExpressoParser}
 *
 * @param <S> The sub-type of stream to parse
 */
public class ExpressoParserDebugGUI<S extends CharSequenceStream> extends JPanel
implements org.expresso.parse.debug.ExpressoParsingDebugger<S> {
	enum StepTargetType {
		EXIT, CHILD, DESCENDANT
	}

	private static final Color SELECTED = new Color(100, 100, 255);
	private static final ImageIcon RESUME_ICON = getIcon("play.png", 24, 24);
	private static final ImageIcon PAUSE_ICON = getIcon("pause.png", 24, 24);
	private static final long REFRESH_INTERVAL = 1000;

	private static final String OVER_TEXT = "Skip to the next matcher at the current level";
	private static final String INTO_TEXT = "Descend into the components of the current matcher";
	private static final String OUT_TEXT = "Skip out to the completion of the current matcher's parent";
	private static final String RESUME_TEXT = "Allow parsing to continue until the next breakpoint condition";
	private static final String RUN_TO_TEXT = "Parse until the selected matcher";
	private static final String DEBUG_MODE_TEXT = "Enter deep debug mode: catch the breakpoint in the java debugger and ignore all breakpoints";

	private JTextPane theMainText;
	private JScrollPane theMainTextScroll;

	private ParsingExpressionTreeModel theInternalTreeModel;
	private ParsingExpressionTreeModel theDisplayedTreeModel;
	private JTree theParseTree;

	private JScrollPane theTreeScroll;
	private JTable theBreakpointList;
	private JList<MatcherObject> theDebugPane;

	private JScrollPane theDebugPaneScroll;

	private JButton theOverButton;
	private JButton theIntoButton;
	private JButton theOutButton;
	private JButton theResumeButton;

	private JButton theRunToButton;
	private JToggleButton theDebugButton;
	private JLabel theAddBreakpointLabel;

	private JSplitPane theMainSplit;
	private JSplitPane theBottomSplit;
	private JSplitPane theRightSplit;

	private File theConfigFile;

	private ExpressoParser<?> theParser;
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

	private LinkedList<ParseMatcher<?>> theStepTargetDescendant;
	private boolean isDebugging;
	private int theLastBreakIndex;
	private long theLastRefresh;

	private boolean theCallbackLock;

	private java.util.Set<ExpressoParserBreakpoint> theIndexBreakpoints;

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
		theMainText.getCaret().setVisible(true);
		theMainText.getCaret().setSelectionVisible(true);
		theInternalTreeModel = new ParsingExpressionTreeModel();
		theDisplayedTreeModel = new ParsingExpressionTreeModel();
		theParseTree = new JTree(theDisplayedTreeModel);
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
		theParseTree.setCellRenderer(new ParseNodeCellRenderer());
		theOverButton = new JButton(getIcon("arrow180.png", 24, 24));
		theOverButton.setToolTipText(OVER_TEXT);
		theIntoButton = new JButton(getIcon("arrow90down.png", 24, 24));
		theIntoButton.setToolTipText(INTO_TEXT);
		theOutButton = new JButton(getIcon("arrow90right.png", 24, 24));
		theOutButton.setToolTipText(OUT_TEXT);
		theResumeButton = new JButton(RESUME_ICON);
		theResumeButton.setToolTipText(RESUME_TEXT);
		// DON'T ERASE: Sprint icon made by freepik from www.flaticon.com
		theRunToButton = new JButton(getIcon("sprint.png", 24, 24));
		theRunToButton.setToolTipText(RUN_TO_TEXT);
		theDebugButton = new JToggleButton(getIcon("bug.png", 24, 24));
		theDebugButton.setToolTipText(DEBUG_MODE_TEXT);
		theAddBreakpointLabel = new JLabel(getIcon("bluePlus.png", 16, 16));
		theAddBreakpointLabel.setToolTipText("Create a new breakpoint to watch for");
		theDebugPane = new JList<>(new DefaultListModel<MatcherObject>());
		theDebugPane.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		theDebugPane.setCellRenderer(new MatcherRenderer());
		theDebugPane.addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				if(e.getValueIsAdjusting() || theCallbackLock)
					return;
				theRunToButton.setEnabled(canRunTo(theInternalTreeModel.getCursor()));
				theDebugPane.repaint();
			}
		});
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
		theMainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		theBottomSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		theRightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

		theBreakpoints = new java.util.concurrent.CopyOnWriteArrayList<>();

		theMainTextScroll = new JScrollPane(theMainText);
		theMainTextScroll.setPreferredSize(new Dimension(100, 200));
		add(theMainSplit, BorderLayout.CENTER);
		theMainSplit.setTopComponent(theMainTextScroll);
		theMainSplit.setBottomComponent(theBottomSplit);
		theTreeScroll = new JScrollPane(theParseTree);
		theTreeScroll.setPreferredSize(new Dimension(450, 400));
		theBottomSplit.setLeftComponent(theTreeScroll);
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
		buttonPanel.add(theOverButton);
		buttonPanel.add(theIntoButton);
		buttonPanel.add(theOutButton);
		buttonPanel.add(theResumeButton);
		buttonPanel.add(theRunToButton);
		buttonPanel.add(theDebugButton);
		JPanel rightPanel = new JPanel(new BorderLayout());
		theBottomSplit.setRightComponent(rightPanel);
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
		theDebugPaneScroll = new JScrollPane(theDebugPane);
		theRightSplit.setTopComponent(theDebugPaneScroll);

		theOpNames = new ArrayList<>();

		PropertyChangeListener divLocPCL = e -> writeConfig();
		theMainSplit.addPropertyChangeListener(JSplitPane.LAST_DIVIDER_LOCATION_PROPERTY, divLocPCL);
		theBottomSplit.addPropertyChangeListener(JSplitPane.LAST_DIVIDER_LOCATION_PROPERTY, divLocPCL);
		theRightSplit.addPropertyChangeListener(JSplitPane.LAST_DIVIDER_LOCATION_PROPERTY, divLocPCL);

		theAddBreakpointLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				addBreakpoint();
			}
		});
		theOverButton.addActionListener(e -> stepOver());
		theIntoButton.addActionListener(e -> stepInto());
		theOutButton.addActionListener(e -> stepOut());
		theResumeButton.addActionListener(e -> suspendOrResume());
		theRunToButton.addActionListener(e -> runTo());
		theDebugButton.addActionListener(e -> debug());

		theLastBreakIndex = -1;
		theIndexBreakpoints = new java.util.HashSet<>();
		theStepTargetDescendant = new LinkedList<>();

		reset();
		readConfig();
	}

	@Override
	public void display() {
		setGuiEnabled(true);
		isHolding = true;
		safe(() -> render());
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
		theCallbackLock = true;
		try {
			MutableConfig config = getConfig();
			Window window = getWindow();
			if (window != null) {
				window.setBounds(config.getInt("x", window.getX()), config.getInt("y", window.getY()),
						config.getInt("w", window.getWidth()), config.getInt("h", window.getHeight()));
			}
			if (getWidth() > 0) {
				theMainSplit.setDividerLocation(config.getFloat("main-split", .5f));
				theBottomSplit.setDividerLocation(config.getFloat("bottom-split", .5f));
				theRightSplit.setDividerLocation(config.getFloat("right-split", .5f));
			}
		} finally {
			theCallbackLock = false;
		}
	}

	private void readConfig() {
		theCallbackLock = true;
		try {
			MutableConfig config = getConfig();
			if (config.subConfig("breakpoints") != null) {
				for (MutableConfig breakpointConfig : config.subConfig("breakpoints").subConfigs("breakpoint")) {
					ExpressoParserBreakpoint breakpoint = new ExpressoParserBreakpoint();
					breakpoint.setPreCursorText(breakpointConfig.get("pre") == null ? null
							: Pattern.compile(".*" + breakpointConfig.get("pre"), Pattern.DOTALL));
					breakpoint.setPostCursorText(breakpointConfig.get("post") == null ? null
							: Pattern.compile(breakpointConfig.get("post"), Pattern.DOTALL));
					breakpoint.setMatcherName(breakpointConfig.get("operator"));
					breakpoint.setEnabled(breakpointConfig.is("enabled", true));
					theBreakpoints.add(breakpoint);
					((javax.swing.table.DefaultTableModel) theBreakpointList.getModel())
					.addRow(new Object[] { breakpoint, breakpoint, breakpoint, breakpoint });
				}
			}
		} finally {
			theCallbackLock = false;
		}
	}

	private void writeConfig() {
		if (theCallbackLock)
			return;
		MutableConfig config = getConfig();
		Window window = getWindow();
		if (window == null || !window.isVisible())
			return;
		config.set("x", "" + window.getX());
		config.set("y", "" + window.getY());
		config.set("w", "" + window.getWidth());
		config.set("h", "" + window.getHeight());
		if(getWidth() > 0) {
			config.set("main-split", "" + (theMainSplit.getDividerLocation() * 1.0f / theMainSplit.getHeight()));
			config.set("bottom-split", "" + (theBottomSplit.getDividerLocation() * 1.0f / theBottomSplit.getWidth()));
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
		theParser = parser;
		theOpNames.clear();
		for(ParseMatcher<?> op : parser.getComposed())
			theOpNames.add(op.getName());
		reset();
	}

	@Override
	public void start(S text) {
		if(EventQueue.isDispatchThread())
			throw new IllegalStateException("Parser debugging on the AWT event thread is not allowed");
		theStream = text;
	}

	@Override
	public void end(ParseMatch<? extends S> bestMatch) {
		theStream = null;
	}

	@Override
	public void fail(S stream, ParseMatch<? extends S> match) {
		theStream = null;
	}

	@Override
	public void preParse(S stream, MatchData<? extends S> match) {
		if(theLastBreakIndex != stream.getPosition()) {
			theLastBreakIndex = -1;
			theIndexBreakpoints.clear();
		}
		if(match.getMatcher().getTags().contains(ExpressoParser.IGNORABLE)) {
			inIgnorable++;
			return;
		} else if(inIgnorable > 0)
			return;
		System.out.println("pre " + match);
		boolean isOnStepTarget = false;
		ParseNode cursor = theInternalTreeModel.getCursor();
		if(theStepTarget == null || theStepTargetType == StepTargetType.EXIT) {}
		else if(theStepTarget == cursor)
			isOnStepTarget = true;
		else {
			if(cursor.isFinished) {
				theInternalTreeModel.ascend();
				cursor = cursor.theParent;
			}
			if(cursor == theStepTarget)
				isOnStepTarget = true;
		}

		theInternalTreeModel.startNew(match, stream);
		cursor = theInternalTreeModel.getCursor();
		if(isOnStepTarget && theStepTargetType == StepTargetType.DESCENDANT && !theStepTargetDescendant.isEmpty()) {
			isOnStepTarget = false; //Not on the target yet
			if (cursor.theMatch.getMatcher() == theStepTargetDescendant.getFirst()) {
				//We're one level closer to the target
				theStepTarget = cursor;
				theStepTargetDescendant.removeFirst();
			}
		}
		int position = stream.getPosition();
		update(position);

		boolean didSuspend = false;
		if (isDebugging) {
		} else if (isSuspended || isOnStepTarget) {
			suspend(null);
			didSuspend = true;
		} else if (theStepTarget == null) {
			CharSequence pre = theStream.subSequence(0, position);
			CharSequence post = stream;
			for(ExpressoParserBreakpoint breakpoint : theBreakpoints) {
				if(!breakpoint.isEnabled() || theIndexBreakpoints.contains(breakpoint))
					continue;
				if(breakpoint.getPreCursorText() == null && breakpoint.getPostCursorText() == null && breakpoint.getMatcherName() == null)
					continue;
				if(breakpoint.getPreCursorText() != null && !breakpoint.getPreCursorText().matcher(pre).matches())
					continue;
				if (breakpoint.getPostCursorText() != null && !breakpoint.getPostCursorText().matcher(post).lookingAt())
					continue;
				if (breakpoint.getMatcherName() != null && !breakpoint.getMatcherName().equals(match.getMatcher().getName()))
					continue;
				theLastBreakIndex = position;
				theIndexBreakpoints.add(breakpoint);
				suspend(breakpoint);
				didSuspend = true;
				break;
			}
		}
		if(isDebugging || (!didSuspend && System.currentTimeMillis() - theLastRefresh >= REFRESH_INTERVAL))
			safe(() -> render());
	}

	private void safe(Runnable run) {
		if(EventQueue.isDispatchThread())
			run.run();
		else
			try {
				EventQueue.invokeAndWait(run);
			} catch(InvocationTargetException | InterruptedException e) {
				throw new IllegalStateException(e);
			}
	}

	@Override
	public void postParse(S stream, MatchData<? extends S> match) {
		if (match.getMatcher().getTags().contains(ExpressoParser.IGNORABLE)) {
			inIgnorable--;
			return;
		} else if(inIgnorable > 0)
			return;
		System.out.println("post " + match);
		ParseNode cursor = theInternalTreeModel.getCursor();
		if (cursor == null) {
			System.out.println("Post-parse for an operator that was not pre-parsed or has already been post-parsed! " + match.getMatcher());
			return;
		}
		if (match.getMatcher() != cursor.theMatch.getMatcher()) {
			if (theInternalTreeModel.getCursor().theParent != null
					&& match.getMatcher() == theInternalTreeModel.getCursor().theParent.theMatch.getMatcher())
				theInternalTreeModel.ascend();
			else {
				System.out.println(
						"Post-parse for an operator that was not pre-parsed or has already been post-parsed! " + match.getMatcher());
				return;
			}
		}

		boolean isOnStepTarget = false;
		if(theStepTarget == theInternalTreeModel.getCursor())
			isOnStepTarget = true;

		theInternalTreeModel.finish(match);
		update(stream.getPosition());

		if(isOnStepTarget && !isDebugging)
			suspend(null);
		else if(isDebugging || System.currentTimeMillis() - theLastRefresh >= REFRESH_INTERVAL)
			safe(() -> render());
	}

	private void reset() {
		theStream = null;
		thePosition = 0;
	}

	private void update(int position) {
		thePosition = position;
	}

	private void render() {
		theLastRefresh = System.currentTimeMillis();
		if(theInternalTreeModel.isDirty) {
			int preVScroll = theTreeScroll.getVerticalScrollBar().getValue();
			int preHScroll = theTreeScroll.getHorizontalScrollBar().getValue();
			theInternalTreeModel.isDirty = false;
			theInternalTreeModel.sync(theDisplayedTreeModel);
			theDisplayedTreeModel.syncSelection();

			if(theParseTree.getSelectionPath() != null) {
				Rectangle bounds = theParseTree.getPathBounds(theParseTree.getSelectionPath());
				if(bounds != null) {
					EventQueue.invokeLater(() -> {
						JScrollBar vBar = theTreeScroll.getVerticalScrollBar();
						JScrollBar hBar = theTreeScroll.getHorizontalScrollBar();
						int vScroll = adjustScroll(preVScroll, vBar.getModel(), bounds.getMinY(), bounds.getMaxY(),
								theParseTree.getHeight());
						int hScroll = adjustScroll(preHScroll, hBar.getModel(), bounds.getMinX(), bounds.getMaxX(),
								theParseTree.getWidth());
						vBar.setValue(vScroll);
						hBar.setValue(hScroll);
					});
				}
			}
		}
		if(isPopupWhenHit && isSuspended) {
			Window window = getWindow();
			if (window != null && !window.isVisible()) {
				theCallbackLock = true; // Prevent writing the config just for displaying the frame
				window.setVisible(true);
				EventQueue.invokeLater(() -> theCallbackLock = false);
			}
		}

		if(theStream == null)
			theMainText.setText("");
		else {
			ParseNode selected;
			if (theParseTree.getSelectionPath() != null)
				selected = (ExpressoParserDebugGUI<S>.ParseNode) theParseTree.getSelectionPath().getLastPathComponent();
			else if (theInternalTreeModel.getCursor() != null)
				selected = theInternalTreeModel.getCursor();
			else
				selected = null;
			int streamPos;
			if (selected != null)
				streamPos = selected.theMatch == null ? thePosition : selected.theStreamCapture.getPosition();
			else
				streamPos = thePosition;
			int preVScroll = theMainTextScroll.getVerticalScrollBar().getValue();
			int preHScroll = theMainTextScroll.getHorizontalScrollBar().getValue();
			int textPos = 1;
			// The numbers for textPos were determined experimentally. Basically only content counts.
			StringBuilder sb = new StringBuilder("<html>");
			for (int c = 0; c < streamPos; c++) {
				char ch = theStream.charAt(c);
				if (ch == '<') {
					sb.append("&lt;");
					textPos++;
				} else if (ch == '\n') {
					sb.append("<br>");
					textPos++;
				} else if (ch == '\r') {
				} else if (ch == '\t') {
					sb.append("&nbsp;&nbsp;&nbsp;&nbsp;");
					textPos += 4;
				} else if (ch == ' ') {
					sb.append("&nbsp;");
					textPos++;
				} else {
					sb.append(ch);
					textPos++;
				}
			}
			sb.append("<b><font color=\"red\" weight=\"bold\" size=\"4\">\u25BA</font></b>");
			textPos++;
			for (int c = streamPos; c < theStream.length(); c++) {
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
			theMainText.setText(sb.toString());
			Rectangle bounds;
			try {
				bounds = theMainText.modelToView(textPos);
			} catch (BadLocationException e) {
				bounds = null;
			}
			if (bounds != null) {
				Rectangle visBounds = bounds;
				EventQueue.invokeLater(() -> {
					int vScroll = preVScroll;
					int hScroll = preHScroll;
					JScrollBar vBar = theMainTextScroll.getVerticalScrollBar();
					JScrollBar hBar = theMainTextScroll.getHorizontalScrollBar();
					vScroll = adjustScroll(vScroll, vBar.getModel(), visBounds.getMinY(), visBounds.getMaxY(), theMainText.getHeight());
					hScroll = adjustScroll(hScroll, hBar.getModel(), visBounds.getMinX(), visBounds.getMaxX(), theMainText.getWidth());
					vBar.setValue(vScroll);
					hBar.setValue(hScroll);
				});
			}
		}
	}

	private int adjustScroll(int scroll, BoundedRangeModel model, double min, double max, int size) {
		int modelRange = model.getMaximum() - model.getMinimum();
		int buffer = (int) ((max - min) / size * modelRange * 3); // 3x buffer
		double minNeeded = min / size;
		double minDisplayed = scroll * 1.0 / modelRange;
		if (minDisplayed > minNeeded)
			return model.getMinimum() + (int) (minNeeded * modelRange) - buffer;

		double maxNeeded = max / size;
		double maxDisplayed = (scroll + model.getExtent()) * 1.0 / modelRange;
		if (maxDisplayed < maxNeeded) {
			int ret = (int) (maxNeeded * modelRange) - model.getExtent() + buffer;
			minDisplayed = ret * 1.0 / modelRange;
			if (minDisplayed > minNeeded)
				return model.getMinimum() + (int) (minNeeded * modelRange) - buffer;
			else
				return model.getMinimum() + ret;
		}
		return scroll;
	}

	private void setDebugOperator(ParseNode op) {
		int preVScroll = theDebugPaneScroll.getVerticalScrollBar().getValue();
		int preHScroll = theDebugPaneScroll.getHorizontalScrollBar().getValue();
		List<MatcherObject> newModel = new ArrayList<>();
		if (op == null) {
		} else if (op.theParent != null && op.theParent.theMatch.getMatcher() instanceof ReferenceMatcher) {
			int pastCursor = -1;
			boolean allCached = true;
			Set<String> cached = new java.util.LinkedHashSet<>();
			ParseNode cursor = theInternalTreeModel.theCursor;
			boolean sameParent;
			if (cursor.theParent == null)
				sameParent = op.theParent == null;
			else if (op.theParent == null)
				sameParent = false;
			else
				sameParent = cursor.theParent.theMatch == op.theParent.theMatch;
			if (!sameParent) {
				allCached = false;
			} else {
				for (ParseMatcher<?> m : ((ReferenceMatcher<?>) op.theParent.theMatch.getMatcher()).getReference(theParser,
						op.theParent.theMatch.getSession())) {
					if (pastCursor < 0 && m == cursor.theMatch.getMatcher())
						pastCursor = 0;
					boolean mCached;
					if (pastCursor < 0 || (pastCursor == 0 && cursor.isFinished)) {
						mCached = cursor.theMatch.isCached();
						if (!mCached) {
							for (ParseNode node : cursor.theParent.theChildren) {
								if (node.theMatch.getMatcher() == m) {
									mCached = node.theMatch.isCached();
									break;
								} else if (node == cursor)
									break;
							}
						}
					} else if (op.theMatch.getSession() != null && op.theParent != null && op.theParent.theStreamCapture != null)
						mCached = op.theMatch.getSession().isCached(op.theMatch.getMatcher().getName(), op.theParent.theStreamCapture);
					else
						mCached = false;

					if(mCached)
						cached.add(m.getName());
					else
						allCached = false;
				}
			}
			MatcherObject refObj = addToModel(newModel, null, op.theParent.theMatch.getMatcher(), 0, false, allCached);
			for (ParseMatcher<?> matcher : ((ReferenceMatcher<?>) op.theParent.theMatch.getMatcher()).getReference(theParser,
					op.theParent.theMatch.getSession()))
				addToModel(newModel, refObj, matcher, 1, false, cached.contains(matcher.getName()));
			addEndToModel(newModel, null, op.theParent.theMatch.getMatcher(), 0);
		} else {
			while (op.theParent != null && !(op.theParent.theMatch.getMatcher() instanceof ReferenceMatcher))
				op = op.theParent;
			addToModel(newModel, null, op.theMatch.getMatcher(), 0, true, op.theMatch.isCached());
		}

		DefaultListModel<MatcherObject> model = (DefaultListModel<MatcherObject>) theDebugPane.getModel();
		boolean changed = model.getSize() != newModel.size();
		if(!changed) {
			for(int i = 0; i < newModel.size(); i++) {
				if(model.getElementAt(i).theMatcher != newModel.get(i).theMatcher) {
					changed = true;
					break;
				} else
					model.set(i, newModel.get(i));
			}
		}
		if(changed) {
			model.removeAllElements();
			for(MatcherObject obj : newModel)
				model.addElement(obj);
		}

		// Adjust the scroll to ensure the cursor location is visible in the list
		for(int i = 0; i < theDebugPane.getModel().getSize(); i++) {
			MatcherObject o = theDebugPane.getModel().getElementAt(i);
			boolean selected = !o.isTerminal && theParseTree.getSelectionPath() != null
					&& ((ParseNode) theParseTree.getSelectionPath().getLastPathComponent()).theMatch.getMatcher() == o.theMatcher;
			if(selected) {
				Rectangle bounds = theDebugPane.getCellBounds(i, i);
				JScrollBar vBar = theDebugPaneScroll.getVerticalScrollBar();
				JScrollBar hBar = theDebugPaneScroll.getHorizontalScrollBar();

				EventQueue.invokeLater(() -> {
					int vScroll = adjustScroll(preVScroll, vBar.getModel(), bounds.getMinY(), bounds.getMaxY(), theDebugPane.getHeight());
					int hScroll = adjustScroll(preHScroll, hBar.getModel(), bounds.getMinX(), bounds.getMaxX(), theDebugPane.getWidth());
					vBar.setValue(vScroll);
					hBar.setValue(hScroll);
				});
			}
		}
	}

	private MatcherObject addToModel(List<MatcherObject> model, MatcherObject parent, ParseMatcher<?> op, int indent,
			boolean withChildren, boolean cached) {
		MatcherObject newObj = new MatcherObject(parent, op, indent, cached, false);
		model.add(newObj);
		boolean needsEnd = false;
		if(withChildren) {
			for(ParseMatcher<?> sub : op.getComposed()) {
				needsEnd = true;
				addToModel(model, newObj, sub, indent + 1, true, false);
			}
		}
		if(needsEnd)
			addEndToModel(model, parent, op, indent);
		return newObj;
	}

	private void addEndToModel(List<MatcherObject> model, MatcherObject parent, ParseMatcher<?> op, int indent) {
		model.add(new MatcherObject(parent, op, indent, false, true));
	}

	private void suspend(ExpressoParserBreakpoint breakpoint) {
		theStepTarget = null;
		theStepTargetDescendant.clear();
		isSuspended = true;
		setGuiEnabled(true);
		isHolding = true;
		try {
			safe(() -> render());
			while(isSuspended) {
				if(isDebugging) {
					suspendOrResume();
					org.qommons.BreakpointHere.breakpoint();
				} else {
					try {
						Thread.sleep(50);
					} catch(InterruptedException e) {}
				}
			}
		} finally {
			isHolding = false;
		}
	}

	private void stepOver() {
		ParseNode target = getInternalSelection();
		if(target == null)
			throw new IllegalStateException("No selection or cursor!");
		theStepTarget = target.theParent;
		theStepTargetType = StepTargetType.CHILD;
		setGuiEnabled(false);
		isSuspended = false;
		theLastRefresh = System.currentTimeMillis();
	}

	private void stepInto() {
		ParseNode target = getInternalSelection();
		if(target == null)
			throw new IllegalStateException("No selection or cursor!");
		theStepTarget = target;
		theStepTargetType = StepTargetType.CHILD;
		setGuiEnabled(false);
		isSuspended = false;
		theLastRefresh = System.currentTimeMillis();
	}

	private void stepOut() {
		ParseNode target = getInternalSelection();
		if(target == null)
			throw new IllegalStateException("No selection or cursor!");
		theStepTarget = target.theParent;
		theStepTargetType = StepTargetType.EXIT;
		setGuiEnabled(false);
		isSuspended = false;
		theLastRefresh = System.currentTimeMillis();
	}

	private void runTo(){
		ParseNode target = getInternalSelection();
		if(target == null)
			throw new IllegalStateException("No selection or cursor!");
		theStepTarget = target.theParent;
		theStepTargetType = StepTargetType.DESCENDANT;
		theStepTargetDescendant.clear();
		MatcherObject listSelection = theDebugPane.getSelectedValue();
		while (listSelection != null && listSelection.theMatcher != target.theParent.theMatch.getMatcher()) {
			theStepTargetDescendant.addFirst(listSelection.theMatcher);
			listSelection = listSelection.theParent;
		}
		setGuiEnabled(false);
		isSuspended = false;
		theLastRefresh = System.currentTimeMillis();
	}

	private void suspendOrResume() {
		if(isSuspended) {
			theStepTarget = null;
			setGuiEnabled(false);
			isSuspended = false;
			theLastRefresh = System.currentTimeMillis();
		} else {
			isSuspended = true;
			theResumeButton.setIcon(RESUME_ICON);
			if(isHolding)
				setGuiEnabled(true);
		}
	}

	private void debug() {
		isDebugging = theDebugButton.isSelected();
		if(theDebugButton.isSelected())
			theDebugButton.setToolTipText("Exit deep debug mode: watch for breakpoints");
		else
			theDebugButton.setToolTipText("Enter deep debug mode: catch the breakpoint in the java debugger and ignore all breakpoints");
	}

	private void setGuiEnabled(boolean enabled) {
		theOverButton.setToolTipText(OVER_TEXT);
		theIntoButton.setToolTipText(INTO_TEXT);
		theOutButton.setToolTipText(OUT_TEXT);
		theRunToButton.setToolTipText(RUN_TO_TEXT);
		theResumeButton.setToolTipText(RESUME_TEXT);
		if(enabled) {
			safe(() -> render());
			ParseNode cursor = theInternalTreeModel.getCursor();
			theOverButton.setEnabled(canStepOver(cursor));
			theIntoButton.setEnabled(canStepInto(cursor));
			theOutButton.setEnabled(canStepOut(cursor));
			theRunToButton.setEnabled(canRunTo(cursor));
			theResumeButton.setIcon(RESUME_ICON);
			theResumeButton.setToolTipText("Allow parsing to continue until the next breakpoint condition");
		} else {
			theOverButton.setEnabled(false);
			theIntoButton.setEnabled(false);
			theOutButton.setEnabled(false);
			theResumeButton.setIcon(PAUSE_ICON);
			theRunToButton.setEnabled(false);
			theResumeButton.setToolTipText("Suspend parsing when the next matcher is started or completed");
		}
	}

	private boolean canStepOver(ParseNode cursor) {
		return isSteppable(cursor);
	}

	private boolean canStepOut(ParseNode cursor) {
		return isSteppable(cursor) && cursor.theParent != null;
	}

	private boolean canStepInto(ParseNode cursor) {
		if (!isSteppable(cursor)) {
			return false;
		}
		ParseNode selected = getInternalSelection();
		if (cursor != selected) {
			theIntoButton.setToolTipText("Descent only possible when the selected matcher is the current matcher being parsed");
			return false;
		}
		if (selected.isFinished) {
			if (selected.theMatch.isCached())
				theIntoButton.setToolTipText("The selected matcher was returned from the cache");
			else
				theIntoButton.setToolTipText("The selected matcher has already been parsed");
			return false;
		}
		if (cursor.theMatch.getMatcher().getComposed().isEmpty() && !(cursor.theMatch.getMatcher() instanceof ReferenceMatcher)) {
			theIntoButton.setToolTipText("The selected matcher does not have any children to descend into");
			return false;
		}
		return true;
	}

	private boolean canRunTo(ParseNode cursor) {
		if (!isSteppable(cursor))
			return false;
		// TODO Not at all confident that I captured every use case here
		if (theDebugPane.getSelectedValuesList().size() != 1) {
			theRunToButton.setToolTipText("Select a matcher in the list");
			return false;
		}
		ParseNode selection = getInternalSelection();
		if (selection == null || selection.theParent == null)
			return false;
		MatcherObject listSelection = theDebugPane.getSelectedValue();
		if (selection.theParent.theMatch.getMatcher() instanceof ReferenceMatcher) {
			if (listSelection.theMatcher == cursor.theMatch.getMatcher()) {
				theRunToButton.setToolTipText("Select a different matcher to run to");
				return false;
			}
			// At the moment, we don't keep track of which reference matchers have already been parsed, and anyway it's possible that it may
			// need to be re-parsed, so we won't try to screen anything here.
			return true;
		} else {
			while (listSelection.theParent != null && listSelection.theParent.theParent != null)
				listSelection = listSelection.theParent;
			int selectIndex = selection.theParent.theMatch.getMatcher().getComposed().indexOf(listSelection.theMatcher);
			while (selection.theParent != null && selection.theParent.theParent != null
					&& !(selection.theParent.theParent.theMatch.getMatcher() instanceof ReferenceMatcher))
				selection = selection.theParent;
			if (selection.theParent == null
					|| selection.theParent.theMatch.getMatcher().getComposed().indexOf(selection.theMatch.getMatcher()) >= selectIndex) {
				theRunToButton.setToolTipText("The selected matcher has already been parsed");
				return false; // Already passed the selected matcher
			}
			return true;
		}
	}

	private boolean isSteppable(ParseNode cursor) {
		return cursor != null;
	}

	private ParseNode getInternalSelection() {
		if(theParseTree.getSelectionPath() == null)
			return null;
		ParseNode selected = (ParseNode) theParseTree.getSelectionPath().getLastPathComponent();
		if(selected != null)
			return theInternalTreeModel.navigateTo(selected);
		else
			return theInternalTreeModel.getCursor();
	}

	/**
	 * Creates a frame containing the given debugger panel
	 *
	 * @param debugger The debugger to frame
	 * @return The frame containing the given debugger
	 */
	public static JFrame getDebuggerFrame(final ExpressoParserDebugGUI<?> debugger) {
		JFrame ret = new JFrame("Prisms Parser Debug");
		ret.setIconImage(getIcon("bug.png", 16, 16).getImage());
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
					debugger.suspendOrResume();
			}
		});
		return ret;
	}

	private StringBuilder htmlIze(ParseMatcher<?> matcher, int spacing) {
		StringBuilder text = new StringBuilder("<html>");
		for(int i = 0; i < spacing; i++)
			text.append("&nbsp;&nbsp;&nbsp;&nbsp;");
		text.append("<font color=\"blue\">&lt;</font><font color=\"red\">");
		text.append(matcher.getTypeName()).append("</font>");
		if(!(matcher instanceof WhitespaceMatcher) && matcher.getName() != null)
			text.append(" name=\"").append(matcher.getName()).append('"');
		for(Map.Entry<String, String> attr : matcher.getAttributes().entrySet())
			text.append(' ').append(attr.getKey()).append("=\"").append(attr.getValue()).append('"');
		boolean needsEnding = matcher.getComposed().isEmpty();
		if(matcher instanceof SimpleValueMatcher) {
			text.append("<font color=\"blue\">&gt;</font>");
			String value = ((SimpleValueMatcher<?>) matcher).getValueString();
			value = value.replaceAll("<", "&amp;lt;").replaceAll(">", "&amp;gt;").replaceAll("\n", "\\\\n").replaceAll("\t", "\\\\t");
			text.append(value);
			if(needsEnding) {
				text.append("<font color=\"blue\">&lt;/</font><font color=\"red\">");
				text.append(matcher.getTypeName()).append("</font>");
			}
		} else if(needsEnding)
			text.append(" <font color=\"blue\">/</font>");
		text.append("<font color=\"blue\">&gt;</font>");
		text.append("</html>");
		return text;
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

	private enum TreeEventType {
		ADD, REMOVE, CHANGE;
	}

	private class ParsingExpressionTreeModel implements javax.swing.tree.TreeModel {
		private ParseNode theRoot;

		private ParseNode theCursor;

		private List<TreeModelListener> theListeners = new ArrayList<>();

		private boolean isDirty;

		ParseNode getCursor() {
			return theCursor;
		}

		void ascend() {
			theCursor = theCursor.theParent;
		}

		void startNew(MatchData<? extends S> match, S stream) {
			if (theCursor != null && theCursor.theParent != null) {
				for (ParseNode child : theCursor.theParent.theChildren)
					if (child.theMatch == match)
						theCursor = child;
			}
			if (theCursor == null || theCursor.theMatch != match)
				theCursor = add(match, stream);
		}

		void finish(MatchData<? extends S> match) {
			if(theCursor == null)
				return;
			if(theCursor.isFinished)
				ascend();
			if (theCursor == null)
				return;
			theCursor.isFinished = true;
			theCursor.isSynced = false;
			isDirty = true;
		}

		private ParseNode add(MatchData<? extends S> match, S stream) {
			if(theCursor != null && theCursor.isFinished)
				ascend();
			ParseNode newNode = new ParseNode(theCursor, match, stream == null ? null : (S) stream.branch());
			boolean newRoot = theCursor == null;
			if(newRoot)
				theRoot = newNode;
			else
				theCursor.theChildren.add(newNode);

			isDirty = true;
			return newNode;
		}

		void syncSelection() {
			if(theCursor != null) {
				theParseTree.setSelectionPath(new TreePath(getPath(theCursor)));
				if(theCursor.theParent != null)
					theParseTree.expandPath(new TreePath(getPath(theCursor.theParent)));
			}
		}

		ParseNode [] getPath(ParseNode node) {
			ArrayList<ParseNode> ret = new ArrayList<>();
			while (node != null) {
				ret.add(0, node);
				node = node.theParent;
			}
			return ret.toArray(new ExpressoParserDebugGUI.ParseNode[ret.size()]);
		}

		@Override
		public Object getRoot() {
			if(theRoot == null)
				return new ParseNode(null, new MatchData<>(new EmptyMatcher(), null), null);
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

		private int [] indexes(ParseNode... nodes) {
			int [] ret = new int[nodes.length];
			for (int i = 0; i < ret.length; i++) {
				if (nodes[i].theParent == null)
					ret[i] = 0;
				else
					ret[i] = nodes[i].theParent.theChildren.indexOf(nodes[i]);
			}
			return ret;
		}

		private void fire(TreeEventType type, int [] indexes, ParseNode... nodes) {
			if (nodes[0].theParent == null)
				return;
			if(indexes == null && type != TreeEventType.REMOVE)
				indexes = indexes(nodes);
			TreeModelEvent evt = new TreeModelEvent(this, getPath(nodes[0].theParent), indexes, nodes);
			for(TreeModelListener listener : theListeners) {
				switch (type) {
				case ADD:
					listener.treeNodesInserted(evt);
					break;
				case REMOVE:
					listener.treeNodesRemoved(evt);
					break;
				case CHANGE:
					listener.treeNodesChanged(evt);
					break;
				}
			}
		}

		private ParseNode navigateTo(ParseNode node) {
			ParseNode [] path = getPath(node);
			if (path[0].theMatch != theRoot.theMatch)
				throw new IllegalArgumentException("No such node in this tree");
			ParseNode ret = theRoot;
			for(int pathIdx = 1; pathIdx < path.length; pathIdx++) {
				int dupIdx = 0;
				for(ParseNode c : path[pathIdx - 1].theChildren)
					if (c != path[pathIdx] && c.theMatch == path[pathIdx].theMatch)
						dupIdx++;
				boolean found = false;
				for(ParseNode child : ret.theChildren) {
					if (child.theMatch == path[pathIdx].theMatch) {
						if(dupIdx == 0) {
							found = true;
							ret = child;
							break;
						} else
							dupIdx--;
					}
				}
				if(!found)
					throw new IllegalArgumentException("No such node in this tree");
			}
			return ret;
		}

		private ParseNode copyNode(ParseNode node, ParseNode parent) {
			ParseNode ret = new ParseNode(parent, node.theMatch, node.theStreamCapture);
			ret.isFinished = node.isFinished;
			for(ParseNode child : node.theChildren)
				ret.theChildren.add(copyNode(child, ret));
			return ret;
		}

		/**
		 * Synchronizes this model's data into the given structure
		 *
		 * @param model The model to synchronize with this model's data
		 */
		void sync(ParsingExpressionTreeModel model) {
			if(model.theRoot == null) {
				model.theRoot = copyNode(theRoot, null);
				for(TreeModelListener listener : model.theListeners)
					listener.treeStructureChanged(new TreeModelEvent(this, new ExpressoParserDebugGUI.ParseNode[] {model.theRoot}));
			} else
				sync(theRoot, model.theRoot, model);
			if(theCursor == null)
				model.theCursor = null;
			else
				model.theCursor = model.navigateTo(theCursor);
		}

		private void sync(ParseNode from, ParseNode into, ParsingExpressionTreeModel model) {
			if (!from.isSynced) {
				into.isSynced = true;
				from.isSynced = true;
				model.fire(TreeEventType.CHANGE, null, into);
			}
			// Do adjustment in multiple phases to reduce possibility of index errors
			org.qommons.IntList removedIndexes = new org.qommons.IntList(true, true);
			List<ParseNode> removedChildren = new ArrayList<>();
			ArrayUtils.adjust(into.theChildren, from.theChildren, new ArrayUtils.DifferenceListener<ParseNode, ParseNode>() {
				@Override
				public boolean identity(ParseNode o1, ParseNode o2) {
					return o1.theMatch == o2.theMatch;
				}

				@Override
				public ParseNode added(ParseNode o, int mIdx, int retIdx) {
					return null;
				}

				@Override
				public ParseNode removed(ParseNode o, int oIdx, int incMod, int retIdx) {
					removedChildren.add(o);
					removedIndexes.add(oIdx);
					return null;
				}

				@Override
				public ParseNode set(ParseNode o1, int idx1, int incMod, ParseNode o2, int idx2, int retIdx) {
					return o1;
				}
			});
			if(!removedChildren.isEmpty())
				model.fire(TreeEventType.REMOVE, removedIndexes.toArray(),
						removedChildren.toArray(new ExpressoParserDebugGUI.ParseNode[removedChildren.size()]));

			List<ParseNode> addedChildren = new ArrayList<>();
			List<ParseNode> changedChildren = new ArrayList<>();
			ArrayUtils.adjust(into.theChildren, from.theChildren, new ArrayUtils.DifferenceListener<ParseNode, ParseNode>() {
				@Override
				public boolean identity(ParseNode o1, ParseNode o2) {
					return o1.theMatch == o2.theMatch;
				}

				@Override
				public ParseNode added(ParseNode o, int mIdx, int retIdx) {
					ParseNode ret = copyNode(o, into);
					ret.isFinished = o.isFinished;
					addedChildren.add(ret);
					o.isSynced = true;
					ret.isSynced = true;
					return ret;
				}

				@Override
				public ParseNode removed(ParseNode o, int oIdx, int incMod, int retIdx) {
					return null;
				}

				@Override
				public ParseNode set(ParseNode o1, int idx1, int incMod, ParseNode o2, int idx2, int retIdx) {
					boolean changed = false;
					if (!o1.isSynced || !o2.isSynced) {
						changed = true;
						o1.isSynced = true;
						o2.isSynced = true;
					}
					if(o1.isFinished != o2.isFinished) {
						o1.isFinished = o2.isFinished;
						changed = true;
					}
					if(changed)
						changedChildren.add(o1);
					return o1;
				}
			});
			if(!addedChildren.isEmpty())
				model.fire(TreeEventType.ADD, null, addedChildren.toArray(new ExpressoParserDebugGUI.ParseNode[addedChildren.size()]));
			if(!changedChildren.isEmpty())
				model.fire(TreeEventType.CHANGE, null,
						changedChildren.toArray(new ExpressoParserDebugGUI.ParseNode[changedChildren.size()]));

			for(int i = 0; i < from.theChildren.size(); i++) {
				if(addedChildren.contains(into.theChildren.get(i)))
					continue;
				sync(from.theChildren.get(i), into.theChildren.get(i), model);
			}
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
			return Collections.EMPTY_MAP;
		}

		@Override
		public Set<String> getTags() {
			return Collections.EMPTY_SET;
		}

		@Override
		public Set<String> getPotentialBeginningTypeReferences(ExpressoParser<?> parser, ParseSession session) {
			return Collections.EMPTY_SET;
		}

		@Override
		public List<ParseMatcher<? super BranchableStream<?, ?>>> getComposed() {
			return Collections.EMPTY_LIST;
		}

		@Override
		public <SS extends BranchableStream<?, ?>> ExIterable<ParseMatch<SS>, IOException> match(SS stream,
				ExpressoParser<? super SS> parser, ParseSession session) {
			throw new IllegalStateException("This placeholder does not do any parsing");
		}
	}

	private class ParseNode {
		final ParseNode theParent;
		final MatchData<? extends S> theMatch;

		final S theStreamCapture;
		final List<ParseNode> theChildren = new ArrayList<>();
		private final String theString;

		boolean isFinished;
		boolean isSynced;

		ParseNode(ParseNode parent, MatchData<? extends S> match, S stream) {
			theParent = parent;
			theMatch = match;
			theStreamCapture = stream;
			StringBuilder str = new StringBuilder();
			str.append("<").append(match.getMatcher().getTypeName());
			if(!(match.getMatcher() instanceof WhitespaceMatcher) && match.getMatcher().getName() != null)
				str.append(" name=\"").append(match.getMatcher().getName()).append('"');
			for(Map.Entry<String, String> attr : match.getMatcher().getAttributes().entrySet())
				str.append(' ').append(attr.getKey()).append("=\"").append(attr.getValue()).append('"');
			str.append(">");
			theString = str.toString();
		}

		@Override
		public String toString() {
			if (theMatch.getBestMatch() == null)
				return theString;
			else
				return theString + " - \"" + theMatch.getBestMatch().flatText() + "\"";
		}
	}

	private static class MatcherObject {
		final MatcherObject theParent;
		final ParseMatcher<?> theMatcher;
		final boolean isCached;

		final int theIndent;

		final boolean isTerminal;

		MatcherObject(MatcherObject parent, ParseMatcher<?> op, int indent, boolean cached, boolean terminal) {
			theParent=parent;
			theMatcher = op;
			theIndent = indent;
			isCached = cached;
			isTerminal = terminal;
		}

		@Override
		public String toString() {
			return theMatcher.toString();
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
					text.append(blue).append("(</font>").append(post.replaceAll("<", "&lt;")).append(blue)
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
			Set<String> opNames = new TreeSet<>(theOpNames);
			for (String opName : opNames)
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
				boolean listSelected, boolean cellHasFocus) {
			StringBuilder text;
			if(value.isTerminal) {
				text = new StringBuilder("<html>");
				for(int i = 0; i < value.theIndent; i++)
					text.append("&nbsp;&nbsp;&nbsp;&nbsp;");
				text.append("<font color=\"blue\">&lt;</font><font color=\"red\">").append('/').append(value.theMatcher.getTypeName());
				text.append("<font color=\"blue\">&gt;</font>");
				text.append("</html>");
			} else
				text = htmlIze(value.theMatcher, value.theIndent);
			theLabel.setText(text.toString());

			Color bg = Color.white;
			boolean cursor = theDisplayedTreeModel.getCursor() != null
					&& theDisplayedTreeModel.getCursor().theMatch.getMatcher() == value.theMatcher;
			if(cursor)
				cursor = (theDisplayedTreeModel.getCursor().theMatch != null) == value.isTerminal;
			boolean selected = !value.isTerminal && theParseTree.getSelectionPath() != null
					&& ((ParseNode) theParseTree.getSelectionPath().getLastPathComponent()).theMatch.getMatcher() == value.theMatcher;
			if(cursor && selected) {
				if(value.isCached)
					bg = new Color(128, 255, 255);
				else
					bg = new Color(0, 255, 255);
			} else if(cursor)
				bg = Color.green;
			else if(selected)
				bg = SELECTED;
			else if(listSelected)
				bg = Color.orange;
			else if(value.isCached)
				bg = Color.yellow;
			setBackground(bg);
			return this;
		}
	}

	private class ParseNodeCellRenderer extends DefaultTreeCellRenderer {
		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row,
				boolean hasFocus2) {
			super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus2);
			setIcon(null);
			if(!(value instanceof ExpressoParserDebugGUI.ParseNode))
				return this;
			ParseNode node=(ParseNode) value;
			StringBuilder ret = htmlIze(node.theMatch.getMatcher(), 0);
			ParseMatch<?> match = node.theMatch.getBestMatch();
			if (match != null) {
				ret.delete(ret.length() - 7, ret.length()); // Drop the </html>
				ret.append(" - ");
				boolean red = !match.isComplete() || match.getError() != null;
				if(red)
					ret.append("<font color=\"red\">");
				String flat = match.flatText();
				if(flat.length() > 48)
					flat = flat.substring(0, 46) + '\u2026'; // ellipsis
				ret.append(flat.replaceAll("<", "&lt;"));
				if(red)
					ret.append("</font");
				if (node.theMatch.getMatcher() instanceof ReferenceMatcher && !match.getChildren().isEmpty())
					ret.append(" (").append(match.getChildren().get(0).getMatcher().getName()).append(")");
				ret.append("</html>");
			}
			setText(ret.toString());
			return this;
		}
	}
}
