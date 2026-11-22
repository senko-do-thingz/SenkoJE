package com.senkoje.domain.fsrs;

import java.util.Arrays;

/**
 * Bộ tham số bất biến của thuật toán lập lịch FSRS-6 gồm phiên bản thuật toán,
 * mã phiên bản bộ tham số và 21 trọng số chuẩn w0 đến w20.
 */
public final class FsrsParameters {

    public static final String ALGORITHM_VERSION = "FSRS-6";
    public static final String DEFAULT_PARAMETER_VERSION = "fsrs-6-default-v1";
    public static final int PARAMETER_COUNT = 21;
    public static final double STABILITY_MIN = 0.001;
    public static final double MIN_DIFFICULTY = 1.0;
    public static final double MAX_DIFFICULTY = 10.0;

    // 21 trọng số mặc định chuẩn của FSRS-6 (w0..w20) theo đặc tả tham chiếu mở
    private static final double[] DEFAULT_WEIGHTS = {
            0.212, 1.2931, 2.3065, 8.2956,
            6.4133, 0.8334, 3.0194, 0.001,
            1.8722, 0.1666, 0.796, 1.4835,
            0.0614, 0.2629, 1.6483, 0.6014,
            1.8729, 0.5425, 0.0912, 0.0658,
            0.1542
    };

    private static final FsrsParameters DEFAULTS = new FsrsParameters(
            ALGORITHM_VERSION,
            DEFAULT_PARAMETER_VERSION,
            DEFAULT_WEIGHTS
    );

    private final String algorithmVersion;
    private final String parameterVersion;
    private final double[] weights;
    private final double decay;
    private final double factor;

    /**
     * Khởi tạo bộ tham số FSRS-6 với phiên bản và mảng 21 trọng số đã được kiểm tra hợp lệ.
     *
     * @param algorithmVersion mã phiên bản thuật toán (phải là FSRS-6)
     * @param parameterVersion mã định danh phiên bản bộ tham số
     * @param weights mảng chính xác 21 trọng số FSRS-6
     */
    public FsrsParameters(String algorithmVersion, String parameterVersion, double[] weights) {
        if (algorithmVersion == null || algorithmVersion.isBlank()) {
            throw new IllegalArgumentException("Algorithm version must not be blank");
        }
        if (!ALGORITHM_VERSION.equals(algorithmVersion.trim())) {
            throw new IllegalArgumentException("Unsupported FSRS algorithm version: " + algorithmVersion);
        }
        if (parameterVersion == null || parameterVersion.isBlank()) {
            throw new IllegalArgumentException("Parameter version must not be blank");
        }
        if (weights == null || weights.length != PARAMETER_COUNT) {
            throw new IllegalArgumentException(
                    "FSRS-6 requires exactly " + PARAMETER_COUNT + " parameters"
            );
        }

        double[] copy = Arrays.copyOf(weights, weights.length);
        for (int i = 0; i < copy.length; i++) {
            if (!Double.isFinite(copy[i])) {
                throw new IllegalArgumentException("FSRS-6 weight at index " + i + " must be finite");
            }
        }
        // Các trọng số độ ổn định khởi đầu (w0..w3) phải dương để đảm bảo mô hình trí nhớ hợp lệ
        for (int i = 0; i < 4; i++) {
            if (copy[i] <= 0.0) {
                throw new IllegalArgumentException("Initial stability weight w[" + i + "] must be positive");
            }
        }
        // Trọng số suy giảm đường cong quên w20 phải dương để tính hệ số factor hợp lệ
        if (copy[20] <= 0.0) {
            throw new IllegalArgumentException("Decay weight w[20] must be positive");
        }

        this.algorithmVersion = algorithmVersion.trim();
        this.parameterVersion = parameterVersion.trim();
        this.weights = copy;
        this.decay = -copy[20];
        this.factor = Math.pow(0.9, 1.0 / this.decay) - 1.0;
    }

    /**
     * Trả về bộ tham số FSRS-6 mặc định an toàn.
     *
     * @return đối tượng FsrsParameters mặc định
     */
    public static FsrsParameters defaults() {
        return DEFAULTS;
    }

    /**
     * Tạo bộ tham số FSRS-6 tùy chỉnh với mã phiên bản tham số chỉ định.
     *
     * @param parameterVersion mã phiên bản bộ tham số tùy chỉnh
     * @param weights mảng 21 trọng số FSRS-6
     * @return đối tượng FsrsParameters mới
     */
    public static FsrsParameters of(String parameterVersion, double[] weights) {
        return new FsrsParameters(ALGORITHM_VERSION, parameterVersion, weights);
    }

    /**
     * Trả về mã phiên bản thuật toán (FSRS-6).
     *
     * @return chuỗi phiên bản thuật toán
     */
    public String algorithmVersion() {
        return algorithmVersion;
    }

    /**
     * Trả về mã phiên bản bộ tham số đang sử dụng.
     *
     * @return chuỗi phiên bản tham số
     */
    public String parameterVersion() {
        return parameterVersion;
    }

    /**
     * Lấy giá trị trọng số tại chỉ số xác định từ 0 đến 20.
     *
     * @param index chỉ số trọng số (0-20)
     * @return giá trị trọng số tương ứng
     */
    public double weight(int index) {
        return weights[index];
    }

    /**
     * Trả về bản sao phòng thủ của mảng 21 trọng số FSRS-6.
     *
     * @return mảng bản sao gồm 21 trọng số
     */
    public double[] weightsCopy() {
        return Arrays.copyOf(weights, weights.length);
    }

    /**
     * Trả về số mũ suy giảm âm (-w20) của đường cong quên FSRS-6.
     *
     * @return giá trị decay âm
     */
    public double decay() {
        return decay;
    }

    /**
     * Trả về hệ số tỷ lệ factor sao cho R(S, S) luôn bằng 0.90.
     *
     * @return hệ số tỷ lệ đường cong quên
     */
    public double factor() {
        return factor;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FsrsParameters that)) {
            return false;
        }
        return algorithmVersion.equals(that.algorithmVersion)
                && parameterVersion.equals(that.parameterVersion)
                && Arrays.equals(weights, that.weights);
    }

    @Override
    public int hashCode() {
        int result = algorithmVersion.hashCode();
        result = 31 * result + parameterVersion.hashCode();
        result = 31 * result + Arrays.hashCode(weights);
        return result;
    }
}
