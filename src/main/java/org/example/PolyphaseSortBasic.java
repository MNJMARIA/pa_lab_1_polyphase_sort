package org.example;

import java.io.*;
import java.util.*;

public class PolyphaseSortBasic {
    private static long totalLines = 0;
    private static long totalRuns = 0;
    private static int mergeCount = 0;
    private static final String RUN_SEPARATOR = "---RUN_END---";

    public static void main(String[] args) {
        // Початок програми: вимірювання часу
        long start = System.currentTimeMillis();

        // Підготовчий етап: генерація вхідного файлу
        generateLargeFile("input.txt", 1024);  // 1024 МБ ≈ 1 ГБ

        // Виконання багатофазного сортування
        polyphaseMergeSort();

        // Завершення: вивід часу та результату
        long end = System.currentTimeMillis();
        System.out.printf("Сортування завершено за %.2f секунд%n", (end - start) / 1000.0);
        System.out.println("Результат у файлі: output.txt");
    }

    // Генерація тестового файлу розміром ≈ sizeMB мегабайт
    /*private static void generateLargeFile(String filename, int sizeMB) {
        // Цільовий розмір у байтах
        long targetBytes = sizeMB * 1024L * 1024L;
        long currentBytes = 0;
        Random random = new Random();

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // Генеруємо записи, доки не досягнемо потрібного розміру
            while (currentBytes < targetBytes) {
                char key = (char) ('A' + random.nextInt(26));  // Ключ: літера A-Z
                String data = "";
                int len = random.nextInt(36) + 10;  // Рядок довжиною 10–45 символів
                for (int i = 0; i < len; i++) {
                    data += (char) ('a' + random.nextInt(26));  // Повільне накопичення
                }
                String phone = "+380" + (random.nextInt(900000000) + 100000000);  // Телефон
                String line = key + "-" + data + "-" + phone;

                writer.println(line);  // Записуємо рядок
                currentBytes += (line + "\n").getBytes().length;  // Оновлюємо поточний розмір
                totalLines++;  // рахуємо рядки
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }*/
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


    // Основний метод багатофазного сортування

    private static void polyphaseMergeSort() {
        // Створюємо 3 тимчасових файли (m=3)
        String[] tempFiles = {"temp1.txt", "temp2.txt", "temp3.txt"};
        for (String f : tempFiles) {
            new File(f).delete();  // Видаляємо попередні версії, якщо є
        }

        // Динамічне обчислення розміру шматка
        long maxMem = Runtime.getRuntime().maxMemory();  // Максимум доступної пам'яті в байтах
        int avgSize = 150;  // Середній розмір одного запису з overhead
        int chunkSize = (int) (maxMem / avgSize);  // Скільки рядків вміщається

        // Нижнє обмеження: не менше ~100 МБ
        chunkSize = Math.max(chunkSize, 1_000_000);

        // Верхнє обмеження: щоб уникнути OutOfMemoryError при дуже великій RAM
        chunkSize = Math.min(chunkSize, 5_000_000);

        System.out.println("Пам'ять: " + (maxMem / 1024 / 1024) + " MB");
        System.out.println("Шматок: " + chunkSize + " рядків");

        // Фаза 1: Розбиття на початкові серії
        System.out.println("Фаза 1: Розподіл на серії (сортування шматків у пам'яті)...");
        distributeInitialRuns(chunkSize);
        System.out.printf("Створено %d серій з %d рядків%n", totalRuns, totalLines);


        // Фаза 2: Багатофазне злиття
        int mergeCount = 0;
        int outputIndex = 2;  // Починаємо з temp3 як перший цільовий

        while (true) {
            // Знаходимо два файли з серіями (не порожні)
            int input1 = -1, input2 = -1;
            int nonEmptyCount = 0;

            for (int i = 0; i < 3; i++) {
                long len = new File(tempFiles[i]).length();
                if (len > 0) {
                    nonEmptyCount++;
                    if (input1 == -1) input1 = i;
                    else if (input2 == -1) input2 = i;
                }
            }

            // Якщо тільки один файл непорожній — це фінальний результат
            if (nonEmptyCount <= 1) {
                String finalFile = tempFiles[input1 != -1 ? input1 : outputIndex];
                new File(finalFile).renameTo(new File("output.txt"));
                System.out.println("Знайдено фінальний файл: " + finalFile);
                break;
            }

            // Цільовий файл — той, який не є input1 і не input2
            int targetIndex = 3 - (input1 + input2); // 0+1→2, 0+2→1, 1+2→0
            String target = tempFiles[targetIndex];
            String source1 = tempFiles[input1];
            String source2 = tempFiles[input2];

            System.out.printf("Злиття #%d: %s + %s → %s%n",
                    mergeCount + 1, source1, source2, target);

            // Зливаємо
            mergeTwoRuns(source1, source2, target);

            mergeCount++;

            // Очищаємо використані файли (тепер вони порожні)
            new File(source1).delete();
            new File(source2).delete();

            // Наступний цільовий буде іншим
            outputIndex = targetIndex;
        }

        // Виводимо статистику
        System.out.println("Зроблено злиттів: " + mergeCount);

        // Очищення залишків
        for (String f : tempFiles) {
            new File(f).delete();
        }
    }


    // Розбиття на початкові серії (runs)
    // Розбиття на початкові серії (runs) з розподілом за принципом Фібоначчі
    private static void distributeInitialRuns(int chunkSize) {
        try (Scanner reader = new Scanner(new File("input.txt"))) {

            try (PrintWriter file1 = new PrintWriter(new BufferedWriter(new FileWriter("temp1.txt")));
                 PrintWriter file2 = new PrintWriter(new BufferedWriter(new FileWriter("temp2.txt")))) {

                // !!! Тут буде писатися велика кількість серій, тому спочатку просто рахуємо
                long totalRunsEstimate = estimateRunsNeeded(chunkSize);

                // Отримуємо два найбільші числа Фібоначчі, сума яких <= оцінки кількості серій
                long[] fib = getFibonacciDistribution(totalRunsEstimate);
                long target1 = fib[0];  // більше число → temp1
                long target2 = fib[1];  // менше число → temp2

                System.out.printf("Цільовий розподіл за Фібоначчі: temp1 ≈ %d, temp2 ≈ %d (сума %d з %d)%n",
                        target1, target2, target1 + target2, totalRunsEstimate);

                long writtenTo1 = 0;
                long writtenTo2 = 0;

                while (reader.hasNextLine()) {
                    List<String> chunk = new ArrayList<>(Math.min(chunkSize, 500_000));

                    for (int i = 0; i < chunkSize && reader.hasNextLine(); i++) {
                        chunk.add(reader.nextLine());
                        totalLines++;
                    }

                    if (chunk.isEmpty()) break;

                    Collections.sort(chunk, Comparator.comparing(s -> s.charAt(0)));

                    // Вирішуємо, куди писати цю серію
                    PrintWriter target;
                    if (writtenTo1 < target1) {
                        target = file1;
                        writtenTo1++;
                    } else {
                        target = file2;
                        writtenTo2++;
                    }

                    for (String line : chunk) {
                        target.println(line);
                    }
                    target.println(); // маркер кінця серії

                    totalRuns++;
                }

                System.out.printf("Фактичний розподіл: temp1 = %d серій, temp2 = %d серій%n",
                        writtenTo1, writtenTo2);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Дуже приблизно оцінюємо скільки серій буде
    private static long estimateRunsNeeded(int chunkSize) {
        try {
            long size = new File("input.txt").length();
            return (size / (chunkSize * 130L)) + 1; // ~130 байт на рядок середній
        } catch (Exception e) {
            return 1000;
        }
    }

    private static long[] getFibonacciDistribution(long totalRuns) {
        if (totalRuns <= 1) {
            return new long[]{totalRuns, 0};
        }

        long a = 1, b = 1;
        while (b + a <= totalRuns) {
            long next = a + b;
            a = b;
            b = next;
        }
        // Повертаємо два найбільші числа Фібоначчі
        return new long[]{b, a}; // наприклад, для 24 → 13 і 8
    }

    // список чисел Фібоначчі
    private static List<Integer> fibonacci(int n) {
        List<Integer> f = new ArrayList<>();
        f.add(1);
        f.add(1);
        while (f.get(f.size() - 1) < n) {
            int size = f.size();
            f.add(f.get(size - 1) + f.get(size - 2));
        }
        return f;
    }



    // Злиття двох файлів (серій) у третій
    private static void mergeTwoRuns(String fileA, String fileB, String outputFile) {
        try (Scanner readerA = new Scanner(new File(fileA));
             Scanner readerB = new Scanner(new File(fileB));
             PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {

            PriorityQueue<RunEntry> pq = new PriorityQueue<>(Comparator.comparingInt(e -> e.key));

            // Початкові елементи
            addNextLine(readerA, pq);
            addNextLine(readerB, pq);

            while (!pq.isEmpty()) {
                RunEntry min = pq.poll();
                writer.println(min.line);
                addNextLine(min.scanner, pq);
            }

            writer.println();  // Маркер кінця нової серії
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Допоміжний метод: додавання наступного рядка до черги
    private static void addNextLine(Scanner reader, PriorityQueue<RunEntry> pq) {
        if (reader.hasNextLine()) {
            String line = reader.nextLine();
            if (!line.isEmpty()) {  // Ігноруємо порожні рядки-маркери
                pq.add(new RunEntry(line.charAt(0), line, reader));
            }
        }
    }

    // Допоміжний клас для пріоритетної черги
    static class RunEntry {
        char key;
        String line;
        Scanner scanner;

        RunEntry(char key, String line, Scanner scanner) {
            this.key = key;
            this.line = line;
            this.scanner = scanner;
        }
    }
}

//Компіляція
//javac -d . src/main/java/org/example/PolyphaseSortBasic.java
//обмеження ОП до 150МБ
//java -Xmx150m org.example.PolyphaseSortBasic
