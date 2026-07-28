import com.solar.launcher.PlayQueue;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PlayQueueBenchmark {
    public static void main(String[] args) {
        PlayQueue queue = new PlayQueue();
        List<PlayQueue.QueueItem> items = new ArrayList<>();
        // Add items to the queue
        for (int i = 0; i < 50000; i++) {
            items.add(PlayQueue.QueueItem.music(new File("/a" + i + ".mp3")));
        }
        queue.setAll(items, 0);

        long start = System.nanoTime();

        File oldFile = new File("/a49999.mp3");
        File newFile = new File("/new_file.mp3");
        queue.replaceFileRef(oldFile, newFile, "New Title");
        queue.promoteStreamToMusic(oldFile, newFile);

        long end = System.nanoTime();
        System.out.println("Benchmark time: " + (end - start) / 1_000_000.0 + " ms");
    }
}
