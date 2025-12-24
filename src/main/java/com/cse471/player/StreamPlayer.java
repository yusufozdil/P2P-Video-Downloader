package com.cse471.player;

import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent;
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery;

import javax.swing.*;
import java.awt.*;

public class StreamPlayer extends JPanel {
    private CallbackMediaPlayerComponent mediaPlayerComponent;

    public StreamPlayer() {
        setLayout(new BorderLayout());
        setBackground(Color.BLACK);

        try {
            // Find VLC
            new NativeDiscovery().discover();

            // Use CallbackMediaPlayerComponent for Software Rendering
            // This bypasses hardware surface issues on macOS
            mediaPlayerComponent = new CallbackMediaPlayerComponent();
            add(mediaPlayerComponent, BorderLayout.CENTER);

            // Add Diagnostic Listener
            mediaPlayerComponent.mediaPlayer().events()
                    .addMediaPlayerEventListener(new uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter() {
                        @Override
                        public void playing(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer) {
                            System.out.println("VLCj (Software) Status: Playing");
                        }

                        @Override
                        public void error(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer) {
                            System.err.println("VLCj (Software) Status: Error occurred.");
                        }

                        @Override
                        public void finished(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer) {
                            System.out.println("VLCj (Software) Status: Finished");
                        }
                    });

        } catch (Error | Exception e) {
            JLabel errorLabel = new JLabel("<html><center>VLCj Error: VLC not found or library missing.<br>"
                    + e.getMessage() + "</center></html>");
            errorLabel.setForeground(Color.RED);
            errorLabel.setHorizontalAlignment(SwingConstants.CENTER);
            add(errorLabel, BorderLayout.CENTER);
            e.printStackTrace();

        }

        initControls();
    }

    private void initControls() {
        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        controlsPanel.setBackground(Color.DARK_GRAY);

        JButton pauseButton = new JButton("Pause/Resume");
        JButton stopButton = new JButton("Stop");

        pauseButton.addActionListener(e -> pause());
        stopButton.addActionListener(e -> stop());

        controlsPanel.add(pauseButton);
        controlsPanel.add(stopButton);

        add(controlsPanel, BorderLayout.SOUTH);
    }

    private String currentFilePath;

    public void play(String filePath) {
        this.currentFilePath = filePath;
        System.out.println("StreamPlayer (Software): play() called for " + filePath);
        if (mediaPlayerComponent != null) {
            boolean result = mediaPlayerComponent.mediaPlayer().media().play(filePath);
            System.out.println("Invocation Result: " + result);
        }
    }

    public void stop() {
        if (mediaPlayerComponent != null) {
            mediaPlayerComponent.mediaPlayer().controls().stop();
        }
    }

    public void pause() {
        if (mediaPlayerComponent != null) {
            var player = mediaPlayerComponent.mediaPlayer();
            // If the video ended or stopped, restart properly
            if (currentFilePath != null && !player.status().isPlaying() && !player.status().isPlayable()) {
                play(currentFilePath);
            } else {
                player.controls().pause();
            }
        }
    }

    public void release() {
        if (mediaPlayerComponent != null) {
            mediaPlayerComponent.release();
        }
    }
}
