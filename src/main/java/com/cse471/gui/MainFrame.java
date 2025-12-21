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
    private JList<String> activeStreamsList;
    private DefaultListModel<FileInfo> availableListModel;
    private DefaultListModel<String> activeStreamsModel;
    private com.cse471.player.StreamPlayer streamPlayer;

    public MainFrame() {
        initUI();
    }

    public com.cse471.player.StreamPlayer getStreamPlayer() {
        return streamPlayer;
    }

    private void initUI() {
        setTitle("P2P Video Streaming - CSE471");
        setSize(1000, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Menu
        JMenuBar menuBar = new JMenuBar();
        JMenu streamMenu = new JMenu("Stream");
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

        helpMenu.add(new JMenuItem("About Developer"));

        menuBar.add(streamMenu);
        menuBar.add(helpMenu);
        setJMenuBar(menuBar);

        // Layout
        setLayout(new BorderLayout());

        // Top Panel: Search
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchField = new JTextField(30);
        searchButton = new JButton("Search");
        topPanel.add(new JLabel("Search File:"));
        topPanel.add(searchField);
        topPanel.add(searchButton);
        add(topPanel, BorderLayout.NORTH);

        // Center Split: Left (Network Files), Center (Player Placeholder), Right
        // (Active Streams)

        // Left: Available Videos
        availableListModel = new DefaultListModel<>();
        availableList = new JList<>(availableListModel);
        JScrollPane leftScroll = new JScrollPane(availableList);
        leftScroll.setBorder(BorderFactory.createTitledBorder("Available Videos on Network"));
        leftScroll.setPreferredSize(new Dimension(250, 0));

        // Right (or Bottom): Active Streams
        activeStreamsModel = new DefaultListModel<>();
        activeStreamsList = new JList<>(activeStreamsModel);
        JScrollPane rightScroll = new JScrollPane(activeStreamsList);
        rightScroll.setBorder(BorderFactory.createTitledBorder("Active Streams"));
        rightScroll.setPreferredSize(new Dimension(250, 0));

        // Center: Player
        streamPlayer = new com.cse471.player.StreamPlayer();
        streamPlayer.setBorder(BorderFactory.createTitledBorder("Video Player"));

        // Main Split
        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, streamPlayer, rightScroll);
        rightSplit.setResizeWeight(0.8);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, rightSplit);
        mainSplit.setResizeWeight(0.2);

        add(mainSplit, BorderLayout.CENTER);

        // Actions
        connectItem.addActionListener(e -> com.cse471.app.AppController.getInstance().startNetwork());
        disconnectItem.addActionListener(e -> com.cse471.app.AppController.getInstance().stopNetwork());

        setRootItem.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                com.cse471.app.AppController.getInstance().setRootFolder(chooser.getSelectedFile());
            }
        });

        setBufferItem.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                com.cse471.app.AppController.getInstance().setBufferFolder(chooser.getSelectedFile());
            }
        });

        // Initialize Controller
        com.cse471.app.AppController.getInstance().initialize(this);

        // Search Action
        searchButton.addActionListener(e -> {
            String q = searchField.getText().trim();
            if (!q.isEmpty()) {
                com.cse471.app.AppController.getInstance().searchFiles(q);
            }
        });
    }

    public void updateAvailableFiles(java.util.List<FileInfo> files) {
        availableListModel.clear();
        for (FileInfo f : files) {
            availableListModel.addElement(f);
        }

        // Add Listener if not already (simple check or re-add)
        for (java.awt.event.MouseListener ml : availableList.getMouseListeners()) {
            availableList.removeMouseListener(ml);
        }
        availableList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    FileInfo selected = availableList.getSelectedValue();
                    if (selected != null) {
                        com.cse471.app.AppController.getInstance().playVideo(selected);
                    }
                }
            }
        });
    }

    public void addActiveStream(String status) {
        activeStreamsModel.addElement(status);
    }
}
