package com.cse471.app;

import com.cse471.gui.MainFrame;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        boolean headless = false;
        for (String arg : args) {
            if (arg.equals("--headless")) {
                headless = true;
                break;
            }
        }

        if (headless) {
            System.out.println("Starting in Headless Mode...");
            com.cse471.app.AppController.getInstance().initialize(null);
            com.cse471.app.AppController.getInstance().startNetwork();

            // Keep alive
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            // Setup Look and Feel
            try {
                UIManager.setLookAndFeel(new FlatDarkLaf());
            } catch (Exception ex) {
                System.err.println("Failed to initialize FlatLaf");
            }

            // Launch GUI
            SwingUtilities.invokeLater(() -> {
                MainFrame frame = new MainFrame();
                frame.setVisible(true);
            });
        }
    }
}
