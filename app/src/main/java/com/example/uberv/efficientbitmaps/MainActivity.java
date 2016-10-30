package com.example.uberv.efficientbitmaps;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.support.v4.util.LruCache;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    public static final String TAG=MainActivity.class.getSimpleName();

    private Button loadImageBtn;
    private ImageView imageIv;
    private TextView imageInfoTv;
    private TextView progressTv;
    private SeekBar sampleSizeSb;

    private LruCache<String, Bitmap> mMemoryCache;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageIv= (ImageView) findViewById(R.id.image_iv);
        imageInfoTv= (TextView) findViewById(R.id.image_info_tv);
        progressTv=(TextView)findViewById(R.id.progress_tv);
        sampleSizeSb= (SeekBar) findViewById(R.id.sample_size_seekbar);
        loadImageBtn= (Button) findViewById(R.id.load_image_btn);
        loadImageBtn.setOnClickListener(this);
        sampleSizeSb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progressTv.setText(Math.pow(2,progress)+"");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        // Prepare LruCache
        // Get max available VM memory, exceeding this amount will throw an
        // OutOfMemory exception. Stored in kilobytes as LruCache takes an
        // int in its constructor.
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        // Use 1/8th of the available memory for this memory cache.
        final int cacheSize = maxMemory / 8;
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                // The cache size will be measured in kilobytes rather than
                // number of items.
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    public void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (getBitmapFromMemCache(key) == null) {
            mMemoryCache.put(key, bitmap);
        }
    }

    public Bitmap getBitmapFromMemCache(String key) {
        return mMemoryCache.get(key);
    }

    public static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // For example, an image with resolution 2048x1536 that is decoded with an inSampleSize of 4 produces a bitmap of approximately 512x384
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    @Override
    public void onClick(View v) {
        StringBuilder infoStringBuilder = new StringBuilder();
        BitmapFactory.Options options = new BitmapFactory.Options();
        // just calculate image properties (avoid memory allocation for image)
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(getResources(), R.drawable.yuri_gagarin, options);

        int imageHeight = options.outHeight;
        int imageWidth = options.outWidth;
        String imageType = options.outMimeType;
        infoStringBuilder.append("height: "+imageHeight+", width: "+imageWidth+", type: "+imageType);

        options.inJustDecodeBounds=false;
        options.inSampleSize= (int) Math.pow(2,sampleSizeSb.getProgress());
        infoStringBuilder.append("\nrecommended sample size: "+calculateInSampleSize(options,imageIv.getWidth(), imageIv.getHeight())+
                "\nused sample size: "+options.inSampleSize);

        long t1=System.nanoTime();
        // Using AsyncTask and LruCache
        loadBitmap(R.drawable.yuri_gagarin,imageIv);
        // Other implementation
//        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.yuri_gagarin, options);
//        imageIv.setImageBitmap(bitmap);
        // Default
//        imageIv.setImageResource(R.drawable.yuri_gagarin);
        long t2=System.nanoTime();
        infoStringBuilder.append("\ntime spent: "+(t2-t1)/1000000+"ms");
//        infoStringBuilder.append("\nsize: "+bitmap.getByteCount()/1024f/1024f+"mb");
        infoStringBuilder.append("\ncache size: "+mMemoryCache.size());

        imageInfoTv.setText(infoStringBuilder.toString());
    }

    public void loadBitmap(int resId, ImageView imageView) {
        final String imageKey = String.valueOf(resId);

        final Bitmap bitmap = getBitmapFromMemCache(imageKey);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        } else {
            imageView.setImageResource(R.drawable.yuri_gagarin);
            BitmapWorkerTask task = new BitmapWorkerTask(imageView);
            task.execute(resId);
        }
    }

    class BitmapWorkerTask extends AsyncTask<Integer, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        private int data = 0;
        private int width;
        private int height;

        public BitmapWorkerTask(ImageView imageView) {
            // Use a WeakReference to ensure the ImageView can be garbage collected
            width=imageView.getWidth();
            height=imageView.getHeight();
            imageViewReference = new WeakReference<ImageView>(imageView);
        }

        // Decode image in background.
        @Override
        protected Bitmap doInBackground(Integer... params) {
            final Bitmap bitmap = decodeSampledBitmapFromResource(
                    getResources(), params[0], width,height);
            addBitmapToMemoryCache(String.valueOf(params[0]), bitmap);
            Log.d(TAG,bitmap.getByteCount()+"");
            return bitmap;
        }

        // Once complete, see if ImageView is still around and set bitmap.
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (imageViewReference != null && bitmap != null) {
                final ImageView imageView = imageViewReference.get();
                if (imageView != null) {
                    imageView.setImageBitmap(bitmap);
                }
            }
        }
    }

    public static Bitmap decodeSampledBitmapFromResource(Resources res, int resId,
                                                         int reqWidth, int reqHeight) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        Log.d(TAG,"sample size: "+ options.inSampleSize);
        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(res, resId, options);
    }

}
