package org.example;

import java.io.*;
import java.util.*;

public class PolyphaseSortModified {
    public static void main(String[] args) {
        String inputFile = "data.txt";
        String outputFile = "sorted_data.txt";
        int approxFileSizeMB = 1024;  // Розмір файлу для генерації
        int chunkSize = 1000000;  // Кількість рядків в шматку (~100 МБ, динамічно, залежно від пам'яті)

        generateFile(inputFile, approxFileSizeMB);  // Генеруємо файл
        polyphaseMergeSort(inputFile, outputFile, chunkSize);  // Сортуємо
    }

    //  метод створює великий файл з випадковими рядками типу: K-hello-+380991234567
    private static void generateFile(String filename, int sizeMB) {
        long targetSize = sizeMB * 1024L * 1024L;  // переводимо мегабайти в байти - цільовий розмір
        long currentSize = 0; //фактичний/згенерований розмір
        Random random = new Random(); // генератор випадкових чисел

        // try-with-resources, щоб файл сам закрився після роботи
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            while (currentSize < targetSize) { // поки не наберемо потрібний розмір файлу
                char key = (char) ('A' + random.nextInt(26));  // випадкова літера A-Z (65-90)
                String strPart = generateRandomString(random, 1, 45);  // Рядок 1-45 символів
                //Передаємо random для скорочення часу(не треба створювати новий об'єкт)
                String phone = generateRandomPhone(random);  // Телефон: +380...
                String line = key + "-" + strPart + "-" + phone + "\n";
                writer.write(line);
                currentSize += line.getBytes().length;
            }
        } catch (IOException e) {
            e.printStackTrace(); // якщо щось пішло не так — покаже помилку
        }
    }

    // Допоміжний: Генерує випадковий рядок
    private static String generateRandomString(Random random, int minLen, int maxLen) {
        int len = random.nextInt(maxLen - minLen + 1) + minLen;
        // не +- бо в Java string-незмінний, тобто кожен += створює новий об'єкт а це довго
        StringBuilder sb = new StringBuilder(len); //резерв місця під len символів
        for (int i = 0; i < len; i++) {
            sb.append((char) ('a' + random.nextInt(26))); //а-97, z-122
        }
        return sb.toString();
    }

    // Допоміжний: Генерує випадковий телефон
    private static String generateRandomPhone(Random random) {
        return "+380" + (random.nextInt(900000000) + 100000000);

        //або(можу бути повільніше)
        /*String code = codes[random.nextInt(codes.length)];
        int rest = random.nextInt(10000000);  // 7 цифр
        return "+380" + code + String.format("%07d", rest);*/
    }

    // Основний метод сортування: Багатофазне злиття
    private static void polyphaseMergeSort(String inputFile, String outputFile, int chunkSize) {
        long startTime = System.currentTimeMillis(); // запам'ятовуємо час початку

        // Створюємо 3 тимчасових файли для фаз (стандарт для polyphase)
        String[] tempFiles = {"temp1.txt", "temp2.txt", "temp3.txt"};
        for (String file : tempFiles) {
            new File(file).delete();  // Видаляємо старі, якщо є
        }

        // Фаза розподілу: Розподіляємо рани по temp1 і temp2 (temp3 - для злиття)
        distributeRuns(inputFile, tempFiles[0], tempFiles[1], chunkSize);

        // Фази злиття: Циклічно зливаємо, доки не залишиться один файл
        int phase = 0;
        while (true) {
            // Визначаємо, які файли зливати в який
            String target = tempFiles[phase % 3];
            String source1 = tempFiles[(phase + 1) % 3];
            String source2 = tempFiles[(phase + 2) % 3];

            // Якщо один з джерел порожній - кінець
            if (new File(source1).length() == 0 || new File(source2).length() == 0) {
                // Перейменовуємо непорожній в output
                String finalTemp = new File(source1).length() > 0 ? source1 : source2;
                new File(finalTemp).renameTo(new File(outputFile));
                break;
            }

            // Зливаємо source1 і source2 в target (multi-way merge)
            mergeTwoFiles(source1, source2, target);

            // Очищуємо джерела для наступної фази
            new File(source1).delete();
            new File(source2).delete();
            phase++;
        }

        // Видаляємо залишки
        for (String file : tempFiles) {
            new File(file).delete();
        }

        long endTime = System.currentTimeMillis();
        System.out.println("Сортування завершено за " + (endTime - startTime) / 1000.0 + " сек.");
    }

    // Фаза розподілу: Читаємо шматки, сортуємо в ОП, пишемо чергуючи в два файли
    private static void distributeRuns(String inputFile, String temp1, String temp2, int chunkSize) {
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
            boolean toTemp1 = true;  // Чергування файлів, починаємо писати в temp1
            while (true) {
                List<String> chunk = new ArrayList<>(chunkSize);
                String line;
                for (int i = 0; i < chunkSize && (line = reader.readLine()) != null; i++) {
                    chunk.add(line);
                }
                if (chunk.isEmpty()) break;

                // Сортуємо шматок в пам'яті (ascending за ключем - першою літерою) - run
                chunk.sort(Comparator.comparing(PolyphaseSortModified::getKey));

                // Пишемо в тимчасовий файл
                String target = toTemp1 ? temp1 : temp2;
                //Відкриваємо файл для запису в режимі дописування (true)
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(target, true))) {  // append mode
                    for (String sortedLine : chunk) {
                        writer.write(sortedLine + "\n");
                    }
                }
                toTemp1 = !toTemp1;  // Чергувати
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Допоміжний: Отримує ключ з рядка (перший сивол)
    private static char getKey(String line) {
        return line.charAt(0);  // Ключ - перша літера
    }

    // Метод злиття двох файлів в третій (використовуємо PriorityQueue для multi-way, але оскільки 2 - просто)
    private static void mergeTwoFiles(String file1, String file2, String output) {
        try {
            BufferedReader reader1 = new BufferedReader(new FileReader(file1));
            BufferedReader reader2 = new BufferedReader(new FileReader(file2));
            BufferedWriter writer = new BufferedWriter(new FileWriter(output));

            // PriorityQueue для злиття (min-heap за ключем)
            PriorityQueue<MergeEntry> pq = new PriorityQueue<>(Comparator.comparingInt(e -> e.key));

            // Додаємо перші рядки
            String line1 = reader1.readLine();
            if (line1 != null) pq.add(new MergeEntry(getKey(line1), line1, reader1));
            String line2 = reader2.readLine();
            if (line2 != null) pq.add(new MergeEntry(getKey(line2), line2, reader2));

            while (!pq.isEmpty()) {
                MergeEntry entry = pq.poll();
                writer.write(entry.line + "\n");

                // Додаємо наступний рядок з того ж читача
                String nextLine = entry.reader.readLine();
                if (nextLine != null) {
                    pq.add(new MergeEntry(getKey(nextLine), nextLine, entry.reader));
                }
            }

            reader1.close();
            reader2.close();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Допоміжний клас для купи: Тримає ключ, рядок, читач
    static class MergeEntry {
        char key;
        String line;
        BufferedReader reader;

        MergeEntry(char key, String line, BufferedReader reader) {
            this.key = key;
            this.line = line;
            this.reader = reader;
        }
    }
}