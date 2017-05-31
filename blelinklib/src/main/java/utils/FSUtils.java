package utils;

import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Created by francis on 5/5/16.
 */
public class FSUtils {
    public static String getStorageDirectory() {
        return Environment.getExternalStorageDirectory() + "/promos/";
    }

    public static String getStorageSaveDirectory() {
        return Environment.getExternalStorageDirectory() + "/promos/recvd/";
    }

    public static String getParentDir(String path) {
        File targetFile = new File(path);
        if (!targetFile.exists()) {
            Logger.err("Invalid file/directory path: " + path);
            return null;
        }

        return targetFile.getParent();
    }

    public static String[] listDir(String path) {
        File storageDir = new File(path);
        if (!storageDir.isDirectory()) {
            Logger.err("Invalid directory path: " + path);
            return null;
        }
        return storageDir.list();
    }

    public static boolean isDirectory(String path) {
        File target = new File(path);
        return target.isDirectory();
    }

    public static boolean isFile(String path) {
        File target = new File(path);
        return target.isFile();
    }

    public static long getFileSize(String path) {
        File target = new File(path);
        if (!target.isFile()) {
            return 0;
        }

        return target.length();
    }

    public static FileInputStream getFileStream(String path) throws IOException {
        File target = new File(path);
        if (!target.isFile()) {
            return null;
        }

        return new FileInputStream(target);
    }

    public static String getStoredFilenames() {
        String storagePath = getStorageDirectory();
        File storageDir = new File(storagePath);

        if (!storageDir.exists()) {
            return null;
        }

        String filenamesStr = "";
        String filenames[] = storageDir.list();
        for (int iIdx = 0; iIdx < filenames.length; iIdx++) {
            if (iIdx > 0) {
                filenamesStr += ",";
            }
            filenamesStr += filenames[iIdx];
        }

        return filenamesStr;
    }
}
