package com.solar.launcher.soulseek;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;

public class SoulseekSearchRankingPerfTest {

    @Test
    public void testRelevanceScorePerformance() {
        String query = "Gorillaz Clint Eastwood";
        List<String> filenames = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            filenames.add("@@u\\Music\\Pop\\Gorillaz\\Gorillaz - Clint Eastwood " + i + ".mp3");
            filenames.add("@@u\\Music\\Pop\\Blur\\Song 2 " + i + ".mp3");
            filenames.add("@@u\\Music\\Rock\\Clint Eastwood - Random Track " + i + ".mp3");
        }

        long start = System.nanoTime();
        long totalScore = 0;
        for (String filename : filenames) {
            totalScore += SoulseekSearchRanking.relevanceScore(query, filename);
        }
        long end = System.nanoTime();

        System.out.println("Total Score: " + totalScore);
        System.out.println("Time taken: " + (end - start) / 1_000_000.0 + " ms");
    }
}
