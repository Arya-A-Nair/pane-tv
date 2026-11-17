package tv.nova;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Line icons drawn as paths on a 24-unit grid; no image assets, crisp at any size. */
final class Icon extends View {
    static final int HOME = 0, GRID = 1, AMBIENT = 2, SLIDERS = 3, PLAY = 4, PIN = 5, WIFI = 6, INFO = 7, APPS = 8;
    private final int kind;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int color = 0xffe9ecf2;

    Icon(Context c, int kind) {
        super(c);
        this.kind = kind;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    void setColor(int c) { color = c; invalidate(); }

    @Override protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight()) / 24f;
        c.save();
        c.translate((getWidth() - 24 * s) / 2, (getHeight() - 24 * s) / 2);
        c.scale(s, s);
        p.setColor(color);
        p.setStrokeWidth(1.6f);
        p.setStyle(Paint.Style.STROKE);
        path.reset();
        switch (kind) {
            case HOME:
                path.moveTo(4, 11); path.lineTo(12, 4.5f); path.lineTo(20, 11);
                path.moveTo(6.5f, 9.5f); path.lineTo(6.5f, 19.5f); path.lineTo(17.5f, 19.5f); path.lineTo(17.5f, 9.5f);
                path.moveTo(10.2f, 19.5f); path.lineTo(10.2f, 14.5f); path.lineTo(13.8f, 14.5f); path.lineTo(13.8f, 19.5f);
                break;
            case GRID: case APPS:
                for (int i = 0; i < 4; i++) {
                    float x = 5 + (i % 2) * 8, y = 5 + (i / 2) * 8;
                    path.addRoundRect(new RectF(x, y, x + 6, y + 6), 1.6f, 1.6f, Path.Direction.CW);
                }
                break;
            case AMBIENT: // crescent and a small star
                path.addCircle(11, 13, 7, Path.Direction.CW);
                c.drawPath(path, p);
                path.reset();
                p.setStyle(Paint.Style.FILL);
                p.setColor(color);
                Paint cut = new Paint(Paint.ANTI_ALIAS_FLAG);
                cut.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
                p.setStyle(Paint.Style.STROKE);
                path.moveTo(18.5f, 3.5f); path.lineTo(18.5f, 7.5f); path.moveTo(16.5f, 5.5f); path.lineTo(20.5f, 5.5f);
                break;
            case SLIDERS:
                path.moveTo(5, 8); path.lineTo(19, 8); path.moveTo(5, 16); path.lineTo(19, 16);
                c.drawPath(path, p);
                path.reset();
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(9, 8, 2.4f, p);
                c.drawCircle(15, 16, 2.4f, p);
                p.setStyle(Paint.Style.STROKE);
                break;
            case PLAY:
                p.setStyle(Paint.Style.FILL);
                path.moveTo(8, 5.5f); path.lineTo(18.5f, 12); path.lineTo(8, 18.5f); path.close();
                break;
            case PIN:
                path.moveTo(12, 5); path.lineTo(12, 19); path.moveTo(5, 12); path.lineTo(19, 12);
                break;
            case WIFI:
                path.addArc(new RectF(3, 6, 21, 24), 225, 90);
                path.addArc(new RectF(6.5f, 9.5f, 17.5f, 20.5f), 225, 90);
                c.drawPath(path, p);
                path.reset();
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(12, 17.5f, 1.4f, p);
                p.setStyle(Paint.Style.STROKE);
                break;
            case INFO:
                path.addCircle(12, 12, 8, Path.Direction.CW);
                path.moveTo(12, 11); path.lineTo(12, 16);
                c.drawPath(path, p);
                path.reset();
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(12, 8, 1.1f, p);
                p.setStyle(Paint.Style.STROKE);
                break;
        }
        c.drawPath(path, p);
        c.restore();
    }
}
