package com.yashoid.twitterimagepicker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GalleryFragment extends Fragment {

    private static final int CHECK_FADE_DURATION = 200;

    public static final int SELECTION_MODE_SINGLE_IMMEDIATE = 0;
    public static final int SELECTION_MODE_SINGLE_WAIT = 1;
    public static final int SELECTION_MODE_MULTIPLE_LONG_PRESS = 2;
    public static final int SELECTION_MODE_MULTIPLE = 3;

    public static final int DEFAULT_MULTI_SELECT_MAX_COUNT = 7;

    public static GalleryFragment newInstance() {
        return new GalleryFragment();
    }

    private GalleryAccess mGalleryAccess;

    private RecyclerView mListThumbnails;

    private ThumbnailAdapter mAdapter;

    private int mImageBucketIndex = -1;

    private int mSelectionMode = SELECTION_MODE_MULTIPLE_LONG_PRESS;
    private int mMultiSelectMaxCount = DEFAULT_MULTI_SELECT_MAX_COUNT;

    private List<Uri> mSelectedImages = new ArrayList<>(DEFAULT_MULTI_SELECT_MAX_COUNT);

    private Map<Uri, SquareImageViewHolder> mUriHolders = new HashMap<>(21);

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mGalleryAccess = GalleryAccess.getInstance(getContext());

        mAdapter = new ThumbnailAdapter();
        mAdapter.setImageBucketIndex(mImageBucketIndex);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return new RecyclerView(inflater.getContext());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.setBackgroundResource(R.color.twitterimagepicker_background);

        mListThumbnails = (RecyclerView) view;

        mListThumbnails.setAdapter(mAdapter);
        mListThumbnails.setLayoutManager(new GridLayoutManager(getContext(), 3) {

            @Override
            public SpanSizeLookup getSpanSizeLookup() {
                return new SpanSizeLookup() {

                    @Override
                    public int getSpanSize(int position) {
                        return 1;
                    }

                };
            }

        });
    }

    public void setImageBucketIndex(int index) {
        mImageBucketIndex = index;

        if (mAdapter != null) {
            mAdapter.setImageBucketIndex(index);
        }
    }

    public void setSelectionMode(int selectionMode) {
        mSelectionMode = selectionMode;
    }

    public boolean isSelectionModeMultiSelect() {
        return mSelectionMode == SELECTION_MODE_MULTIPLE || mSelectionMode == SELECTION_MODE_MULTIPLE_LONG_PRESS;
    }

    public void setMultiSelectMaxCount(int count) {
        if (count == 0) {
            count = Integer.MAX_VALUE;
        }

        mMultiSelectMaxCount = count;
    }

    public List<Uri> getSelectedImages() {
        return mSelectedImages;
    }

    private class ThumbnailAdapter extends RecyclerView.Adapter<SquareImageViewHolder>
            implements GalleryAccess.OnPreparedListener {

        private int mBucketIndex = -1;

        public ThumbnailAdapter() {
            if (!mGalleryAccess.isPrepared()) {
                mGalleryAccess.registerOnPreparedListener(this);
            }
        }

        @Override
        public void onGalleryAccessPrepared(GalleryAccess galleryAccess) {
            notifyDataSetChanged();
        }

        public void setImageBucketIndex(int index) {
            mBucketIndex = index;

            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public SquareImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return newSquareImageViewHolder(parent);
        }

        @Override
        public void onBindViewHolder(@NonNull final SquareImageViewHolder holder, final int position) {
            Uri imageUri;

            if (mBucketIndex == -1) {
                imageUri = mGalleryAccess.getImageUri(position);
            }
            else {
                imageUri = mGalleryAccess.getImageUri(mBucketIndex, position);
            }

            holder.setUri(imageUri);
        }

        @Override
        public int getItemCount() {
            if (!mGalleryAccess.isPrepared()) {
                return 0;
            }

            return mBucketIndex == -1 ? mGalleryAccess.getImageCount() : mGalleryAccess.getImageCount(mBucketIndex);
        }

    }

    public SquareImageViewHolder newSquareImageViewHolder(ViewGroup parent) {
        SquareImageView view = new SquareImageView(parent.getContext());
        view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return new SquareImageViewHolder(view);
    }

    public class SquareImageViewHolder extends RecyclerView.ViewHolder implements
            View.OnClickListener, View.OnLongClickListener {

        private int mPadding;

        private Uri mUri;

        private ImageDrawable mImageDrawable;

        private SquareImageViewHolder(@NonNull View itemView) {
            super(itemView);

            mImageDrawable = new ImageDrawable(itemView.getContext());
            ((ImageView) itemView).setImageDrawable(mImageDrawable);

            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);

            mPadding = itemView.getResources().getDimensionPixelSize(R.dimen.twitterimagepicker_itemPadding);
        }

        public void setUri(Uri uri) {
            mUri = uri;

            mUriHolders.put(mUri, this);

            mImageDrawable.setImageDrawable(null);
            mImageDrawable.setChecked(mSelectedImages.contains(mUri));

            final int position = getAdapterPosition();

            mGalleryAccess.getThumbnail(mUri, new GalleryAccess.OnThumbnailReadyReceiver() {

                @Override
                public void onThumbnailReady(Uri imageUri, Drawable thumbnail) {
                    if (getAdapterPosition() != position) {
                        return;
                    }

                    mImageDrawable.setImageDrawable(thumbnail);
                }

            });

            if (position == 0) {
                itemView.setPadding(0, 0, mPadding, mPadding);
            }
            else if (position == 1) {
                itemView.setPadding(mPadding, 0, mPadding, mPadding);
            }
            else if (position == 2) {
                itemView.setPadding(mPadding, 0, 0, mPadding);
            }
            else if (position % 3 == 0) {
                itemView.setPadding(0, mPadding, mPadding, mPadding);
            }
            else if (position % 3 == 1) {
                itemView.setPadding(mPadding, mPadding, mPadding, mPadding);
            }
            else {
                itemView.setPadding(mPadding, mPadding, 0, mPadding);
            }
        }

        public void check() {
            mImageDrawable.check();
        }

        public void uncheck() {
            mImageDrawable.uncheck();;
        }

        @Override
        public void onClick(View v) {
            switch (mSelectionMode) {
                case SELECTION_MODE_SINGLE_IMMEDIATE:
                    mSelectedImages.clear();
                    mSelectedImages.add(mUri);

                    ((TwitterImagePickerActivity) getActivity()).onSingleImageSelected(mUri);
                    return;
                case SELECTION_MODE_SINGLE_WAIT:
                    if (!mSelectedImages.contains(mUri) || mSelectedImages.size() > 1) {
                        clearSelection();
                        mSelectedImages.add(mUri);
                        check();
                    }
                    return;
                case SELECTION_MODE_MULTIPLE_LONG_PRESS:
                    if (mSelectedImages.size() > 1) {
                        if (mSelectedImages.contains(mUri)) {
                            mSelectedImages.remove(mUri);
                            uncheck();
                        }
                        else if (mSelectedImages.size() < mMultiSelectMaxCount) {
                            mSelectedImages.add(mUri);
                            check();
                        }
                    }
                    else if (!mSelectedImages.contains(mUri)) {
                        clearSelection();
                        mSelectedImages.add(mUri);
                        check();
                    }
                    else {
                        mSelectedImages.clear();
                        uncheck();
                    }
                    return;
                case SELECTION_MODE_MULTIPLE:
                    if (mSelectedImages.contains(mUri)) {
                        mSelectedImages.remove(mUri);
                        uncheck();
                    }
                    else if (mSelectedImages.size() < mMultiSelectMaxCount) {
                        mSelectedImages.add(mUri);
                        check();
                    }
                    return;
            }
        }

        @Override
        public boolean onLongClick(View v) {
            if (mSelectionMode != SELECTION_MODE_MULTIPLE_LONG_PRESS) {
                return false;
            }

            if (mSelectedImages.contains(mUri)) {
                mSelectedImages.remove(mUri);
                uncheck();
            }
            else if (mSelectedImages.size() < mMultiSelectMaxCount) {
                mSelectedImages.add(mUri);
                check();
            }

            return true;
        }

    }

    private void clearSelection() {
        for (Uri uri: mSelectedImages) {
            SquareImageViewHolder holder = mUriHolders.get(uri);

            if (holder != null && (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT || holder.itemView.isAttachedToWindow())) {
                holder.uncheck();
            }
        }

        mSelectedImages.clear();
    }

    public static class SquareImageView extends AppCompatImageView {

        public SquareImageView(Context context) {
            super(context);

            setScaleType(ScaleType.CENTER_CROP);
            setCropToPadding(true);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, widthMeasureSpec);
        }

    }

    public static class ImageDrawable extends Drawable implements Drawable.Callback {

        private Context mContext;

        private Drawable mImageDrawable = null;
        private Drawable mCheckDrawable = null;
        private TransitionDrawable mCheckTransitionDrawable = null;

        private boolean mChecking = false;

        private Rect mHelperBounds = new Rect();

        public ImageDrawable(Context context) {
            mContext = context;
        }

        public void setImageDrawable(Drawable drawable) {
            if (mImageDrawable != null) {
                mImageDrawable.setCallback(null);
            }

            mImageDrawable = drawable;

            if (mImageDrawable != null) {
                mImageDrawable.setCallback(this);

                setImageBounds();
            }

            invalidateSelf();
        }

        public void setChecked(boolean checked) {
            if (mCheckTransitionDrawable != null) {
                mCheckTransitionDrawable.setCallback(null);
                mCheckTransitionDrawable = null;
            }

            if (checked) {
                if (mCheckDrawable == null) {
                    mCheckDrawable = createCheckDrawable();
                    mCheckDrawable.setCallback(this);
                    mCheckDrawable.setBounds(getBounds());
                }
            }
            else {
                if (mCheckDrawable != null){
                    mCheckDrawable.setCallback(null);
                    mCheckDrawable = null;
                }
            }

            invalidateSelf();
        }

        public void check() {
            if (mCheckDrawable != null) {
                mCheckDrawable.setCallback(null);
                mCheckDrawable = null;
            }

            if (mCheckTransitionDrawable != null) {
                if (!mChecking) {
                    mCheckTransitionDrawable.reverseTransition(CHECK_FADE_DURATION);
                }
            }
            else {
                mCheckTransitionDrawable = createCheckDrawableTransition();
                mCheckTransitionDrawable.setCallback(this);
                mCheckTransitionDrawable.setBounds(getBounds());
                mCheckTransitionDrawable.startTransition(CHECK_FADE_DURATION);
            }

            mChecking = true;

            invalidateSelf();
        }

        public void uncheck() {
            if (mCheckDrawable != null) {
                mCheckDrawable.setCallback(null);
                mCheckDrawable = null;
            }

            if (mCheckTransitionDrawable != null) {
                if (mChecking) {
                    mCheckTransitionDrawable.reverseTransition(CHECK_FADE_DURATION);
                }
            }
            else {
                mCheckTransitionDrawable = createCheckDrawableTransition();
                mCheckTransitionDrawable.setCallback(this);
                mCheckTransitionDrawable.setBounds(getBounds());
                mCheckTransitionDrawable.startTransition(CHECK_FADE_DURATION);
                mCheckTransitionDrawable.reverseTransition(CHECK_FADE_DURATION);
            }

            mChecking = false;

            invalidateSelf();
        }

        private Drawable createCheckDrawable() {
            return AppCompatResources.getDrawable(mContext, R.drawable.twitterimagepicker_checkforegroud).mutate();
        }

        private TransitionDrawable createCheckDrawableTransition() {
            Drawable check = createCheckDrawable();

            return new TransitionDrawable(new Drawable[] { new ColorDrawable(0), check} );
        }

        @Override
        public int getIntrinsicWidth() {
            if (mCheckTransitionDrawable != null) {
                return mCheckTransitionDrawable.getIntrinsicWidth();
            }

            if (mCheckDrawable != null) {
                return mCheckDrawable.getIntrinsicWidth();
            }

            return 400;
        }

        @Override
        public int getIntrinsicHeight() {
            if (mCheckTransitionDrawable != null) {
                return mCheckTransitionDrawable.getIntrinsicHeight();
            }

            if (mCheckDrawable != null) {
                return mCheckDrawable.getIntrinsicHeight();
            }

            return 400;
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);

            if (mImageDrawable != null) {
                setImageBounds();
            }

            if (mCheckDrawable != null) {
                mCheckDrawable.setBounds(bounds);
            }

            if (mCheckTransitionDrawable != null) {
                mCheckTransitionDrawable.setBounds(bounds);
            }
        }

        private void setImageBounds() {
            int size = getBounds().width();

            int imageWidth = mImageDrawable.getIntrinsicWidth();
            int imageHeight = mImageDrawable.getIntrinsicHeight();

            if (imageWidth > imageHeight) {
                imageWidth = imageWidth * size / imageHeight;
                imageHeight = size;
            }
            else {
                imageHeight = imageHeight * size / imageWidth;
                imageWidth = size;
            }

            mHelperBounds.set(0, 0, imageWidth, imageHeight);
            mHelperBounds.offset((size - imageWidth) / 2, (size - imageHeight) / 2);

            mImageDrawable.setBounds(mHelperBounds);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (mImageDrawable != null) {
                mImageDrawable.draw(canvas);
            }

            if (mCheckDrawable != null) {
                mCheckDrawable.draw(canvas);
            }

            if (mCheckTransitionDrawable != null) {
                mCheckTransitionDrawable.draw(canvas);
            }
        }

        @Override
        public void setAlpha(int alpha) { }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) { }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }

        @Override
        public void invalidateDrawable(@NonNull Drawable who) {
            invalidateSelf();
        }

        @Override
        public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
            scheduleSelf(what, when);
        }

        @Override
        public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
            unscheduleSelf(what);
        }

    }

}
