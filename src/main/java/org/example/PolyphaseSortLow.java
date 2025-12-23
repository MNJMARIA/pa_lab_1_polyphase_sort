package org.example;

import java.io.*;
import java.util.*;

public class PolyphaseSortLow {
    // Динамічне обмеження пам'яті - реально працює
    private static final long MAX_MEMORY_LIMIT = 50 * 1024 * 1024; // 50 МБ

    public static void main(String[] args) {
        String inputFile = "data_basic.txt";
        String outputFile = "sorted_basic.txt";
        int sizeMB = 100;

        System.out.println("=== БАЗОВА ВЕРСІЯ (обмеження 50 МБ) ===");
        System.out.println("Генерація файлу " + sizeMB + " МБ...");
        generateFileBasic(inputFile, sizeMB);

        System.out.println("Сортування...");
        polyphaseSortBasic(inputFile, outputFile);
    }

    // Базова генерація файлу
    private static void generateFileBasic(String filename, int sizeMB) {
        long targetSize = (long) sizeMB * 1024 * 1024;
        long currentSize = 0;
        Random random = new Random();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            while (currentSize < targetSize) {
                char key = (char) ('A' + random.nextInt(26));

                // Неефективно, але не надто повільно
                String text = "";
                int len = random.nextInt(45) + 1;
                for (int i = 0; i < len; i++) {
                    text += (char) ('a' + random.nextInt(26));
                }

                String phone = "+380" + (random.nextInt(900000000) + 100000000);
                String line = key + "-" + text + "-" + phone + "\n";

                writer.write(line);
                currentSize += line.getBytes().length;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Файл створено: " + filename + " (" + sizeMB + " МБ)");
    }

    // Багатофазне сортування з обмеженням пам'яті
    private static void polyphaseSortBasic(String inputFile, String output) {
        long startTime = System.currentTimeMillis();

        String[] tempFiles = {"temp1.txt", "temp2.txt", "temp3.txt"};
        for (String f : tempFiles) {
            new File(f).delete();
        }

        // 1. Розподіл на серії з обмеженням пам'яті
        int chunkSize = calculateChunkSize();
        System.out.println("Розмір серії: " + chunkSize + " рядків");
        distributeBasic(inputFile, tempFiles[0], tempFiles[1], chunkSize);

        // 2. Багатофазне злиття
        int phase = 0;
        int totalPhases = 0;

        while (true) {
            totalPhases++;
            String source1 = tempFiles[phase % 3];
            String source2 = tempFiles[(phase + 1) % 3];
            String target = tempFiles[(phase + 2) % 3];

            File f1 = new File(source1);
            File f2 = new File(source2);

            if (f1.length() == 0 || f2.length() == 0) {
                String result = f1.length() > 0 ? source1 : source2;
                new File(result).renameTo(new File(output));
                break;
            }

            System.out.print("Фаза " + totalPhases + ": " +
                    (f1.length()/1024) + "КБ + " +
                    (f2.length()/1024) + "КБ → " +
                    (f1.length()/1024 + f2.length()/1024) + "КБ... ");

            long mergeStart = System.currentTimeMillis();
            mergeBasic(source1, source2, target);
            long mergeEnd = System.currentTimeMillis();

            System.out.println((mergeEnd - mergeStart)/1000.0 + " сек");

            f1.delete();
            f2.delete();
            phase++;
        }

        for (String f : tempFiles) {
            new File(f).delete();
        }

        long endTime = System.currentTimeMillis();
        System.out.println("\n=== результати базової версії ===");
        System.out.println("Час сортування: " + (endTime - startTime) / 1000.0 + " сек");
        System.out.println("Всього фаз злиття: " + totalPhases);
        System.out.println("Обмеження пам'яті: " + MAX_MEMORY_LIMIT/(1024*1024) + " МБ");
    }

    // Розрахунок розміру серії на основі обмеження пам'яті
    private static int calculateChunkSize() {
        // 100 байт на рядок в середньому
        long bytesPerRecord = 100;

        // Використовуємо 60% від обмеження для даних
        long availableForData = (long)(MAX_MEMORY_LIMIT * 0.6);

        // Кількість рядків
        int chunkSize = (int)(availableForData / bytesPerRecord);

        // Мінімум 5,000, максимум 30,000
        return Math.max(5000, Math.min(chunkSize, 30000));
    }

    // Розподіл на серії
    private static void distributeBasic(String inputFile, String temp1, String temp2, int chunkSize) {
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
            boolean toTemp1 = true;
            List<String> chunk = new ArrayList<>(chunkSize);
            String line;
            int runCount = 0;

            while ((line = reader.readLine()) != null) {
                chunk.add(line);

                if (chunk.size() >= chunkSize) {
                    runCount++;

                    // Сортування Bubble Sort для серії (повільно, але не надто)
                    bubbleSort(chunk);

                    // Запис у файл
                    String target = toTemp1 ? temp1 : temp2;
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(target, true))) {
                        for (String s : chunk) {
                            writer.write(s + "\n");
                        }
                    }

                    chunk.clear();
                    toTemp1 = !toTemp1;

                    if (runCount % 10 == 0) {
                        System.out.println("  Створено " + runCount + " серій");
                        checkMemory();
                    }
                }
            }

            // Остання серія
            if (!chunk.isEmpty()) {
                bubbleSort(chunk);
                String target = toTemp1 ? temp1 : temp2;
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(target, true))) {
                    for (String s : chunk) {
                        writer.write(s + "\n");
                    }
                }
            }

            System.out.println("Всього створено " + (chunk.isEmpty() ? runCount : runCount + 1) + " серій");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Bubble Sort - повільний, але не катастрофічно
    private static void bubbleSort(List<String> list) {
        int n = list.size();
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - i - 1; j++) {
                char key1 = list.get(j).charAt(0);
                char key2 = list.get(j + 1).charAt(0);
                if (key1 > key2) {
                    // Обмін
                    String temp = list.get(j);
                    list.set(j, list.get(j + 1));
                    list.set(j + 1, temp);
                }
            }
        }
    }

    // Базова реалізація злиття
    private static void mergeBasic(String file1, String file2, String out) {
        try (BufferedReader reader1 = new BufferedReader(new FileReader(file1));
             BufferedReader reader2 = new BufferedReader(new FileReader(file2));
             BufferedWriter writer = new BufferedWriter(new FileWriter(out))) {

            String line1 = reader1.readLine();
            String line2 = reader2.readLine();

            while (line1 != null && line2 != null) {
                // Порівняння за ключем
                char key1 = getKeyBasic(line1);
                char key2 = getKeyBasic(line2);

                if (key1 <= key2) {
                    writer.write(line1 + "\n");
                    line1 = reader1.readLine();
                } else {
                    writer.write(line2 + "\n");
                    line2 = reader2.readLine();
                }
            }

            // Залишки
            while (line1 != null) {
                writer.write(line1 + "\n");
                line1 = reader1.readLine();
            }

            while (line2 != null) {
                writer.write(line2 + "\n");
                line2 = reader2.readLine();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Отримання ключа
    private static char getKeyBasic(String line) {
        return line.charAt(0);
    }

    // Перевірка пам'яті
    private static void checkMemory() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();

        if ((double)used / max > 0.7) {
            System.gc();
            try {
                Thread.sleep(50); // Невелика пауза для GC
            } catch (InterruptedException e) {}
        }
    }
}