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
        try {
            File directory = new File("received");
            if (!directory.exists() && !directory.mkdir()) {
                System.err.println(" No se pudo crear el directorio de destino.");
                return;
            }

            File destination = new File(directory, fileName);
            Files.write(destination.toPath(), fileContent);

            System.out.printf(" Archivo recibido desde %s: %s", senderName, fileName);
            System.out.println(" Guardado en: " + destination.getAbsolutePath());

        } catch (Exception e) {
            System.err.println(" Error al guardar el archivo recibido: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
