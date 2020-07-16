package org.zorgblub.typhon;

import org.geometerplus.zlibrary.text.view.ZLTextElementArea;
import org.zorgblub.rikai.glosslist.TextPosition;

import java.util.List;

public class BookReaderPosition implements TextPosition {
    private List<ZLTextElementArea> areaList;

    public BookReaderPosition(List<ZLTextElementArea> areaList) {
        this.areaList = areaList;
    }

    public List<ZLTextElementArea> getAreaList() {
        return areaList;
    }
}
