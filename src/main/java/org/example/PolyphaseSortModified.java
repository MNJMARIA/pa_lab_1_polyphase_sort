package org.example;

import java.io.*;
import java.util.*;

public class PolyphaseSortModified {
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
    private static void generateLargeFile(String filename, int sizeMB) {
        // Цільовий розмір у байтах
        long targetBytes = sizeMB * 1024L * 1024L;
        long currentBytes = 0;
        Random random = new Random();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename), 8 * 1024 * 1024)) {
            // Генеруємо записи, доки не досягнемо потрібного розміру
            while (currentBytes < targetBytes) {
                char key = (char) ('A' + random.nextInt(26)); // Ключ: літера A-Z
                String data = "";
                int len = random.nextInt(36) + 10; // Рядок довжиною 10–45 символів
                for (int i = 0; i < len; i++) {
                    data += (char) ('a' + random.nextInt(26));  // Повільне накопичення
                }
                String phone = "+380" + (random.nextInt(900000000) + 100000000);  // Телефон
                String line = key + "-" + data + "-" + phone + "\n";

                writer.write(line);  // Записуємо рядок
                currentBytes += line.getBytes().length;  // Оновлюємо поточний розмір
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Основний метод багатофазного сортування
    private static void polyphaseMergeSort() {
        // Створюємо 3 тимчасових файли (m=3
        String[] tempFiles = {"temp1.txt", "temp2.txt", "temp3.txt"};
        for (String f : tempFiles) {
            new File(f).delete(); // Видаляємо попередні версії, якщо є
        }

        // Динамічне обчислення розміру шматка
        long maxMem = Runtime.getRuntime().maxMemory();  // Максимум доступної пам'яті в байтах
        int avgSize = 150;  // Середній розмір одного запису з overhead
        int chunkSize = (int) (maxMem / avgSize);  // Скільки рядків вміщається

        // Верхнє обмеження: не більше ~500 МБ
        // (щоб уникнути OutOfMemoryError при дуже великій RAM)
        chunkSize = Math.min(chunkSize, 5_000_000);
        // Нижнє обмеження: не менше ~100 МБ
        chunkSize = Math.max(chunkSize, 1_000_000);

        System.out.println("Пам'ять: " + (maxMem / 1024 / 1024) + " MB");
        System.out.println("Шматок: " + chunkSize + " рядків");

        // Фаза 1: Розбиття на початкові серії з визначенням природних серій
        distributeNaturalRuns(chunkSize);

        // Фаза 2: Багатофазне злиття
        int phase = 0;
        while (true) {
            String target = tempFiles[phase % 3];
            String source1 = tempFiles[(phase + 1) % 3];
            String source2 = tempFiles[(phase + 2) % 3];

            // Якщо один з джерел порожній — це кінець
            if (new File(source1).length() == 0 || new File(source2).length() == 0) {
                String finalFile = new File(source1).length() > 0 ? source1 : source2;
                new File(finalFile).renameTo(new File("output.txt"));
                break;
            }

            // Злиття двох файлів у target
            mergeTwoRuns(source1, source2, target);

            // Очищення використаних файлів
            new File(source1).delete();
            new File(source2).delete();
            phase++;
        }

        // Очищення залишків
        for (String f : tempFiles) {
            new File(f).delete();
        }
    }

    // Розбиття на початкові серії (runs) з визначенням природних серій
    private static void distributeNaturalRuns(int maxChunkSize) {
        try (BufferedReader reader = new BufferedReader(new FileReader("input.txt"), 16 * 1024 * 1024)) {
            boolean toTemp1 = true;

            // Відкриваємо обидва файли один раз
            try (BufferedWriter writer1 = new BufferedWriter(new FileWriter("temp1.txt", true), 8 * 1024 * 1024);
                 BufferedWriter writer2 = new BufferedWriter(new FileWriter("temp2.txt", true), 8 * 1024 * 1024)) {

                BufferedWriter currentWriter = writer1;
                String prevLine = null;
                char prevKey = 0;
                List<String> chunk = new ArrayList<>();

                String line;
                while ((line = reader.readLine()) != null) {
                    char currentKey = line.charAt(0);

                    if (prevKey != 0 && currentKey < prevKey) {
                        // Кінець природної серії — записуємо
                        writeChunkToWriter(chunk, currentWriter);
                        chunk.clear();
                        currentWriter = (currentWriter == writer1) ? writer2 : writer1;
                    }

                    chunk.add(line);
                    prevKey = currentKey;

                    // Захист від надто довгої серії
                    if (chunk.size() >= maxChunkSize) {
                        writeChunkToWriter(chunk, currentWriter);
                        chunk.clear();
                    }
                }

                // Запис останньої серії
                if (!chunk.isEmpty()) {
                    writeChunkToWriter(chunk, currentWriter);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Запис шматка (серії) у файл
    private static void writeChunkToWriter(List<String> chunk, BufferedWriter writer) throws IOException {
        for (String s : chunk) {
            writer.write(s + "\n");
        }
        writer.write("\n");
    }

    // Злиття двох файлів (серій) у третій
    private static void mergeTwoRuns(String fileA, String fileB, String outputFile) {
        try (BufferedReader readerA = new BufferedReader(new FileReader(fileA), 4 * 1024 * 1024);
             BufferedReader readerB = new BufferedReader(new FileReader(fileB), 4 * 1024 * 1024);
             BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile), 8 * 1024 * 1024)) {

            PriorityQueue<RunEntry> pq = new PriorityQueue<>(Comparator.comparingInt(e -> e.key));

            // Початкові елементи
            addNextLine(readerA, pq);
            addNextLine(readerB, pq);

            while (!pq.isEmpty()) {
                RunEntry min = pq.poll();
                writer.write(min.line + "\n");
                addNextLine(min.reader, pq);
            }

            writer.write("\n"); // Маркер кінця нової серії
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Допоміжний метод: додавання наступного рядка до черги
    private static void addNextLine(BufferedReader reader, PriorityQueue<RunEntry> pq) throws IOException {
        String line = reader.readLine();
        if (line != null && !line.isEmpty()) { // Ігноруємо порожні рядки-маркери
            pq.add(new RunEntry(line.charAt(0), line, reader));
        }
    }

    // Допоміжний клас для пріоритетної черги
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

//Компіляція
//javac -d . src/main/java/org/example/PolyphaseSortModified.java
//обмеження ОП до 150МБ
//java -Xmx150m org.example.PolyphaseSortModified
