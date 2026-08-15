package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
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
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.plaf.basic.BasicButtonUI;

final class ModernFilePicker
{
	private static final Color BACKGROUND = new Color(244, 247, 251);
	private static final Color SURFACE = Color.WHITE;
	private static final Color SURFACE_ALT = new Color(248, 250, 253);
	private static final Color BORDER = new Color(207, 216, 227);
	private static final Color TEXT = new Color(24, 29, 36);
	private static final Color MUTED = new Color(91, 101, 116);
	private static final Color BLUE = new Color(0, 113, 227);
	private static final Color BLUE_HOVER = new Color(38, 139, 236);
	private static final Color GREEN = new Color(20, 150, 105);
	private static final Color WARNING = new Color(185, 96, 18);
	private static final Color SELECTION = new Color(217, 236, 255);
	private static final int MAX_SEARCHED_ENTRIES = 20000;
	private static final int MAX_SEARCH_RESULTS = 1000;
	private static final String FILTER_DATASETS = "Dataset files (.txt, .csv, .tsv, .xlsx)";
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
		private final JTable filesTable = new JTable(tableModel);
		private final JTextField searchField = new JTextField();
		private final JTextField pathField = new JTextField();
		private final JLabel statusLabel = new JLabel(" ");
		private final JLabel selectedNameLabel = new JLabel("No file selected");
		private final JLabel selectedInfoLabel = new JLabel("Choose a readable file to continue.");
		private final JLabel compatibilityLabel = new JLabel(" ");
		private final JButton backButton = createButton("Back", false);
		private final JButton upButton = createButton("Up", false);
		private final JButton homeButton = createButton("Home", false);
		private final JButton refreshButton = createButton("Refresh", false);
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
			JPanel root = new JPanel(new BorderLayout(0, 0));
			root.setBackground(BACKGROUND);
			root.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

			root.add(createHeader(), BorderLayout.NORTH);
			root.add(createBrowser(), BorderLayout.CENTER);
			root.add(createFooter(), BorderLayout.SOUTH);
			setContentPane(root);
			getRootPane().setDefaultButton(openButton);
		}

		private JComponent createHeader()
		{
			JPanel header = new JPanel(new BorderLayout(24, 0));
			header.setBackground(SURFACE);
			header.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
					BorderFactory.createEmptyBorder(18, 22, 18, 22)));

			JPanel identity = new JPanel();
			identity.setOpaque(false);
			identity.setLayout(new javax.swing.BoxLayout(
					identity, javax.swing.BoxLayout.Y_AXIS));
			JLabel title = new JLabel(inspectDataset ? "Open a dataset" : "Open a text file");
			title.setFont(font(Font.BOLD, 20));
			title.setForeground(TEXT);
			JLabel subtitle = new JLabel(inspectDataset
					? "Browse or search your folders. Dataset structure is checked before loading."
					: "Browse or search your folders for a corpus file.");
			subtitle.setFont(font(Font.PLAIN, 12));
			subtitle.setForeground(MUTED);
			identity.add(title);
			identity.add(javax.swing.Box.createRigidArea(new Dimension(0, 4)));
			identity.add(subtitle);

			JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
			searchPanel.setOpaque(false);
			searchPanel.setPreferredSize(new Dimension(350, 38));
			JLabel searchLabel = new JLabel("Search");
			searchLabel.setFont(font(Font.BOLD, 12));
			searchLabel.setForeground(MUTED);
			searchField.setFont(font(Font.PLAIN, 13));
			searchField.setToolTipText(
					"Search file and folder names inside the current location");
			searchField.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(BORDER, 1),
					BorderFactory.createEmptyBorder(8, 10, 8, 10)));
			searchPanel.add(searchLabel, BorderLayout.WEST);
			searchPanel.add(searchField, BorderLayout.CENTER);

			header.add(identity, BorderLayout.CENTER);
			header.add(searchPanel, BorderLayout.EAST);
			return header;
		}

		private JComponent createBrowser()
		{
			JPanel browser = new JPanel(new BorderLayout(0, 0));
			browser.setBackground(BACKGROUND);

			locationsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			locationsList.setCellRenderer(new LocationRenderer());
			locationsList.setBackground(SURFACE_ALT);
			locationsList.setBorder(BorderFactory.createEmptyBorder(12, 8, 12, 8));
			JScrollPane locationsScroll = new JScrollPane(locationsList);
			locationsScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER));
			locationsScroll.setPreferredSize(new Dimension(190, 100));

			JPanel fileBrowser = new JPanel(new BorderLayout(0, 0));
			fileBrowser.setBackground(SURFACE);
			fileBrowser.add(createNavigationBar(), BorderLayout.NORTH);
			fileBrowser.add(createFileTable(), BorderLayout.CENTER);

			JSplitPane splitPane = new JSplitPane(
					JSplitPane.HORIZONTAL_SPLIT, locationsScroll, fileBrowser);
			splitPane.setDividerSize(1);
			splitPane.setDividerLocation(190);
			splitPane.setResizeWeight(0.0);
			splitPane.setBorder(null);
			browser.add(splitPane, BorderLayout.CENTER);
			return browser;
		}

		private JComponent createNavigationBar()
		{
			JPanel toolbar = new JPanel(new BorderLayout(10, 0));
			toolbar.setBackground(SURFACE);
			toolbar.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
					BorderFactory.createEmptyBorder(10, 12, 10, 12)));

			JPanel navigationButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
			navigationButtons.setOpaque(false);
			navigationButtons.add(backButton);
			navigationButtons.add(upButton);
			navigationButtons.add(homeButton);
			navigationButtons.add(refreshButton);

			pathField.setFont(font(Font.PLAIN, 12));
			pathField.setForeground(TEXT);
			pathField.setBackground(SURFACE_ALT);
			pathField.setToolTipText("Type a folder path and press Enter");
			pathField.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(BORDER, 1),
					BorderFactory.createEmptyBorder(7, 9, 7, 9)));

			toolbar.add(navigationButtons, BorderLayout.WEST);
			toolbar.add(pathField, BorderLayout.CENTER);
			return toolbar;
		}

		private JComponent createFileTable()
		{
			filesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			filesTable.setRowHeight(30);
			filesTable.setShowHorizontalLines(false);
			filesTable.setShowVerticalLines(false);
			filesTable.setIntercellSpacing(new Dimension(0, 0));
			filesTable.setFillsViewportHeight(true);
			filesTable.setBackground(SURFACE);
			filesTable.setForeground(TEXT);
			filesTable.setSelectionBackground(SELECTION);
			filesTable.setSelectionForeground(TEXT);
			filesTable.setFont(font(Font.PLAIN, 12));
			filesTable.getTableHeader().setFont(font(Font.BOLD, 11));
			filesTable.getTableHeader().setBackground(SURFACE_ALT);
			filesTable.getTableHeader().setForeground(MUTED);
			filesTable.getTableHeader().setReorderingAllowed(false);
			filesTable.setAutoCreateRowSorter(true);

			filesTable.getColumnModel().getColumn(0).setPreferredWidth(330);
			filesTable.getColumnModel().getColumn(1).setPreferredWidth(100);
			filesTable.getColumnModel().getColumn(2).setPreferredWidth(95);
			filesTable.getColumnModel().getColumn(3).setPreferredWidth(155);
			filesTable.getColumnModel().getColumn(4).setPreferredWidth(280);
			filesTable.getColumnModel().getColumn(0).setCellRenderer(new FileNameRenderer());
			filesTable.getColumnModel().getColumn(2).setCellRenderer(new FileSizeRenderer());
			filesTable.getColumnModel().getColumn(3).setCellRenderer(new DateRenderer());

			JScrollPane tableScroll = new JScrollPane(filesTable);
			tableScroll.setBorder(null);
			tableScroll.getViewport().setBackground(SURFACE);
			return tableScroll;
		}

		private JComponent createFooter()
		{
			JPanel footer = new JPanel(new BorderLayout(16, 0));
			footer.setBackground(SURFACE);
			footer.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
					BorderFactory.createEmptyBorder(12, 18, 14, 18)));

			JPanel selectedPanel = new JPanel();
			selectedPanel.setOpaque(false);
			selectedPanel.setLayout(new javax.swing.BoxLayout(
					selectedPanel, javax.swing.BoxLayout.Y_AXIS));
			selectedNameLabel.setFont(font(Font.BOLD, 13));
			selectedNameLabel.setForeground(TEXT);
			selectedInfoLabel.setFont(font(Font.PLAIN, 11));
			selectedInfoLabel.setForeground(MUTED);
			compatibilityLabel.setFont(font(Font.BOLD, 11));
			compatibilityLabel.setForeground(MUTED);
			selectedPanel.add(selectedNameLabel);
			selectedPanel.add(javax.swing.Box.createRigidArea(new Dimension(0, 3)));
			selectedPanel.add(selectedInfoLabel);
			selectedPanel.add(javax.swing.Box.createRigidArea(new Dimension(0, 3)));
			selectedPanel.add(compatibilityLabel);

			JPanel controls = new JPanel(new GridLayout(2, 1, 0, 8));
			controls.setOpaque(false);
			JPanel options = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
			options.setOpaque(false);
			filterBox.setFont(font(Font.PLAIN, 11));
			filterBox.setPreferredSize(new Dimension(210, 28));
			hiddenFilesBox.setOpaque(false);
			hiddenFilesBox.setForeground(MUTED);
			hiddenFilesBox.setFont(font(Font.PLAIN, 11));
			options.add(hiddenFilesBox);
			options.add(filterBox);

			JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
			actions.setOpaque(false);
			actions.add(cancelButton);
			actions.add(openButton);
			controls.add(options);
			controls.add(actions);

			JPanel footerCenter = new JPanel(new BorderLayout(12, 0));
			footerCenter.setOpaque(false);
			statusLabel.setFont(font(Font.PLAIN, 11));
			statusLabel.setForeground(MUTED);
			footerCenter.add(selectedPanel, BorderLayout.CENTER);
			footerCenter.add(statusLabel, BorderLayout.SOUTH);

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
			locationsModel.addElement(new Location("Recent files", null, true));

			File project = new File(System.getProperty("user.dir"));
			addLocation("Project", project);
			addLocation("Dataset", new File(project, "Dataset"));
			addLocation("Tested datasets", new File(project, "TestedDataset"));

			File home = new File(System.getProperty("user.home"));
			addLocation("Home", home);
			addLocation("Downloads", new File(home, "Downloads"));
			addLocation("Desktop", new File(home, "Desktop"));
			addLocation("Documents", new File(home, "Documents"));

			File[] roots = File.listRoots();
			if (roots != null)
			{
				for (File root : roots)
				{
					addLocation(root.getPath(), root);
				}
			}
		}

		private void addLocation(String name, File directory)
		{
			if (directory != null && directory.isDirectory())
			{
				locationsModel.addElement(new Location(name, directory, false));
			}
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

			statusLabel.setForeground(MUTED);
			statusLabel.setText(recursive ? "Searching this folder and its subfolders..."
					: "Loading files...");
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
						statusLabel.setForeground(MUTED);
					}
					catch (Exception exception)
					{
						tableModel.setEntries(Collections.<FileEntry>emptyList());
						statusLabel.setText("Files could not be loaded: "
								+ exception.getMessage());
						statusLabel.setForeground(WARNING);
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
			statusLabel.setForeground(MUTED);
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
			openButton.setText(file.isDirectory() ? "Open Folder" : "Open");
			openButton.setEnabled(file.isDirectory()
					? file.canRead() : file.isFile() && file.canRead());
			compatibilityLabel.setText(" ");
			compatibilityLabel.setForeground(MUTED);
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
			compatibilityLabel.setText(" ");
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
			compatibilityLabel.setText("Checking dataset structure...");
			compatibilityLabel.setForeground(MUTED);
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
						compatibilityLabel.setText(result.substring(result.indexOf('\t') + 1));
						compatibilityLabel.setForeground(ready ? GREEN : WARNING);
					}
					catch (Exception exception)
					{
						compatibilityLabel.setText("The file could not be inspected.");
						compatibilityLabel.setForeground(WARNING);
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

		Location(String name, File directory, boolean recent)
		{
			this.name = name;
			this.directory = directory;
			this.recent = recent;
		}
	}

	private static final class LocationRenderer extends DefaultListCellRenderer
	{
		private static final long serialVersionUID = 1L;

		public Component getListCellRendererComponent(
				JList<?> list,
				Object value,
				int index,
				boolean isSelected,
				boolean cellHasFocus)
		{
			JLabel label = (JLabel) super.getListCellRendererComponent(
					list, value, index, isSelected, cellHasFocus);
			Location location = (Location) value;
			label.setText(location.name);
			label.setFont(font(Font.BOLD, 12));
			label.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
			label.setForeground(isSelected ? BLUE : TEXT);
			label.setBackground(isSelected ? SELECTION : SURFACE_ALT);
			return label;
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
			Icon systemIcon;
			try
			{
				systemIcon = FileSystemView.getFileSystemView().getSystemIcon(file);
			}
			catch (RuntimeException exception)
			{
				systemIcon = file.isDirectory()
						? UIManager.getIcon("FileView.directoryIcon")
						: UIManager.getIcon("FileView.fileIcon");
			}
			icon = systemIcon;
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
				{"Name", "Kind", "Size", "Modified", "Location"};
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

	private static final class FileNameRenderer extends JLabel
			implements TableCellRenderer
	{
		private static final long serialVersionUID = 1L;

		FileNameRenderer()
		{
			setOpaque(true);
			setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 6));
			setFont(font(Font.PLAIN, 12));
		}

		public Component getTableCellRendererComponent(
				JTable table,
				Object value,
				boolean isSelected,
				boolean hasFocus,
				int row,
				int column)
		{
			FileEntry entry = (FileEntry) value;
			setText(entry == null ? "" : entry.file.getName());
			setIcon(entry == null ? null : entry.icon);
			setIconTextGap(8);
			setForeground(TEXT);
			setBackground(isSelected ? SELECTION
					: row % 2 == 0 ? SURFACE : SURFACE_ALT);
			return this;
		}
	}

	private static final class FileSizeRenderer extends DefaultTableCellRenderer
	{
		private static final long serialVersionUID = 1L;

		FileSizeRenderer()
		{
			setHorizontalAlignment(SwingConstants.RIGHT);
		}

		protected void setValue(Object value)
		{
			setText(value instanceof Long ? formatBytes(((Long) value).longValue()) : "");
		}
	}

	private static final class DateRenderer extends DefaultTableCellRenderer
	{
		private static final long serialVersionUID = 1L;

		protected void setValue(Object value)
		{
			setText(value instanceof Date ? formatDate(((Date) value).getTime()) : "");
		}
	}

	private static JButton createButton(String text, final boolean primary)
	{
		final JButton button = new JButton(text);
		button.setUI(new BasicButtonUI());
		button.setFont(font(Font.BOLD, 12));
		button.setFocusPainted(false);
		button.setOpaque(true);
		button.setContentAreaFilled(true);
		button.setBorderPainted(true);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.setMargin(new Insets(7, 13, 7, 13));
		button.setForeground(primary ? Color.WHITE : TEXT);
		button.setBackground(primary ? BLUE : new Color(239, 243, 248));
		button.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(primary ? BLUE : BORDER, 1),
				BorderFactory.createEmptyBorder(2, 4, 2, 4)));
		button.addPropertyChangeListener("enabled", event ->
				applyButtonState(button, primary));
		button.addMouseListener(new MouseAdapter() {
			public void mouseEntered(MouseEvent event)
			{
				if (button.isEnabled())
				{
					button.setBackground(primary ? BLUE_HOVER : new Color(228, 235, 244));
				}
			}

			public void mouseExited(MouseEvent event)
			{
				applyButtonState(button, primary);
			}
		});
		return button;
	}

	private static void applyButtonState(JButton button, boolean primary)
	{
		boolean enabled = button.isEnabled();
		button.setForeground(enabled && primary ? Color.WHITE
				: enabled ? TEXT : new Color(139, 149, 163));
		button.setBackground(enabled && primary ? BLUE : new Color(239, 243, 248));
		button.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(enabled && primary ? BLUE : BORDER, 1),
				BorderFactory.createEmptyBorder(2, 4, 2, 4)));
	}

	private static Font font(int style, int size)
	{
		return new Font("SansSerif", style, size);
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
		DateFormat format = new SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.US);
		return format.format(new Date(timestamp));
	}
}
