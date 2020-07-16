package org.zorgblub.typhon;

import com.github.axet.bookreader.app.BookApplication;
import com.github.axet.bookreader.widgets.FBReaderView;

import org.geometerplus.zlibrary.text.view.ZLTextElement;
import org.geometerplus.zlibrary.text.view.ZLTextElementArea;
import org.geometerplus.zlibrary.text.view.ZLTextRegion;
import org.zorgblub.rikai.glosslist.SelectedWord;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TyphonIntegration {

    private static TyphonIntegration instance;

    private static Logger logger = Logger.getLogger("TyphonIntegration");

    public static TyphonIntegration createInstance(FBReaderView fb){
        instance = new TyphonIntegration(fb);
        return instance;
    }

    public static TyphonIntegration getInstance(){
        return instance;
    }


    private FBReaderView fb;

    private WordListener wordListener;


    public TyphonIntegration(FBReaderView fb) {
        this.fb = fb;
    }

    public void setWordListener(WordListener wordListener) {
        this.wordListener = wordListener;
    }

    public void onTap(ZLTextRegion textRegion){
        if(textRegion == null){
            return;
        }
        /**
         * Ugly way to get the text that was touched. Would be nice to replace it with something that respects the API but I could not find a
         * way to do it better without modifying the fbreader library.
         */
        Field myAreaList = null, myFromIndex = null, myToIndex = null, element = null; //NoSuchFieldException
        try {
            myAreaList = ZLTextRegion.class.getDeclaredField("myAreaList");
            myAreaList.setAccessible(true);
            myFromIndex = ZLTextRegion.class.getDeclaredField("myFromIndex");
            myFromIndex.setAccessible(true);
            myToIndex = ZLTextRegion.class.getDeclaredField("myToIndex");
            myToIndex.setAccessible(true);

            element = ZLTextElementArea.class.getDeclaredField("Element");
            element.setAccessible(true);


            StringBuffer buffer = new StringBuffer("");
            List<ZLTextElementArea> elements = new ArrayList<>();

            List<ZLTextElementArea> areaList = (List<ZLTextElementArea>) myAreaList.get(textRegion);
            int fromIndex = (int) myFromIndex.get(textRegion);

            for (int i = 0; buffer.length() < 16 && fromIndex + i < areaList.size(); i++) {
                ZLTextElementArea textElementArea = areaList.get(fromIndex + i);
                ZLTextElement textElement = (ZLTextElement) element.get(textElementArea);
                elements.add(textElementArea);
                buffer.append(textElement);
            }



            if(wordListener != null)
                wordListener.onWordChanged(new SelectedWord(new BookReaderPosition(elements), buffer.toString(), ""));

        } catch (NoSuchFieldException e) {
            logger.log(Level.WARNING, "Unable to get the text clicked", e);
        } catch (IllegalAccessException e) {
            logger.log(Level.WARNING, "Unable to get the text clicked", e);
        }
    }

    public void setMatch(SelectedWord word, int matchLength){
        BookReaderPosition position = (BookReaderPosition) word.getPosition();
        if(position == null)
            return;

        List<ZLTextElementArea> elements = position.getAreaList();


        Field myAreaList = null, myFromIndex = null, myToIndex = null, element = null, length = null; //NoSuchFieldException
        try {
            myAreaList = ZLTextRegion.class.getDeclaredField("myAreaList");
            myAreaList.setAccessible(true);
            myFromIndex = ZLTextRegion.class.getDeclaredField("myFromIndex");
            myFromIndex.setAccessible(true);
            myToIndex = ZLTextRegion.class.getDeclaredField("myToIndex");
            myToIndex.setAccessible(true);

            element = ZLTextElementArea.class.getDeclaredField("Element");
            element.setAccessible(true);

            length = ZLTextElementArea.class.getDeclaredField("Length");
            length.setAccessible(true);


            int cumulativeLength = 0;
            ZLTextElementArea first = elements.get(0);
            ZLTextElementArea last = null;

            for (int i = 0; i < elements.size(); i++) {
                ZLTextElementArea textElementArea = elements.get(i);
                int l = (int) length.get(textElementArea);
                cumulativeLength += l;
                if(cumulativeLength >= matchLength){
                    last = textElementArea;
                    break;
                }
            }


            FBReaderView.FBReaderApp app = this.fb.getApp();
            app.getTextView().highlight(first, last);


        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public void removeMatch(){
        FBReaderView.FBReaderApp app = this.fb.getApp();

        app.getTextView().clearHighlighting();
    }
}
