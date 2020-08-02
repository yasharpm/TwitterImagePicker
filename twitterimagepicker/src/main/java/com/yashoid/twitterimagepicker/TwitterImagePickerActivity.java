package com.yashoid.twitterimagepicker;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class TwitterImagePickerActivity extends AppCompatActivity implements
        GalleryAccess.OnPreparedListener, AdapterView.OnItemSelectedListener,
        View.OnClickListener {

    public static final String EXTRA_SELECTION_MODE = "selection_mode";
    public static final String EXTRA_MULTI_SELECT_MAX_COUNT = "multi_select_max_count";

    public static final String EXTRA_OUTPUT = MediaStore.EXTRA_OUTPUT;

    /**
     * EXTRA_OUTPUT will contain a single image Uri.
     * @param context
     * @param finishOnSelection If true, the selection will return immediately after clicking an image.
     * @return
     */
    public static Intent getSingleSelectionIntent(Context context, boolean finishOnSelection) {
        Intent intent = new Intent(context, TwitterImagePickerActivity.class);

        intent.putExtra(EXTRA_SELECTION_MODE, finishOnSelection ? GalleryFragment.SELECTION_MODE_SINGLE_IMMEDIATE : GalleryFragment.SELECTION_MODE_SINGLE_WAIT);

        return intent;
    }

    /**
     * EXTRA_OUTPUT will contain an array of image uris.
     * @param context
     * @param defaultMultiSelect  If false, the user enters multi selection mode by long pressing a second image.
     * @param maxCount 0 for unlimited
     * @return
     */
    public static Intent getMultiSelectionIntent(Context context, boolean defaultMultiSelect, int maxCount) {
        Intent intent = new Intent(context, TwitterImagePickerActivity.class);

        intent.putExtra(EXTRA_SELECTION_MODE, defaultMultiSelect ? GalleryFragment.SELECTION_MODE_MULTIPLE : GalleryFragment.SELECTION_MODE_MULTIPLE_LONG_PRESS);
        intent.putExtra(EXTRA_MULTI_SELECT_MAX_COUNT, maxCount);

        return intent;
    }

    private GalleryAccess mGalleryAccess;

    private Spinner mSpinnerBucket;

    private GalleryFragment mGalleryFragment;

    private BucketAdapter mAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_twitterimagepicker);

        mGalleryAccess = GalleryAccess.getInstance(this);
        mGalleryAccess.prepare();

        mSpinnerBucket = findViewById(R.id.spinner_bucket);

        findViewById(R.id.button_close).setOnClickListener(this);
        findViewById(R.id.button_done).setOnClickListener(this);

        mGalleryFragment = GalleryFragment.newInstance();

        getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.container, mGalleryFragment)
                .commit();

        mGalleryFragment.setImageBucketIndex(-1);

        mAdapter = new BucketAdapter();
        mSpinnerBucket.setAdapter(mAdapter);
        mSpinnerBucket.setOnItemSelectedListener(this);

        mGalleryAccess.registerOnPreparedListener(this);

        Intent intent = getIntent();

        mGalleryFragment.setSelectionMode(intent.getIntExtra(EXTRA_SELECTION_MODE, GalleryFragment.SELECTION_MODE_MULTIPLE_LONG_PRESS));
        mGalleryFragment.setMultiSelectMaxCount(intent.getIntExtra(EXTRA_MULTI_SELECT_MAX_COUNT, GalleryFragment.DEFAULT_MULTI_SELECT_MAX_COUNT));
    }

    @Override
    public void onGalleryAccessPrepared(GalleryAccess galleryAccess) {
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        setSelectedBucket(position);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        setSelectedBucket(0);
    }

    private void setSelectedBucket(int index) {
        mGalleryFragment.setImageBucketIndex(index - 1);
    }

    protected void onSingleImageSelected(Uri uri) {
        Intent data = new Intent();
        data.putExtra(EXTRA_OUTPUT, uri);

        setResult(RESULT_OK, data);
        finish();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.button_done) {
            List<Uri> uris = mGalleryFragment.getSelectedImages();

            if (uris.size() == 0) {
                return;
            }

            boolean multiSelect = mGalleryFragment.isSelectionModeMultiSelect();

            Intent data = new Intent();

            if (multiSelect) {
                data.putExtra(EXTRA_OUTPUT, uris.toArray(new Uri[0]));
            }
            else {
                data.putExtra(EXTRA_OUTPUT, uris.get(0));
            }

            setResult(RESULT_OK, data);
        }
        else if (v.getId() == R.id.button_close) {
            setResult(RESULT_CANCELED);
        }

        finish();
    }

    private class BucketAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return mGalleryAccess == null ? 0 : (mGalleryAccess.getBucketCount() + 1);
        }

        @Override
        public Object getItem(int position) {
            return position == 0 ? getString(R.string.twitterimagepicker_gallery) : mGalleryAccess.getBucketAtPosition(position - 1);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView view = null;

            if (convertView != null) {
                view = (TextView) convertView;
            }
            else {
                view = (TextView) getLayoutInflater().inflate(R.layout.item_twitterimagepicker, parent, false);
            }

            view.setText((String) getItem(position));

            return view;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            TextView view = null;

            if (convertView != null) {
                view = (TextView) convertView;
            }
            else {
                view = (TextView) getLayoutInflater().inflate(R.layout.dropdownitem_twitterimagepicker, parent, false);
            }

            view.setText((String) getItem(position));

            return view;
        }

    }

}
