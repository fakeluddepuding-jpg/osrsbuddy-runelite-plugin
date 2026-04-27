package com.osrsbuddy;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;

@Singleton
public class OsrsBuddyPanel extends PluginPanel
{
    @Inject private OsrsBuddyConfig config;

    private OsrsBuddyPlugin plugin;
    private final JLabel status = new JLabel("Not paired");
    private final JTextField codeField = new JTextField();
    private final JButton pairBtn = new JButton("Pair");
    private final JButton syncBtn = new JButton("Sync now");
    private final JButton unpairBtn = new JButton("Unpair");
    private final JLabel message = new JLabel(" ");

    public void init(OsrsBuddyPlugin plugin)
    {
        this.plugin = plugin;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel("OSRSBuddy");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        add(title);
        add(Box.createVerticalStrut(8));

        add(status);
        add(Box.createVerticalStrut(12));

        JLabel hint = new JLabel("<html>Generate a pairing code on<br>osrsbuddy.com → Character page,<br>then paste it below.</html>");
        hint.setForeground(Color.LIGHT_GRAY);
        add(hint);
        add(Box.createVerticalStrut(8));

        codeField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        add(codeField);
        add(Box.createVerticalStrut(6));

        pairBtn.addActionListener(e -> {
            String c = codeField.getText();
            if (c == null || c.trim().isEmpty()) { showError("Enter a pairing code"); return; }
            pairBtn.setEnabled(false);
            plugin.pair(c, () -> SwingUtilities.invokeLater(() -> {
                pairBtn.setEnabled(true);
                refreshStatus();
            }));
        });
        add(pairBtn);
        add(Box.createVerticalStrut(12));

        syncBtn.addActionListener(e -> plugin.manualSync());
        add(syncBtn);
        add(Box.createVerticalStrut(6));

        unpairBtn.addActionListener(e -> plugin.unpair());
        add(unpairBtn);
        add(Box.createVerticalStrut(12));

        message.setForeground(Color.LIGHT_GRAY);
        add(message);

        refreshStatus();
    }

    public void refreshStatus()
    {
        SwingUtilities.invokeLater(() -> {
            boolean paired = plugin != null && plugin.isPaired();
            status.setText(paired ? "Paired ✓" : "Not paired");
            status.setForeground(paired ? new Color(120, 200, 120) : Color.LIGHT_GRAY);
            syncBtn.setEnabled(paired);
            unpairBtn.setEnabled(paired);
            pairBtn.setEnabled(!paired);
            codeField.setEnabled(!paired);
        });
    }

    public void showError(String msg)
    {
        SwingUtilities.invokeLater(() -> {
            message.setForeground(new Color(220, 120, 120));
            message.setText("<html>" + msg + "</html>");
        });
    }

    public void showInfo(String msg)
    {
        SwingUtilities.invokeLater(() -> {
            message.setForeground(Color.LIGHT_GRAY);
            message.setText("<html>" + msg + "</html>");
        });
    }
}
