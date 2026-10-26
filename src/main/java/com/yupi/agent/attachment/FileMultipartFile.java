package com.yupi.agent.attachment;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 把一个本地文件包装成 MultipartFile，
 * 这样就能复用 FilePictureUpload 的校验与上传逻辑（10MB、后缀白名单）。
 */
public class FileMultipartFile implements MultipartFile {

    private final File file;

    private final String originalFilename;

    private final String contentType;

    public FileMultipartFile(File file, String originalFilename, String contentType) {
        this.file = file;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
    }

    @Override
    public String getName() {
        return "file";
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return file == null || file.length() == 0;
    }

    @Override
    public long getSize() {
        return file.length();
    }

    @Override
    public byte[] getBytes() throws IOException {
        return java.nio.file.Files.readAllBytes(file.toPath());
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new FileInputStream(file);
    }

    @Override
    public void transferTo(File dest) throws IOException, IllegalStateException {
        java.nio.file.Files.copy(file.toPath(), dest.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
