package tv.nova;

import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** The navigation: a glass bar naming the pages. The focused name turns to solid Porcelain; the page on show is faintly marked. */
final class BottomBar {
    final GlassPanel view;
    private final List<TextView> items = new ArrayList<>();

    BottomBar(Ui ui, String[] names) {
        view = new GlassPanel(ui.context, ui.dp, 22);
        LinearLayout row = new LinearLayout(ui.context);
        int inset = ui.px(4);
        row.setPadding(inset, inset, inset, inset);
        for (String name : names) {
            TextView item = ui.text(ui.interMedium, 15, Ui.PORCELAIN);
            item.setGravity(Gravity.CENTER);
            item.setPadding(ui.px(20), 0, ui.px(20), 0);
            GradientDrawable capsule = new GradientDrawable();
            capsule.setCornerRadius(ui.px(18));
            item.setBackground(capsule);
            item.setText(name);
            row.addView(item, new LinearLayout.LayoutParams(-2, ui.px(36)));
            items.add(item);
        }
        view.addView(row);
    }

    /** focused is the item under focus, or -1 when focus is in the page; current is the page on show. */
    void show(int focused, int current) {
        for (int i = 0; i < items.size(); i++) {
            TextView item = items.get(i);
            ((GradientDrawable) item.getBackground()).setColor(i == focused ? Ui.PORCELAIN : i == current ? 0x26e9ecf2 : 0);
            item.setTextColor(i == focused ? Ui.OBSIDIAN : i == current ? Ui.PORCELAIN : Ui.MIST);
        }
    }
}
