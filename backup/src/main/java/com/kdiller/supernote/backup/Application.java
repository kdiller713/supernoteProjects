package com.kdiller.supernote.backup;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.BoxLayout;

import java.awt.FlowLayout;
import java.awt.GridLayout;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Application extends JFrame implements BackupTask.BackupTaskListener {
    private static final DateTimeFormatter FILENAME_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyMMdd_HHmmss");
    private static Logger logger = LoggerFactory.getLogger(Application.class);
    
    public static void main(String[] args){
        SwingUtilities.invokeLater(() -> {
            Application app = new Application();
            app.setVisible(true);
        });
    }
    
    private JTextField ipBox, portBox;
    private JButton runButton;
    private JFileChooser outputDirectoryChooser;
    private JLabel outputDirectoryLabel;
    
    public Application() {
        super("Super Note Backup Utility");
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        ipBox = new JTextField("192.168.1.1", 15);
        portBox = new JTextField("8089", 5);
        
        JPanel urlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        urlPanel.add(new JLabel("http://"));
        urlPanel.add(ipBox);
        urlPanel.add(new JLabel(":"));
        urlPanel.add(portBox);
        
        runButton = new JButton("Run Backup");
        runButton.addActionListener((e) -> {
            runBackup();
        });
        
        outputDirectoryChooser = new JFileChooser();
        outputDirectoryChooser.setCurrentDirectory(new File("."));
        outputDirectoryChooser.setDialogTitle("Backup Directory");
        outputDirectoryChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        outputDirectoryChooser.setAcceptAllFileFilterUsed(false);
        
        outputDirectoryLabel = new JLabel(outputDirectoryChooser.getCurrentDirectory().getAbsolutePath());
        
        JButton selectOutput = new JButton("Change Directory");
        selectOutput.addActionListener((e) -> {
            if (outputDirectoryChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                logger.debug("Output Directory: " + outputDirectoryChooser.getSelectedFile().getAbsolutePath());
                outputDirectoryLabel.setText(outputDirectoryChooser.getSelectedFile().getAbsolutePath());
            }
        });
        
        JPanel outputPanel = new JPanel();
        outputPanel.setLayout(new BoxLayout(outputPanel, BoxLayout.X_AXIS));
        outputPanel.add(selectOutput);
        outputPanel.add(outputDirectoryLabel);
        
        JPanel topPanel = new JPanel(new GridLayout(5, 1));
        topPanel.add(new JLabel("1. Connect Super Note to wifi"));
        topPanel.add(new JLabel("2. Enable Browse and Access"));
        topPanel.add(urlPanel);
        topPanel.add(outputPanel);
        topPanel.add(runButton);
        
        this.add(topPanel);
        this.pack();
    }
    
    public void runBackup() {
        runButton.setEnabled(false);
        
        LocalDateTime curDateTime = LocalDateTime.now();
        String zipFileName = "backup_" + curDateTime.format(FILENAME_DATE_FORMATTER) + ".zip";
        
        String baseUrl = "http://" + ipBox.getText() + ":" + portBox.getText();
        File outputFile = new File(outputDirectoryChooser.getSelectedFile(), zipFileName);
        logger.info("Running Backup for " + baseUrl + " and backing up to " + outputFile.getAbsolutePath());
        
        Thread t = new Thread(new BackupTask(baseUrl, outputFile, this));
        t.start();
    }
    
    public void backupStatusUpdate(String status) {
        SwingUtilities.invokeLater(() -> {
            runButton.setText(status);
        });
    }
    
    public void onComplete(boolean success, String outputFile) {
        logger.info("Backup Complete: " + success);
        
        SwingUtilities.invokeLater(() -> {
            runButton.setEnabled(true);
            runButton.setText("Run Backup");
        });
        
        if(success){
            JOptionPane.showMessageDialog(this, "Backed up Super Note to " + outputFile, "Backup Success", JOptionPane.INFORMATION_MESSAGE);
        }else{
            JOptionPane.showMessageDialog(this, "Backup Failed", "Backup Failed", JOptionPane.ERROR_MESSAGE);
        }
    }
}
