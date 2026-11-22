package com.senkoje.domain.repository;

import com.senkoje.domain.model.AppSetting;
import java.util.List;
import java.util.Optional;

/**
 * Giao diện kho lưu trữ cho bảng cấu hình ứng dụng cục bộ app_settings.
 */
public interface AppSettingRepository {

    /**
     * Đọc một cấu hình ứng dụng theo khóa định danh.
     *
     * @param settingKey khóa cấu hình
     * @return cấu hình tìm thấy nếu tồn tại
     */
    Optional<AppSetting> findByKey(String settingKey);

    /**
     * Lấy toàn bộ các cặp cấu hình đang được lưu trong cơ sở dữ liệu.
     *
     * @return danh sách cấu hình theo thứ tự khóa tăng dần
     */
    List<AppSetting> findAll();

    /**
     * Lưu mới hoặc ghi đè giá trị của một khóa cấu hình ứng dụng.
     *
     * @param setting cặp khóa và giá trị cần lưu
     * @return cấu hình đã lưu thành công
     */
    AppSetting save(AppSetting setting);

    /**
     * Lưu nguyên tử nhiều cặp khóa và giá trị cấu hình trong cùng một giao dịch cơ sở dữ liệu.
     *
     * @param settings danh sách cấu hình cần lưu
     */
    void saveAll(List<AppSetting> settings);

    /**
     * Xóa một khóa cấu hình khỏi cơ sở dữ liệu.
     *
     * @param settingKey khóa cấu hình cần xóa
     * @return true nếu có cấu hình bị xóa
     */
    boolean deleteByKey(String settingKey);
}
