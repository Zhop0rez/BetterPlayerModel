package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.resource.YSMBinaryDeserializer;
import com.elfmcys.yesstevemodel.resource.YSMFolderDeserializer;
import com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel;
import rip.ysm.security.YsmCrypt;

import java.io.File;
import java.nio.file.Files;

public class TestParser {
    public static void main(String[] args) {
        if (args.length == 1) {
            testModel(args[0]);
            return;
        }
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
            if (path.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
                try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(f.toPath())) {
                    RawYsmModel rawModel = deserializer.deserialize();
                    printModelSummary(rawModel);
                }
                return;
            }
            byte[] data = Files.readAllBytes(f.toPath());
            byte[] decrypted = YsmCrypt.decryptYsmFile(data);
            System.out.println("Decrypted size: " + decrypted.length);

            try (YSMBinaryDeserializer deserializer = new YSMBinaryDeserializer(decrypted)) {
                RawYsmModel rawModel = deserializer.deserializeKeepOpen();
                deserializer.parseYSMFooter(rawModel);
                printModelSummary(rawModel);
            }
        } catch (Throwable t) {
            t.printStackTrace(System.out);
        }
    }

    private static void printModelSummary(RawYsmModel rawModel) {
        System.out.println("SUCCESSFULLY PARSED! Format version: " + rawModel.formatVersion);
        System.out.println("Default texture: " + rawModel.properties.defaultTexture);
        System.out.println("Main geometry texture size: "
                + rawModel.mainEntity.mainModel.textureWidth + "x" + rawModel.mainEntity.mainModel.textureHeight);
        rawModel.mainEntity.textures.forEach((name, texture) -> System.out.println(
                "Texture " + name + ": " + texture.width + "x" + texture.height
                        + ", format=" + texture.imageFormat + ", bytes=" + texture.data.length));
        rawModel.projectiles.forEach((name, projectile) -> {
            System.out.println("Projectile " + name + ": id=" + projectile.identifier
                    + ", geometry=" + (projectile.model == null ? "none" : projectile.model.textureWidth + "x" + projectile.model.textureHeight));
            projectile.textures.forEach((textureName, texture) -> System.out.println(
                    "  Projectile texture " + textureName + ": " + texture.width + "x" + texture.height));
        });
    }
}
