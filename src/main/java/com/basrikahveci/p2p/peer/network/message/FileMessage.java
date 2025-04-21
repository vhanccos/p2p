package com.basrikahveci.p2p.peer.network.message;

import com.basrikahveci.p2p.peer.Peer;
import com.basrikahveci.p2p.peer.network.Connection;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Files;

public class FileMessage implements Message, Serializable {

    private final String fileName;
    private final byte[] fileContent;
    private final String senderName;

    public FileMessage(String fileName, byte[] fileContent, String senderName) {
        this.fileName = fileName;
        this.fileContent = fileContent;
        this.senderName = senderName;
    }

    public String getFileName() {
        return fileName;
    }

    public byte[] getFileContent() {
        return fileContent;
    }

    public String getSenderName() {
        return senderName;
    }

    @Override
    public void handle(Peer peer, Connection connection) {
        File directory = new File("received");
    
        if (!directory.exists() && !directory.mkdir()) {
            System.err.println("Failed to create destination directory.");
            return;
        }
    
        File destination = new File(directory, fileName);
    
        try {
            Files.write(destination.toPath(), fileContent);
    
            System.out.printf("File received from %s: %s%n", senderName, fileName);
            System.out.println("Saved to: " + destination.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Error saving received file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
