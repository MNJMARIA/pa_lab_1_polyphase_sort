package org.example;


import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class PolyphaseSortBasicAlgorithm {
    private static long totalLines = 0;
    private static long totalRuns = 0;
    private static int mergeCount = 0;

    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        long start = System.currentTimeMillis();

        generateLargeFile("input.txt", 1024);  // 1 ГБ

        polyphaseMergeSort();

        long end = System.currentTimeMillis();
        System.out.printf("Сортування завершено за %.2f секунд%n", (end - start) / 1000.0);
        System.out.println("Результат у файлі: output.txt");
        System.out.println("Зроблено злиттів: " + mergeCount);
    }

    private static void generateLargeFile(String filename, int sizeMB) {
        long targetBytes = sizeMB * 1024L * 1024L;
        long currentBytes = 0;
        Random random = new Random();

        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(filename)))) {
            while (currentBytes < targetBytes) {
                char key = (char) ('A' + random.nextInt(26));
                StringBuilder data = new StringBuilder();
                int len = random.nextInt(36) + 10;
                for (int i = 0; i < len; i++) {
                    data.append((char) ('a' + random.nextInt(26)));
                }
                String phone = "+380" + (100000000 + random.nextInt(900000000));
                String line = key + "-" + data + "-" + phone;

                writer.println(line);
                currentBytes += line.length() + 1; // +1 для \n
                totalLines++;
            }
            System.out.printf("Згенеровано: %d рядків, %d MB%n",
                    totalLines, currentBytes / (1024 * 1024));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void polyphaseMergeSort() {
        String[] tempFiles = {"temp1.txt", "temp2.txt", "temp3.txt"};
        /*for (String f : tempFiles) {
            new File(f).delete();
        }*/
        for (String f : tempFiles) {
            new File(f).delete();
            // Створити порожні файли, щоб вони існували
            try {
                new File(f).createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        long maxMem = Runtime.getRuntime().maxMemory();
        int avgSize = 150;
        int chunkSize = (int) (maxMem / avgSize);
        chunkSize = Math.max(chunkSize, 1_000_000);
        chunkSize = Math.min(chunkSize, 5_000_000);

        System.out.println("Пам'ять: " + (maxMem / 1024 / 1024) + " MB");
        System.out.println("Шматок: " + chunkSize + " рядків");

        System.out.println("Фаза 1: Розподіл на серії (сортування шматків у пам'яті)...");

// Ось цей виклик — єдиний правильний на зараз
        int[] runCounts = distributeInitialRuns(chunkSize, tempFiles);

        System.out.printf("Фактичний розподіл: temp1 = %d серій, temp2 = %d серій, temp3 = 0 серій%n",
                runCounts[0], runCounts[1]);

        System.out.printf("Створено %d серій з %d рядків%n", totalRuns, totalLines);

        int phase = 0;
        while (countNonZero(runCounts) > 1) {
        //while (runCounts[0] + runCounts[1] + runCounts[2] > 1) {
        /*while (true) {
            int nonZero = 0;
            for (int c : runCounts) {
                if (c > 0) nonZero++;
            }
            if (nonZero == 1) break;*/

            /*int targetIdx = phase % 3;
            int source1Idx = (phase + 1) % 3;
            int source2Idx = (phase + 2) % 3;*/
            int[] choice = chooseFilesToMerge(runCounts);
            int targetIdx = choice[0];
            int source1Idx = choice[1];
            int source2Idx = choice[2];

            String target = tempFiles[targetIdx];
            String source1 = tempFiles[source1Idx];
            String source2 = tempFiles[source2Idx];

            System.out.printf("Фаза %d, Злиття #%d: %s + %s → %s (серій: %d + %d → %d)%n",
                    phase + 1, mergeCount + 1, source1, source2, target,
                    runCounts[source1Idx], runCounts[source2Idx],
                    Math.min(runCounts[source1Idx], runCounts[source2Idx]) + Math.abs(runCounts[source1Idx] - runCounts[source2Idx]));

            mergeTwoRuns(source1, source2, target);

            int min = Math.min(runCounts[source1Idx], runCounts[source2Idx]);
            int remaining = Math.abs(runCounts[source1Idx] - runCounts[source2Idx]);
            /*runCounts[targetIdx] = min + remaining;
            runCounts[source1Idx] = 0;
            runCounts[source2Idx] = 0;*/

            /*int mergedRuns = Math.min(runCounts[source1Idx], runCounts[source2Idx]);
            int leftover = Math.abs(runCounts[source1Idx] - runCounts[source2Idx]);

            runCounts[targetIdx] = mergedRuns + leftover;
            runCounts[source1Idx] = 0;
            runCounts[source2Idx] = 0;*/
            int merged = Math.min(runCounts[source1Idx], runCounts[source2Idx]);

            runCounts[targetIdx] = merged;
            runCounts[source1Idx] -= merged;
            runCounts[source2Idx] -= merged;

            mergeCount++;

            System.out.printf("Перерозподіл з %s на %s і %s%n", target, source1, source2);
            /*int[] newCounts = distributeRuns(target, source1, source2, runCounts[targetIdx]);
            runCounts[source1Idx] = newCounts[0];
            runCounts[source2Idx] = newCounts[1];
            runCounts[targetIdx] = 0;*/
            if (runCounts[source1Idx] == 0 || runCounts[source2Idx] == 0) {
                int[] newCounts = distributeRuns(target, source1, source2, runCounts[targetIdx]);
                runCounts[source1Idx] = newCounts[0];
                runCounts[source2Idx] = newCounts[1];
                runCounts[targetIdx] = 0;
                new File(target).delete();
            }

            if (mergeCount > totalRuns * 2) {
                System.out.println("Примусове завершення: досягнуто фінального стану");
                break;
            }

            //new File(target).delete(); // очищаємо приймач

            phase++;
        }

        /*for (int i = 0; i < 3; i++) {
            if (runCounts[i] == 1) {
                new File(tempFiles[i]).renameTo(new File("output.txt"));
                System.out.println("Знайдено фінальний файл: " + tempFiles[i]);
                break;
            }
        }*/
        for (int i = 0; i < 3; i++) {
            if (runCounts[i] > 0) {
                new File("output.txt").delete();
                new File(tempFiles[i]).renameTo(new File("output.txt"));
                System.out.println("Знайдено фінальний файл: " + tempFiles[i]);
                break;
            }
        }


        for (String f : tempFiles) {
            new File(f).delete();
        }
    }

    /*private static int[] chooseFilesToMerge(int[] counts) {
        // Знаходимо два файли з найменшою кількістю серій (які > 0)
        int min1 = -1, min2 = -1;
        int smallest = Integer.MAX_VALUE;

        for (int i = 0; i < 3; i++) {
            if (counts[i] > 0 && counts[i] < smallest) {
                smallest = counts[i];
                min1 = i;
            }
        }

        smallest = Integer.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            if (i != min1 && counts[i] > 0 && counts[i] < smallest) {
                smallest = counts[i];
                min2 = i;
            }
        }

        // Приймач — той, що має найбільше серій
        int target = 3 - min1 - min2;  // бо 0+1+2=3

        return new int[]{target, min1, min2};
    }*/

    // 1. Змінений вибір файлів — завжди пишемо в той, де зараз найменше серій
    /*private static int[] chooseFilesToMerge(int[] counts) {
        // Знаходимо індекс з мінімальною кількістю серій для запису (target)
        int targetIdx = 0;
        for (int i = 1; i < 3; i++) {
            if (counts[i] < counts[targetIdx]) {
                targetIdx = i;
            }
        }

        // Два інших — джерела (сортуємо за кількістю серій, беремо два найменші)
        List<Integer> sources = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            if (i != targetIdx && counts[i] > 0) {
                sources.add(i);
            }
        }

        // Сортуємо джерела за кількістю серій (від найменшого)
        sources.sort(Comparator.comparingInt(a -> counts[a]));

        if (sources.size() < 2) {
            throw new IllegalStateException("Неможливо виконати злиття: недостатньо активних файлів");
        }

        return new int[]{targetIdx, sources.get(0), sources.get(1)};
    }
*/
    private static int countNonZero(int[] counts) {
        int c = 0;
        for (int x : counts) {
            if (x > 0) c++;
        }
        return c;
    }

    private static int[] chooseFilesToMerge(int[] counts) {
        // У polyphase ми завжди зливаємо два файли з **найменшою** кількістю серій
        // у файл з **найбільшою** кількістю серій

        int min1 = -1, min2 = -1;
        int smallest = Integer.MAX_VALUE;

        for (int i = 0; i < 3; i++) {
            if (counts[i] > 0 && counts[i] < smallest) {
                smallest = counts[i];
                min1 = i;
            }
        }

        smallest = Integer.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            if (i != min1 && counts[i] > 0 && counts[i] < smallest) {
                smallest = counts[i];
                min2 = i;
            }
        }

        if (min1 == -1 || min2 == -1) {
            throw new IllegalStateException("Неможливо знайти два джерела для злиття");
        }

        // Цільовий файл — той, який НЕ є min1 і НЕ є min2
        int target = 3 - min1 - min2;

        return new int[]{target, min1, min2};
    }



    /*private static int[] distributeInitialRuns(int chunkSize, String[] tempFiles) {
        int[] runCounts = new int[3];
        long totalRunsEstimate = (totalLines + chunkSize - 1) / chunkSize; // Точніше

        long[] fib = getFibonacciDistribution(totalRunsEstimate);
        long targetForTemp1 = fib[0]; // більше
        long targetForTemp2 = fib[1]; // менше
        System.out.printf("Цільовий розподіл за Фібоначчі: temp1 ≈ %d, temp2 ≈ %d (сума %d з %d)%n",
                targetForTemp1, targetForTemp2, targetForTemp1 + targetForTemp2, totalRunsEstimate);

        long writtenTo1 = 0;
        long writtenTo2 = 0;

        try (Scanner reader = new Scanner(new File("input.txt"));
             PrintWriter file1 = new PrintWriter(new FileWriter(tempFiles[0], true));
             PrintWriter file2 = new PrintWriter(new FileWriter(tempFiles[1], true))) {

            while (reader.hasNextLine()) {
                List<String> chunk = new ArrayList<>();

                for (int i = 0; i < chunkSize && reader.hasNextLine(); i++) {
                    chunk.add(reader.nextLine());
                }

                if (chunk.isEmpty()) break;

                Collections.sort(chunk, Comparator.comparing(s -> s.charAt(0)));

                PrintWriter target = (writtenTo1 < targetForTemp1) ? file1 : file2;
                for (String line : chunk) {
                    target.println(line);
                }
                target.println(); // маркер

                if (writtenTo1 < targetForTemp1) {
                    writtenTo1++;
                    runCounts[0]++;
                } else {
                    writtenTo2++;
                    runCounts[1]++;
                }

                totalRuns++;
            }

            // Dummies якщо потрібно (порожні серії)
            long dummies = (targetForTemp1 + targetForTemp2) - totalRuns;
            if (dummies > 0) {
                PrintWriter dummyTarget = file1; // Додаємо в temp1
                for (long i = 0; i < dummies; i++) {
                    dummyTarget.println();
                }
                runCounts[0] += dummies;
                writtenTo1 += dummies;
            }

            System.out.printf("Фактичний розподіл: temp1 = %d серій, temp2 = %d серій%n",
                    writtenTo1, writtenTo2);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return runCounts;
    }
*/
    // 2. Найпростіший і найстабільніший перерозподіл (саме це зараз рятує ситуацію)
    /*private static int[] distributeInitialRuns(String fromFile, String to1, String to2, int totalRunsInFrom) {
        int[] newCounts = new int[2]; // [кількість в to1, кількість в to2]

        try (Scanner reader = new Scanner(new BufferedReader(new FileReader(fromFile)));
             PrintWriter w1 = new PrintWriter(new BufferedWriter(new FileWriter(to1), 8192));
             PrintWriter w2 = new PrintWriter(new BufferedWriter(new FileWriter(to2), 8192))) {

            int targetTo1 = (totalRunsInFrom + 1) / 2;  // ≈ половина, можна +1
            int writtenTo1 = 0;

            while (reader.hasNextLine()) {
                PrintWriter current = (writtenTo1 < targetTo1) ? w1 : w2;
                String line;
                boolean hasContent = false;

                while (reader.hasNextLine() && !(line = reader.nextLine()).isEmpty()) {
                    current.println(line);
                    hasContent = true;
                }

                if (hasContent) {
                    current.println();  // маркер кінця серії
                    if (writtenTo1 < targetTo1) {
                        writtenTo1++;
                        newCounts[0]++;
                    } else {
                        newCounts[1]++;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return newCounts;
    }
*/


    private static int[] distributeInitialRuns(int chunkSize, String[] tempFiles) {
        int[] runCounts = new int[3];  // 0 = temp1, 1 = temp2, 2 = temp3 (спочатку 0)

        try (Scanner reader = new Scanner(new BufferedReader(new FileReader("input.txt")));
             PrintWriter w1 = new PrintWriter(new BufferedWriter(new FileWriter(tempFiles[0]), 8192));
             PrintWriter w2 = new PrintWriter(new BufferedWriter(new FileWriter(tempFiles[1]), 8192))) {

            int currentFile = 0;  // 0 = temp1, 1 = temp2
            totalRuns = 0;

            while (reader.hasNextLine()) {
                List<String> chunk = new ArrayList<>(chunkSize);

                for (int i = 0; i < chunkSize && reader.hasNextLine(); i++) {
                    chunk.add(reader.nextLine());
                }

                if (chunk.isEmpty()) break;

                // Сортуємо шматок за ключем (літера на початку)
                Collections.sort(chunk, Comparator.comparing(s -> s.charAt(0)));

                PrintWriter writer = (currentFile == 0) ? w1 : w2;

                for (String line : chunk) {
                    writer.println(line);
                }
                writer.println();  // ← найважливіше! Маркер кінця серії

                if (currentFile == 0) {
                    runCounts[0]++;
                } else {
                    runCounts[1]++;
                }

                totalRuns++;
                currentFile = 1 - currentFile;  // чергування
            }

            System.out.printf("Створено %d початкових серій (runs)%n", totalRuns);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return runCounts;
    }


    private static long[] getFibonacciDistribution(long totalRuns) {
        if (totalRuns <= 1) {
            return new long[]{totalRuns, 0};
        }

        long a = 1, b = 1;
        while (a + b <= totalRuns) {
            long next = a + b;
            a = b;
            b = next;
        }
        return new long[]{b, a}; // {більше, менше}
    }

    private static void mergeTwoRuns(String fileA, String fileB, String outputFile) {
        try (Scanner scannerA = new Scanner(new File(fileA));
             Scanner scannerB = new Scanner(new File(fileB));
             PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {

            RunReader rA = new RunReader(scannerA);
            RunReader rB = new RunReader(scannerB);

            while (rA.hasNextRun() && rB.hasNextRun()) {
                PriorityQueue<RunEntry> pq = new PriorityQueue<>(Comparator.comparingInt(e -> e.key));

                String lA = rA.getFirstLine();
                if (lA != null) pq.add(new RunEntry(lA.charAt(0), lA, rA));

                String lB = rB.getFirstLine();
                if (lB != null) pq.add(new RunEntry(lB.charAt(0), lB, rB));

                while (!pq.isEmpty()) {
                    RunEntry min = pq.poll();
                    writer.println(min.line);
                    String next = min.reader.getNextLine();
                    if (next != null) {
                        pq.add(new RunEntry(next.charAt(0), next, min.reader));
                    }
                }

                writer.println(); // маркер кінця нової серії
            }

            // Копіювати залишки з того файлу, де ще є серії
            if (rA.hasNextRun()) {
                copyRemainingRuns(rA, writer);
            }
            if (rB.hasNextRun()) {
                copyRemainingRuns(rB, writer);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void copyRemainingRuns(RunReader reader, PrintWriter writer) {
        while (reader.hasNextRun()) {
            String l = reader.getFirstLine();
            if (l != null) writer.println(l);
            String next;
            while ((next = reader.getNextLine()) != null) {
                writer.println(next);
            }
            writer.println(); // маркер
        }
    }

  /*  private static int[] distributeRuns(String fromFile, String to1, String to2, int totalRunsInFrom) {
        int[] newCounts = new int[2]; // to1, to2

        long[] fib = getFibonacciDistribution(totalRunsInFrom);
        long targetForTo1 = fib[0]; // більше
        long targetForTo2 = fib[1]; // менше

        long writtenTo1 = 0;
        long writtenTo2 = 0;

        try (Scanner reader = new Scanner(new File(fromFile));
             PrintWriter writer1 = new PrintWriter(new FileWriter(to1));
             PrintWriter writer2 = new PrintWriter(new FileWriter(to2))) {

            while (reader.hasNextLine()) {
                PrintWriter current = (writtenTo1 < targetForTo1) ? writer1 : writer2;
                String l;
                boolean hasContent = false;
                while (reader.hasNextLine() && !(l = reader.nextLine()).isEmpty()) {
                    current.println(l);
                    hasContent = true;
                }
                if (hasContent) {
                    current.println(); // маркер
                    if (writtenTo1 < targetForTo1) {
                        writtenTo1++;
                        newCounts[0]++;
                    } else {
                        writtenTo2++;
                        newCounts[1]++;
                    }
                }
            }

            // Dummies
            long dummies = (targetForTo1 + targetForTo2) - totalRunsInFrom;
            if (dummies > 0) {
                PrintWriter dummyTarget = writer1;
                for (long i = 0; i < dummies; i++) {
                    dummyTarget.println();
                }
                newCounts[0] += dummies;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return newCounts;
    }
*/

    /*private static int[] distributeRuns(String fromFile, String to1, String to2, int totalRunsInFrom) {
        int[] newCounts = new int[2];

        try (BufferedReader reader = new BufferedReader(new FileReader(fromFile));
             PrintWriter w1 = new PrintWriter(new BufferedWriter(new FileWriter(to1), 8192));
             PrintWriter w2 = new PrintWriter(new BufferedWriter(new FileWriter(to2), 8192))) {

            int targetTo1 = (totalRunsInFrom + 1) / 2;
            int writtenTo1 = 0;

            String prevLine = null;
            char prevKey = 0;

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue; // на всяк випадок, якщо десь залишився старий маркер

                char currentKey = line.charAt(0);

                // Якщо ключ менший за попередній → це нова серія
                if (prevKey != 0 && currentKey < prevKey) {
                    // Переходимо на інший файл, якщо потрібно
                    if (writtenTo1 < targetTo1) {
                        writtenTo1++;
                        newCounts[0]++;
                    } else {
                        newCounts[1]++;
                    }
                }

                PrintWriter current = (writtenTo1 < targetTo1) ? w1 : w2;
                current.println(line);

                prevLine = line;
                prevKey = currentKey;
            }

            // Записуємо останню серію
            if (prevLine != null) {
                if (writtenTo1 < targetTo1) {
                    newCounts[0]++;
                } else {
                    newCounts[1]++;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return newCounts;
    }
    */

    /*private static int[] distributeRuns(String fromFile, String to1, String to2, int totalRunsInFrom) {
        int[] newCounts = new int[2];

        try (Scanner reader = new Scanner(new File(fromFile));
             PrintWriter writer1 = new PrintWriter(new FileWriter(to1));
             PrintWriter writer2 = new PrintWriter(new FileWriter(to2))) {

            int targetTo1 = (totalRunsInFrom + 1) / 2;
            int writtenTo1 = 0;

            while (reader.hasNextLine()) {
                PrintWriter current = (writtenTo1 < targetTo1) ? writer1 : writer2;
                String l;
                boolean hasContent = false;
                while (reader.hasNextLine() && !(l = reader.nextLine()).isEmpty()) {
                    current.println(l);
                    hasContent = true;
                }
                if (hasContent) {
                    current.println(); // маркер
                    if (writtenTo1 < targetTo1) {
                        writtenTo1++;
                        newCounts[0]++;
                    } else {
                        newCounts[1]++;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return newCounts;
    }
*/


    private static int[] distributeRuns(String fromFile, String to1, String to2, int totalRunsInFrom) {
        int[] newCounts = new int[2]; // to1, to2

        try (Scanner reader = new Scanner(new BufferedReader(new FileReader(fromFile)));
             PrintWriter writer1 = new PrintWriter(new BufferedWriter(new FileWriter(to1), 8192));
             PrintWriter writer2 = new PrintWriter(new BufferedWriter(new FileWriter(to2), 8192))) {

            int targetTo1 = (totalRunsInFrom + 1) / 2;
            int writtenTo1 = 0;

            while (reader.hasNextLine()) {
                PrintWriter current = (writtenTo1 < targetTo1) ? writer1 : writer2;
                String line;
                boolean hasContent = false;

                while (reader.hasNextLine() && !(line = reader.nextLine()).isEmpty()) {
                    current.println(line);
                    hasContent = true;
                }

                if (hasContent) {
                    current.println(); // маркер кінця серії
                    if (writtenTo1 < targetTo1) {
                        writtenTo1++;
                        newCounts[0]++;
                    } else {
                        newCounts[1]++;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return newCounts;
    }

    /*private static int[] distributeRuns(String fromFile, String to1, String to2, int totalRunsInFrom) {
        int[] newCounts = new int[2];  // [to1, to2]

        // Обчислюємо, скільки серій повинно піти в кожен файл за ідеєю Фібоначчі
        long[] fib = getFibonacciDistribution(totalRunsInFrom);
        long targetTo1 = fib[0];   // більше
        long targetTo2 = fib[1];   // менше

        long writtenTo1 = 0;

        try (Scanner reader = new Scanner(new BufferedReader(new FileReader(fromFile)));
             PrintWriter writer1 = new PrintWriter(new BufferedWriter(new FileWriter(to1), 8192));
             PrintWriter writer2 = new PrintWriter(new BufferedWriter(new FileWriter(to2), 8192))) {

            while (reader.hasNextLine()) {
                PrintWriter current = (writtenTo1 < targetTo1) ? writer1 : writer2;

                String line;
                boolean hasContent = false;
                while (reader.hasNextLine() && !(line = reader.nextLine()).isEmpty()) {
                    current.println(line);
                    hasContent = true;
                }

                if (hasContent) {
                    current.println();  // маркер кінця серії
                    if (writtenTo1 < targetTo1) {
                        writtenTo1++;
                        newCounts[0]++;
                    } else {
                        newCounts[1]++;
                    }
                }
            }

            // Додаємо dummy-серії (порожні), якщо потрібно для збереження Фібоначчі-властивостей
            long totalWritten = writtenTo1 + newCounts[1];
            long dummiesNeeded = (targetTo1 + targetTo2) - totalWritten;

            if (dummiesNeeded > 0) {
                for (long i = 0; i < dummiesNeeded; i++) {
                    writer1.println();  // порожня серія в більший файл
                }
                newCounts[0] += dummiesNeeded;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return newCounts;
    }
*/


    static class RunEntry {
        char key;
        String line;
        RunReader reader;

        RunEntry(char key, String line, RunReader reader) {
            this.key = key;
            this.line = line;
            this.reader = reader;
        }
    }

    static class RunReader {
        Scanner scanner;
        String pending = null;

        RunReader(Scanner s) {
            scanner = s;
            advance();
        }

        private void advance() {
            pending = null;
            while (scanner.hasNextLine()) {
                String l = scanner.nextLine();
                if (!l.isEmpty()) {
                    pending = l;
                    break;
                }
            }
        }

        boolean hasNextRun() {
            return pending != null;
        }

        String getFirstLine() {
            String l = pending;
            advance();
            return l;
        }

        String getNextLine() {
            if (scanner.hasNextLine()) {
                String l = scanner.nextLine();
                if (!l.isEmpty()) {
                    return l;
                }
            }
            return null;
        }
    }
}