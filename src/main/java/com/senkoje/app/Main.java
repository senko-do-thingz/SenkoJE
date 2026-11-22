package com.senkoje.app;

import javafx.application.Application;

/**
 * Điểm khởi chạy chính của ứng dụng desktop SenkoJE.
 * Lớp này tách biệt khỏi lớp kế thừa JavaFX Application nhằm đảm bảo khởi chạy ổn định.
 */
public final class Main {

    // Ngăn khởi tạo thể hiện cho lớp điểm khởi chạy tĩnh
    private Main() {
    }

    /**
     * Khởi chạy vòng đời ứng dụng JavaFX SenkoJE.
     *
     * @param args các tham số dòng lệnh truyền vào ứng dụng
     */
    public static void main(String[] args) {
        Application.launch(SenkoJeApplication.class, args);
    }
}
