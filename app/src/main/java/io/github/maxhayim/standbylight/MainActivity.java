package io.github.maxhayim.standbylight;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One screen: what happened last time, switch the light off (and keep it off), or put it back. */
public final class MainActivity extends Activity {
    private TextView status;
    private Button off, normal;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.rgb(0x15, 0x17, 0x1c));
        int pad = dp(64);
        page.setPadding(pad, dp(48), pad, pad);

        page.addView(text("Standby Light", 34, Color.WHITE));
        page.addView(text("Keeps the light under the screen off: while the TV is on, in standby and in "
                + "low-power standby. It switches it off again after every restart and power cut.\n\n"
                + "The light is only reachable through the TV's own ADB (port 5555). The first time, the TV asks "
                + "\"Allow debugging?\" for this app: tick Always allow and choose Allow.", 18, 0xFFB8BCC6));

        status = text("", 18, 0xFFF2B84B);
        status.setPadding(0, dp(24), 0, dp(24));
        page.addView(status);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.START);
        off = button("Switch off and keep off", true);
        normal = button("Back to normal", false);
        row.addView(off);
        row.addView(normal);
        page.addView(row);
        setContentView(page);
        off.requestFocus();
        show();
    }

    private Button button(String label, boolean keepOff) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(16);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> run(keepOff));
        return b;
    }

    private void run(boolean keepOff) {
        Light.setKeepOff(this, keepOff);
        off.setEnabled(false);
        normal.setEnabled(false);
        status.setText("Working… if the TV asks \"Allow debugging?\", allow it.");
        new Thread(() -> {
            // Two tries, and a minute for the owner to answer the TV's prompt.
            Light.apply(this, keepOff, 2, 60_000);
            runOnUiThread(() -> {
                off.setEnabled(true);
                normal.setEnabled(true);
                show();
            });
        }, "standby-light").start();
    }

    private void show() {
        status.setText((Light.keepOff(this) ? "Kept off after every restart." : "Not kept off.")
                + "\nLast: " + Light.last(this));
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
