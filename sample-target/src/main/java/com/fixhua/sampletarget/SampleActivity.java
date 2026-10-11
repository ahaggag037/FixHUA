package com.fixhua.sampletarget;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public final class SampleActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView text = new TextView(this);
        text.setPadding(32, 48, 32, 32);
        text.setText("FixHUA Sample Target\nHost baseline:\n" + ProbeEntry.run(this));
        setContentView(text);
    }
}
