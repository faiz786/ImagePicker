package com.example.imagepicker.internal.ui;


import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.viewpager.widget.ViewPager;

import com.example.imagepicker.R;
import com.example.imagepicker.internal.entity.Album;
import com.example.imagepicker.internal.entity.IncapableCause;
import com.example.imagepicker.internal.entity.Item;
import com.example.imagepicker.internal.entity.SelectionSpec;
import com.example.imagepicker.internal.model.AlbumMediaCollection;
import com.example.imagepicker.internal.model.SelectedItemCollection;
import com.example.imagepicker.internal.ui.adapter.PreviewPagerAdapter;
import com.example.imagepicker.internal.ui.widget.CheckRadioView;
import com.example.imagepicker.internal.ui.widget.CheckView;
import com.example.imagepicker.internal.ui.widget.IncapableDialog;
import com.example.imagepicker.internal.utils.PhotoMetadataUtils;
import com.example.imagepicker.internal.utils.Platform;
import com.example.imagepicker.listener.OnFragmentInteractionListener;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 */
public class BasePreviewFragment extends Fragment implements View.OnClickListener,
        ViewPager.OnPageChangeListener, OnFragmentInteractionListener,
        AlbumMediaCollection.AlbumMediaCallbacks {

    public static final String EXTRA_DEFAULT_BUNDLE = "extra_default_bundle";
    public static final String EXTRA_RESULT_BUNDLE = "extra_result_bundle";
    public static final String EXTRA_RESULT_APPLY = "extra_result_apply";
    public static final String EXTRA_RESULT_ORIGINAL_ENABLE = "extra_result_original_enable";
    public static final String CHECK_STATE = "checkState";

    protected final SelectedItemCollection mSelectedCollection = new SelectedItemCollection(getContext());
    protected SelectionSpec mSpec;
    protected ViewPager mPager;

    protected PreviewPagerAdapter mAdapter;

    protected CheckView mCheckView;
    protected TextView mButtonBack;
    protected TextView mButtonApply;
    protected TextView mSize;

    protected int mPreviousPos = -1;

    private LinearLayout mOriginalLayout;
    private CheckRadioView mOriginal;
    protected boolean mOriginalEnable;

    private FrameLayout mBottomToolbar;
    private FrameLayout mTopToolbar;
    private boolean mIsToolbarHide = false;

    View view;
    Bundle bundle;
    List<Item> selected;
    Boolean originalAvailable;
    int view_type;
    private AlbumMediaCollection mCollection = new AlbumMediaCollection();
    public static final String EXTRA_ALBUM = "extra_album";
    public static final String EXTRA_ITEM = "extra_item";
    private boolean mIsAlreadySetPosition;

    public static BasePreviewFragment newInstance(Bundle bundle, Boolean mOriginalEnable, int viewType) {
        BasePreviewFragment fragment = new BasePreviewFragment();
        Bundle args = new Bundle();
        args.putBundle(BasePreviewFragment.EXTRA_DEFAULT_BUNDLE, bundle);
        args.putBoolean(BasePreviewFragment.EXTRA_RESULT_ORIGINAL_ENABLE, mOriginalEnable);
        args.putParcelable(BasePreviewFragment.EXTRA_ALBUM, bundle.getParcelable(EXTRA_ALBUM));
        args.putParcelable(BasePreviewFragment.EXTRA_ITEM, bundle.getParcelable(EXTRA_ITEM));
        args.putInt("view_type", viewType);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            bundle = getArguments().getBundle(BasePreviewFragment.EXTRA_DEFAULT_BUNDLE);
            bundle = bundle.getBundle(BasePreviewFragment.EXTRA_DEFAULT_BUNDLE);
            originalAvailable = getArguments().getBoolean(BasePreviewActivity.EXTRA_RESULT_ORIGINAL_ENABLE);
            view_type = getArguments().getInt("view_type");
        }

    }


    public BasePreviewFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getActivity().setTheme(SelectionSpec.getInstance().themeId);
        super.onCreate(savedInstanceState);
//        if (!SelectionSpec.getInstance().hasInited) {
//            getActivity().setResult(Activity.RESULT_CANCELED);
//            getActivity().finish();
//            return null;
//        }
        // Inflate the layout for this fragment
//        View view = provideYourFragmentView(inflater,container,savedInstanceState);
        view = inflater.inflate(R.layout.fragment_base_preview, container, false);

        if (Platform.hasKitKat()) {
            getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }

        mSpec = SelectionSpec.getInstance();
        if (mSpec.needOrientationRestriction()) {
            getActivity().setRequestedOrientation(mSpec.orientation);
        }

        if (savedInstanceState == null) {
            mSelectedCollection.onCreate(bundle);
            mOriginalEnable = originalAvailable;
        } else {
            mSelectedCollection.onCreate(savedInstanceState);
            mOriginalEnable = savedInstanceState.getBoolean(CHECK_STATE);
        }
        mButtonBack = (TextView) view.findViewById(R.id.button_back);
        mButtonApply = (TextView) view.findViewById(R.id.button_apply);
        mSize = (TextView) view.findViewById(R.id.size);
        mButtonBack.setOnClickListener(this);
        mButtonApply.setOnClickListener(this);

        mPager = (ViewPager) view.findViewById(R.id.pager);
        mPager.addOnPageChangeListener(this);
        mAdapter = new PreviewPagerAdapter(getActivity().getSupportFragmentManager(), null);
        mPager.setAdapter(mAdapter);
        mCheckView = (CheckView) view.findViewById(R.id.check_view);
        mCheckView.setCountable(mSpec.countable);
        mBottomToolbar = view.findViewById(R.id.bottom_toolbar);
        mTopToolbar = view.findViewById(R.id.top_toolbar);

        mCheckView.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Item item = mAdapter.getMediaItem(mPager.getCurrentItem());
                if (mSelectedCollection.isSelected(item)) {
                    mSelectedCollection.remove(item);
                    if (mSpec.countable) {
                        mCheckView.setCheckedNum(CheckView.UNCHECKED);
                    } else {
                        mCheckView.setChecked(false);
                    }
                } else {
                    if (assertAddSelection(item)) {
                        mSelectedCollection.add(item);
                        if (mSpec.countable) {
                            mCheckView.setCheckedNum(mSelectedCollection.checkedNumOf(item));
                        } else {
                            mCheckView.setChecked(true);
                        }
                    }
                }
                updateApplyButton();

                if (mSpec.onSelectedListener != null) {
                    mSpec.onSelectedListener.onSelected(
                            mSelectedCollection.asListOfUri(), mSelectedCollection.asListOfString());
                }
            }
        });


        mOriginalLayout = view.findViewById(R.id.originalLayout);
        mOriginal = view.findViewById(R.id.original);
        mOriginalLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                int count = countOverMaxSize();
                if (count > 0) {
                    IncapableDialog incapableDialog = IncapableDialog.newInstance("",
                            getString(R.string.error_over_original_count, count, mSpec.originalMaxSize));
                    incapableDialog.show(getActivity().getSupportFragmentManager(),
                            IncapableDialog.class.getName());
                    return;
                }

                mOriginalEnable = !mOriginalEnable;
                mOriginal.setChecked(mOriginalEnable);
                if (!mOriginalEnable) {
                    mOriginal.setColor(Color.WHITE);
                }


                if (mSpec.onCheckedListener != null) {
                    mSpec.onCheckedListener.onCheck(mOriginalEnable);
                }
            }
        });

        updateApplyButton();

        if (view_type == 1) {
            if (!SelectionSpec.getInstance().hasInited) {
                getActivity().setResult(Activity.RESULT_CANCELED);
                getActivity().finish();
                return null;
            }

            List<Item> selected = bundle.getParcelableArrayList(SelectedItemCollection.STATE_SELECTION);

            mAdapter.addAll(selected);
            mAdapter.notifyDataSetChanged();
            if (mSpec.countable) {
                mCheckView.setCheckedNum(1);
            } else {
                mCheckView.setChecked(true);
            }
            mPreviousPos = 0;
            updateSize(selected.get(0));
        } else if (view_type == 2) {
            if (!SelectionSpec.getInstance().hasInited) {
                getActivity().setResult(Activity.RESULT_CANCELED);
                getActivity().finish();
                return null;
            }
            mCollection.onCreate(getActivity(), this);
            Album album = getArguments().getParcelable(EXTRA_ALBUM);
            mCollection.load(album);

            Item item = getArguments().getParcelable(EXTRA_ITEM);
            if (mSpec.countable) {
                mCheckView.setCheckedNum(mSelectedCollection.checkedNumOf(item));
            } else {
                mCheckView.setChecked(mSelectedCollection.isSelected(item));
            }
            updateSize(item);
        }

        return view;
    }


    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.button_back) {
            getActivity().onBackPressed();
        } else if (v.getId() == R.id.button_apply) {
            sendBackResult(true);
            getActivity().finish();
        }
    }

    @Override
    public void onClick() {
        if (!mSpec.autoHideToobar) {
            return;
        }

        if (mIsToolbarHide) {
            mTopToolbar.animate()
                    .setInterpolator(new FastOutSlowInInterpolator())
                    .translationYBy(mTopToolbar.getMeasuredHeight())
                    .start();
            mBottomToolbar.animate()
                    .translationYBy(-mBottomToolbar.getMeasuredHeight())
                    .setInterpolator(new FastOutSlowInInterpolator())
                    .start();
        } else {
            mTopToolbar.animate()
                    .setInterpolator(new FastOutSlowInInterpolator())
                    .translationYBy(-mTopToolbar.getMeasuredHeight())
                    .start();
            mBottomToolbar.animate()
                    .setInterpolator(new FastOutSlowInInterpolator())
                    .translationYBy(mBottomToolbar.getMeasuredHeight())
                    .start();
        }

        mIsToolbarHide = !mIsToolbarHide;

    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

    }

    @Override
    public void onPageSelected(int position) {
        PreviewPagerAdapter adapter = (PreviewPagerAdapter) mPager.getAdapter();
        if (mPreviousPos != -1 && mPreviousPos != position) {
            ((PreviewItemFragment) adapter.instantiateItem(mPager, mPreviousPos)).resetView();

            Item item = adapter.getMediaItem(position);
            if (mSpec.countable) {
                int checkedNum = mSelectedCollection.checkedNumOf(item);
                mCheckView.setCheckedNum(checkedNum);
                if (checkedNum > 0) {
                    mCheckView.setEnabled(true);
                } else {
                    mCheckView.setEnabled(!mSelectedCollection.maxSelectableReached());
                }
            } else {
                boolean checked = mSelectedCollection.isSelected(item);
                mCheckView.setChecked(checked);
                if (checked) {
                    mCheckView.setEnabled(true);
                } else {
                    mCheckView.setEnabled(!mSelectedCollection.maxSelectableReached());
                }
            }
            updateSize(item);
        }
        mPreviousPos = position;
    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }

    private void updateApplyButton() {
        int selectedCount = mSelectedCollection.count();
        if (selectedCount == 0) {
            mButtonApply.setText(R.string.button_apply_default);
            mButtonApply.setEnabled(false);
        } else if (selectedCount == 1 && mSpec.singleSelectionModeEnabled()) {
            mButtonApply.setText(R.string.button_apply_default);
            mButtonApply.setEnabled(true);
        } else {
            mButtonApply.setEnabled(true);
            mButtonApply.setText(getString(R.string.button_apply, selectedCount));
        }

        if (mSpec.originalable) {
            mOriginalLayout.setVisibility(View.VISIBLE);
            updateOriginalState();
        } else {
            mOriginalLayout.setVisibility(View.GONE);
        }
    }


    private void updateOriginalState() {
        mOriginal.setChecked(mOriginalEnable);
        if (!mOriginalEnable) {
            mOriginal.setColor(Color.WHITE);
        }

        if (countOverMaxSize() > 0) {

            if (mOriginalEnable) {
                IncapableDialog incapableDialog = IncapableDialog.newInstance("",
                        getString(R.string.error_over_original_size, mSpec.originalMaxSize));
                incapableDialog.show(getActivity().getSupportFragmentManager(),
                        IncapableDialog.class.getName());

                mOriginal.setChecked(false);
                mOriginal.setColor(Color.WHITE);
                mOriginalEnable = false;
            }
        }
    }


    private int countOverMaxSize() {
        int count = 0;
        int selectedCount = mSelectedCollection.count();
        for (int i = 0; i < selectedCount; i++) {
            Item item = mSelectedCollection.asList().get(i);
            if (item.isImage()) {
                float size = PhotoMetadataUtils.getSizeInMB(item.size);
                if (size > mSpec.originalMaxSize) {
                    count++;
                }
            }
        }
        return count;
    }

    protected void updateSize(Item item) {
        if (item.isGif()) {
            mSize.setVisibility(View.VISIBLE);
            mSize.setText(PhotoMetadataUtils.getSizeInMB(item.size) + "M");
        } else {
            mSize.setVisibility(View.GONE);
        }

        if (item.isVideo()) {
            mOriginalLayout.setVisibility(View.GONE);
        } else if (mSpec.originalable) {
            mOriginalLayout.setVisibility(View.VISIBLE);
        }
    }

    protected void sendBackResult(boolean apply) {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_RESULT_BUNDLE, mSelectedCollection.getDataWithBundle());
        intent.putExtra(EXTRA_RESULT_APPLY, apply);
        intent.putExtra(EXTRA_RESULT_ORIGINAL_ENABLE, mOriginalEnable);
        getActivity().setResult(Activity.RESULT_OK, intent);
    }

    private boolean assertAddSelection(Item item) {
        IncapableCause cause = mSelectedCollection.isAcceptable(item);
        IncapableCause.handleCause(getActivity(), cause);
        return cause == null;
    }

    //    public abstract View provideYourFragmentView(LayoutInflater inflater,ViewGroup parent, Bundle savedInstanceState);
    @Override
    public void onAlbumMediaLoad(Cursor cursor) {
        List<Item> items = new ArrayList<>();
        while (cursor.moveToNext()) {
            items.add(Item.valueOf(cursor));
        }
//        cursor.close();

        if (items.isEmpty()) {
            return;
        }

        PreviewPagerAdapter adapter = (PreviewPagerAdapter) mPager.getAdapter();
        adapter.addAll(items);
        adapter.notifyDataSetChanged();
        if (!mIsAlreadySetPosition) {
            //onAlbumMediaLoad is called many times..
            mIsAlreadySetPosition = true;
            Item selected = getArguments().getParcelable(EXTRA_ITEM);
            int selectedIndex = items.indexOf(selected);
            mPager.setCurrentItem(selectedIndex, false);
            mPreviousPos = selectedIndex;
        }
    }

    @Override
    public void onAlbumMediaReset() {

    }


}
