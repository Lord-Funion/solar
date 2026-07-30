package dev.lordfunion.rockboxsolar;

import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class LibraryScanner {
    private static final Set<String> AUDIO_EXTENSIONS = new HashSet<String>();
    static {
        Collections.addAll(AUDIO_EXTENSIONS, "mp3", "flac", "ogg", "oga", "wav", "m4a", "aac", "opus", "wma", "ape");
    }

    static boolean isAudio(File file) {
        if (file == null || !file.isFile()) return false;
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        return AUDIO_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.US));
    }

    static List<File> listDirectory(File directory) {
        ArrayList<File> result = new ArrayList<File>();
        if (directory != null && directory.getParentFile() != null) {
            result.add(directory.getParentFile());
        }
        File[] children = directory == null ? null : directory.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory() || isAudio(child)) result.add(child);
            }
        }
        Collections.sort(result, new Comparator<File>() {
            @Override public int compare(File left, File right) {
                if (left.isDirectory() != right.isDirectory()) return left.isDirectory() ? -1 : 1;
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        return result;
    }

    static List<File> scanAll() {
        ArrayList<File> tracks = new ArrayList<File>();
        HashSet<String> visited = new HashSet<String>();
        for (File root : roots()) scan(root, tracks, visited, 0);
        Collections.sort(tracks, new Comparator<File>() {
            @Override public int compare(File left, File right) {
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        return tracks;
    }

    static List<File> roots() {
        ArrayList<File> roots = new ArrayList<File>();
        File external = Environment.getExternalStorageDirectory();
        addIfReadable(roots, new File(external, "Music"));
        addIfReadable(roots, external);
        addIfReadable(roots, new File("/storage/sdcard1/Music"));
        addIfReadable(roots, new File("/storage/sdcard1"));
        addIfReadable(roots, new File("/mnt/sdcard2/Music"));
        return roots;
    }

    private static void addIfReadable(List<File> roots, File file) {
        if (file.exists() && file.isDirectory() && file.canRead()) roots.add(file);
    }

    private static void scan(File directory, List<File> tracks, Set<String> visited, int depth) {
        if (directory == null || depth > 12 || tracks.size() >= 10000) return;
        String path;
        try { path = directory.getCanonicalPath(); } catch (Exception e) { path = directory.getAbsolutePath(); }
        if (!visited.add(path)) return;
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (tracks.size() >= 10000) return;
            if (child.isDirectory()) scan(child, tracks, visited, depth + 1);
            else if (isAudio(child)) tracks.add(child);
        }
    }
}
