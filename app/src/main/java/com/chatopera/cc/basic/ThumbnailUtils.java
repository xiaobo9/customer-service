package com.chatopera.cc.basic;

import net.coobird.thumbnailator.Thumbnails;

import java.io.File;
import java.io.IOException;

public class ThumbnailUtils {

    public static File processImage(final File destFile, final File imageFile) throws IOException {
        if (imageFile != null && imageFile.exists()) {
            Thumbnails.of(imageFile).width(460).keepAspectRatio(true).toFile(destFile);
        }
        return destFile;
    }

    public static File scaleImage(final File destFile, final File imageFile, float quality) throws IOException {
        if (imageFile != null && imageFile.exists()) {
            Thumbnails.of(imageFile).scale(1f).outputQuality(quality).toFile(destFile);
        }
        return destFile;
    }
}
