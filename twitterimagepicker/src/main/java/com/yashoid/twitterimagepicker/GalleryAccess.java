package com.yashoid.twitterimagepicker;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.util.SparseArray;

import com.yashoid.office.office.Office;
import com.yashoid.office.task.DefaultTaskManager;
import com.yashoid.office.task.TaskManager;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GalleryAccess {

    private static final String TAG = "GalleryAccess";

    private static final String IMAGE_ID = MediaStore.Images.Media._ID;
    private static final String IMAGE_DISPLAY_NAME = MediaStore.Images.Media.DISPLAY_NAME;
    private static final String BUCKET_DISPLAY_NAME = "bucket_display_name";

    private static final String[] SELECTION = { IMAGE_ID, IMAGE_DISPLAY_NAME, BUCKET_DISPLAY_NAME };
    private static final String SORT = MediaStore.Images.Media.DATE_MODIFIED + " DESC";

    public interface OnPreparedListener {

        void onGalleryAccessPrepared(GalleryAccess galleryAccess);

    }

    public interface OnThumbnailReadyReceiver {

        void onThumbnailReady(Uri imageUri, Drawable thumbnail);

    }

    private static GalleryAccess mInstance = null;

    public static GalleryAccess getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new GalleryAccess(context.getApplicationContext());
        }

        return mInstance;
    }

    private Context mContext;

    private int mThumbnailSize;

    private TaskManager mTaskManager = null;

    private boolean mInitialized = false;
    private boolean mPrepared = false;
    private boolean mPreparing = false;

    private List<OnPreparedListener> mOnPreparedListeners = new ArrayList<>();

    private List<String> mBucketNames = null;
    private long[] mImageIds;
    private SparseArray<List<Integer>> mBucketImages = null;

    private BitmapCache mBitmapCache = new BitmapCache(2 * 1024 * 1024);
    private HashMap<Uri, GetThumbnailTask> mThumbnailTasks = new HashMap<>(50);

    private GalleryAccess(Context context) {
        mContext = context;

        mThumbnailSize = context.getResources().getDisplayMetrics().widthPixels / 3;
    }

    public void setTaskManager(TaskManager taskManager) {
        if (mInitialized) {
            return;
        }

        mTaskManager = taskManager;
    }

    private void initialize() {
        if (mInitialized) {
            return;
        }

        if (mTaskManager == null) {
            mTaskManager = DefaultTaskManager.getInstance();
        }

        boolean hasCalculationSection = false;

        for (Office.SectionDescription sectionDescription: mTaskManager.getSectionDescriptions()) {
            if (TaskManager.CALCULATION.equals(sectionDescription.name)) {
                hasCalculationSection = true;
                break;
            }
        }

        if (!hasCalculationSection) {
            mTaskManager.addSection(TaskManager.CALCULATION, DefaultTaskManager.DEFAULT_CALCULATION_WORKERS);
        }

        mInitialized = true;
    }

    synchronized public void prepare() {
        if (mPrepared || mPreparing) {
            return;
        }

        mPreparing = true;

        initialize();

        mTaskManager.runTask(TaskManager.CALCULATION, mPrepareTask, 0);
    }

    synchronized public boolean isPrepared() {
        return mPrepared;
    }

    synchronized public void registerOnPreparedListener(final OnPreparedListener onPreparedListener) {
        if (mPrepared) {
            mTaskManager.runTask(TaskManager.MAIN, new Runnable() {

                @Override
                public void run() {
                    onPreparedListener.onGalleryAccessPrepared(GalleryAccess.this);
                }

            }, 0);
            return;
        }

        mOnPreparedListeners.remove(onPreparedListener);
        mOnPreparedListeners.add(onPreparedListener);
    }

    synchronized public void unregisterOnPreparedListener(OnPreparedListener onPreparedListener) {
        mOnPreparedListeners.remove(onPreparedListener);
    }

    public int getBucketCount() {
        return mBucketNames == null ? 0 : mBucketNames.size();
    }

    public String getBucketAtPosition(int position) {
        return mBucketNames.get(position);
    }

    public int getImageCount() {
        return mImageIds.length;
    }

    public int getImageCount(int bucketIndex) {
        return mBucketImages.get(bucketIndex).size();
    }

    public Uri getImageUri(int position) {
        return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mImageIds[position]);
    }

    public Uri getImageUri(int bucketIndex, int position) {
        return getImageUri(mBucketImages.get(bucketIndex).get(position));
    }

    synchronized public void getThumbnail(final Uri imageUri, final OnThumbnailReadyReceiver receiver) {
        final Drawable drawable = mBitmapCache.get(imageUri);

        if (drawable != null) {
            mTaskManager.runTask(TaskManager.MAIN, new Runnable() {

                @Override
                public void run() {
                    receiver.onThumbnailReady(imageUri, drawable);
                }

            }, 0);
            return;
        }

        GetThumbnailTask task = mThumbnailTasks.get(imageUri);

        boolean newTask = false;

        if (task == null) {
            task = new GetThumbnailTask(imageUri);

            mThumbnailTasks.put(imageUri, task);

            newTask = true;
        }

        task.addReceiver(receiver);

        if (newTask) {
            mTaskManager.runTask(TaskManager.CALCULATION, task, 0);
        }
    }

    private Runnable mPrepareTask = new Runnable() {

        @Override
        public void run() {
            ContentResolver contentResolver = mContext.getContentResolver();

            Cursor cursor = contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    SELECTION, null, null, SORT);

            if (cursor == null) {
                throw new RuntimeException("Image media content expected to exist but didn't.");
            }

            final int idColumn = cursor.getColumnIndex(IMAGE_ID);
            final int nameColumn = cursor.getColumnIndex(IMAGE_DISPLAY_NAME);
            final int bucketColumn = cursor.getColumnIndex(BUCKET_DISPLAY_NAME);

            final int count = cursor.getCount();

            mImageIds = new long[count];

            if (bucketColumn >= 0) {
                mBucketNames = new ArrayList<>(20);
                mBucketImages = new SparseArray<>(20);
            }

            int index = 0;

            if (cursor.moveToFirst()) {
                do {
                    long imageId = cursor.getLong(idColumn);

                    mImageIds[index] = imageId;

                    if (bucketColumn >= 0) {
                        String bucketName = cursor.getString(bucketColumn);

                        int bucketIndex = mBucketNames.indexOf(bucketName);

                        if (bucketIndex == -1) {
                            bucketIndex = mBucketNames.size();

                            mBucketNames.add(bucketName);
                        }

                        List<Integer> bucketImages = mBucketImages.get(bucketIndex);

                        if (bucketImages == null) {
                            bucketImages = new ArrayList<>(count / 20);

                            mBucketImages.put(bucketIndex, bucketImages);
                        }

                        bucketImages.add(index);
                    }

                    index++;
                } while (cursor.moveToNext());
            }

            cursor.close();

            synchronized (this) {
                mPreparing = false;
                mPrepared = true;

                notifyPrepared();
            }
        }

    };

    private void notifyPrepared() {
        mTaskManager.runTask(TaskManager.MAIN, mNotifyPreparedTask, 0);
    }

    private Runnable mNotifyPreparedTask = new Runnable() {

        @Override
        public void run() {
            List<OnPreparedListener> listeners;

            synchronized (GalleryAccess.this) {
                listeners = new ArrayList<>(mOnPreparedListeners);
            }

            for (OnPreparedListener listener: listeners) {
                listener.onGalleryAccessPrepared(GalleryAccess.this);
            }
        }

    };

    private class BitmapCache {

        private long mAvailable;

        private HashMap<Uri, WeakReference<Drawable>> mDrawables = new HashMap<>(50);
        private HashMap<Uri, Bitmap> mBitmaps = new HashMap<>(50);

        public BitmapCache(long maxAllocation) {
            mAvailable = maxAllocation;
        }

        synchronized public Drawable get(Uri uri) {
            WeakReference<Drawable> reference = mDrawables.get(uri);

            if (reference != null) {
                Drawable drawable = reference.get();

                if (drawable == null) {
                    Bitmap bitmap = mBitmaps.get(uri);

                    drawable = new BitmapDrawable(mContext.getResources(), bitmap);

                    mDrawables.put(uri, new WeakReference<>(drawable));
                }

                return drawable;
            }

            return null;
        }

        synchronized public Drawable put(Uri uri, Bitmap bitmap) {
            Bitmap cachedBitmap = mBitmaps.get(uri);

            if (cachedBitmap != null) {
                int size = cachedBitmap.getWidth() * cachedBitmap.getHeight() * 4;

                cachedBitmap.recycle();

                mAvailable += size;
            }

            Drawable drawable = new BitmapDrawable(mContext.getResources(), bitmap);

            WeakReference<Drawable> reference = new WeakReference<>(drawable);

            mDrawables.put(uri, reference);
            mBitmaps.put(uri, bitmap);

            int size = bitmap.getWidth() * bitmap.getHeight() * 4;
            mAvailable -= size;

            while (mAvailable < 0) {
                Uri freeUri = findFreeBitmap();

                if (freeUri == null) {
                    break;
                }

                mDrawables.remove(freeUri);

                Bitmap freeBitmap = mBitmaps.remove(freeUri);

                if (freeBitmap == null) {
                    continue;
                }

                size = freeBitmap.getWidth() * freeBitmap.getHeight() * 4;

                freeBitmap.recycle();

                mAvailable += size;
            }

            return drawable;
        }

        private Uri findFreeBitmap() {
            for (Uri uri: mDrawables.keySet()) {
                WeakReference<Drawable> reference = mDrawables.get(uri);

                if (reference == null) {
                    return uri;
                }
            }

            return null;
        }

    }

    private class GetThumbnailTask implements Runnable {

        private Uri mUri;

        private List<WeakReference<OnThumbnailReadyReceiver>> mReceivers = new ArrayList<>(2);

        private Drawable mResult = null;

        private GetThumbnailTask(Uri uri) {
            mUri = uri;
        }

        synchronized public void addReceiver(final OnThumbnailReadyReceiver receiver) {
            if (mResult != null) {
                mTaskManager.runTask(TaskManager.MAIN, new Runnable() {

                    @Override
                    public void run() {
                        receiver.onThumbnailReady(mUri, mResult);
                    }

                }, 0);
                return;
            }

            mReceivers.add(new WeakReference<>(receiver));
        }

        @Override
        public void run() {
            ContentResolver contentResolver = mContext.getContentResolver();

            Bitmap bitmap;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Size size = new Size(mThumbnailSize, mThumbnailSize);

                try {
                    bitmap = contentResolver.loadThumbnail(mUri, size, null);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to get bitmap for uri: " + mUri, e);

                    synchronized (GalleryAccess.this) {
                        mThumbnailTasks.remove(mUri);
                    }

                    return;
                }
            }
            else {
                long imageId = ContentUris.parseId(mUri);

                bitmap = MediaStore.Images.Thumbnails.getThumbnail(contentResolver, imageId,
                        MediaStore.Images.Thumbnails.MINI_KIND, null);

                if (bitmap == null) {
                    Log.e(TAG, "Failed to get bitmap for uri: " + mUri);

                    synchronized (GalleryAccess.this) {
                        mThumbnailTasks.remove(mUri);
                    }

                    return;
                }
            }

            mResult = mBitmapCache.put(mUri, bitmap);

            mTaskManager.runTask(TaskManager.MAIN, mNotifyResultTask, 0);
        }

        private Runnable mNotifyResultTask = new Runnable() {

            @Override
            public void run() {
                synchronized (GalleryAccess.this) {
                    List<WeakReference<OnThumbnailReadyReceiver>> receivers = new ArrayList<>(mReceivers);

                    mReceivers.clear();

                    for (WeakReference<OnThumbnailReadyReceiver> reference: receivers) {
                        OnThumbnailReadyReceiver receiver = reference.get();

                        if (receiver != null) {
                            receiver.onThumbnailReady(mUri, mResult);
                        }
                    }

                    mThumbnailTasks.remove(mUri);
                }
            }

        };

    }

}
