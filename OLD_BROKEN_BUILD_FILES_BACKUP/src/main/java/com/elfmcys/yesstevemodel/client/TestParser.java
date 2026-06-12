package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.resource.YSMBinaryDeserializer;
import com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel;
import rip.ysm.security.YsmCrypt;

import java.io.File;
import java.nio.file.Files;

public class TestParser {
    public static void main(String[] args) {
        File customDir = new File("E:\\Prism\\PrismLauncher\\instances\\1.21.11\\minecraft\\config\\better_player_model\\custom");
        if (customDir.exists() && customDir.isDirectory()) {
            scanAndTest(customDir);
        }
    }

    private static void scanAndTest(File dir) {
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                scanAndTest(f);
            } else if (f.isFile() && f.getName().endsWith(".ysm")) {
                testModel(f.getAbsolutePath());
            }
        }
    }

    private static void testModel(String path) {
        System.out.println("=========================================");
        System.out.println("TESTING MODEL: " + path);
        File f = new File(path);
        if (!f.exists()) {
            System.out.println("File not found!");
            return;
        }
        try {
            byte[] data = Files.readAllBytes(f.toPath());
            byte[] decrypted = YsmCrypt.decryptYsmFile(data);
            System.out.println("Decrypted size: " + decrypted.length);

            try (YSMBinaryDeserializer deserializer = new YSMBinaryDeserializer(decrypted)) {
                RawYsmModel rawModel = deserializer.deserializeKeepOpen();
                deserializer.parseYSMFooter(rawModel);
                System.out.println("SUCCESSFULLY PARSED! Format version: " + rawModel.formatVersion);
            }
        } catch (Throwable t) {
            t.printStackTrace(System.out);
        }
    }
}
