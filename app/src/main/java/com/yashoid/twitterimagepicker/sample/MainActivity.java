package com.yashoid.twitterimagepicker.sample;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.storage.StorageManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.yashoid.twitterimagepicker.TwitterImagePickerActivity;

import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int REQUEST_SINGLE = 0;
    private static final int REQUEST_MULTIPLE = 1;

    private TextView mTextOutput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextOutput = findViewById(R.id.text_output);

        findViewById(R.id.button_single).setOnClickListener(this);
        findViewById(R.id.button_multiple).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        Intent intent;
        switch (v.getId()) {
            case R.id.button_single:
                intent = TwitterImagePickerActivity.getSingleSelectionIntent(this, true);
                startActivityForResult(intent, REQUEST_SINGLE);
                break;
            case R.id.button_multiple:
                intent = TwitterImagePickerActivity.getMultiSelectionIntent(this, true, 7);
                startActivityForResult(intent, REQUEST_MULTIPLE);
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_CANCELED) {
            mTextOutput.setText("CANCELED");
            return;
        }

        String text = null;

        switch (requestCode) {
            case REQUEST_SINGLE:
                text = ((Uri) data.getParcelableExtra(TwitterImagePickerActivity.EXTRA_OUTPUT)).toString();
                break;
            case REQUEST_MULTIPLE:
                Parcelable[] uris = data.getParcelableArrayExtra(TwitterImagePickerActivity.EXTRA_OUTPUT);

                text = "";

                for (Parcelable uri: uris) {
                    text = text + uri + "\n";
                }
                break;
        }

        mTextOutput.setText(text);
    }

}