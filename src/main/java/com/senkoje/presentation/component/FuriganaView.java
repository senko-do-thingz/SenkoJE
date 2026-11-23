package com.senkoje.presentation.component;

import com.senkoje.infrastructure.nlp.FuriganaPart;
import com.senkoje.infrastructure.nlp.FuriganaSegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Thành phần JavaFX tái sử dụng dùng để hiển thị văn bản tiếng Nhật kèm cách đọc Furigana
 * phía trên đúng các cụm chữ Hán, hỗ trợ bật/tắt Furigana, chọn token tương tác và cập nhật cách đọc trực tiếp.
 */
public class FuriganaView extends VBox {

    private final BooleanProperty showFurigana = new SimpleBooleanProperty(true);
    private final ObjectProperty<FuriganaSegment> selectedSegment = new SimpleObjectProperty<>();

    private final List<FuriganaSegment> segments = new ArrayList<>();
    private final List<Node> segmentNodes = new ArrayList<>();
    private int selectedIndex = -1;
    private BiConsumer<Integer, FuriganaSegment> onSegmentSelected;

    /**
     * Khởi tạo thành phần hiển thị Furigana với khoảng cách dòng gọn gàng và bật Furigana mặc định.
     */
    public FuriganaView() {
        setSpacing(8);
        setFillWidth(true);
        getStyleClass().add("furigana-view");
    }

    /**
     * Thuộc tính điều khiển việc hiển thị hoặc ẩn cách đọc Furigana phía trên chữ Hán.
     *
     * @return thuộc tính boolean bật/tắt Furigana
     */
    public BooleanProperty showFuriganaProperty() {
        return showFurigana;
    }

    public boolean isShowFurigana() {
        return showFurigana.get();
    }

    public void setShowFurigana(boolean show) {
        showFurigana.set(show);
    }

    /**
     * Thuộc tính đại diện cho phân đoạn đang được người dùng chọn.
     *
     * @return thuộc tính phân đoạn đang chọn
     */
    public ObjectProperty<FuriganaSegment> selectedSegmentProperty() {
        return selectedSegment;
    }

    public FuriganaSegment getSelectedSegment() {
        return selectedSegment.get();
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    /**
     * Đăng ký hàm gọi lại khi người dùng nhấp chọn một phân đoạn từ vựng.
     *
     * @param listener hàm nhận chỉ số và phân đoạn vừa được chọn
     */
    public void setOnSegmentSelected(BiConsumer<Integer, FuriganaSegment> listener) {
        this.onSegmentSelected = listener;
    }

    /**
     * Thiết lập danh sách phân đoạn Furigana mới và dựng lại toàn bộ giao diện hiển thị.
     *
     * @param newSegments danh sách phân đoạn cần hiển thị
     */
    public void setSegments(List<FuriganaSegment> newSegments) {
        segments.clear();
        if (newSegments != null) {
            segments.addAll(newSegments);
        }
        selectedIndex = -1;
        selectedSegment.set(null);
        rebuildView();
    }

    /**
     * Lấy bản sao bất biến của danh sách phân đoạn hiện tại.
     *
     * @return danh sách phân đoạn hiện đang hiển thị
     */
    public List<FuriganaSegment> getSegments() {
        return List.copyOf(segments);
    }

    /**
     * Cập nhật một phân đoạn cụ thể sau khi người dùng chỉnh sửa cách đọc Furigana và giữ nguyên lựa chọn.
     *
     * @param index chỉ số phân đoạn cần cập nhật
     * @param updatedSegment phân đoạn mới đã căn chỉnh lại
     */
    public void updateSegment(int index, FuriganaSegment updatedSegment) {
        Objects.requireNonNull(updatedSegment, "updatedSegment must not be null");
        if (index < 0 || index >= segments.size()) {
            throw new IndexOutOfBoundsException("Segment index out of bounds: " + index);
        }
        segments.set(index, updatedSegment);
        int previousSelection = selectedIndex;
        rebuildView();
        if (previousSelection >= 0 && previousSelection < segments.size()) {
            applySelection(previousSelection, false);
        }
    }

    /**
     * Chọn một phân đoạn theo chỉ số (chỉ áp dụng cho phân đoạn từ vựng có thể tra cứu).
     *
     * @param index chỉ số phân đoạn cần chọn
     */
    public void selectSegment(int index) {
        if (index < 0 || index >= segments.size()) {
            return;
        }
        if (!segments.get(index).isInspectable()) {
            return;
        }
        applySelection(index, true);
    }

    /**
     * Xóa toàn bộ nội dung đang hiển thị và hủy lựa chọn hiện tại.
     */
    public void clear() {
        segments.clear();
        segmentNodes.clear();
        selectedIndex = -1;
        selectedSegment.set(null);
        getChildren().clear();
    }

    private void rebuildView() {
        getChildren().clear();
        segmentNodes.clear();
        if (segments.isEmpty()) {
            return;
        }

        FlowPane currentLine = createLinePane();
        getChildren().add(currentLine);

        for (int i = 0; i < segments.size(); i++) {
            FuriganaSegment segment = segments.get(i);
            if (segment.containsLineBreak()) {
                // Giữ nguyên số lượng dòng trống theo đúng văn bản gốc
                String normalized = segment.surface().replace("\r\n", "\n").replace('\r', '\n');
                long breakCount = normalized.chars().filter(ch -> ch == '\n').count();
                segmentNodes.add(null);
                for (long b = 0; b < breakCount; b++) {
                    currentLine = createLinePane();
                    getChildren().add(currentLine);
                }
                continue;
            }

            Node node = buildSegmentNode(i, segment);
            segmentNodes.add(node);
            currentLine.getChildren().add(node);
        }
    }

    private FlowPane createLinePane() {
        FlowPane linePane = new FlowPane();
        linePane.setHgap(2);
        linePane.setVgap(8);
        linePane.setAlignment(Pos.BOTTOM_LEFT);
        linePane.getStyleClass().add("furigana-line");
        linePane.prefWrapLengthProperty().bind(widthProperty().subtract(16));
        return linePane;
    }

    private Node buildSegmentNode(int index, FuriganaSegment segment) {
        HBox tokenBox = new HBox(0);
        tokenBox.setAlignment(Pos.BOTTOM_LEFT);

        for (FuriganaPart part : segment.parts()) {
            tokenBox.getChildren().add(buildPartNode(part));
        }

        if (segment.isInspectable()) {
            tokenBox.getStyleClass().add("furigana-token");
            if (segment.customEdited()) {
                tokenBox.getStyleClass().add("furigana-token-edited");
            }
            tokenBox.setCursor(Cursor.HAND);
            tokenBox.setAccessibleRole(AccessibleRole.BUTTON);
            tokenBox.setAccessibleText(segment.surface());
            tokenBox.setOnMouseClicked(event -> applySelection(index, true));
        } else {
            tokenBox.getStyleClass().add("furigana-punctuation");
        }
        return tokenBox;
    }

    private Node buildPartNode(FuriganaPart part) {
        Label baseLabel = new Label(part.text());
        baseLabel.getStyleClass().add("furigana-base");

        if (!part.hasReading()) {
            return baseLabel;
        }

        Label rubyLabel = new Label(part.reading());
        rubyLabel.getStyleClass().add("furigana-ruby");
        rubyLabel.visibleProperty().bind(showFurigana);
        rubyLabel.managedProperty().bind(showFurigana);

        VBox rubyBox = new VBox(0, rubyLabel, baseLabel);
        rubyBox.setAlignment(Pos.BOTTOM_CENTER);
        return rubyBox;
    }

    private void applySelection(int index, boolean notifyListener) {
        if (selectedIndex >= 0 && selectedIndex < segmentNodes.size()) {
            Node previousNode = segmentNodes.get(selectedIndex);
            if (previousNode != null) {
                previousNode.getStyleClass().remove("furigana-token-selected");
            }
        }
        selectedIndex = index;
        FuriganaSegment segment = segments.get(index);
        selectedSegment.set(segment);

        Node activeNode = segmentNodes.get(index);
        if (activeNode != null && !activeNode.getStyleClass().contains("furigana-token-selected")) {
            activeNode.getStyleClass().add("furigana-token-selected");
        }
        if (notifyListener && onSegmentSelected != null) {
            onSegmentSelected.accept(index, segment);
        }
    }
}
