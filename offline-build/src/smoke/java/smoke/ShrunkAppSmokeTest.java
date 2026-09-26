package smoke;

import android.app.Activity;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.robolectric.Shadows.shadowOf;

/** Runs the ProGuard-processed code (exactly what gets dexed into the APK). Java only, so no Kotlin stdlib clash. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class ShrunkAppSmokeTest {
    static void idle() { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500)); }

    static void walk(View v, List<View> out) {
        out.add(v);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) walk(((ViewGroup) v).getChildAt(i), out);
    }

    static View find(Activity a, String textOrDesc) {
        List<View> all = new ArrayList<>();
        walk(a.getWindow().getDecorView(), all);
        for (View v : all) {
            if (!v.isShown()) continue;
            CharSequence d = v.getContentDescription();
            if (d != null && d.toString().equals(textOrDesc)) return v;
            if (v instanceof TextView && ((TextView) v).getText().toString().equals(textOrDesc)) {
                View c = v;
                while (c != null && !c.isClickable()) c = c.getParent() instanceof View ? (View) c.getParent() : null;
                return c != null ? c : v;
            }
        }
        throw new AssertionError("not found: " + textOrDesc);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void launchAndNavigate() throws Exception {
        Class<? extends Activity> main = (Class<? extends Activity>) Class.forName("com.netspeedtest.MainActivity");
        ActivityController<? extends Activity> c = Robolectric.buildActivity(main).setup();
        idle();
        Activity a = c.get();
        for (String target : new String[] {"Settings", "History", "Battery", "Temp", "Network", "Memory"}) {
            find(a, target).performClick();
            idle();
            a.onBackPressed();
            idle();
        }
        find(a, "Start test").performClick();
        idle();
        c.pause().stop().destroy();
    }
}
