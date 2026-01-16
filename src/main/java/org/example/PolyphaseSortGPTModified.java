package org.example;

import java.io.*;
import java.util.*;

public class PolyphaseSortGPTModified {
    // Оптимізовані константи
    private static final int AVG_LINE_SIZE_BYTES = 120;
    private static final double MEMORY_USAGE_RATIO = 0.65;
    private static final int BUFFER_SIZE_READER = 16 * 1024 * 1024; // 16MB
    private static final int BUFFER_SIZE_WRITER = 8 * 1024 * 1024;  // 8MB

    public static void main(String[] args) {
        int memoryLimitMB = 250; // За замовчуванням

        if (args.length > 0) {
            memoryLimitMB = Integer.parseInt(args[0]);
        }

        long start = System.currentTimeMillis();

        // Генерація файлу тільки якщо потрібно
        File inputFile = new File("input.txt");
        if (!inputFile.exists() || inputFile.length() < 1024 * 1024 * 1024L) {
            System.out.println("Генеруємо вхідний файл...");
            generateLargeFile("input.txt", 1024);
        }

        System.out.println("Початок сортування...");
        polyphaseMergeSort(memoryLimitMB);

        long end = System.currentTimeMillis();
        System.out.printf("Сортування завершено за %.2f секунд%n", (end - start) / 1000.0);
        System.out.println("Результат у файлі: output.txt");
    }

    private static void generateLargeFile(String filename, int sizeMB) {
        long bytes = sizeMB * 1024L * 1024L;
        long written = 0;
        Random rnd = new Random();

        // Оптимізація: використання StringBuilder для формування рядків
        try (BufferedWriter w = new BufferedWriter(
                new FileWriter(filename), BUFFER_SIZE_WRITER)) {

            StringBuilder sb = new StringBuilder(60);
            while (written < bytes) {
                char k = (char) ('A' + rnd.nextInt(26));
                int len = rnd.nextInt(36) + 10;

                sb.setLength(0);
                sb.append(k).append('-');

                for (int i = 0; i < len; i++) {
                    sb.append((char) ('a' + rnd.nextInt(26)));
                }

                sb.append("-+380").append(100000000 + rnd.nextInt(900000000));
                String line = sb.toString();
                w.write(line);
                w.newLine();
                written += line.length() + 1; // +1 для newLine
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void polyphaseMergeSort(int memoryLimitMB) {
        String[] tempFiles = {"temp1.txt", "temp2.txt", "temp3.txt"};

        // Очищення тимчасових файлів
        for (String f : tempFiles) {
            new File(f).delete();
        }

        // Розрахунок розміру чанка з пам'яттю
        long usableMemory = (long) (memoryLimitMB * 1024L * 1024L * MEMORY_USAGE_RATIO);
        int maxChunkSize = (int) (usableMemory / AVG_LINE_SIZE_BYTES);

        // Обмеження розміру чанка
        maxChunkSize = Math.max(maxChunkSize, 500_000);
        maxChunkSize = Math.min(maxChunkSize, 2_000_000); // Зменшено для кращого кешування

        System.out.println("Ліміт пам'яті: " + memoryLimitMB + " MB");
        System.out.println("Розмір шматка: " + maxChunkSize + " рядків");

        // Фаза 1: Розподіл природних серій
        distributeNaturalRuns(maxChunkSize);

        // Фаза 2: Багатофазне злиття
        int phase = 0;
        int mergeCount = 0;

        while (true) {
            String target = tempFiles[phase % 3];
            String src1 = tempFiles[(phase + 1) % 3];
            String src2 = tempFiles[(phase + 2) % 3];

            File f1 = new File(src1);
            File f2 = new File(src2);

            if (f1.length() == 0 || f2.length() == 0) {
                String result = f1.length() > 0 ? src1 : src2;
                new File(result).renameTo(new File("output.txt"));
                System.out.println("Зроблено злиттів: " + mergeCount);
                break;
            }

            mergeTwoRuns(src1, src2, target);

            // Видаляємо вхідні файли після злиття
            f1.delete();
            f2.delete();

            phase++;
            mergeCount++;

            if (mergeCount % 10 == 0) {
                System.out.println("Виконано " + mergeCount + " злиттів...");
            }
        }

        // Очищення залишкових файлів
        for (String f : tempFiles) {
            new File(f).delete();
        }
    }

    private static void distributeNaturalRuns(int maxChunkSize) {
        System.out.println("Фаза 1: Розподіл природних серій...");

        try (BufferedReader reader = new BufferedReader(
                new FileReader("input.txt"), BUFFER_SIZE_READER)) {

            // Відкриваємо файли один раз
            try (BufferedWriter writer1 = new BufferedWriter(
                    new FileWriter("temp1.txt", true), BUFFER_SIZE_WRITER);
                 BufferedWriter writer2 = new BufferedWriter(
                         new FileWriter("temp2.txt", true), BUFFER_SIZE_WRITER)) {

                BufferedWriter currentWriter = writer1;
                char prevKey = 0;
                List<String> chunk = new ArrayList<>(maxChunkSize);
                int runCount = 0;
                long totalLines = 0;

                String line;
                while ((line = reader.readLine()) != null) {
                    totalLines++;
                    if (line.isEmpty()) continue;

                    char currentKey = line.charAt(0);

                    // Кінець природної серії
                    if (prevKey != 0 && currentKey < prevKey) {
                        if (!chunk.isEmpty()) {
                            // Сортуємо чанк в пам'яті
                            Collections.sort(chunk, Comparator.comparing(s -> s.charAt(0)));
                            writeChunkToWriter(chunk, currentWriter);
                            chunk.clear();
                            runCount++;
                        }
                        // Перемикаємо файл
                        currentWriter = (currentWriter == writer1) ? writer2 : writer1;
                    }

                    chunk.add(line);
                    prevKey = currentKey;

                    // Якщо чанк досяг максимального розміру, записуємо його
                    if (chunk.size() >= maxChunkSize) {
                        Collections.sort(chunk, Comparator.comparing(s -> s.charAt(0)));
                        writeChunkToWriter(chunk, currentWriter);
                        chunk.clear();
                        runCount++;
                    }
                }

                // Останній чанк
                if (!chunk.isEmpty()) {
                    Collections.sort(chunk, Comparator.comparing(s -> s.charAt(0)));
                    writeChunkToWriter(chunk, currentWriter);
                    runCount++;
                }

                System.out.println("Створено " + runCount + " серій з " + totalLines + " рядків");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeChunkToWriter(List<String> chunk, BufferedWriter writer)
            throws IOException {
        for (String s : chunk) {
            writer.write(s);
            writer.newLine();
        }
        // Маркер кінця серії
        writer.newLine();
        writer.flush();
    }

    private static void mergeTwoRuns(String a, String b, String out) {
        try (BufferedReader ra = new BufferedReader(new FileReader(a), BUFFER_SIZE_READER);
             BufferedReader rb = new BufferedReader(new FileReader(b), BUFFER_SIZE_READER);
             BufferedWriter w = new BufferedWriter(new FileWriter(out), BUFFER_SIZE_WRITER)) {

            // Враховуємо маркери кінця серій
            PriorityQueue<RunEntry> pq = new PriorityQueue<>(
                    1000, Comparator.comparingInt(e -> e.key));

            // Ініціалізація черги
            addNextRunEntry(ra, pq);
            addNextRunEntry(rb, pq);

            while (!pq.isEmpty()) {
                RunEntry e = pq.poll();
                w.write(e.line);
                w.newLine();
                addNextRunEntry(e.reader, pq);
            }

            // Маркер кінця нової серії
            w.newLine();
            w.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void addNextRunEntry(BufferedReader r, PriorityQueue<RunEntry> pq)
            throws IOException {
        String line = r.readLine();

        // Пропускаємо порожні рядки (маркери кінця серій)
        while (line != null && line.isEmpty()) {
            line = r.readLine();
        }

        if (line != null) {
            pq.add(new RunEntry(line.charAt(0), line, r));
        }
    }

    static class RunEntry {
        char key;
        String line;
        BufferedReader reader;

        RunEntry(char key, String line, BufferedReader reader) {
            this.key = key;
            this.line = line;
            this.reader = reader;
        }
    }
}
