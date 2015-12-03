package com.android.launcher3;

import java.util.ArrayList;

import com.asus.launcher.R;
import com.asus.launcher.compat.DisplayCompat;
import com.asus.launcher.settings.fonts.FontManager;
import com.asus.launcher.settings.fonts.FontUtils;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Typeface;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class ActivityFolder extends LinearLayout implements View.OnClickListener {

    private static final String TAG = "[ActivityFolder]";
    private static final boolean DEBUG = Utilities.DEBUG;
    private static final int MIN_CONTENT_DIMEN = 5;
    private CellLayout mContent;
    private FullScreenFolderScrollView mScrollView;
    private TextView mFolderName;
    private final LayoutInflater mInflater;
    private final IconCache mIconCache;
    protected Launcher mLauncher;
    private int mMaxCountX;
    private int mMaxCountY;
    boolean mItemsInvalidated = false;
    private ArrayList<View> mItemsInReadingOrder = new ArrayList<View>();
    private int mFolderNameHeight;
    // +++ Lambert
    boolean mHasNavigationBar = false;
    private RelativeLayout mFolderNameWrap;
    private int mFolderLeftRightPadding;
    // ---

    private int mDeviceWidth = 0;
    private int mDeviceHeight = 0;
    private int mNavigationBarHeight = 0;

    // +++ even folder cell height and width
    private int mMaxContentHeight;
    private int mMaxContentWidth;
    private int mMaxContentRow;
    private int mCellHeight;
    private int mCellWidth;
    // ---

    public ActivityFolder(Context context, AttributeSet attrs) {
        super(context, attrs);

        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
        setAlwaysDrawnWithCacheEnabled(false);

        mInflater = LayoutInflater.from(context);
        mIconCache = app.getIconCache();
        mLauncher = (Launcher) context;

        DisplayMetrics dm = new DisplayMetrics();
        mLauncher.getWindowManager().getDefaultDisplay().getMetrics(dm);

        mHasNavigationBar = Utilities.hasNavigationBar();
        if (mHasNavigationBar) {
            mNavigationBarHeight = (int) getResources().getDimension(
                    Resources.getSystem().getIdentifier("navigation_bar_height", "dimen",
                            "android"));
        }

        mDeviceWidth = dm.widthPixels;
        mDeviceHeight = dm.heightPixels + mNavigationBarHeight;

        Resources res = getResources();
        mMaxCountX = res.getInteger(R.integer.folder_max_column);
        mMaxCountY = (int) Integer.MAX_VALUE;
    }

    public static ActivityFolder fromXml(Context context) {
        return (ActivityFolder) LayoutInflater.from(context).inflate(R.layout.activity_folder, null);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();

        mScrollView = (FullScreenFolderScrollView) findViewById(R.id.scroll_view);
        mContent = (CellLayout) findViewById(R.id.folder_content);
        mFolderName = (TextView) findViewById(R.id.folder_name);

        int measureSpec = MeasureSpec.UNSPECIFIED;
        int statusBarHeight = Utilities.getStatusBarHeight(getContext());
        mFolderLeftRightPadding = getResources().getDimensionPixelSize(R.dimen.folder_left_right_padding);

        // +++ folder name wrap
        int folderNameWrapPaddingTop = getResources().getDimensionPixelSize(R.dimen.folder_name_wrap_padding_top) + statusBarHeight;
        int folderNameWrapPaddingBottom = getResources().getDimensionPixelSize(R.dimen.folder_name_wrap_padding_bottom);
        mFolderNameWrap = (RelativeLayout) findViewById(R.id.folder_name_wrap);
        mFolderNameWrap.setPadding(mFolderLeftRightPadding, folderNameWrapPaddingTop, mFolderLeftRightPadding, folderNameWrapPaddingBottom);
        mFolderNameWrap.measure(measureSpec, measureSpec);
        mFolderNameHeight = mFolderNameWrap.getMeasuredHeight();
        // ---

        // +++ even folder cell height
        mMaxContentHeight = getFolderHeight() - mFolderNameHeight - mNavigationBarHeight;
        mMaxContentWidth = getContentAreaWidth() - mFolderLeftRightPadding * 2;
        mMaxContentRow = getResources().getInteger(R.integer.folder_max_row);
        mCellHeight = (int) (mMaxContentHeight / mMaxContentRow);
        mCellWidth = (int) (mMaxContentWidth / mMaxCountX);
        mContent.setCellDimensions(mCellWidth, mCellHeight);
        // ---

        Bitmap wallpaper = BlurBuilder.getBlurWallpaper();
        if (wallpaper == null) { // live wallpaper
            mLauncher.hideAllBackgroundItems();
            setBackground(null);
            setBackgroundColor(Color.BLACK);
            if (getBackground() != null) {
                getBackground().setAlpha((int)(255 * mLauncher.getAllAppsTransparency() / 100));
            } else {
                Log.d(TAG, "folder get background is null when opening folder");
            }
        } else {
            int currentPage = mLauncher.getWorkspace().getCurrentPage();
            int nextPage = mLauncher.getWorkspace().getNextPage();
            int blurPage = currentPage;
            if (currentPage != nextPage) { // workspace is scrolling
                blurPage = nextPage;
            }
            wallpaper = BlurBuilder.cutBitmap(getContext(), wallpaper, mLauncher.getWallpaperOffsetX(), blurPage, mLauncher.getWorkspace().getChildCount());
            wallpaper = BlurBuilder.makeTransparent(mLauncher.getAllAppsTransparency(), wallpaper);
            setBackground(new BitmapDrawable(getResources(), wallpaper));
        }

        mFolderName.setTextColor(FontUtils.getFontColor());
        final boolean hasCustomFont = FontManager.hasCustomTypefaceSetting(getContext());
        Typeface tf = hasCustomFont ? FontManager.getCustomTypeface(getContext()) : Utilities.getDefaultTypeface(getContext());
        mFolderName.setTypeface(tf);

        mFolderName.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // ignore clicking on folder title
                return;
            }
        });

        mContent.getShortcutsAndWidgets().setMotionEventSplittingEnabled(false);
        mContent.setInvertIfRtl(true);
        mContent.setIsFullscreenFolder(true);
    }

    private int[] getFixedCellDimensions() {
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
        int cellDimen[] = {0, 0};
        // Adds left/right padding, so width of cellWidth should be reduced.
        cellDimen[0] = (int)((mDeviceWidth - 2 * mFolderLeftRightPadding) / mMaxCountX);
        cellDimen[1] = grid.folderCellHeightPx;
        return cellDimen;
    }

    public int getIconSize() {
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
        return grid.iconSizePx;
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int contentAreaWidthSpec = MeasureSpec.makeMeasureSpec(getContentAreaWidth(),
                MeasureSpec.EXACTLY);
        int contentAreaHeightSpec = MeasureSpec.makeMeasureSpec(getContentAreaHeight(),
                MeasureSpec.EXACTLY);

        // Don't cap the height of the content to allow scrolling.
        // +++ Adds left/right padding, so width of cellWidth should be reduced.
        int contentWidth = getContentAreaWidth() - mFolderLeftRightPadding * 2;
        // ---
        mContent.setFixedSize(contentWidth, getContentAreaHeight());
        mFolderNameWrap.measure(contentAreaWidthSpec,
                MeasureSpec.makeMeasureSpec(mFolderNameHeight, MeasureSpec.EXACTLY));
        int scrollViewHeight = mDeviceHeight - mNavigationBarHeight - mFolderNameHeight;
        mScrollView.measure(contentAreaWidthSpec,
                MeasureSpec.makeMeasureSpec(scrollViewHeight, MeasureSpec.EXACTLY));
        setMeasuredDimension(mDeviceWidth, mDeviceHeight);
    }

    private int getFolderHeight() {
        int height = getScreenHeight();
        return height;
    }

    private int getScreenHeight(){
        Point smallestSize = new Point();
        Point largestSize = new Point();
        Point realSize = new Point();

        WindowManager windowManager = (WindowManager) this.getContext().getSystemService((Context.WINDOW_SERVICE));
        Display display = windowManager.getDefaultDisplay();
        display.getCurrentSizeRange(smallestSize, largestSize);
        DisplayCompat.getRealSize(display,realSize);
        return realSize.y;
    }

    private int getContentAreaHeight() {
        return Math.max(mContent.getDesiredHeight(), MIN_CONTENT_DIMEN);
    }

    private int getContentAreaWidth() {
        return Math.max(mContent.getDesiredWidth(), MIN_CONTENT_DIMEN);
    }

    protected boolean findAndSetEmptyCells(ShortcutInfo item) {
        int[] emptyCell = new int[2];
        if (mContent.findCellForSpan(emptyCell, item.spanX, item.spanY)) {
            item.cellX = emptyCell[0];
            item.cellY = emptyCell[1];
            return true;
        } else {
            return false;
        }
    }

    protected boolean createAndAddShortcut(ShortcutInfo item) {
        final BubbleTextView textView =
                (BubbleTextView) mInflater.inflate(R.layout.application, this, false);
        textView.setCompoundDrawables(null,
                Utilities.createIconDrawable(item.getIcon(mIconCache)), null, null);
        textView.setText(item.title);
        textView.setTag(item);
        textView.setTextColor(FontUtils.getFontColor());
        textView.setShadowsEnabled(true);

        textView.setOnClickListener(this);

        // We need to check here to verify that the given item's location isn't already occupied
        // by another item.
        if (mContent.getChildAt(item.cellX, item.cellY) != null || item.cellX < 0 || item.cellY < 0
                || item.cellX >= mContent.getCountX() || item.cellY >= mContent.getCountY()) {
            // This shouldn't happen, log it.
            Log.e(TAG, "Folder order not properly persisted during bind");
            if (!findAndSetEmptyCells(item)) {
                return false;
            }
        }

        CellLayout.LayoutParams lp =
                new CellLayout.LayoutParams(item.cellX, item.cellY, item.spanX, item.spanY);
        boolean insert = false;
        mContent.addViewToCellLayout(textView, insert ? 0 : -1, (int)item.id, lp, true);
        return true;
    }


    public boolean setContent(ArrayList<ShortcutInfo> apps) {
        // clear views
        mContent.removeAllViews();
        setupContentForNumItems(0);

        // add BubbleTextViews 1-by-1
        for (ShortcutInfo item : apps) {
            if (!findAndSetEmptyCells(item)) {
                // The current layout is full, can we expand it?
                setupContentForNumItems(getItemCount() + 1);
                findAndSetEmptyCells(item);
            }
            createAndAddShortcut(item);
        }
        return true;
    }

    @Override
    public void onClick(View v) {
        Object tag = v.getTag();
        if (tag instanceof ShortcutInfo) {
            // Open shortcut
            final ShortcutInfo shortcut = (ShortcutInfo) tag;
            final Intent intent = new Intent(shortcut.intent);

            // Start activities
            int[] pos = new int[2];
            v.getLocationOnScreen(pos);
            intent.setSourceBounds(new Rect(pos[0], pos[1],
                    pos[0] + v.getWidth(), pos[1] + v.getHeight()));

            boolean success = mLauncher.startActivitySafely(v, intent, tag);

        }
        v.clearFocus();
        mContent.clearFocus();

        // Clear the pressed state if necessary
        if (v instanceof BubbleTextView) {
            BubbleTextView icon = (BubbleTextView)v;
            icon.clearPressedOrFocusedBackground();
        }
    }

    public int getItemCount() {
        return mContent.getShortcutsAndWidgets().getChildCount();
    }

    private void setupContentForNumItems(int count) {
        setupContentDimensions(count);
    }

    private void setupContentDimensions(int count) {
        ArrayList<View> list = getItemsInReadingOrder();

        int countX = mContent.getCountX();
        int countY = mContent.getCountY();
        boolean done = false;

        while (!done) {
            int oldCountX = countX = mMaxCountX;
            int oldCountY = countY;
            if (countX * countY < count) {
                if (countY < mMaxCountY) {
                    countY++;
                }
                if (countY == 0){
                    countY++;
                }
            } else if ((countY - 1) * countX >= count && countY >= countX) {
                countY = Math.max(0, countY - 1);
            }
            done = countX == oldCountX && countY == oldCountY;
        }
        mContent.setGridSize(countX, countY);
        arrangeChildren(list);
    }

    public ArrayList<View> getItemsInReadingOrder() {
        if (mItemsInvalidated) {
            mItemsInReadingOrder.clear();
            for (int j = 0; j < mContent.getCountY(); j++) {
                for (int i = 0; i < mContent.getCountX(); i++) {
                    View v = mContent.getChildAt(i, j);
                    if (v != null) {
                        mItemsInReadingOrder.add(v);
                    }
                }
            }
            mItemsInvalidated = false;
        }
        return mItemsInReadingOrder;
    }

    private void arrangeChildren(ArrayList<View> list) {
        int[] vacant = new int[2];
        if (list == null) {
            list = getItemsInReadingOrder();
        }
        mContent.removeAllViews();

        for (int i = 0; i < list.size(); i++) {
            View v = list.get(i);
            mContent.getVacantCell(vacant, 1, 1);
            CellLayout.LayoutParams lp = (CellLayout.LayoutParams) v.getLayoutParams();
            lp.cellX = vacant[0];
            lp.cellY = vacant[1];
            ItemInfo info = (ItemInfo) v.getTag();
            if (info.cellX != vacant[0] || info.cellY != vacant[1]) {
                info.cellX = vacant[0];
                info.cellY = vacant[1];
            }
            boolean insert = false;
            mContent.addViewToCellLayout(v, insert ? 0 : -1, (int)info.id, lp, true);
        }

        mItemsInvalidated = true;
    }

    public void setFolderName(String folderName) {
        mFolderName.setText(folderName);
    }

    public int getContentMaxSize() {
        int maxContentRowPortrait = getResources().getInteger(R.integer.folder_max_row_portrait);
        return maxContentRowPortrait * mMaxCountX;
    }
}
