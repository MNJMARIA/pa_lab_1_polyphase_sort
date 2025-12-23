package org.example;

import java.io.*;
import java.util.*;

public class PolyphaseSort {
    public static void main(String[] args) {
        String inputFile = "data.txt"; // назва файлу, який сортуватимемо
        String outputFile = "sorted_data.txt"; // відсортований файл
        int sizeMB = 100; // скільки мегабайт має бути файл (можна змінити на 1024 = 1 ГБ)

        generateFile(inputFile, sizeMB); //створюємо великий файл з випадковими даними
        simplePolyphaseSort(inputFile, outputFile); // сортуємо його
    }

    //  метод створює великий файл з випадковими рядками типу: K-hello-+380991234567
    private static void generateFile(String filename, int sizeMB) {
        long targetSize = (long) sizeMB * 1024 * 1024; // переводимо мегабайти в байти - цільовий розмір
        long currentSize = 0;  //фактичний/згенерований розмір
        Random random = new Random();  // генератор випадкових чисел

        // try-with-resources, щоб файл сам закрився після роботи
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            while (currentSize < targetSize) {  // поки не наберемо потрібний розмір файлу
                char key = (char) ('A' + random.nextInt(26));  // випадкова літера A-Z (65-90)
                String text = "";
                int len = random.nextInt(45) + 1;  // довжина тексту від 1 до 45 символів
                for (int i = 0; i < len; i++) {
                    text += (char) ('a' + random.nextInt(26));  // додаємо випадкові маленькі літери
                }
                String phone = "+380" + (random.nextInt(900000000) + 100000000); // випадковий телефон
                String line = key + "-" + text + "-" + phone + "\n";  // один рядок файлу

                writer.write(line); // записуємо рядок у файл
                currentSize += line.getBytes().length; // рахуємо, скільки байтів додали
            }
        } catch (IOException e) {
            e.printStackTrace();  // якщо щось пішло не так — покаже помилку
        }
    }

    // сортування
    private static void simplePolyphaseSort(String inputFile, String output) {
        long startTime = System.currentTimeMillis();  // запам'ятовуємо час початку

        // Створюємо 3 тимчасових файли для фаз (стандарт для polyphase)
        String[] tempFiles = {"temp1.txt", "temp2.txt", "temp3.txt"};
        for (int i = 0; i < tempFiles.length; i++) {
            String filename = tempFiles[i];
            File f = new File(filename); // створюється об'єкт файлу
            f.delete(); // видаляється файл з диска, якщо він є
        }

        // Фаза розподілу: Розбиваємо великий файл на маленькі відсортовані шматки (по 500 000 рядків)
        distributeSimple(inputFile, tempFiles[0], tempFiles[1]);

        // Крок 2: тепер зливаємо їх по колу, як воду переливаємо між коробками
        int phase = 0;  // номер фази (етапу)
        while (true) {
            String source1 = tempFiles[phase % 3]; // перший файл для злиття (залежно від числа фази остача від 0 до 2)
            String source2 = tempFiles[(phase + 1) % 3]; // другий файл
            String target = tempFiles[(phase + 2) % 3]; // файл для запису результату

            // Якщо один з файлів порожній — сортування закінчено
            if (new File(source1).length() == 0 || new File(source2).length() == 0) {
                String result = new File(source1).length() > 0 ? source1 : source2;  // який файл не порожній — той і результат
                new File(result).renameTo(new File(output));          // перейменовуємо його в sorted.txt
                break;  // виходимо з циклу
            }

            // Зливаємо два файли в третій
            mergeSimple(source1, source2, target);

            // Очищаємо старі файли — вони нам більше не потрібні
            new File(source1).delete();
            new File(source2).delete();

            phase++;  // переходимо до наступної фази
        }

        // Прибираємо за собою — видаляємо всі тимчасові файли
        for (String f : tempFiles) new File(f).delete();

        long end = System.currentTimeMillis();
        System.out.println("Перша версія: " + (end - startTime) / 1000.0 + " сек");
    }

    // Розподіл даних на відсортовані шматки
    private static void distributeSimple(String inputFile, String temp1, String temp2) {
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
            boolean toTemp1 = true;  // куди писати наступний шматок: спочатку в temp1
            List<String> chunk = new ArrayList<>();  // у списку збираємо один шматок
            String line;

            while ((line = reader.readLine()) != null) {
                chunk.add(line);  // додаємо рядок у шматок

                // Коли набрали 500 000 рядків — сортуємо і записуємо
                if (chunk.size() >= 500000) {
                    chunk.sort(Comparator.comparing(l -> l.charAt(0)));  // сортуємо за першою літерою

                    String target = toTemp1 ? temp1 : temp2;  // куди писати
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(target, true))) {
                        for (String string : chunk) {
                            writer.write(string + "\n");  // записуємо відсортований шматок
                        }
                    }
                    chunk.clear();  // очищаємо список
                    toTemp1 = !toTemp1; // наступного разу пишемо в інший файл
                }
            }

            // Якщо залишився останній маленький шматок — теж сортуємо і записуємо
            if (!chunk.isEmpty()) {
                chunk.sort(Comparator.comparing(l -> l.charAt(0)));
                String target = toTemp1 ? temp1 : temp2;
                try (BufferedWriter w = new BufferedWriter(new FileWriter(target, true))) {
                    for (String s : chunk) w.write(s + "\n");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // злиття двох відсортованих файлів
    private static void mergeSimple(String file1, String file2, String out) {
        try (BufferedReader reader1 = new BufferedReader(new FileReader(file1));
             BufferedReader reader2 = new BufferedReader(new FileReader(file2));
             BufferedWriter writer = new BufferedWriter(new FileWriter(out))) {

            String line1 = reader1.readLine();  // перший рядок з першого файлу
            String line2 = reader2.readLine();  // перший рядок з другого файлу

            // Поки в обох файлах є рядки — порівнюємо їх
            while (line1 != null && line2 != null) {
                if (line1.charAt(0) <= line2.charAt(0)) {  // якщо літера в першому менша або рівна
                    writer.write(line1 + "\n");
                    line1 = reader1.readLine();  // беремо наступний з першого файлу
                } else {
                    writer.write(line2 + "\n");
                    line2 = reader2.readLine();  // беремо наступний з другого файлу
                }
            }

            // Якщо в якомусь файлі ще щось залишилося — дописуємо
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
}
