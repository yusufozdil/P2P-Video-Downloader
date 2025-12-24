package com.cse471.gui;

import com.cse471.file.FileInfo;
import com.cse471.network.DiscoveryManager;
import com.cse471.network.TransferManager;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame {

    private JMenuItem connectItem;
    private JMenuItem disconnectItem;
    private JMenuItem setRootItem;
    private JMenuItem setBufferItem;
    private JTextField searchField;
    private JButton searchButton;
    private JList<FileInfo> availableList;
    private JTable activeStreamsTable;
    private DefaultListModel<FileInfo> availableListModel;
    private javax.swing.table.DefaultTableModel activeStreamsModel;
    private com.cse471.player.StreamPlayer streamPlayer;
    private JTextArea eventLog;
    private JLabel statusLabel;

    // Theme
    private boolean isDarkTheme = true;

    public MainFrame() {
        initUI();
    }

    public com.cse471.player.StreamPlayer getStreamPlayer() {
        return streamPlayer;
    }

    private void initUI() {
        // Apply Initial Theme
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception ex) {
            System.err.println("Failed to initialize LaF");
        }

        setTitle("P2P Video Streaming - CSE471");
        setSize(1200, 800);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Menu
        JMenuBar menuBar = new JMenuBar();
        JMenu streamMenu = new JMenu("Stream");
        JMenu viewMenu = new JMenu("View");
        JMenu helpMenu = new JMenu("Help");

        connectItem = new JMenuItem("Connect");
        disconnectItem = new JMenuItem("Disconnect");
        setRootItem = new JMenuItem("Set Root Video Folder");
        setBufferItem = new JMenuItem("Set Buffer Folder");

        streamMenu.add(connectItem);
        streamMenu.add(disconnectItem);
        streamMenu.addSeparator();
        streamMenu.add(setRootItem);
        streamMenu.add(setBufferItem);

        JMenuItem toggleThemeItem = new JMenuItem("Toggle Theme (Dark/Light)");
        viewMenu.add(toggleThemeItem);

        JMenuItem aboutItem = new JMenuItem("About Developer");
        helpMenu.add(aboutItem);

        menuBar.add(streamMenu);
        menuBar.add(viewMenu);
        menuBar.add(helpMenu);
        setJMenuBar(menuBar);

        // -- Actions --

        // Theme Action
        toggleThemeItem.addActionListener(e -> {
            isDarkTheme = !isDarkTheme;
            try {
                if (isDarkTheme) {
                    UIManager.setLookAndFeel(new FlatDarkLaf());
                } else {
                    UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatLightLaf());
                }
                SwingUtilities.updateComponentTreeUI(this);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        // About Action
        aboutItem.addActionListener(e -> {
            JOptionPane.showMessageDialog(this,
                    "Developer: Yusuf Özdil\nContact: yusuf.ozdil@std.yeditepe.edu.tr",
                    "About Developer",
                    JOptionPane.INFORMATION_MESSAGE);
        });

        // -- Layout Construction --
        setLayout(new BorderLayout());

        // 1. Status Panel (Top)
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel = new JLabel("Status: DISCONNECTED | Root: Not Set | Buffer: Not Set");
        statusPanel.add(statusLabel);
        statusPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY));
        add(statusPanel, BorderLayout.NORTH);

        // 2. Main Split Pane (Left vs Right)

        // Left Panel: Search + Available List
        JPanel leftPanel = new JPanel(new BorderLayout());

        JPanel searchPanel = new JPanel(new GridBagLayout());
        searchPanel.setBorder(BorderFactory.createTitledBorder("Search Videos"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        searchField = new JTextField();
        searchButton = new JButton("Search");

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        searchPanel.add(searchField, gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        searchPanel.add(searchButton, gbc);

        availableListModel = new DefaultListModel<>();
        availableList = new JList<>(availableListModel);
        JScrollPane listScroll = new JScrollPane(availableList);
        listScroll.setBorder(BorderFactory.createTitledBorder("Available Videos in Network"));

        leftPanel.add(searchPanel, BorderLayout.NORTH);
        leftPanel.add(listScroll, BorderLayout.CENTER);
        leftPanel.setPreferredSize(new Dimension(300, 0));

        // Right Panel: Active Streams (Top) + Video Player (Center)
        // We use a Vertical Split Pane

        // Active Streams Table
        String[] columnNames = { "Video", "Source Peer", "Progress %", "Status" };
        activeStreamsModel = new javax.swing.table.DefaultTableModel(columnNames, 0);
        activeStreamsTable = new JTable(activeStreamsModel);
        JScrollPane tableScroll = new JScrollPane(activeStreamsTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Active Streams"));
        tableScroll.setPreferredSize(new Dimension(0, 150)); // Fixed height for table

        // Video Player
        streamPlayer = new com.cse471.player.StreamPlayer();
        streamPlayer.setBorder(BorderFactory.createTitledBorder("Video Player")); // Clean border

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, streamPlayer);
        rightSplit.setResizeWeight(0.3); // Table gets 30%, Player gets 70%
        rightSplit.setDividerLocation(200);

        // Combine Left and Right
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightSplit);
        mainSplit.setResizeWeight(0.25); // Left panel width ratio
        mainSplit.setDividerLocation(300);

        add(mainSplit, BorderLayout.CENTER);

        // 3. Event Log (Bottom)
        eventLog = new JTextArea(5, 50);
        eventLog.setEditable(false);
        JScrollPane logScroll = new JScrollPane(eventLog);
        logScroll.setBorder(BorderFactory.createTitledBorder("Event Log"));
        add(logScroll, BorderLayout.SOUTH);

        // -- Logic Links --

        connectItem.addActionListener(e -> {
            com.cse471.app.AppController.getInstance().startNetwork();
            log("Network Started.");
            updateStatusLabel("CONNECTED");
        });

        disconnectItem.addActionListener(e -> {
            com.cse471.app.AppController.getInstance().stopNetwork();
            log("Network Stopped.");
            updateStatusLabel("DISCONNECTED");
        });

        setRootItem.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                com.cse471.app.AppController.getInstance().setRootFolder(chooser.getSelectedFile());
                log("Root folder set: " + chooser.getSelectedFile().getAbsolutePath());
                updateStatusLabel("CONNECTED"); // Simplified update
            }
        });

        setBufferItem.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                com.cse471.app.AppController.getInstance().setBufferFolder(chooser.getSelectedFile());
                log("Buffer folder set: " + chooser.getSelectedFile().getAbsolutePath());
                updateStatusLabel("CONNECTED");
            }
        });

        // Initialize Controller
        com.cse471.app.AppController.getInstance().initialize(this);
        log("Application initialized.");

        // Search Action
        searchButton.addActionListener(e -> {
            String q = searchField.getText().trim();
            if (!q.isEmpty()) {
                com.cse471.app.AppController.getInstance().searchFiles(q);
                log("Searching for: " + q);
            }
        });
    }

    public void updateAvailableFiles(java.util.List<FileInfo> files) {
        availableListModel.clear();
        for (FileInfo f : files) {
            availableListModel.addElement(f);
        }
        log("Search returned " + files.size() + " result(s).");

        // Add Listener
        for (java.awt.event.MouseListener ml : availableList.getMouseListeners()) {
            availableList.removeMouseListener(ml);
        }
        availableList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    FileInfo selected = availableList.getSelectedValue();
                    if (selected != null) {
                        log("Requesting: " + selected.getFileName());
                        com.cse471.app.AppController.getInstance().playVideo(selected);
                    }
                }
            }
        });
    }

    // Updated for structured table data
    public void addActiveStream(String video, String source, String progress, String status) {
        activeStreamsModel.addRow(new Object[] { video, source, progress, status });
        // Scroll to the bottom
        activeStreamsTable
                .scrollRectToVisible(activeStreamsTable.getCellRect(activeStreamsTable.getRowCount() - 1, 0, true));
    }

    public void log(String message) {
        eventLog.append(message + "\n");
        eventLog.setCaretPosition(eventLog.getDocument().getLength());
    }

    private void updateStatusLabel(String connectionStatus) {
        // This would ideally pull from AppController state
        statusLabel.setText("Status: " + connectionStatus);
    }
}
