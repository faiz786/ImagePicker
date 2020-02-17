package com.example.imagepicker.ui;


import android.app.Activity;
import android.content.Intent;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.imagepicker.MainActivity;
import com.example.imagepicker.R;
import com.example.imagepicker.internal.entity.Album;
import com.example.imagepicker.internal.entity.Item;
import com.example.imagepicker.internal.entity.SelectionSpec;
import com.example.imagepicker.internal.model.AlbumCollection;
import com.example.imagepicker.internal.model.SelectedItemCollection;
import com.example.imagepicker.internal.ui.BasePreviewActivity;
import com.example.imagepicker.internal.ui.BasePreviewFragment;
import com.example.imagepicker.internal.ui.MediaSelectionFragment;
import com.example.imagepicker.internal.ui.adapter.AlbumMediaAdapter;
import com.example.imagepicker.internal.ui.adapter.AlbumsAdapter;
import com.example.imagepicker.internal.ui.widget.AlbumsSpinner;
import com.example.imagepicker.internal.ui.widget.CheckRadioView;
import com.example.imagepicker.internal.ui.widget.IncapableDialog;
import com.example.imagepicker.internal.utils.MediaStoreCompat;
import com.example.imagepicker.internal.utils.PathUtils;
import com.example.imagepicker.internal.utils.PhotoMetadataUtils;
import com.example.imagepicker.internal.utils.SingleMediaScanner;

import java.util.ArrayList;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;

/**
 * A simple {@link Fragment} subclass.
 */
public class MatisseFragment extends Fragment implements
        AlbumCollection.AlbumCallbacks, AdapterView.OnItemSelectedListener,
        MediaSelectionFragment.SelectionProvider, View.OnClickListener,
        AlbumMediaAdapter.CheckStateListener, AlbumMediaAdapter.OnMediaClickListener,
        AlbumMediaAdapter.OnPhotoCapture{

    public static final String EXTRA_RESULT_SELECTION = "extra_result_selection";
    public static final String EXTRA_RESULT_SELECTION_PATH = "extra_result_selection_path";
    public static final String EXTRA_RESULT_ORIGINAL_ENABLE = "extra_result_original_enable";
    private static final int REQUEST_CODE_PREVIEW = 23;
    private static final int REQUEST_CODE_CAPTURE = 24;
    public static final String CHECK_STATE = "checkState";
    private final AlbumCollection mAlbumCollection = new AlbumCollection();
    private MediaStoreCompat mMediaStoreCompat;
//    private SelectedItemCollection mSelectedCollection = new SelectedItemCollection(getActivity());
    private SelectedItemCollection mSelectedCollection;
    private SelectionSpec mSpec;

    private AlbumsSpinner mAlbumsSpinner;
    private AlbumsAdapter mAlbumsAdapter;
    private TextView mButtonPreview;
    private TextView mButtonApply;
    private View mContainer;
    private View mEmptyView;

    private LinearLayout mOriginalLayout;
    private CheckRadioView mOriginal;
    private boolean mOriginalEnable;
    private static final int REQUEST_CODE_CHOOSE = 23;

    View view;
    MainActivity mainContext;


    public MatisseFragment() {
        // Required empty public constructor
    }

    public MatisseFragment(MainActivity mainActivity) {
        // Required empty public constructor
        mainContext = mainActivity;
        mSelectedCollection = new SelectedItemCollection(mainContext);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mSpec = SelectionSpec.getInstance();
        getActivity().setTheme(mSpec.themeId);
        super.onCreate(savedInstanceState);
        if (!mSpec.hasInited) {
            getActivity().setResult(RESULT_CANCELED);
            getActivity().finish();
            return null;
        }
        // Inflate the layout for this fragment
        view = inflater.inflate(R.layout.fragment_matisse, container, false);
        // programmatically set theme before super.onCreate()


        if (mSpec.needOrientationRestriction()) {
            getActivity().setRequestedOrientation(mSpec.orientation);
        }

        if (mSpec.capture) {
            mMediaStoreCompat = new MediaStoreCompat(getActivity());
            if (mSpec.captureStrategy == null)
                throw new RuntimeException("Don't forget to set CaptureStrategy.");
            mMediaStoreCompat.setCaptureStrategy(mSpec.captureStrategy);
        }

        Toolbar toolbar = (Toolbar) view.findViewById(R.id.toolbar);
        ((AppCompatActivity)getActivity()).setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getActivity().onBackPressed();
            }
        });
        ActionBar actionBar = ((AppCompatActivity)getActivity()).getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);
        Drawable navigationIcon = toolbar.getNavigationIcon();
        TypedArray ta = getActivity().getTheme().obtainStyledAttributes(new int[]{R.attr.album_element_color});
        int color = ta.getColor(0, 0);
        ta.recycle();
        navigationIcon.setColorFilter(color, PorterDuff.Mode.SRC_IN);

        mButtonPreview = (TextView) view.findViewById(R.id.button_preview);
        mButtonApply = (TextView) view.findViewById(R.id.button_apply);
        mButtonPreview.setOnClickListener(this);
        mButtonApply.setOnClickListener(this);
        mContainer = view.findViewById(R.id.container);
        mEmptyView = view.findViewById(R.id.empty_view);
        mOriginalLayout = view.findViewById(R.id.originalLayout);
        mOriginal = view.findViewById(R.id.original);
        mOriginalLayout.setOnClickListener(this);

        mSelectedCollection.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mOriginalEnable = savedInstanceState.getBoolean(CHECK_STATE);
        }
        updateBottomToolbar();

        mAlbumsAdapter = new AlbumsAdapter(getActivity(), null, false);
        mAlbumsSpinner = new AlbumsSpinner(getActivity());
        mAlbumsSpinner.setOnItemSelectedListener(this);
        mAlbumsSpinner.setSelectedTextView((TextView) view.findViewById(R.id.selected_album));
        mAlbumsSpinner.setPopupAnchorView(view.findViewById(R.id.toolbar));
        mAlbumsSpinner.setAdapter(mAlbumsAdapter);
        mAlbumCollection.onCreate(getActivity(), this);
        mAlbumCollection.onRestoreInstanceState(savedInstanceState);
        mAlbumCollection.loadAlbums();

        return view;
    }

    public void onDestroy() {
        super.onDestroy();
        mAlbumCollection.onDestroy();
        mSpec.onCheckedListener = null;
        mSpec.onSelectedListener = null;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

//    @Override
    public void onBackPressed() {
        getActivity().setResult(Activity.RESULT_CANCELED);
        getActivity().onBackPressed();
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK)
            return;

        if (requestCode == REQUEST_CODE_PREVIEW) {
            Bundle resultBundle = data.getBundleExtra(BasePreviewActivity.EXTRA_RESULT_BUNDLE);
            ArrayList<Item> selected = resultBundle.getParcelableArrayList(SelectedItemCollection.STATE_SELECTION);
            mOriginalEnable = data.getBooleanExtra(BasePreviewActivity.EXTRA_RESULT_ORIGINAL_ENABLE, false);
            int collectionType = resultBundle.getInt(SelectedItemCollection.STATE_COLLECTION_TYPE,
                    SelectedItemCollection.COLLECTION_UNDEFINED);
            if (data.getBooleanExtra(BasePreviewActivity.EXTRA_RESULT_APPLY, false)) {
                Intent result = new Intent();
                ArrayList<Uri> selectedUris = new ArrayList<>();
                ArrayList<String> selectedPaths = new ArrayList<>();
                if (selected != null) {
                    for (Item item : selected) {
                        selectedUris.add(item.getContentUri());
                        selectedPaths.add(PathUtils.getPath(getActivity(), item.getContentUri()));
                    }
                }
                result.putParcelableArrayListExtra(EXTRA_RESULT_SELECTION, selectedUris);
                result.putStringArrayListExtra(EXTRA_RESULT_SELECTION_PATH, selectedPaths);
                result.putExtra(EXTRA_RESULT_ORIGINAL_ENABLE, mOriginalEnable);
                getActivity().setResult(RESULT_OK, result);
                getActivity().finish();
            } else {
                mSelectedCollection.overwrite(selected, collectionType);
                Fragment mediaSelectionFragment = getActivity().getSupportFragmentManager().findFragmentByTag(
                        MediaSelectionFragment.class.getSimpleName());
                if (mediaSelectionFragment instanceof MediaSelectionFragment) {
                    ((MediaSelectionFragment) mediaSelectionFragment).refreshMediaGrid();
                }
                updateBottomToolbar();
            }
        } else if (requestCode == REQUEST_CODE_CAPTURE) {
            // Just pass the data back to previous calling Activity.
            Uri contentUri = mMediaStoreCompat.getCurrentPhotoUri();
            String path = mMediaStoreCompat.getCurrentPhotoPath();
            ArrayList<Uri> selected = new ArrayList<>();
            selected.add(contentUri);
            ArrayList<String> selectedPath = new ArrayList<>();
            selectedPath.add(path);
            Intent result = new Intent();
            result.putParcelableArrayListExtra(EXTRA_RESULT_SELECTION, selected);
            result.putStringArrayListExtra(EXTRA_RESULT_SELECTION_PATH, selectedPath);
            getActivity().setResult(RESULT_OK, result);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
                getActivity().revokeUriPermission(contentUri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);

            new SingleMediaScanner(getActivity().getApplicationContext(), path, new SingleMediaScanner.ScanListener() {
                @Override public void onScanFinish() {
                    Log.i("SingleMediaScanner", "scan finish!");
                }
            });
            getActivity().finish();
        }
    }

    private void updateBottomToolbar() {

        int selectedCount = mSelectedCollection.count();
        if (selectedCount == 0) {
            mButtonPreview.setEnabled(false);
            mButtonApply.setEnabled(false);
            mButtonApply.setText(getString(R.string.button_apply_default));
        } else if (selectedCount == 1 && mSpec.singleSelectionModeEnabled()) {
            mButtonPreview.setEnabled(true);
            mButtonApply.setText(R.string.button_apply_default);
            mButtonApply.setEnabled(true);
        } else {
            mButtonPreview.setEnabled(true);
            mButtonApply.setEnabled(true);
            mButtonApply.setText(getString(R.string.button_apply, selectedCount));
        }


        if (mSpec.originalable) {
            mOriginalLayout.setVisibility(View.VISIBLE);
            updateOriginalState();
        } else {
            mOriginalLayout.setVisibility(View.INVISIBLE);
        }


    }


    private void updateOriginalState() {
        mOriginal.setChecked(mOriginalEnable);
        if (countOverMaxSize() > 0) {

            if (mOriginalEnable) {
                IncapableDialog incapableDialog = IncapableDialog.newInstance("",
                        getString(R.string.error_over_original_size, mSpec.originalMaxSize));
                incapableDialog.show(getActivity().getSupportFragmentManager(),
                        IncapableDialog.class.getName());

                mOriginal.setChecked(false);
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

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.button_preview) {
//            Intent intent = new Intent(this, SelectedPreviewActivity.class);
//            intent.putExtra(BasePreviewActivity.EXTRA_DEFAULT_BUNDLE, mSelectedCollection.getDataWithBundle());
//            intent.putExtra(BasePreviewActivity.EXTRA_RESULT_ORIGINAL_ENABLE, mOriginalEnable);
//            startActivityForResult(intent, REQUEST_CODE_PREVIEW);

            Bundle bundle = new Bundle();
            bundle.putBundle(BasePreviewFragment.EXTRA_DEFAULT_BUNDLE, mSelectedCollection.getDataWithBundle());
            bundle.putBoolean(BasePreviewFragment.EXTRA_RESULT_ORIGINAL_ENABLE, mOriginalEnable);

            Fragment fragment = BasePreviewFragment.newInstance(bundle,mOriginalEnable,1);
            getActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.main_container, fragment, BasePreviewFragment.class.getSimpleName())
                    .commitAllowingStateLoss();
        } else if (v.getId() == R.id.button_apply) {
            Intent result = new Intent();
            ArrayList<Uri> selectedUris = (ArrayList<Uri>) mSelectedCollection.asListOfUri();
            result.putParcelableArrayListExtra(EXTRA_RESULT_SELECTION, selectedUris);
            ArrayList<String> selectedPaths = (ArrayList<String>) mSelectedCollection.asListOfString();
            result.putStringArrayListExtra(EXTRA_RESULT_SELECTION_PATH, selectedPaths);
            result.putExtra(EXTRA_RESULT_ORIGINAL_ENABLE, mOriginalEnable);
            getActivity().setResult(RESULT_OK, result);
            getActivity().finish();
        } else if (v.getId() == R.id.originalLayout) {
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

            if (mSpec.onCheckedListener != null) {
                mSpec.onCheckedListener.onCheck(mOriginalEnable);
            }
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        mAlbumCollection.setStateCurrentSelection(position);
        mAlbumsAdapter.getCursor().moveToPosition(position);
        Album album = Album.valueOf(mAlbumsAdapter.getCursor());
        if (album.isAll() && SelectionSpec.getInstance().capture) {
            album.addCaptureCount();
        }
        onAlbumSelected(album);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    @Override
    public void onAlbumLoad(final Cursor cursor) {
        mAlbumsAdapter.swapCursor(cursor);
        // select default album.
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {

            @Override
            public void run() {
                cursor.moveToPosition(mAlbumCollection.getCurrentSelection());
                mAlbumsSpinner.setSelection(getActivity(),
                        mAlbumCollection.getCurrentSelection());
                Album album = Album.valueOf(cursor);
                if (album.isAll() && SelectionSpec.getInstance().capture) {
                    album.addCaptureCount();
                }
                onAlbumSelected(album);
            }
        });
    }

    @Override
    public void onAlbumReset() {
        mAlbumsAdapter.swapCursor(null);
    }

    private void onAlbumSelected(Album album) {
        if (album.isAll() && album.isEmpty()) {
            mContainer.setVisibility(View.GONE);
            mEmptyView.setVisibility(View.VISIBLE);
        } else {
            mContainer.setVisibility(View.VISIBLE);
            mEmptyView.setVisibility(View.GONE);
            Fragment fragment = MediaSelectionFragment.newInstance(album,mOriginalEnable,this);
            getActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container, fragment, MediaSelectionFragment.class.getSimpleName())
                    .commitAllowingStateLoss();
        }
    }

    @Override
    public void onUpdate() {
        // notify bottom toolbar that check state changed.
        updateBottomToolbar();

        if (mSpec.onSelectedListener != null) {
            mSpec.onSelectedListener.onSelected(
                    mSelectedCollection.asListOfUri(), mSelectedCollection.asListOfString());
        }
    }

    @Override
    public void onMediaClick(Album album, Item item, int adapterPosition) {
        System.out.println("clicked on media");
//        Intent intent = new Intent(this, AlbumPreviewActivity.class);
//        intent.putExtra(AlbumPreviewActivity.EXTRA_ALBUM, album);
//        intent.putExtra(AlbumPreviewActivity.EXTRA_ITEM, item);
//        intent.putExtra(BasePreviewActivity.EXTRA_DEFAULT_BUNDLE, mSelectedCollection.getDataWithBundle());
//        intent.putExtra(BasePreviewActivity.EXTRA_RESULT_ORIGINAL_ENABLE, mOriginalEnable);
//        startActivityForResult(intent, REQUEST_CODE_PREVIEW);

//        Bundle bundle = new Bundle();
//        bundle.putParcelable(BasePreviewFragment.EXTRA_ALBUM, album);
//        bundle.putParcelable(BasePreviewFragment.EXTRA_ITEM, item);
//        bundle.putBundle(BasePreviewFragment.EXTRA_DEFAULT_BUNDLE, mSelectedCollection.getDataWithBundle());
//        bundle.putBoolean(BasePreviewFragment.EXTRA_RESULT_ORIGINAL_ENABLE, mOriginalEnable);
//
//        Fragment fragment = BasePreviewFragment.newInstance(bundle,mOriginalEnable,2);
//        getSupportFragmentManager()
//                .beginTransaction()
//                .replace(R.id.main_container, fragment, BasePreviewFragment.class.getSimpleName())
//                .commitAllowingStateLoss();

        Intent result = new Intent();
        mSelectedCollection.add(item);
        ArrayList<Uri> selectedUris = (ArrayList<Uri>) mSelectedCollection.asListOfUri();
        result.putParcelableArrayListExtra(EXTRA_RESULT_SELECTION, selectedUris);
        ArrayList<String> selectedPaths = (ArrayList<String>) mSelectedCollection.asListOfString();
        result.putStringArrayListExtra(EXTRA_RESULT_SELECTION_PATH, selectedPaths);
        result.putExtra(EXTRA_RESULT_ORIGINAL_ENABLE, mOriginalEnable);
//        getActivity().setResult(RESULT_OK, result);
//        getActivity().finish();

//        Intent result = new Intent();
//        mSelectedCollection.add(item);
//        ArrayList<Uri> selectedUris = (ArrayList<Uri>) mSelectedCollection.asListOfUri();
//        result.putParcelableArrayListExtra(EXTRA_RESULT_SELECTION, selectedUris);
//        ArrayList<String> selectedPaths = (ArrayList<String>) mSelectedCollection.asListOfString();
//        result.putStringArrayListExtra(EXTRA_RESULT_SELECTION_PATH, selectedPaths);
//        result.putExtra(EXTRA_RESULT_ORIGINAL_ENABLE, mOriginalEnable);
//        mainActivity.showPreviousFragment(mainActivity.getViewStack().size());

        mainContext.getSupportFragmentManager().popBackStack();
//        mainContext.getSupportFragmentManager().beginTransaction()
//                .replace(R.id.container, mainFragment, MatisseFragment.class.getSimpleName())
//                .commitAllowingStateLoss();
        mainContext.onActivityResult(REQUEST_CODE_CHOOSE, RESULT_OK,result);
    }

    @Override
    public SelectedItemCollection provideSelectedItemCollection() {
        return mSelectedCollection;
    }

    @Override
    public void capture() {
        if (mMediaStoreCompat != null) {
            mMediaStoreCompat.dispatchCaptureIntent(getActivity(), REQUEST_CODE_CAPTURE);
        }
    }
}
