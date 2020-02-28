package pony.xcode.jcamera.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileUtil {

    public static boolean deleteFile(String filePath) {
        if(filePath == null) return false;
        boolean result = false;
        File file = new File(filePath);
        if (file.exists()) {
            result = file.delete();
        }
        return result;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static String generatePath(Context context) {
        File fileDir = context.getApplicationContext().getExternalCacheDir();
        String path = null;
        if (fileDir != null) {
            path = fileDir.getAbsolutePath() + File.separator + "jcamera";
        } else {
            fileDir = context.getApplicationContext().getExternalFilesDir(null);
            if (fileDir != null) {
                path = fileDir.getAbsolutePath() + File.separator + "jcamera";
            }
        }
        if (path != null) {
            File file = new File(path);
            if (!file.exists()) {
                file.mkdirs();
            }
            return file.getAbsolutePath();
        }
        return null;
    }

    //拍照存放的目录
    public static String generateCapturePath(Context context) {
        String parentPath = generatePath(context);
        if (!TextUtils.isEmpty(parentPath)) {
            String path = parentPath + File.separator + "capture";
            File file = new File(path);
            boolean isFileExists;
            if (!file.exists()) {
                isFileExists = file.mkdirs();
            } else {
                isFileExists = true;
            }
            if (isFileExists) {
                return file.getAbsolutePath();
            }
            return null;
        }
        return null;
    }

    //bitmap转文件路径
    public static String convert(@Nullable String rootPath, @Nullable Bitmap bitmap) {
        if (rootPath == null || bitmap == null) return null;
        File parentFile = new File(rootPath);
        boolean isFileExists;
        if (!parentFile.exists()) {
            isFileExists = parentFile.mkdirs();
        } else {
            isFileExists = true;
        }
        if (!isFileExists) return null;
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
        Date date = new Date();
        //图片名
        String filename = "picture_" + format.format(date) + ".png";
        File file = new File(rootPath, filename);
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;
        try {
            fos = new FileOutputStream(file);
            bos = new BufferedOutputStream(fos);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            bos.flush();
            bos.close();
            return file.getAbsolutePath();
        } catch (Exception e) {
            if (bos != null) {
                try {
                    bos.close();
                } catch (Exception ignored) {
                }
            }
            return null;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (Exception ignored) {
                }
            }

        }
    }
}
