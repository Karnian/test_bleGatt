package com.example.android.bluetoothlegatt;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.view.View;
import android.widget.Button;

public class MainActivity extends Activity {
    public StartActivity mStartActivity;
    public Button buttonConnect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonConnect = (Button) findViewById(R.id.button);
        buttonConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mStartActivity = new StartActivity();
                mStartActivity.execute();
            }
        });
    }

    /*
    public void onAddButtonClicked(View v) {
        mStartActivity = new StartActivity();
        mStartActivity.execute();
    }
*/
    class StartActivity extends AsyncTask {

        @Override
        protected Object doInBackground(Object[] objects) {
            startActivity(new Intent(MainActivity.this, DeviceScanActivity.class));
            return null;
        }
    }
}
