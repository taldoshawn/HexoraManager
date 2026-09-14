package com.hexora.manager.privileged;

import android.os.ParcelFileDescriptor;

interface IPrivilegedFileService {
    void destroy() = 16777114;
    int uid() = 1;
    String stat(String path) = 2;
    List<String> list(String path) = 3;
    ParcelFileDescriptor openRead(String path) = 4;
    ParcelFileDescriptor openWrite(String path, boolean truncate) = 5;
    boolean createFile(String path) = 6;
    boolean createDirectory(String path) = 7;
    boolean rename(String source, String target) = 8;
    boolean delete(String path, boolean recursive) = 9;
}
