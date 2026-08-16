package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

final class ModernFilePicker
{
	private static final Color SURFACE = NebulaDesign.SURFACE;
	private static final Color SURFACE_ALT = NebulaDesign.SURFACE_ALT;
	private static final Color BORDER = NebulaDesign.BORDER_SUBTLE;
	private static final Color TEXT = NebulaDesign.TEXT_PRIMARY;
	private static final Color MUTED = NebulaDesign.TEXT_SECONDARY;
	private static final Color FAINT = NebulaDesign.TEXT_TERTIARY;
	private static final Color BLUE = NebulaDesign.ACCENT;
	private static final Color GREEN = NebulaDesign.SUCCESS;
	private static final Color WARNING = NebulaDesign.WARNING;
	private static final Color SELECTION = NebulaDesign.SELECTION;
	private static final String HOVER_KEY = "nebula.hoverIndex";
	private static final Icon FOLDER_ICON = new GlyphIcon(Glyphs.FOLDER, 15, true);
	private static final Icon FILE_ICON = new GlyphIcon(Glyphs.FILE, 15, false);
	private static final int MAX_SEARCHED_ENTRIES = 20000;
	private static final int MAX_SEARCH_RESULTS = 1000;
	private static final String FILTER_DATASETS = "Datasets (.txt .csv .tsv .xlsx)";
	private static final String FILTER_ALL = "All files";
	private static final String RECENTS_KEY = "recentDatasetFiles";
	private static final String LAST_DIRECTORY_KEY = "lastDatasetDirectory";

	private ModernFilePicker()
	{
	}

	static File chooseDataset(
			Component owner,
			File initialDirectory,
			File previousSelection)
	{
		Window window = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
		PickerDialog dialog = new PickerDialog(
				window, initialDirectory, previousSelection, true);
		dialog.setVisible(true);
		return dialog.getSelectedFile();
	}

	static File chooseTextFile(
			Component owner,
			File initialDirectory,
			File previousSelection)
	{
		Window window = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
		PickerDialog dialog = new PickerDialog(
				window, initialDirectory, previousSelection, false);
		dialog.setVisible(true);
		return dialog.getSelectedFile();
	}

	private static final class PickerDialog extends JDialog
	{
		private static final long serialVersionUID = 1L;

		private final Preferences preferences =
				Preferences.userNodeForPackage(ModernFilePicker.class);
		private final boolean inspectDataset;
		private final DefaultListModel<Location> locationsModel =
				new DefaultListModel<Location>();
		private final JList<Location> locationsList = new JList<Location>(locationsModel);
		private final FileTableModel tableModel = new FileTableModel();
		private final JTable filesTable = new JTable(tableModel)
		{
			private static final long serialVersionUID = 1L;

			protected void paintComponent(Graphics graphics)
			{
				super.paintComponent(graphics);
				if (getRowCount() > 0)
				{
					return;
				}
				Graphics2D g2 = (Graphics2D)graphics.create();
				NebulaDesign.prepare(g2);
				int centerX = getWidth() / 2;
				int centerY = Math.min(getHeight() / 2, 190);
				Glyphs.paint(g2, Glyphs.FOLDER, centerX - 15, centerY - 46, 30,
						NebulaDesign.withAlpha(NebulaDesign.currentBorderStrong(), 190));
				g2.setFont(NebulaDesign.fontSemiBold(13.5f));
				g2.setColor(MUTED);
				java.awt.FontMetrics metrics = g2.getFontMetrics();
				g2.drawString(emptyTitle,
						centerX - metrics.stringWidth(emptyTitle) / 2, centerY);
				g2.setFont(NebulaDesign.fontBody(12f));
				g2.setColor(FAINT);
				metrics = g2.getFontMetrics();
				g2.drawString(emptyDetail,
						centerX - metrics.stringWidth(emptyDetail) / 2, centerY + 20);
				g2.dispose();
			}
		};
		private final JTextField searchField = new JTextField();
		private final JTextField pathField = new JTextField();
		private final JLabel statusLabel = new JLabel(" ");
		private final JLabel selectedNameLabel = new JLabel("No file selected");
		private final JLabel selectedInfoLabel = new JLabel("Choose a readable file to continue.");
		private final PillLabel compatibilityLabel = new PillLabel();
		private final JButton backButton = createNavButton("Back", Glyphs.BACK);
		private final JButton upButton = createNavButton("Up", Glyphs.UP);
		private final JButton homeButton = createNavButton("Home", Glyphs.HOME);
		private final JButton refreshButton = createNavButton("Refresh", Glyphs.REFRESH);
		private String emptyTitle = "Loading files";
		private String emptyDetail = "Reading this folder…";
		private final JButton cancelButton = createButton("Cancel", false);
		private final JButton openButton = createButton("Open", true);
		private final JComboBox<String> filterBox =
				new JComboBox<String>(new String[] {FILTER_DATASETS, FILTER_ALL});
		private final JCheckBox hiddenFilesBox = new JCheckBox("Show hidden files");
		private final Timer searchTimer;
		private final ArrayList<File> history = new ArrayList<File>();

		private File currentDirectory;
		private File selectedFile;
		private int historyIndex = -1;
		private boolean updatingLocationSelection;
		private boolean showingRecents;
		private SwingWorker<List<FileEntry>,Void> loadWorker;
		private SwingWorker<String,Void> inspectionWorker;

		PickerDialog(
				Window owner,
				File initialDirectory,
				File previousSelection,
				boolean inspectDataset)
		{
			super(owner,
					inspectDataset ? "Choose Dataset" : "Choose Text File",
					ModalityType.APPLICATION_MODAL);
			this.inspectDataset = inspectDataset;
			setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			setMinimumSize(new Dimension(860, 560));
			setPreferredSize(new Dimension(1040, 690));

			buildInterface();
			installActions();
			populateLocations();

			searchTimer = new Timer(280, new ActionListener() {
				public void actionPerformed(ActionEvent event)
				{
					refreshFiles();
				}
			});
			searchTimer.setRepeats(false);

			File startDirectory = determineInitialDirectory(
					initialDirectory, previousSelection);
			navigateTo(startDirectory, true);

			pack();
			setLocationRelativeTo(owner);
			SwingUtilities.invokeLater(new Runnable() {
				public void run()
				{
					searchField.requestFocusInWindow();
				}
			});
		}

		File getSelectedFile()
		{
			return selectedFile;
		}

		private void buildInterface()
		{
			JPanel root = new CanvasPanel();
			root.setLayout(new BorderLayout(0, 12));
			root.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

			root.add(createHeader(), BorderLayout.NORTH);
			root.add(createBrowser(), BorderLayout.CENTER);
			root.add(createFooter(), BorderLayout.SOUTH);
			setContentPane(root);
			getRootPane().setDefaultButton(openButton);
		}

		private JComponent createHeader()
		{
			JPanel header = new HeroPanel(new BorderLayout(24, 0));
			header.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 18));

			JPanel identity = new JPanel();
			identity.setOpaque(false);
			identity.setLayout(new javax.swing.BoxLayout(
					identity, javax.swing.BoxLayout.Y_AXIS));
			JLabel eyebrow = new JLabel(
					inspectDataset ? "Dataset library" : "Corpus library");
			NebulaWidgets.styleSectionLabel(eyebrow);
			eyebrow.setForeground(BLUE);
			eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);
			JLabel title = new JLabel(
					inspectDataset ? "Open a dataset" : "Open a text file");
			title.setFont(NebulaDesign.fontSemiBold(20f));
			title.setForeground(TEXT);
			title.setAlignmentX(Component.LEFT_ALIGNMENT);
			JLabel subtitle = new JLabel(inspectDataset
					? "Browse or search your folders. Structure is checked before loading."
					: "Browse or search your folders for a corpus file.");
			subtitle.setFont(NebulaDesign.fontBody(12.5f));
			subtitle.setForeground(MUTED);
			subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
			identity.add(eyebrow);
			identity.add(javax.swing.Box.createRigidArea(new Dimension(0, 4)));
			identity.add(title);
			identity.add(javax.swing.Box.createRigidArea(new Dimension(0, 4)));
			identity.add(subtitle);

			searchField.setFont(NebulaDesign.fontBody(13f));
			searchField.setToolTipText(
					"Search file and folder names inside the current location");
			FieldWell searchWell = new FieldWell(
					searchField, Glyphs.SEARCH, "Search this folder");
			searchWell.setPreferredSize(new Dimension(292, 36));

			JLabel shortcuts = new JLabel("Ctrl-F to search  ·  Esc to close");
			shortcuts.setFont(NebulaDesign.fontMedium(10.5f));
			shortcuts.setForeground(FAINT);
			shortcuts.setHorizontalAlignment(SwingConstants.RIGHT);

			JPanel searchColumn = new JPanel(new BorderLayout(0, 7));
			searchColumn.setOpaque(false);
			searchColumn.add(searchWell, BorderLayout.NORTH);
			searchColumn.add(shortcuts, BorderLayout.CENTER);

			JPanel searchHolder = new JPanel(new BorderLayout());
			searchHolder.setOpaque(false);
			searchHolder.add(searchColumn, BorderLayout.NORTH);

			header.add(identity, BorderLayout.CENTER);
			header.add(searchHolder, BorderLayout.EAST);
			return header;
		}

		private JComponent createBrowser()
		{
			BrowserCard browser = new BrowserCard();

			locationsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			locationsList.setCellRenderer(new LocationRenderer());
			locationsList.setOpaque(false);
			locationsList.setBorder(BorderFactory.createEmptyBorder(12, 10, 12, 10));
			JScrollPane locationsScroll = new JScrollPane(locationsList);
			locationsScroll.setBorder(BorderFactory.createEmptyBorder());
			locationsScroll.setOpaque(false);
			locationsScroll.getViewport().setOpaque(false);
			locationsScroll.setPreferredSize(new Dimension(198, 100));
			polishScroll(locationsScroll);

			JPanel fileBrowser = new JPanel(new BorderLayout(0, 0));
			fileBrowser.setOpaque(false);
			fileBrowser.add(createNavigationBar(), BorderLayout.NORTH);
			fileBrowser.add(createFileTable(), BorderLayout.CENTER);

			JSplitPane splitPane = new JSplitPane(
					JSplitPane.HORIZONTAL_SPLIT, locationsScroll, fileBrowser);
			splitPane.setDividerSize(1);
			splitPane.setDividerLocation(198);
			splitPane.setResizeWeight(0.0);
			splitPane.setBorder(null);
			splitPane.setOpaque(false);
			splitPane.setContinuousLayout(true);
			browser.setLayout(new BorderLayout());
			browser.add(splitPane, BorderLayout.CENTER);
			browser.trackRail(splitPane);
			return browser;
		}

		private JComponent createNavigationBar()
		{
			JPanel toolbar = new JPanel(new BorderLayout(12, 0));
			toolbar.setOpaque(false);
			toolbar.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
					BorderFactory.createEmptyBorder(11, 14, 11, 14)));

			JPanel navigationButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
			navigationButtons.setOpaque(false);
			navigationButtons.add(backButton);
			navigationButtons.add(upButton);
			navigationButtons.add(homeButton);
			navigationButtons.add(refreshButton);

			pathField.setFont(NebulaDesign.fontMono(11.5f));
			pathField.setToolTipText("Type a folder path and press Enter");
			FieldWell pathWell = new FieldWell(pathField, Glyphs.FOLDER, null);

			toolbar.add(navigationButtons, BorderLayout.WEST);
			toolbar.add(pathWell, BorderLayout.CENTER);
			return toolbar;
		}

		private static void polishScroll(JScrollPane scroll)
		{
			scroll.getVerticalScrollBar().setUI(new NebulaWidgets.PillScrollBarUI());
			scroll.getVerticalScrollBar().setOpaque(false);
			scroll.getHorizontalScrollBar().setUI(new NebulaWidgets.PillScrollBarUI());
			scroll.getHorizontalScrollBar().setOpaque(false);
			scroll.getVerticalScrollBar().setUnitIncrement(20);
		}

		private JComponent createFileTable()
		{
			filesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			filesTable.setRowHeight(32);
			filesTable.setShowHorizontalLines(false);
			filesTable.setShowVerticalLines(false);
			filesTable.setIntercellSpacing(new Dimension(0, 0));
			filesTable.setFillsViewportHeight(true);
			filesTable.setOpaque(false);
			filesTable.setForeground(TEXT);
			filesTable.setSelectionBackground(NebulaDesign.ACCENT_SOFT);
			filesTable.setSelectionForeground(TEXT);
			filesTable.setFont(NebulaDesign.fontBody(12.5f));
			filesTable.setAutoCreateRowSorter(true);

			javax.swing.table.JTableHeader header = filesTable.getTableHeader();
			header.setFont(NebulaDesign.fontMedium(11f));
			header.setBackground(NebulaDesign.SURFACE_RAISED);
			header.setForeground(MUTED);
			header.setReorderingAllowed(false);
			header.setBorder(BorderFactory.createEmptyBorder());
			header.setPreferredSize(new Dimension(10, 34));
			header.setDefaultRenderer(new FileHeaderRenderer());

			filesTable.getColumnModel().getColumn(0).setPreferredWidth(322);
			filesTable.getColumnModel().getColumn(1).setPreferredWidth(76);
			filesTable.getColumnModel().getColumn(2).setPreferredWidth(80);
			filesTable.getColumnModel().getColumn(3).setPreferredWidth(146);
			filesTable.getColumnModel().getColumn(4).setPreferredWidth(190);
			filesTable.getColumnModel().getColumn(0).setMinWidth(150);
			filesTable.getColumnModel().getColumn(1).setMinWidth(58);
			filesTable.getColumnModel().getColumn(2).setMinWidth(72);
			filesTable.getColumnModel().getColumn(3).setMinWidth(140);
			filesTable.getColumnModel().getColumn(4).setMinWidth(70);
			FileCellRenderer cells = new FileCellRenderer();
			for (int column = 0; column < 5; column++)
			{
				filesTable.getColumnModel().getColumn(column).setCellRenderer(cells);
			}

			JScrollPane tableScroll = new JScrollPane(filesTable);
			tableScroll.setBorder(BorderFactory.createEmptyBorder());
			tableScroll.setOpaque(false);
			tableScroll.getViewport().setOpaque(false);
			polishScroll(tableScroll);
			return tableScroll;
		}

		private JComponent createFooter()
		{
			JPanel footer = new CardPanel(new BorderLayout(18, 0));
			footer.setBorder(BorderFactory.createEmptyBorder(13, 18, 15, 16));

			JPanel selectedPanel = new JPanel();
			selectedPanel.setOpaque(false);
			selectedPanel.setLayout(new javax.swing.BoxLayout(
					selectedPanel, javax.swing.BoxLayout.Y_AXIS));
			selectedNameLabel.setFont(NebulaDesign.fontSemiBold(13.5f));
			selectedNameLabel.setForeground(TEXT);
			selectedNameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			selectedInfoLabel.setFont(NebulaDesign.fontBody(11.5f));
			selectedInfoLabel.setForeground(MUTED);
			selectedInfoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			compatibilityLabel.setFont(NebulaDesign.fontMedium(11f));
			compatibilityLabel.setForeground(MUTED);
			statusLabel.setFont(NebulaDesign.fontMedium(11f));
			statusLabel.setForeground(FAINT);

			JPanel metaRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			metaRow.setOpaque(false);
			metaRow.setAlignmentX(Component.LEFT_ALIGNMENT);
			metaRow.add(compatibilityLabel);
			metaRow.add(javax.swing.Box.createRigidArea(new Dimension(9, 1)));
			metaRow.add(statusLabel);

			selectedPanel.add(selectedNameLabel);
			selectedPanel.add(javax.swing.Box.createRigidArea(new Dimension(0, 3)));
			selectedPanel.add(selectedInfoLabel);
			selectedPanel.add(javax.swing.Box.createRigidArea(new Dimension(0, 6)));
			selectedPanel.add(metaRow);

			JPanel controls = new JPanel(new GridLayout(2, 1, 0, 9));
			controls.setOpaque(false);
			JPanel options = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
			options.setOpaque(false);
			filterBox.setUI(new NebulaComboUI());
			filterBox.setFont(NebulaDesign.fontMedium(11.5f));
			filterBox.setForeground(TEXT);
			filterBox.setOpaque(false);
			filterBox.setRenderer(new ComboItemRenderer());
			filterBox.setBorder(BorderFactory.createEmptyBorder());
			filterBox.setPreferredSize(new Dimension(226, 32));
			hiddenFilesBox.setOpaque(false);
			hiddenFilesBox.setForeground(MUTED);
			hiddenFilesBox.setFont(NebulaDesign.fontMedium(11.5f));
			hiddenFilesBox.setIcon(new CheckBoxIcon());
			hiddenFilesBox.setIconTextGap(8);
			NebulaWidgets.polishButton(hiddenFilesBox);
			options.add(hiddenFilesBox);
			options.add(filterBox);

			JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
			actions.setOpaque(false);
			actions.add(cancelButton);
			actions.add(openButton);
			controls.add(options);
			controls.add(actions);

			JPanel footerCenter = new JPanel(new BorderLayout(12, 0));
			footerCenter.setOpaque(false);
			footerCenter.add(selectedPanel, BorderLayout.CENTER);

			footer.add(footerCenter, BorderLayout.CENTER);
			footer.add(controls, BorderLayout.EAST);
			openButton.setEnabled(false);
			return footer;
		}

		private void installActions()
		{
			backButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent event)
				{
					navigateBack();
				}
			});
			upButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent event)
				{
					if (currentDirectory != null && currentDirectory.getParentFile() != null)
					{
						navigateTo(currentDirectory.getParentFile(), true);
					}
				}
			});
			homeButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent event)
				{
					navigateTo(new File(System.getProperty("user.home")), true);
				}
			});
			refreshButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent event)
				{
					refreshFiles();
				}
			});
			cancelButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent event)
				{
					selectedFile = null;
					dispose();
				}
			});
			openButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent event)
				{
					openSelection();
				}
			});
			pathField.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent event)
				{
					File requestedPath = new File(pathField.getText().trim());
					if (requestedPath.isDirectory())
					{
						navigateTo(requestedPath, true);
					}
					else
					{
						statusLabel.setText("That folder does not exist or cannot be opened.");
						statusLabel.setForeground(WARNING);
					}
				}
			});

			searchField.getDocument().addDocumentListener(new DocumentListener() {
				public void insertUpdate(DocumentEvent event)
				{
					scheduleSearch();
				}

				public void removeUpdate(DocumentEvent event)
				{
					scheduleSearch();
				}

				public void changedUpdate(DocumentEvent event)
				{
					scheduleSearch();
				}
			});
			filterBox.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent event)
				{
					refreshFiles();
				}
			});
			hiddenFilesBox.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent event)
				{
					refreshFiles();
				}
			});

			locationsList.addListSelectionListener(new ListSelectionListener() {
				public void valueChanged(ListSelectionEvent event)
				{
					if (event.getValueIsAdjusting() || updatingLocationSelection)
					{
						return;
					}
					Location location = locationsList.getSelectedValue();
					if (location != null)
					{
						if (location.recent)
						{
							showRecentFiles();
						}
						else
						{
							navigateTo(location.directory, true);
						}
					}
				}
			});
			filesTable.getSelectionModel().addListSelectionListener(
					new ListSelectionListener() {
						public void valueChanged(ListSelectionEvent event)
						{
							if (!event.getValueIsAdjusting())
							{
								updateSelectionDetails();
							}
						}
					});
			filesTable.addMouseListener(new MouseAdapter() {
				public void mouseClicked(MouseEvent event)
				{
					if (event.getClickCount() == 2
							&& SwingUtilities.isLeftMouseButton(event))
					{
						openSelection();
					}
				}

				public void mouseExited(MouseEvent event)
				{
					setHover(filesTable, -1);
				}
			});
			filesTable.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
				public void mouseMoved(MouseEvent event)
				{
					setHover(filesTable, filesTable.rowAtPoint(event.getPoint()));
				}
			});
			locationsList.addMouseListener(new MouseAdapter() {
				public void mouseExited(MouseEvent event)
				{
					setHover(locationsList, -1);
				}
			});
			locationsList.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
				public void mouseMoved(MouseEvent event)
				{
					setHover(locationsList, locationsList.locationToIndex(
							event.getPoint()));
				}
			});

			InputMap inputMap = getRootPane().getInputMap(
					JComponent.WHEN_IN_FOCUSED_WINDOW);
			getRootPane().getActionMap().put("closePicker", new AbstractAction() {
				private static final long serialVersionUID = 1L;

				public void actionPerformed(ActionEvent event)
				{
					selectedFile = null;
					dispose();
				}
			});
			inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closePicker");
			getRootPane().getActionMap().put("focusSearch", new AbstractAction() {
				private static final long serialVersionUID = 1L;

				public void actionPerformed(ActionEvent event)
				{
					searchField.requestFocusInWindow();
					searchField.selectAll();
				}
			});
			int menuMask = java.awt.Toolkit.getDefaultToolkit()
					.getMenuShortcutKeyMaskEx();
			inputMap.put(
					KeyStroke.getKeyStroke(KeyEvent.VK_F, menuMask), "focusSearch");

			setDropTarget(new DropTarget() {
				private static final long serialVersionUID = 1L;

				public synchronized void drop(DropTargetDropEvent event)
				{
					try
					{
						event.acceptDrop(event.getDropAction());
						Object data = event.getTransferable().getTransferData(
								DataFlavor.javaFileListFlavor);
						if (data instanceof List && !((List<?>) data).isEmpty())
						{
							Object first = ((List<?>) data).get(0);
							if (first instanceof File)
							{
								File droppedFile = (File) first;
								if (droppedFile.isDirectory())
								{
									navigateTo(droppedFile, true);
								}
								else if (droppedFile.isFile() && droppedFile.canRead())
								{
									selectedFile = droppedFile;
									rememberSelection(droppedFile);
									dispose();
								}
							}
						}
						event.dropComplete(true);
					}
					catch (Exception exception)
					{
						event.dropComplete(false);
					}
				}
			});
		}

		private void populateLocations()
		{
			locationsModel.clear();
			locationsModel.addElement(
					new Location("Recent files", null, true, "Pinned", Glyphs.CLOCK));

			File project = new File(System.getProperty("user.dir"));
			addLocation("Project", project, "Workspace", Glyphs.FOLDER);
			addLocation("Dataset", new File(project, "Dataset"),
					"Workspace", Glyphs.FOLDER);
			addLocation("Tested datasets", new File(project, "TestedDataset"),
					"Workspace", Glyphs.FOLDER);

			File home = new File(System.getProperty("user.home"));
			addLocation("Home", home, "Places", Glyphs.HOME);
			addLocation("Downloads", new File(home, "Downloads"),
					"Places", Glyphs.DOWNLOAD);
			addLocation("Desktop", new File(home, "Desktop"),
					"Places", Glyphs.FOLDER);
			addLocation("Documents", new File(home, "Documents"),
					"Places", Glyphs.FOLDER);

			File[] roots = File.listRoots();
			if (roots != null)
			{
				for (File root : roots)
				{
					addLocation(root.getPath(), root, "Drives", Glyphs.DRIVE);
				}
			}
		}

		private void addLocation(String name, File directory, String group, int glyph)
		{
			if (directory != null && directory.isDirectory())
			{
				locationsModel.addElement(
						new Location(name, directory, false, group, glyph));
			}
		}

		private void setHover(JComponent component, int index)
		{
			Object current = component.getClientProperty(HOVER_KEY);
			int previous = current instanceof Integer
					? ((Integer)current).intValue() : -1;
			if (previous == index)
			{
				return;
			}
			component.putClientProperty(HOVER_KEY, Integer.valueOf(index));
			component.repaint();
		}

		private File determineInitialDirectory(
				File initialDirectory,
				File previousSelection)
		{
			if (previousSelection != null && previousSelection.getParentFile() != null
					&& previousSelection.getParentFile().isDirectory())
			{
				return previousSelection.getParentFile();
			}
			String rememberedPath = preferences.get(LAST_DIRECTORY_KEY, "");
			if (rememberedPath.length() > 0)
			{
				File remembered = new File(rememberedPath);
				if (remembered.isDirectory())
				{
					return remembered;
				}
			}
			if (initialDirectory != null && initialDirectory.isDirectory())
			{
				return initialDirectory;
			}
			return new File(System.getProperty("user.dir"));
		}

		private void navigateTo(File directory, boolean addHistory)
		{
			if (directory == null || !directory.isDirectory() || !directory.canRead())
			{
				statusLabel.setText("This folder cannot be opened.");
				statusLabel.setForeground(WARNING);
				return;
			}
			currentDirectory = directory.getAbsoluteFile();
			showingRecents = false;
			pathField.setText(currentDirectory.getAbsolutePath());
			searchField.setText("");
			if (addHistory)
			{
				while (history.size() > historyIndex + 1)
				{
					history.remove(history.size() - 1);
				}
				if (history.isEmpty()
						|| !history.get(history.size() - 1).equals(currentDirectory))
				{
					history.add(currentDirectory);
				}
				historyIndex = history.size() - 1;
			}
			updateNavigationState();
			selectMatchingLocation();
			refreshFiles();
		}

		private void navigateBack()
		{
			if (historyIndex <= 0)
			{
				return;
			}
			historyIndex--;
			File previous = history.get(historyIndex);
			currentDirectory = previous;
			showingRecents = false;
			pathField.setText(previous.getAbsolutePath());
			searchField.setText("");
			updateNavigationState();
			selectMatchingLocation();
			refreshFiles();
		}

		private void updateNavigationState()
		{
			backButton.setEnabled(historyIndex > 0);
			upButton.setEnabled(currentDirectory != null
					&& currentDirectory.getParentFile() != null);
		}

		private void selectMatchingLocation()
		{
			updatingLocationSelection = true;
			locationsList.clearSelection();
			for (int i = 0; i < locationsModel.size(); i++)
			{
				Location location = locationsModel.get(i);
				if (!location.recent && currentDirectory.equals(location.directory))
				{
					locationsList.setSelectedIndex(i);
					break;
				}
			}
			updatingLocationSelection = false;
		}

		private void scheduleSearch()
		{
			if (searchTimer != null)
			{
				searchTimer.restart();
			}
		}

		private void refreshFiles()
		{
			if (currentDirectory == null)
			{
				if (showingRecents)
				{
					showRecentFiles();
				}
				return;
			}
			cancelWorkers();
			clearSelectionDetails();
			final File directory = currentDirectory;
			final String query = searchField.getText().trim().toLowerCase(Locale.US);
			final boolean recursive = query.length() > 0;
			final boolean showHidden = hiddenFilesBox.isSelected();
			final boolean datasetFilter = FILTER_DATASETS.equals(filterBox.getSelectedItem());

			statusLabel.setForeground(FAINT);
			statusLabel.setText(recursive ? "Searching this folder and its subfolders..."
					: "Loading files...");
			emptyTitle = recursive ? "Searching" : "Loading files";
			emptyDetail = recursive
					? "Looking through this folder and its subfolders…"
					: "Reading this folder…";
			setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

			loadWorker = new SwingWorker<List<FileEntry>,Void>() {
				protected List<FileEntry> doInBackground()
				{
					return recursive
							? searchFiles(directory, query, showHidden, datasetFilter)
							: listDirectory(directory, showHidden, datasetFilter);
				}

				protected void done()
				{
					if (isCancelled())
					{
						return;
					}
					try
					{
						List<FileEntry> entries = get();
						tableModel.setEntries(entries);
						String suffix = recursive ? " search result(s)" : " item(s)";
						statusLabel.setText(entries.size() + suffix);
						statusLabel.setForeground(FAINT);
						emptyTitle = recursive ? "No matches" : "This folder is empty";
						emptyDetail = recursive
								? "Try a shorter search term or a different filter."
								: "Try another filter, or show hidden files.";
					}
					catch (Exception exception)
					{
						tableModel.setEntries(Collections.<FileEntry>emptyList());
						statusLabel.setText("Files could not be loaded: "
								+ exception.getMessage());
						statusLabel.setForeground(WARNING);
						emptyTitle = "Files could not be loaded";
						emptyDetail = "Check the folder permissions and try again.";
					}
					finally
					{
						setCursor(Cursor.getDefaultCursor());
					}
				}
			};
			loadWorker.execute();
		}

		private List<FileEntry> listDirectory(
				File directory,
				boolean showHidden,
				boolean datasetFilter)
		{
			ArrayList<FileEntry> entries = new ArrayList<FileEntry>();
			File[] files = directory.listFiles();
			if (files != null)
			{
				for (File file : files)
				{
					if (includeFile(file, showHidden, datasetFilter))
					{
						entries.add(new FileEntry(file, directory));
					}
				}
			}
			sortEntries(entries);
			return entries;
		}

		private List<FileEntry> searchFiles(
				File directory,
				String query,
				boolean showHidden,
				boolean datasetFilter)
		{
			ArrayList<FileEntry> results = new ArrayList<FileEntry>();
			ArrayDeque<File> pending = new ArrayDeque<File>();
			Set<String> visited = new HashSet<String>();
			pending.add(directory);
			int searchedEntries = 0;

			while (!pending.isEmpty() && searchedEntries < MAX_SEARCHED_ENTRIES
					&& results.size() < MAX_SEARCH_RESULTS
					&& !Thread.currentThread().isInterrupted())
			{
				File folder = pending.removeFirst();
				String canonicalPath;
				try
				{
					canonicalPath = folder.getCanonicalPath();
				}
				catch (IOException exception)
				{
					canonicalPath = folder.getAbsolutePath();
				}
				if (!visited.add(canonicalPath))
				{
					continue;
				}
				File[] children = folder.listFiles();
				if (children == null)
				{
					continue;
				}
				for (File child : children)
				{
					searchedEntries++;
					if (!showHidden && isHidden(child))
					{
						continue;
					}
					if (child.isDirectory())
					{
						pending.addLast(child);
					}
					if (child.getName().toLowerCase(Locale.US).contains(query)
							&& includeFile(child, showHidden, datasetFilter))
					{
						results.add(new FileEntry(child, directory));
						if (results.size() >= MAX_SEARCH_RESULTS)
						{
							break;
						}
					}
				}
			}
			sortEntries(results);
			return results;
		}

		private boolean includeFile(
				File file,
				boolean showHidden,
				boolean datasetFilter)
		{
			if (!showHidden && isHidden(file))
			{
				return false;
			}
			if (file.isDirectory())
			{
				return true;
			}
			return file.isFile() && (!datasetFilter || isDatasetFile(file));
		}

		private boolean isHidden(File file)
		{
			try
			{
				return file.isHidden() || file.getName().startsWith(".");
			}
			catch (SecurityException exception)
			{
				return true;
			}
		}

		private boolean isDatasetFile(File file)
		{
			String name = file.getName().toLowerCase(Locale.US);
			return name.endsWith(".txt") || name.endsWith(".csv")
					|| name.endsWith(".tsv") || name.endsWith(".xlsx");
		}

		private void sortEntries(List<FileEntry> entries)
		{
			Collections.sort(entries, new Comparator<FileEntry>() {
				public int compare(FileEntry first, FileEntry second)
				{
					if (first.file.isDirectory() != second.file.isDirectory())
					{
						return first.file.isDirectory() ? -1 : 1;
					}
					return first.file.getName().compareToIgnoreCase(
							second.file.getName());
				}
			});
		}

		private void showRecentFiles()
		{
			cancelWorkers();
			currentDirectory = null;
			showingRecents = true;
			pathField.setText("Recent files");
			backButton.setEnabled(historyIndex > 0);
			upButton.setEnabled(false);
			ArrayList<FileEntry> entries = new ArrayList<FileEntry>();
			String query = searchField.getText().trim().toLowerCase(Locale.US);
			boolean datasetFilter = FILTER_DATASETS.equals(filterBox.getSelectedItem());
			for (File recent : readRecentFiles())
			{
				if (recent.isFile() && recent.canRead()
						&& (!datasetFilter || isDatasetFile(recent))
						&& (query.length() == 0 || recent.getName()
								.toLowerCase(Locale.US).contains(query)))
				{
					entries.add(new FileEntry(recent, recent.getParentFile()));
				}
			}
			tableModel.setEntries(entries);
			statusLabel.setText(entries.size() + " recent file(s)");
			statusLabel.setForeground(FAINT);
			emptyTitle = "No recent files yet";
			emptyDetail = "Files you open here show up in this list.";
			clearSelectionDetails();
		}

		private void updateSelectionDetails()
		{
			FileEntry entry = getSelectedEntry();
			if (entry == null)
			{
				clearSelectionDetails();
				return;
			}
			File file = entry.file;
			selectedNameLabel.setText(file.getName());
			selectedInfoLabel.setText(file.isDirectory()
					? file.getAbsolutePath()
					: formatBytes(file.length()) + "  |  "
							+ formatDate(file.lastModified()) + "  |  "
							+ file.getParent());
			openButton.setText(file.isDirectory() ? "Open folder" : "Open");
			openButton.setEnabled(file.isDirectory()
					? file.canRead() : file.isFile() && file.canRead());
			compatibilityLabel.setTone(" ", MUTED, null);
			if (inspectDataset && file.isFile() && file.canRead())
			{
				inspectDataset(file);
			}
		}

		private void clearSelectionDetails()
		{
			filesTable.clearSelection();
			selectedNameLabel.setText("No file selected");
			selectedInfoLabel.setText("Choose a readable file to continue.");
			compatibilityLabel.setTone(" ", MUTED, null);
			openButton.setText("Open");
			openButton.setEnabled(false);
		}

		private FileEntry getSelectedEntry()
		{
			int viewRow = filesTable.getSelectedRow();
			if (viewRow < 0)
			{
				return null;
			}
			int modelRow = filesTable.convertRowIndexToModel(viewRow);
			return tableModel.getEntry(modelRow);
		}

		private void openSelection()
		{
			FileEntry entry = getSelectedEntry();
			if (entry == null)
			{
				return;
			}
			if (entry.file.isDirectory())
			{
				navigateTo(entry.file, true);
				return;
			}
			if (!entry.file.isFile() || !entry.file.canRead())
			{
				statusLabel.setText("The selected file cannot be read.");
				statusLabel.setForeground(WARNING);
				return;
			}
			selectedFile = entry.file.getAbsoluteFile();
			rememberSelection(selectedFile);
			dispose();
		}

		private void inspectDataset(final File file)
		{
			if (inspectionWorker != null)
			{
				inspectionWorker.cancel(true);
			}
			compatibilityLabel.setTone("Checking structure…", MUTED,
					NebulaDesign.SURFACE_ALT);
			inspectionWorker = new SwingWorker<String,Void>() {
				protected String doInBackground() throws Exception
				{
					BufferedReader reader = new BufferedReader(new FileReader(file));
					try
					{
						String line;
						while ((line = reader.readLine()) != null)
						{
							if (line.trim().length() > 0)
							{
								int fields = line.split("\t", -1).length;
								if (fields >= 6)
								{
									return "READY\t" + fields
											+ " tab-separated columns detected";
								}
								return "WARN\t" + fields
										+ " column(s) detected; this simulator expects 6 tab-separated fields";
							}
						}
						return "WARN\tThe selected file is empty";
					}
					finally
					{
						reader.close();
					}
				}

				protected void done()
				{
					if (isCancelled() || !file.equals(
							getSelectedEntry() == null ? null : getSelectedEntry().file))
					{
						return;
					}
					try
					{
						String result = get();
						boolean ready = result.startsWith("READY\t");
						compatibilityLabel.setTone(
								result.substring(result.indexOf('\t') + 1),
								ready ? GREEN : WARNING,
								ready ? NebulaDesign.SUCCESS_SOFT
										: NebulaDesign.WARNING_SOFT);
					}
					catch (Exception exception)
					{
						compatibilityLabel.setTone("The file could not be inspected.",
								NebulaDesign.DANGER, NebulaDesign.DANGER_SOFT);
					}
				}
			};
			inspectionWorker.execute();
		}

		private void cancelWorkers()
		{
			if (loadWorker != null)
			{
				loadWorker.cancel(true);
			}
			if (inspectionWorker != null)
			{
				inspectionWorker.cancel(true);
			}
			setCursor(Cursor.getDefaultCursor());
		}

		private void rememberSelection(File file)
		{
			File parent = file.getParentFile();
			if (parent != null)
			{
				preferences.put(LAST_DIRECTORY_KEY, parent.getAbsolutePath());
			}
			ArrayList<File> recent = new ArrayList<File>();
			recent.add(file.getAbsoluteFile());
			for (File existing : readRecentFiles())
			{
				if (!existing.equals(file) && recent.size() < 8)
				{
					recent.add(existing);
				}
			}
			StringBuilder stored = new StringBuilder();
			for (File recentFile : recent)
			{
				if (stored.length() > 0)
				{
					stored.append('\n');
				}
				stored.append(recentFile.getAbsolutePath());
			}
			preferences.put(RECENTS_KEY, stored.toString());
		}

		private List<File> readRecentFiles()
		{
			String stored = preferences.get(RECENTS_KEY, "");
			ArrayList<File> files = new ArrayList<File>();
			if (stored.length() == 0)
			{
				return files;
			}
			for (String path : stored.split("\n"))
			{
				if (path.trim().length() > 0)
				{
					files.add(new File(path));
				}
			}
			return files;
		}
	}

	private static final class Location
	{
		final String name;
		final File directory;
		final boolean recent;
		final String group;
		final int glyph;

		Location(String name, File directory, boolean recent, String group, int glyph)
		{
			this.name = name;
			this.directory = directory;
			this.recent = recent;
			this.group = group;
			this.glyph = glyph;
		}
	}

	/**
	 * Sidebar item: grouped section eyebrow, vector glyph, and an accent
	 * selection pill with a rounded left bar.
	 */
	private static final class LocationRenderer extends JComponent
			implements javax.swing.ListCellRenderer<Location>
	{
		private static final long serialVersionUID = 1L;
		private static final int ITEM_HEIGHT = 32;
		private static final int GROUP_SPACE = 26;
		private Location location;
		private boolean selected;
		private boolean hovered;
		private boolean startsGroup;

		public Component getListCellRendererComponent(
				JList<? extends Location> list,
				Location value,
				int index,
				boolean isSelected,
				boolean cellHasFocus)
		{
			location = value;
			selected = isSelected;
			Object hover = list.getClientProperty(HOVER_KEY);
			hovered = !isSelected && hover instanceof Integer
					&& ((Integer)hover).intValue() == index;
			startsGroup = index == 0
					|| !list.getModel().getElementAt(index - 1).group.equals(value.group);
			return this;
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(160,
					ITEM_HEIGHT + 3 + (startsGroup ? GROUP_SPACE : 0));
		}

		protected void paintComponent(Graphics graphics)
		{
			if (location == null)
			{
				return;
			}
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			int top = startsGroup ? GROUP_SPACE : 0;
			int width = getWidth();
			if (startsGroup)
			{
				NebulaDesign.paintEyebrow(g2, location.group, 8, 15, FAINT);
			}
			if (selected)
			{
				g2.setColor(NebulaDesign.ACCENT_SOFT);
				g2.fillRoundRect(0, top, width, ITEM_HEIGHT, 10, 10);
				g2.setColor(BLUE);
				g2.fillRoundRect(0, top + 7, 3, ITEM_HEIGHT - 14, 3, 3);
			}
			else if (hovered)
			{
				g2.setColor(NebulaDesign.withAlpha(
						NebulaDesign.currentSurfaceRaised(), 190));
				g2.fillRoundRect(0, top, width, ITEM_HEIGHT, 10, 10);
			}
			Color tone = selected ? BLUE : MUTED;
			Glyphs.paint(g2, location.glyph, 11, top + (ITEM_HEIGHT - 15) / 2, 15, tone);
			g2.setFont(selected
					? NebulaDesign.fontSemiBold(12.5f)
					: NebulaDesign.fontMedium(12.5f));
			g2.setColor(selected ? TEXT : NebulaDesign.currentTextSecondary());
			java.awt.FontMetrics metrics = g2.getFontMetrics();
			int textX = 34;
			int baseline = top + (ITEM_HEIGHT - metrics.getHeight()) / 2
					+ metrics.getAscent();
			String label = location.name;
			int available = width - textX - 8;
			if (metrics.stringWidth(label) > available)
			{
				while (label.length() > 2
						&& metrics.stringWidth(label + "…") > available)
				{
					label = label.substring(0, label.length() - 1);
				}
				label = label + "…";
			}
			g2.drawString(label, textX, baseline);
			g2.dispose();
		}
	}

	private static final class FileEntry implements Comparable<FileEntry>
	{
		final File file;
		final File searchRoot;
		final Icon icon;

		FileEntry(File file, File searchRoot)
		{
			this.file = file;
			this.searchRoot = searchRoot;
			icon = file.isDirectory() ? FOLDER_ICON : FILE_ICON;
		}

		public int compareTo(FileEntry other)
		{
			return file.getName().compareToIgnoreCase(other.file.getName());
		}

		public String toString()
		{
			return file.getName();
		}
	}

	private static final class FileTableModel extends AbstractTableModel
	{
		private static final long serialVersionUID = 1L;
		private final String[] columns =
				{"Name", "Kind", "Size", "Modified", "Folder"};
		private List<FileEntry> entries = Collections.emptyList();

		void setEntries(List<FileEntry> entries)
		{
			this.entries = entries == null
					? Collections.<FileEntry>emptyList() : entries;
			fireTableDataChanged();
		}

		FileEntry getEntry(int row)
		{
			return row >= 0 && row < entries.size() ? entries.get(row) : null;
		}

		public int getRowCount()
		{
			return entries.size();
		}

		public int getColumnCount()
		{
			return columns.length;
		}

		public String getColumnName(int column)
		{
			return columns[column];
		}

		public Class<?> getColumnClass(int column)
		{
			switch (column)
			{
				case 0:
					return FileEntry.class;
				case 2:
					return Long.class;
				case 3:
					return Date.class;
				default:
					return String.class;
			}
		}

		public Object getValueAt(int row, int column)
		{
			FileEntry entry = entries.get(row);
			File file = entry.file;
			switch (column)
			{
				case 0:
					return entry;
				case 1:
					return file.isDirectory() ? "Folder" : fileType(file);
				case 2:
					return file.isDirectory() ? null : Long.valueOf(file.length());
				case 3:
					return new Date(file.lastModified());
				case 4:
					return file.getParent();
				default:
					return "";
			}
		}
	}

	/**
	 * One renderer for every file column: transparent rows, accent-soft hover
	 * and selection, monospace numerals, and a leading accent bar on the
	 * selected row.
	 */
	private static final class FileCellRenderer extends JLabel
			implements TableCellRenderer
	{
		private static final long serialVersionUID = 1L;
		private boolean selected;
		private boolean hovered;
		private boolean leading;

		FileCellRenderer()
		{
			setOpaque(false);
		}

		public Component getTableCellRendererComponent(
				JTable table,
				Object value,
				boolean isSelected,
				boolean hasFocus,
				int row,
				int column)
		{
			Object hover = table.getClientProperty(HOVER_KEY);
			selected = isSelected;
			hovered = !isSelected && hover instanceof Integer
					&& ((Integer)hover).intValue() == row;
			leading = column == 0;
			setIcon(null);
			setHorizontalAlignment(column == 2
					? SwingConstants.RIGHT : SwingConstants.LEFT);
			setBorder(BorderFactory.createEmptyBorder(
					0, column == 0 ? 12 : 10, 0, column == 2 ? 14 : 10));
			switch (column)
			{
				case 0:
					FileEntry entry = value instanceof FileEntry
							? (FileEntry)value : null;
					setText(entry == null ? "" : entry.file.getName());
					setIcon(entry == null ? null : entry.icon);
					setIconTextGap(9);
					setFont(NebulaDesign.fontMedium(12.5f));
					setForeground(TEXT);
					break;
				case 1:
					setText(value == null ? "" : String.valueOf(value));
					setFont(NebulaDesign.fontMedium(11f));
					setForeground(MUTED);
					break;
				case 2:
					setText(value instanceof Long
							? formatBytes(((Long)value).longValue()) : "");
					setFont(NebulaDesign.fontMono(11f));
					setForeground(MUTED);
					break;
				case 3:
					setText(value instanceof Date
							? formatDate(((Date)value).getTime()) : "");
					setFont(NebulaDesign.fontMono(11f));
					setForeground(MUTED);
					break;
				default:
					setText(relativeLocation(table, row,
							value == null ? "" : String.valueOf(value)));
					setFont(NebulaDesign.fontBody(11.5f));
					setForeground(FAINT);
					break;
			}
			return this;
		}

		/** Shows the folder a row came from relative to the browsed root. */
		private static String relativeLocation(JTable table, int row, String path)
		{
			Object first = table.getValueAt(row, 0);
			if (!(first instanceof FileEntry))
			{
				return path;
			}
			File root = ((FileEntry)first).searchRoot;
			if (root == null)
			{
				return path;
			}
			String rootPath = root.getAbsolutePath();
			if (!path.equals(rootPath)
					&& path.startsWith(rootPath + File.separator))
			{
				return path.substring(rootPath.length() + 1);
			}
			File parent = new File(path);
			String name = parent.getName();
			return name.length() > 0 ? name : path;
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			if (selected)
			{
				g2.setColor(NebulaDesign.ACCENT_SOFT);
				g2.fillRect(0, 0, getWidth(), getHeight());
				if (leading)
				{
					g2.setColor(BLUE);
					g2.fillRect(0, 0, 2, getHeight());
				}
			}
			else if (hovered)
			{
				g2.setColor(NebulaDesign.withAlpha(
						NebulaDesign.currentSurfaceRaised(), 170));
				g2.fillRect(0, 0, getWidth(), getHeight());
			}
			g2.setColor(BORDER);
			g2.fillRect(0, getHeight() - 1, getWidth(), 1);
			g2.dispose();
			super.paintComponent(graphics);
		}
	}

	/** Table header: raised surface, caps-style label, single bottom rule. */
	private static final class FileHeaderRenderer extends DefaultTableCellRenderer
	{
		private static final long serialVersionUID = 1L;

		public Component getTableCellRendererComponent(
				JTable table,
				Object value,
				boolean isSelected,
				boolean hasFocus,
				int row,
				int column)
		{
			JLabel label = (JLabel)super.getTableCellRendererComponent(
					table, value, false, false, row, column);
			label.setOpaque(true);
			label.setBackground(NebulaDesign.SURFACE_RAISED);
			label.setForeground(MUTED);
			label.setFont(NebulaDesign.fontMedium(11f));
			label.setHorizontalAlignment(column == 2
					? SwingConstants.RIGHT : SwingConstants.LEFT);
			label.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(0, 0, 1, 0, NebulaDesign.BORDER_STRONG),
					BorderFactory.createEmptyBorder(
							0, column == 0 ? 12 : 10, 0, column == 2 ? 14 : 10)));
			return label;
		}
	}

	private static JButton createButton(String text, boolean primary)
	{
		PickerButton button = new PickerButton(text, primary);
		NebulaWidgets.polishButton(button);
		return button;
	}

	private static JButton createNavButton(String text, int glyph)
	{
		PickerButton button = new PickerButton(text, false);
		button.setGlyph(glyph);
		NebulaWidgets.polishButton(button);
		return button;
	}

	/** Nebula button: accent primary or neutral outline, painted per mode. */
	private static final class PickerButton extends JButton
	{
		private static final long serialVersionUID = 1L;
		private final boolean primary;
		private int glyph = -1;

		PickerButton(String text, boolean primary)
		{
			super(text);
			this.primary = primary;
			setFont(NebulaDesign.fontMedium(12.5f));
			setFocusPainted(false);
			setRolloverEnabled(true);
			setOpaque(false);
			setContentAreaFilled(false);
			setBorderPainted(false);
			setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
		}

		void setGlyph(int glyph)
		{
			this.glyph = glyph;
			setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 13));
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			int width = getWidth();
			int height = getHeight();
			boolean hover = getModel().isRollover();
			boolean pressed = getModel().isPressed();
			Color fill;
			Color label;
			Color outline = null;
			if (!isEnabled())
			{
				fill = NebulaDesign.SURFACE_ALT;
				label = FAINT;
				outline = BORDER;
			}
			else if (primary)
			{
				fill = pressed ? NebulaDesign.ACCENT_PRESS
						: hover ? NebulaDesign.ACCENT_HOVER : BLUE;
				label = NebulaDesign.TEXT_ON_ACCENT;
			}
			else
			{
				fill = hover || pressed
						? NebulaDesign.SURFACE_RAISED : NebulaDesign.SURFACE;
				label = TEXT;
				outline = hover
						? NebulaDesign.withAlpha(NebulaDesign.currentAccent(), 120)
						: NebulaDesign.BORDER_STRONG;
			}
			g2.setColor(fill);
			g2.fillRoundRect(0, 0, width - 1, height - 1,
					NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
			if (outline != null)
			{
				g2.setColor(outline);
				g2.drawRoundRect(0, 0, width - 1, height - 1,
						NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
			}
			g2.setFont(getFont());
			java.awt.FontMetrics metrics = g2.getFontMetrics();
			int glyphSpan = glyph < 0 ? 0 : 22;
			int textWidth = metrics.stringWidth(getText());
			int startX = (width - textWidth - glyphSpan) / 2;
			int baseline = (height - metrics.getHeight()) / 2 + metrics.getAscent();
			if (glyph >= 0)
			{
				Glyphs.paint(g2, glyph, startX, (height - 14) / 2, 14, label);
			}
			g2.setColor(label);
			g2.drawString(getText(), startX + glyphSpan, baseline);
			g2.dispose();
		}
	}

	/** Dialog canvas: the Nebula vertical wash behind the floating cards. */
	private static final class CanvasPanel extends JPanel
	{
		private static final long serialVersionUID = 1L;

		CanvasPanel()
		{
			setOpaque(true);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			int width = getWidth();
			int height = getHeight();
			g2.setPaint(new java.awt.GradientPaint(
					0, 0, NebulaDesign.CANVAS_TOP,
					0, Math.max(1, height), NebulaDesign.CANVAS_BOTTOM));
			g2.fillRect(0, 0, width, height);
			g2.dispose();
		}
	}

	/** Hero card: surface fill, faint aurora glows, rounded border. */
	private static final class HeroPanel extends JPanel
	{
		private static final long serialVersionUID = 1L;

		HeroPanel(BorderLayout layout)
		{
			super(layout);
			setOpaque(false);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			int width = getWidth();
			int height = getHeight();
			java.awt.Shape round = new java.awt.geom.RoundRectangle2D.Float(
					0, 0, width - 1, height - 1,
					NebulaDesign.RADIUS_CARD, NebulaDesign.RADIUS_CARD);
			g2.setColor(NebulaDesign.SURFACE);
			g2.fill(round);
			java.awt.Shape oldClip = g2.getClip();
			g2.clip(round);
			boolean night = NebulaDesign.night();
			Color accent = night
					? NebulaDesign.NIGHT_ACCENT : NebulaDesign.DAY_ACCENT;
			Color purple = night
					? NebulaDesign.NIGHT_PURPLE : NebulaDesign.DAY_PURPLE;
			int glowAlpha = night ? 26 : 15;
			paintGlow(g2, width * 0.16f, 0f, Math.max(width, height) * 0.45f,
					NebulaDesign.withAlpha(accent, glowAlpha));
			paintGlow(g2, width * 0.88f, height, Math.max(width, height) * 0.40f,
					NebulaDesign.withAlpha(purple, Math.max(6, glowAlpha - 3)));
			g2.setClip(oldClip);
			g2.setColor(BORDER);
			g2.draw(round);
			g2.dispose();
			super.paintComponent(graphics);
		}

		private static void paintGlow(
				Graphics2D g2,
				float centerX,
				float centerY,
				float radius,
				Color color)
		{
			java.awt.RadialGradientPaint paint = new java.awt.RadialGradientPaint(
					new java.awt.geom.Point2D.Float(centerX, centerY),
					Math.max(1f, radius),
					new float[] {0f, 1f},
					new Color[] {color, NebulaDesign.withAlpha(color, 0)});
			g2.setPaint(paint);
			g2.fillRect(
					Math.round(centerX - radius),
					Math.round(centerY - radius),
					Math.round(radius * 2),
					Math.round(radius * 2));
		}
	}

	/** Rounded Nebula card with day shadow / night surface lift. */
	private static class CardPanel extends JPanel
	{
		private static final long serialVersionUID = 1L;

		CardPanel(BorderLayout layout)
		{
			super(layout);
			setOpaque(false);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaWidgets.paintCard(g2, 0, 0, getWidth(), getHeight(),
					NebulaDesign.RADIUS_CARD);
			g2.dispose();
			super.paintComponent(graphics);
		}
	}

	/**
	 * Browser card: paints the sidebar rail band inside the card shape and
	 * clips its contents so the split pane keeps rounded corners.
	 */
	private static final class BrowserCard extends CardPanel
	{
		private static final long serialVersionUID = 1L;
		private JSplitPane rail;

		BrowserCard()
		{
			super(new BorderLayout());
		}

		void trackRail(JSplitPane splitPane)
		{
			rail = splitPane;
		}

		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			if (rail == null)
			{
				return;
			}
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			g2.clip(cardShape());
			int width = Math.max(0, rail.getDividerLocation());
			g2.setColor(NebulaDesign.RAIL);
			g2.fillRect(0, 0, width, getHeight());
			g2.setColor(BORDER);
			g2.fillRect(width, 0, 1, getHeight());
			g2.dispose();
		}

		protected void paintChildren(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			g2.clip(cardShape());
			super.paintChildren(g2);
			g2.dispose();
			Graphics2D outline = (Graphics2D)graphics.create();
			NebulaDesign.prepare(outline);
			outline.setColor(BORDER);
			outline.draw(cardShape());
			outline.dispose();
		}

		private java.awt.Shape cardShape()
		{
			return new java.awt.geom.RoundRectangle2D.Float(
					0, 0, getWidth() - 1, getHeight() - 3,
					NebulaDesign.RADIUS_CARD, NebulaDesign.RADIUS_CARD);
		}
	}

	/** Rounded input well hosting a transparent text field plus a glyph. */
	private static final class FieldWell extends JPanel
	{
		private static final long serialVersionUID = 1L;
		private final JTextField field;
		private final int glyph;
		private final String placeholder;

		FieldWell(JTextField field, int glyph, String placeholder)
		{
			super(new BorderLayout());
			this.field = field;
			this.glyph = glyph;
			this.placeholder = placeholder;
			setOpaque(false);
			setBorder(BorderFactory.createEmptyBorder(
					0, glyph < 0 ? 12 : 33, 0, 12));
			field.setOpaque(false);
			field.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
			field.setForeground(TEXT);
			field.setCaretColor(BLUE);
			field.setSelectionColor(SELECTION);
			field.setSelectedTextColor(TEXT);
			add(field, BorderLayout.CENTER);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			g2.setColor(SURFACE_ALT);
			g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
					NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
			g2.setColor(field.hasFocus()
					? NebulaDesign.withAlpha(NebulaDesign.currentAccent(), 170)
					: BORDER);
			g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
					NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
			if (glyph >= 0)
			{
				Glyphs.paint(g2, glyph, 11, (getHeight() - 15) / 2, 15, FAINT);
			}
			g2.dispose();
			super.paintComponent(graphics);
		}

		protected void paintChildren(Graphics graphics)
		{
			super.paintChildren(graphics);
			if (placeholder == null || field.getText().length() > 0
					|| field.hasFocus())
			{
				return;
			}
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			g2.setFont(field.getFont());
			g2.setColor(FAINT);
			java.awt.FontMetrics metrics = g2.getFontMetrics();
			g2.drawString(placeholder, getInsets().left,
					(getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
			g2.dispose();
		}
	}

	/** Status pill: soft tint capsule behind a strong hue label. */
	private static final class PillLabel extends JLabel
	{
		private static final long serialVersionUID = 1L;
		private Color tint;

		PillLabel()
		{
			super(" ");
			setOpaque(false);
			setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
		}

		void setTone(String text, Color foreground, Color tint)
		{
			this.tint = tint;
			boolean blank = text == null || text.trim().length() == 0;
			setBorder(blank
					? BorderFactory.createEmptyBorder()
					: BorderFactory.createEmptyBorder(3, 10, 3, 10));
			setText(blank ? "" : text);
			setForeground(foreground);
			revalidate();
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			if (tint != null && getText().trim().length() > 0)
			{
				Graphics2D g2 = (Graphics2D)graphics.create();
				NebulaDesign.prepare(g2);
				g2.setColor(tint);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(),
						getHeight(), getHeight());
				g2.dispose();
			}
			super.paintComponent(graphics);
		}
	}

	/** 16px custom checkbox: rounded well, accent fill and tick when on. */
	private static final class CheckBoxIcon implements Icon
	{
		public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			javax.swing.AbstractButton button =
					(javax.swing.AbstractButton)component;
			boolean on = button.isSelected();
			boolean enabled = button.isEnabled();
			int size = getIconWidth();
			if (on && enabled)
			{
				g2.setColor(BLUE);
				g2.fillRoundRect(x, y, size, size, 5, 5);
			}
			else
			{
				g2.setColor(enabled ? SURFACE_ALT : NebulaDesign.INSET);
				g2.fillRoundRect(x, y, size, size, 5, 5);
				g2.setColor(enabled ? NebulaDesign.BORDER_STRONG : BORDER);
				g2.drawRoundRect(x, y, size - 1, size - 1, 5, 5);
			}
			if (on)
			{
				g2.setColor(enabled ? NebulaDesign.TEXT_ON_ACCENT : FAINT);
				g2.setStroke(new java.awt.BasicStroke(2f,
						java.awt.BasicStroke.CAP_ROUND,
						java.awt.BasicStroke.JOIN_ROUND));
				g2.drawLine(x + 4, y + 8, x + 7, y + 11);
				g2.drawLine(x + 7, y + 11, x + 12, y + 5);
			}
			g2.dispose();
		}

		public int getIconWidth()
		{
			return 16;
		}

		public int getIconHeight()
		{
			return 16;
		}
	}

	/** Combo delegate: rounded field, chevron, no bevelled arrow button. */
	private static final class NebulaComboUI
			extends javax.swing.plaf.basic.BasicComboBoxUI
	{
		protected JButton createArrowButton()
		{
			JButton button = new JButton()
			{
				private static final long serialVersionUID = 1L;

				protected void paintComponent(Graphics graphics)
				{
					Graphics2D g2 = (Graphics2D)graphics.create();
					NebulaWidgets.paintChevron(g2, getWidth() / 2,
							getHeight() / 2 - 1, 9, MUTED);
					g2.dispose();
				}
			};
			button.setOpaque(false);
			button.setContentAreaFilled(false);
			button.setBorderPainted(false);
			button.setFocusable(false);
			button.setPreferredSize(new Dimension(26, 26));
			return button;
		}

		public void paint(Graphics graphics, JComponent component)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			g2.setColor(SURFACE_ALT);
			g2.fillRoundRect(0, 0, component.getWidth() - 1,
					component.getHeight() - 1,
					NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
			g2.setColor(NebulaDesign.BORDER_STRONG);
			g2.drawRoundRect(0, 0, component.getWidth() - 1,
					component.getHeight() - 1,
					NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
			g2.dispose();
			super.paint(graphics, component);
		}

		public void paintCurrentValueBackground(
				Graphics graphics,
				java.awt.Rectangle bounds,
				boolean hasFocus)
		{
			// The rounded field fill is painted once in paint().
		}
	}

	/** Popup + closed-state renderer for the filter combo. */
	private static final class ComboItemRenderer extends DefaultListCellRenderer
	{
		private static final long serialVersionUID = 1L;

		public Component getListCellRendererComponent(
				JList<?> list,
				Object value,
				int index,
				boolean isSelected,
				boolean cellHasFocus)
		{
			JLabel label = (JLabel)super.getListCellRendererComponent(
					list, value, index, isSelected, cellHasFocus);
			label.setFont(NebulaDesign.fontMedium(11.5f));
			if (index < 0)
			{
				label.setOpaque(false);
				label.setBorder(BorderFactory.createEmptyBorder(0, 11, 0, 4));
				label.setForeground(TEXT);
			}
			else
			{
				label.setOpaque(true);
				label.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));
				label.setBackground(isSelected
						? NebulaDesign.ACCENT_SOFT : SURFACE);
				label.setForeground(isSelected ? BLUE : TEXT);
			}
			return label;
		}
	}

	/** Icon wrapper so table cells and lists can use the vector glyphs. */
	private static final class GlyphIcon implements Icon
	{
		private final int kind;
		private final int size;
		private final boolean accent;

		GlyphIcon(int kind, int size, boolean accent)
		{
			this.kind = kind;
			this.size = size;
			this.accent = accent;
		}

		public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Glyphs.paint((Graphics2D)graphics, kind, x, y, size,
					accent ? BLUE : FAINT);
		}

		public int getIconWidth()
		{
			return size;
		}

		public int getIconHeight()
		{
			return size;
		}
	}

	/** Small stroked vector glyphs used by the sidebar, toolbar, and fields. */
	private static final class Glyphs
	{
		static final int SEARCH = 0;
		static final int FOLDER = 1;
		static final int CLOCK = 2;
		static final int HOME = 3;
		static final int DRIVE = 4;
		static final int DOWNLOAD = 5;
		static final int BACK = 6;
		static final int UP = 7;
		static final int REFRESH = 8;
		static final int FILE = 9;

		private Glyphs()
		{
		}

		static void paint(
				Graphics2D graphics,
				int kind,
				int x,
				int y,
				int size,
				Color color)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			g2.translate(x, y);
			g2.setColor(color);
			g2.setStroke(new java.awt.BasicStroke(
					Math.max(1.4f, size / 10f),
					java.awt.BasicStroke.CAP_ROUND,
					java.awt.BasicStroke.JOIN_ROUND));
			int span = size;
			int mid = span / 2;
			switch (kind)
			{
				case SEARCH:
					g2.drawOval(1, 1, span - 6, span - 6);
					g2.drawLine(span - 5, span - 5, span - 1, span - 1);
					break;
				case FOLDER:
					g2.drawLine(1, span - 2, span - 2, span - 2);
					g2.draw(new java.awt.geom.RoundRectangle2D.Float(
							1, 3.5f, span - 3, span - 6, 3f, 3f));
					g2.drawLine(1, 3, mid - 1, 3);
					break;
				case CLOCK:
					g2.drawOval(1, 1, span - 3, span - 3);
					g2.drawLine(mid, mid - 3, mid, mid);
					g2.drawLine(mid, mid, mid + 3, mid + 1);
					break;
				case HOME:
					g2.drawLine(1, mid, mid, 1);
					g2.drawLine(mid, 1, span - 2, mid);
					g2.drawLine(3, mid, 3, span - 2);
					g2.drawLine(span - 4, mid, span - 4, span - 2);
					g2.drawLine(3, span - 2, span - 4, span - 2);
					break;
				case DRIVE:
					g2.draw(new java.awt.geom.RoundRectangle2D.Float(
							1, 3f, span - 3, span - 7, 3f, 3f));
					g2.fillOval(span - 6, mid - 1, 2, 2);
					break;
				case DOWNLOAD:
					g2.drawLine(mid, 1, mid, span - 5);
					g2.drawLine(mid - 3, span - 8, mid, span - 5);
					g2.drawLine(mid + 3, span - 8, mid, span - 5);
					g2.drawLine(1, span - 2, span - 2, span - 2);
					break;
				case BACK:
					g2.drawLine(mid + 2, 2, mid - 3, mid);
					g2.drawLine(mid - 3, mid, mid + 2, span - 2);
					break;
				case UP:
					g2.drawLine(mid, span - 2, mid, 2);
					g2.drawLine(mid - 4, 6, mid, 2);
					g2.drawLine(mid + 4, 6, mid, 2);
					break;
				case FILE:
					g2.drawLine(2, 2, span - 5, 2);
					g2.drawLine(2, 2, 2, span - 2);
					g2.drawLine(2, span - 2, span - 3, span - 2);
					g2.drawLine(span - 3, span - 2, span - 3, 5);
					g2.drawLine(span - 5, 2, span - 3, 5);
					break;
				default:
					g2.drawArc(1, 1, span - 3, span - 3, 45, 260);
					g2.drawLine(span - 3, 1, span - 3, 5);
					g2.drawLine(span - 3, 1, span - 7, 1);
					break;
			}
			g2.dispose();
		}
	}

	private static String fileType(File file)
	{
		String name = file.getName();
		int dot = name.lastIndexOf('.');
		if (dot >= 0 && dot < name.length() - 1)
		{
			return name.substring(dot + 1).toUpperCase(Locale.US);
		}
		return "File";
	}

	private static String formatBytes(long bytes)
	{
		if (bytes < 1024)
		{
			return bytes + " B";
		}
		double value = bytes;
		String[] units = {"KB", "MB", "GB", "TB"};
		int unit = -1;
		do
		{
			value /= 1024.0;
			unit++;
		}
		while (value >= 1024.0 && unit < units.length - 1);
		return String.format(Locale.US, value >= 10.0 ? "%.0f %s" : "%.1f %s",
				value, units[unit]);
	}

	private static String formatDate(long timestamp)
	{
		DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
		return format.format(new Date(timestamp));
	}
}
