/*
 * Copyright 2014 Ryan Jones
 *
 * This file is part of smartcard-reader, package org.docrj.smartcard.reader.
 *
 * smartcard-reader is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * smartcard-reader is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with smartcard-reader. If not, see <http://www.gnu.org/licenses/>.
 */

package org.docrj.smartcard.reader;

import java.lang.reflect.Type;
import java.util.ArrayList;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.PorterDuff;
import android.media.AudioManager;
import android.media.SoundPool;
import android.nfc.NfcAdapter.ReaderCallback;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ShareActionProvider;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;


public class AidRouteActivity extends Activity implements ReaderXcvr.UiCallbacks,
    ReaderCallback, SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = LaunchActivity.TAG;

    // HCE demo smartcard app
    static final String DEMO_NAME = "Demo";
    // proprietary unregistered AID starts with 0xFx, length 5 - 16 bytes
    static final String DEMO_AID = "F0646F632D726A";

    // update all five items below when adding/removing default apps!
    static final int NUM_RO_APPS = 11;
    static final int DEFAULT_APP_POS = 0; // demo
    static final String[] APP_NAMES = {
        DEMO_NAME,
        "Amex", "Amex 7-Byte", "Amex 8-Byte",
        "MC", "MC 8-Byte", "Visa", "Visa 8-Byte",
        "Discover Zip", "Test Pay", "Test Other"
    };
    static final String[] APP_AIDS = {
        DEMO_AID,
        "A00000002501", "A0000000250109", "A000000025010988",
        "A0000000041010", "A000000004101088", "A0000000031010", "A000000003101088",
        "A0000003241010", "F07465737420414944", "F07465737420414944"
    };
    // all are payment type except demo and "test other"
    static final int[] APP_TYPES = {
        1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1
    };

    // dialogs
    static final int DIALOG_NEW_APP = 1;
    static final int DIALOG_COPY_LIST = 2;
    static final int DIALOG_COPY_APP = 3;
    static final int DIALOG_EDIT_APP = 4;
    static final int DIALOG_EDIT_ALL_APPS = 5;
    static final int DIALOG_ENABLE_NFC = 6;
    static final int DIALOG_PARSED_MSG = 7;

    // tap feedback values
    static final int TAP_FEEDBACK_NONE = 0;
    static final int TAP_FEEDBACK_VIBRATE = 1;
    static final int TAP_FEEDBACK_AUDIO = 2;

    // test modes
    private static final int TEST_MODE_AID_ROUTE = Launcher.TEST_MODE_AID_ROUTE;
    private static final int TEST_MODE_EMV_READ = Launcher.TEST_MODE_EMV_READ;

    private Handler mHandler;
    private Editor mEditor;
    private MenuItem mEditMenuItem;
    private ImageButton mManualButton;
    private NfcManager mNfcManager;
    private Console mConsole;

    private ListView mEditAllListView;
    private AppAdapter mAppAdapter;
    private AppAdapter mEditAllAdapter;
    private Button mSelectButton;
    private View mSelectSeparator;

    private boolean mAutoClear;
    private boolean mManual;
    private boolean mShowMsgSeparators;
    private int mTapFeedback;
    private boolean mSelectHaptic;

    private int mTapSound;
    private SoundPool mSoundPool;
    private Vibrator mVibrator;
    private int mSelectedAppPos = DEFAULT_APP_POS;
    private ArrayList<SmartcardApp> mApps;
    private boolean mSelectOnCreate;
    private TextView mIntro;
    private Spinner mAidSpinner;
    private ActionBar mActionBar;
    private AlertDialog mNewDialog;
    private AlertDialog mCopyListDialog;
    private AlertDialog mCopyDialog;
    private AlertDialog mEditDialog;
    private AlertDialog mEditAllDialog;
    private int mCopyPos;
    private int mEditPos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
        mActionBar = getActionBar();
        //View titleView = getLayoutInflater().inflate(R.layout.app_title, null);
        //mActionBar.setCustomView(titleView);
        mActionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM
                | ActionBar.DISPLAY_SHOW_HOME);

        SpinnerAdapter sAdapter = ArrayAdapter.createFromResource(this,
                R.array.test_modes, R.layout.spinner_dropdown_item_2);
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        mActionBar.setListNavigationCallbacks(sAdapter, new ActionBar.OnNavigationListener() {
            String[] strings = getResources().getStringArray(R.array.test_modes);

            @Override
            public boolean onNavigationItemSelected(int position, long itemId) {
                int testMode = strings[position].equals(getString(R.string.aid_route)) ?
                        TEST_MODE_AID_ROUTE : TEST_MODE_EMV_READ;
                if (testMode != TEST_MODE_AID_ROUTE) {
                    new Launcher(AidRouteActivity.this).launch(testMode, false);
                    // finish activity so it does not remain on back stack
                    finish();
                    overridePendingTransition(0, 0);
                }
                return true;
            }
        });

        mActionBar.show();

        setContentView(R.layout.activity_aid_route_layout);
        mIntro = (TextView) findViewById(R.id.intro);
        mSelectButton = (Button) findViewById(R.id.manualSelectButton);
        mSelectSeparator = findViewById(R.id.separator2);
        mSelectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSelectHaptic) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                }
                clearMessages();
                // short delay to show cleared messages
                mHandler.postDelayed(new Runnable() {
                    public void run() {
                        onError(getString(R.string.manual_disconnected));
                    }
                }, 50L);
            }
        });

        ListView listView = (ListView) findViewById(R.id.msgListView);
        mConsole = new Console(this, savedInstanceState, listView);

        mHandler = new Handler();
        mNfcManager = new NfcManager(this, this);

        ApduParser.init(this);

        // persistent "shared preferences" store
        SharedPreferences ss = getSharedPreferences("prefs", Context.MODE_PRIVATE);
        mEditor = ss.edit();

        String json = ss.getString("apps", null);
        // if shared prefs is empty, synchronously write defaults
        if (json == null) {
            // initialize default smartcard apps
            mApps = new ArrayList<SmartcardApp>();
            for (int i = 0; i < APP_NAMES.length; i++) {
                mApps.add(new SmartcardApp(APP_NAMES[i], APP_AIDS[i], APP_TYPES[i]));
            }
            // write to shared prefs, serializing list of SmartcardApp
            writePrefs();
            json = ss.getString("apps", null);
        }
        // deserialize list of SmartcardApp
        Gson gson = new Gson();
        Type collectionType = new TypeToken<ArrayList<SmartcardApp>>() {}.getType();
        mApps = gson.fromJson(json, collectionType);

        mAidSpinner = (Spinner) findViewById(R.id.aid);
        mAppAdapter = new AppAdapter(this, mApps, savedInstanceState, false);
        mAidSpinner.setAdapter(mAppAdapter);
        mAidSpinner
                .setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent,
                            View view, int pos, long id) {
                        if (!mSelectOnCreate && !mManual) {
                            clearMessages();
                        }
                        mSelectOnCreate = false;
                        mSelectedAppPos = pos;
                        Log.d(TAG, "App: " + mApps.get(pos).getName()
                                + ", AID: " + mApps.get(pos).getAid());
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });

        mSelectOnCreate = true;
        // mSelectedAppPos saved in onPause(), restored in onResume()

        // persistent settings and settings listener
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        mAutoClear = prefs.getBoolean("pref_auto_clear", true);
        mShowMsgSeparators = prefs.getBoolean("pref_show_separators", true);
        String tapFeedback = prefs.getString("pref_tap_feedback", "1");
        mTapFeedback = Integer.valueOf(tapFeedback);
        mSelectHaptic = prefs.getBoolean("pref_select_haptic", true);

        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    private void prepareViewForMode() {
        mIntro.setText(mManual ? R.string.intro_aid_route_manual : R.string.intro_aid_route);
        mSelectSeparator.setVisibility(mManual ? View.VISIBLE : View.GONE);
        mSelectButton.setVisibility(mManual ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key.equals("pref_auto_clear")){
            mAutoClear = prefs.getBoolean("pref_auto_clear", true);
        } else if (key.equals("pref_show_separators")) {
            mShowMsgSeparators = prefs.getBoolean("pref_show_separators", true);
            clearMessages();
        } else if (key.equals("pref_tap_feedback")) {
            String tapFeedback = prefs.getString("pref_tap_feedback", "1");
            mTapFeedback = Integer.valueOf(tapFeedback);
        } else if (key.equals("pref_select_haptic")) {
            mSelectHaptic = prefs.getBoolean("pref_select_haptic", true);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onResume() {
        super.onResume();
        mActionBar.setSelectedNavigationItem(TEST_MODE_AID_ROUTE);

        // restore mode and selected pos from prefs
        SharedPreferences ss = getSharedPreferences("prefs", Context.MODE_PRIVATE);
        mManual = ss.getBoolean("manual", mManual);
        prepareViewForMode();

        mSelectedAppPos = ss.getInt("selected_aid_pos", mSelectedAppPos);
        mAidSpinner.setSelection(mSelectedAppPos);

        // this delay is a bit hacky; would be better to extend ListView
        // and override onLayout()
        mHandler.postDelayed(new Runnable() {
            public void run() {
                mConsole.smoothScrollToPosition();
            }
        }, 50L);

        mNfcManager.onResume();
        initSoundPool();
    }

    @Override
    public void onPause() {
        super.onPause();
        writePrefs();
        releaseSoundPool();
        mNfcManager.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mNewDialog != null) {
            mNewDialog.dismiss();
        }
        if (mCopyDialog != null) {
            mCopyDialog.dismiss();
        }
        if (mCopyListDialog != null) {
            mCopyListDialog.dismiss();
        }
        if (mEditDialog != null) {
            mEditDialog.dismiss();
        }
        if (mEditAllDialog != null) {
            mEditAllDialog.dismiss();
        }
        // dismiss enable NFC dialog
        mNfcManager.onStop();
        // dismiss parsed message dialog
        mConsole.onStop();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        mConsole.clearShareIntent();
    }

    @Override
    protected void onSaveInstanceState(Bundle outstate) {
        Log.d(TAG, "saving instance state!");
        mConsole.onSaveInstanceState(outstate);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected Dialog onCreateDialog(int id) {
        AlertDialog.Builder builder = new AlertDialog.Builder(
                AidRouteActivity.this, R.style.dialog);
        final LayoutInflater li = getLayoutInflater();
        Dialog dialog = null;
        switch (id) {
        case DIALOG_NEW_APP: {
            final View view = li.inflate(R.layout.dialog_new_app, null);
            builder.setView(view)
                    .setCancelable(false)
                    .setIcon(R.drawable.ic_action_new)
                    .setTitle(R.string.smartcard_app)
                    .setPositiveButton(R.string.dialog_ok, null)
                    .setNegativeButton(R.string.dialog_cancel,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int id) {
                                    dialog.cancel();
                                }
                            });

            mNewDialog = builder.create();
            dialog = mNewDialog;
            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface di) {
                    Button b = mNewDialog
                            .getButton(AlertDialog.BUTTON_POSITIVE);
                    b.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            EditText appName = (EditText) view
                                    .findViewById(R.id.app_name);
                            EditText appAid = (EditText) view
                                    .findViewById(R.id.app_aid);

                            // validate name and aid
                            String name = appName.getText().toString();
                            String aid = appAid.getText().toString();
                            if (name.isEmpty()) {
                                showToast(getString(R.string.empty_name));
                                return;
                            }
                            if (aid.length() < 10 || aid.length() > 32
                                    || aid.length() % 2 != 0) {
                                showToast(getString(R.string.invalid_aid));
                                return;
                            }
                            // ensure name is unique (aid can be dup)
                            for (SmartcardApp app : mApps) {
                                if (app.getName().equals(name)) {
                                    showToast(getString(R.string.name_exists,
                                            name));
                                    return;
                                }
                            }
                            // app type radio group
                            RadioGroup appTypeGrp = (RadioGroup) view
                                    .findViewById(R.id.radio_grp_type);
                            int selectedId = appTypeGrp
                                    .getCheckedRadioButtonId();
                            RadioButton radioBtn = (RadioButton) view
                                    .findViewById(selectedId);
                            int type = radioBtn.getText().toString()
                                    .equals(getString(R.string.radio_payment)) ? SmartcardApp.TYPE_PAYMENT
                                    : SmartcardApp.TYPE_OTHER;

                            // current app checkbox
                            CheckBox cbCurrent = (CheckBox) view
                                    .findViewById(R.id.make_current);
                            if (cbCurrent.isChecked()) {
                                mSelectedAppPos = mApps.size();
                            }

                            // update apps list
                            SmartcardApp newApp = new SmartcardApp(appName
                                    .getText().toString(), appAid.getText()
                                    .toString(), type);
                            Log.d(TAG, "newApp: " + newApp);
                            synchronized (mApps) {
                                mApps.add(newApp);
                                if (mApps.size() == NUM_RO_APPS + 1) {
                                    // enable edit menu item
                                    prepareOptionsMenu();
                                }
                            }

                            mAidSpinner.setAdapter(mAppAdapter);
                            mAidSpinner.setSelection(mSelectedAppPos);
                            mAppAdapter.notifyDataSetChanged();

                            // write apps to shared prefs
                            new writePrefsTask().execute();
                            mNewDialog.dismiss();
                        }
                    });
                }
            });
            break;
        } // case
        case DIALOG_COPY_LIST: {
            final View view = li.inflate(R.layout.dialog_edit_apps, null);
            final ListView listView = (ListView) view
                    .findViewById(R.id.listView);
            listView.setOnItemClickListener(new ListView.OnItemClickListener() {
                @SuppressWarnings("deprecation")
                @Override
                public void onItemClick(AdapterView<?> parent, View view,
                        int pos, long id) {
                    mCopyPos = pos;
                    showDialog(DIALOG_COPY_APP);
                    mCopyListDialog.dismiss();
                }
            });
            listView.setOnItemLongClickListener(new ListView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent,
                        View view, int pos, long id) {
                    return true;
                }
            });

            builder.setView(view).setCancelable(false)
                    .setIcon(R.drawable.ic_action_copy)
                    .setTitle(R.string.smartcard_app)
                    .setPositiveButton(R.string.dialog_cancel, null);

            mCopyListDialog = builder.create();
            dialog = mCopyListDialog;
            break;
        } // case
        case DIALOG_COPY_APP: {
            final View view = li.inflate(R.layout.dialog_new_app, null);
            builder.setView(view)
                    .setCancelable(false)
                    .setIcon(R.drawable.ic_action_new)
                    .setTitle(R.string.smartcard_app)
                    .setPositiveButton(R.string.dialog_ok, null)
                    .setNegativeButton(R.string.dialog_cancel,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int id) {
                                    dialog.cancel();
                                }
                            });

            mCopyDialog = builder.create();
            dialog = mCopyDialog;
            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface di) {
                    Button b = mCopyDialog
                            .getButton(AlertDialog.BUTTON_POSITIVE);
                    b.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            EditText appName = (EditText) view
                                    .findViewById(R.id.app_name);
                            EditText appAid = (EditText) view
                                    .findViewById(R.id.app_aid);

                            // validate name and aid
                            String name = appName.getText().toString();
                            String aid = appAid.getText().toString();
                            if (name.isEmpty()) {
                                showToast(getString(R.string.empty_name));
                                return;
                            }
                            if (aid.length() < 10 || aid.length() > 32
                                    || aid.length() % 2 != 0) {
                                showToast(getString(R.string.invalid_aid));
                                return;
                            }
                            // ensure name is unique (aid can be dup)
                            for (SmartcardApp app : mApps) {
                                if (app.getName().equals(name)) {
                                    showToast(getString(R.string.name_exists,
                                            name));
                                    return;
                                }
                            }
                            // app type radio group
                            RadioGroup appTypeGrp = (RadioGroup) view
                                    .findViewById(R.id.radio_grp_type);
                            int selectedId = appTypeGrp
                                    .getCheckedRadioButtonId();
                            RadioButton radioBtn = (RadioButton) view
                                    .findViewById(selectedId);
                            int type = radioBtn.getText().toString()
                                    .equals(getString(R.string.radio_payment)) ? SmartcardApp.TYPE_PAYMENT
                                    : SmartcardApp.TYPE_OTHER;

                            // current app checkbox
                            CheckBox cbCurrent = (CheckBox) view
                                    .findViewById(R.id.make_current);
                            if (cbCurrent.isChecked()) {
                                mSelectedAppPos = mApps.size();
                            }

                            // update apps list
                            SmartcardApp newApp = new SmartcardApp(appName
                                    .getText().toString(), appAid.getText()
                                    .toString(), type);
                            Log.d(TAG, "newApp: " + newApp);
                            synchronized (mApps) {
                                mApps.add(newApp);
                                if (mApps.size() == NUM_RO_APPS + 1) {
                                    // enable edit menu item
                                    prepareOptionsMenu();
                                }
                            }
 
                            mAidSpinner.setAdapter(mAppAdapter);
                            mAidSpinner.setSelection(mSelectedAppPos);
                            mAppAdapter.notifyDataSetChanged();

                            // write apps to shared prefs
                            new writePrefsTask().execute();
                            mCopyDialog.dismiss();
                        }
                    });
                }
            });
            break;
        } // case
        case DIALOG_EDIT_APP: {
            final View view = li.inflate(R.layout.dialog_new_app, null);
            builder.setView(view)
                    .setCancelable(false)
                    .setIcon(R.drawable.ic_action_edit)
                    .setTitle(R.string.smartcard_app)
                    .setPositiveButton(R.string.dialog_ok, null)
                    .setNegativeButton(R.string.dialog_cancel,
                            new DialogInterface.OnClickListener() {
                                @SuppressWarnings("deprecation")
                                public void onClick(DialogInterface dialog,
                                        int id) {
                                    dismissKeyboard(mEditDialog
                                            .getCurrentFocus());
                                    showDialog(DIALOG_EDIT_ALL_APPS);
                                    dialog.cancel();
                                }
                            });

            mEditDialog = builder.create();
            dialog = mEditDialog;

            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface di) {
                    Button b = mEditDialog
                            .getButton(AlertDialog.BUTTON_POSITIVE);
                    b.setOnClickListener(new View.OnClickListener() {
                        @SuppressWarnings("deprecation")
                        public void onClick(View v) {
                            EditText appName = (EditText) view
                                    .findViewById(R.id.app_name);
                            EditText appAid = (EditText) view
                                    .findViewById(R.id.app_aid);

                            // validate name and aid
                            String name = appName.getText().toString();
                            String aid = appAid.getText().toString();
                            if (name.isEmpty()) {
                                showToast(getString(R.string.empty_name));
                                return;
                            }
                            if (aid.length() < 10 || aid.length() > 32
                                    || aid.length() % 2 != 0) {
                                showToast(getString(R.string.invalid_aid));
                                return;
                            }
                            // ensure name is unique
                            for (int i = 0; i < mApps.size(); i++) {
                                // skip the app being edited
                                if (i == mEditPos)
                                    continue;
                                SmartcardApp app = mApps.get(i);
                                if (app.getName().equals(name)) {
                                    showToast(getString(R.string.name_exists,
                                            name));
                                    return;
                                }
                            }
                            // app type radio group
                            RadioGroup appTypeGrp = (RadioGroup) view
                                    .findViewById(R.id.radio_grp_type);
                            int selectedId = appTypeGrp
                                    .getCheckedRadioButtonId();
                            RadioButton radioBtn = (RadioButton) view
                                    .findViewById(selectedId);
                            int type = radioBtn.getText().toString()
                                    .equals(getString(R.string.radio_payment)) ? SmartcardApp.TYPE_PAYMENT
                                    : SmartcardApp.TYPE_OTHER;

                            // current app checkbox
                            CheckBox cbCurrent = (CheckBox) view
                                    .findViewById(R.id.make_current);
                            if (cbCurrent.isChecked()) {
                                mSelectedAppPos = mEditPos;
                            }

                            // update apps list
                            SmartcardApp app;
                            synchronized (mApps) {
                                app = mApps.get(mEditPos);
                                app.setName(name);
                                app.setAid(aid);
                                app.setType(type);
                            }
                            Log.d(TAG, "app: " + app);

                            mAidSpinner.setSelection(mSelectedAppPos);
                            mAppAdapter.notifyDataSetChanged();

                            SmartcardApp subApp = mEditAllAdapter
                                    .getItem(mEditPos - NUM_RO_APPS);
                            subApp.copy(app);
                            mEditAllAdapter.notifyDataSetChanged();
                            mEditAllListView.setAdapter(mEditAllAdapter);

                            // write shared prefs in another thread
                            new writePrefsTask().execute();
                            dismissKeyboard(mEditDialog.getCurrentFocus());
                            showDialog(DIALOG_EDIT_ALL_APPS);
                            mEditDialog.dismiss();
                        }
                    });
                }
            });
            break;
        } // case
        case DIALOG_EDIT_ALL_APPS: {
            final View view = li.inflate(R.layout.dialog_edit_apps, null);
            final ListView listView = (ListView) view
                    .findViewById(R.id.listView);
            mEditAllListView = listView;
            listView.setOnItemClickListener(new ListView.OnItemClickListener() {
                @SuppressWarnings("deprecation")
                @Override
                public void onItemClick(AdapterView<?> parent, View view,
                        int pos, long id) {
                    mEditPos = NUM_RO_APPS + pos;
                    showDialog(DIALOG_EDIT_APP);
                    mEditAllDialog.dismiss();
                }
            });
            listView.setOnItemLongClickListener(new ListView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent,
                        View view, int pos, long id) {
                    // TODO: confirmation dialog or discard icon?
                    mApps.remove(NUM_RO_APPS + pos);
                    if (mSelectedAppPos == NUM_RO_APPS + pos) {
                        mSelectedAppPos = 0;
                    } else if (mSelectedAppPos > NUM_RO_APPS + pos) {
                        mSelectedAppPos--;
                    }

                    mAidSpinner.setAdapter(mAppAdapter);
                    mAidSpinner.setSelection(mSelectedAppPos);
                    mAppAdapter.notifyDataSetChanged();

                    SmartcardApp app = mEditAllAdapter.getItem(pos);
                    mEditAllAdapter.remove(app);
                    mEditAllAdapter.notifyDataSetChanged();
                    listView.setAdapter(mEditAllAdapter);

                    new writePrefsTask().execute();
                    if (mApps.size() == NUM_RO_APPS) {
                        parent.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        mEditAllDialog.dismiss();
                        // disable edit menu item
                        prepareOptionsMenu();
                    }
                    return true;
                }
            });

            builder.setView(view).setCancelable(false)
                    .setIcon(R.drawable.ic_action_edit)
                    .setTitle(R.string.smartcard_app)
                    .setPositiveButton(R.string.dialog_done, null);

            mEditAllDialog = builder.create();
            dialog = mEditAllDialog;
            break;
        } // case
        case DIALOG_ENABLE_NFC: {
            dialog = mNfcManager.onCreateDialog(id, builder, li);
            break;
        } // case
        case DIALOG_PARSED_MSG: {
            dialog = mConsole.onCreateDialog(id, builder, li);
            break;
        }
        } // switch
        return dialog;
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        switch (id) {
        case DIALOG_NEW_APP: {
            EditText name = (EditText) dialog.findViewById(R.id.app_name);
            EditText aid = (EditText) dialog.findViewById(R.id.app_aid);
            RadioGroup type = (RadioGroup) dialog
                    .findViewById(R.id.radio_grp_type);
            CheckBox current = (CheckBox) dialog
                    .findViewById(R.id.make_current);

            name.setText("");
            name.requestFocus();
            aid.setText("");
            type.check(R.id.radio_payment);
            current.setChecked(false);
            break;
        }
        case DIALOG_COPY_LIST: {
            ListView listView = (ListView) dialog.findViewById(R.id.listView);
            TextView textView = (TextView) dialog.findViewById(R.id.text1);

            textView.setText(R.string.tap_to_copy);
            AppAdapter copyListAdapter = new AppAdapter(this, mApps, null, true);
            listView.setAdapter(copyListAdapter);
            break;
        }
        case DIALOG_COPY_APP: {
            EditText name = (EditText) dialog.findViewById(R.id.app_name);
            EditText aid = (EditText) dialog.findViewById(R.id.app_aid);
            RadioGroup type = (RadioGroup) dialog
                    .findViewById(R.id.radio_grp_type);
            CheckBox current = (CheckBox) dialog
                    .findViewById(R.id.make_current);

            SmartcardApp app = mApps.get(mCopyPos);
            name.setText(app.getName());
            name.requestFocus();
            aid.setText(app.getAid());
            type.check((app.getType() == SmartcardApp.TYPE_OTHER) ? R.id.radio_other
                    : R.id.radio_payment);
            current.setChecked(false);
            break;
        }
        case DIALOG_EDIT_APP: {
            EditText name = (EditText) dialog.findViewById(R.id.app_name);
            EditText aid = (EditText) dialog.findViewById(R.id.app_aid);
            RadioGroup type = (RadioGroup) dialog
                    .findViewById(R.id.radio_grp_type);
            CheckBox current = (CheckBox) dialog
                    .findViewById(R.id.make_current);

            SmartcardApp app = mApps.get(mEditPos);
            name.setText(app.getName());
            name.requestFocus();
            aid.setText(app.getAid());
            type.check((app.getType() == SmartcardApp.TYPE_OTHER) ? R.id.radio_other
                    : R.id.radio_payment);
            current.setChecked(mEditPos == mSelectedAppPos);
            current.setEnabled(mEditPos != mSelectedAppPos);
            break;
        }
        case DIALOG_EDIT_ALL_APPS: {
            ListView listView = (ListView) dialog.findViewById(R.id.listView);
            ArrayList<SmartcardApp> sl = new ArrayList<SmartcardApp>(
                    mApps.subList(NUM_RO_APPS, mApps.size()));
            mEditAllAdapter = new AppAdapter(this, sl, null, true);
            listView.setAdapter(mEditAllAdapter);
            break;
        }
        case DIALOG_ENABLE_NFC: {
            break;
        }
        case DIALOG_PARSED_MSG: {
            mConsole.onPrepareDialog(id, dialog);
            break;
        }
        }
    }

    private void dismissKeyboard(View focus) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_aid_route_menu, menu);
        mEditMenuItem = menu.findItem(R.id.menu_edit_all_apps);
        MenuItem manualMenuItem = menu.findItem(R.id.menu_manual);
        LinearLayout layout = (LinearLayout) manualMenuItem.getActionView();
        mManualButton = (ImageButton) layout.findViewById(R.id.menu_btn);
        mManualButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mManual = !mManual;

                mManualButton.setBackground(mManual ?
                    getResources().getDrawable(R.drawable.button_bg_selected_states) :
                    getResources().getDrawable(R.drawable.button_bg_unselected_states));

                prepareViewForMode();
                clearMessages();
            }
        });

        prepareOptionsMenu();

        MenuItem item = menu.findItem(R.id.menu_share_msgs);
        mConsole.setShareProvider((ShareActionProvider) item.getActionProvider());
        return true;
    }

    private void prepareOptionsMenu() {
        boolean editEnabled = mApps.size() > NUM_RO_APPS;
        Drawable editIcon = getResources().getDrawable(
                R.drawable.ic_action_edit);
        if (!editEnabled) {
            editIcon.mutate().setColorFilter(Color.LTGRAY,
                    PorterDuff.Mode.SRC_IN);
        }
        mEditMenuItem.setIcon(editIcon);
        mEditMenuItem.setEnabled(editEnabled);

        mManualButton.setBackground(mManual ?
            getResources().getDrawable(R.drawable.button_bg_selected_states) :
            getResources().getDrawable(R.drawable.button_bg_unselected_states));
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

        case R.id.menu_manual:
            // handled by android:actionLayout="@layout/menu_button"
            // see onCreateOptionsMenu()
            return true;
        
        case R.id.menu_clear_msgs:
            clearMessages();
            return true;

        case R.id.menu_new_app:
            showDialog(DIALOG_NEW_APP);
            return true;

        case R.id.menu_copy_app:
            showDialog(DIALOG_COPY_LIST);
            return true;

        case R.id.menu_edit_all_apps:
            showDialog(DIALOG_EDIT_ALL_APPS);
            return true;

        case R.id.menu_settings:
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showToast(String text) {
        Toast toast = Toast.makeText(AidRouteActivity.this, text,
                Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER_VERTICAL, 0, -100);
        toast.show();
    }

    private void initSoundPool() {
        synchronized (this) {
            if (mSoundPool == null) {
                mSoundPool = new SoundPool(1, AudioManager.STREAM_NOTIFICATION, 0);
                mTapSound = mSoundPool.load(this, R.raw.tap, 1);
            }
        }
    }

    private void releaseSoundPool() {
        synchronized (this) {
            if (mSoundPool != null) {
                mSoundPool.release();
                mSoundPool = null;
            }
        }
    }

    private void doTapFeedback() {
        if (mTapFeedback == TAP_FEEDBACK_AUDIO) {
            mSoundPool.play(mTapSound, 1.0f, 1.0f, 0, 0, 1.0f); 
        } else if (mTapFeedback == TAP_FEEDBACK_VIBRATE) {
            long[] pattern = {0, 50, 50, 50};
            mVibrator.vibrate(pattern, -1);
        }        
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        doTapFeedback();
        // maybe clear console or show separator, depends on settings
        if (mAutoClear) {
            clearMessages();
        } else {
            addMessageSeparator();
        }
        // get IsoDep handle and run xcvr thread
        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null) {
            onError(getString(R.string.wrong_tag_err));
        } else {
            ReaderXcvr xcvr;
            String name = mApps.get(mSelectedAppPos).getName();
            String aid = mApps.get(mSelectedAppPos).getAid();

            if (DEMO_NAME.equals(name) && DEMO_AID.equals(aid)) {
                xcvr = new DemoReaderXcvr(isoDep, aid, this);
            } else if (mManual) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        StateListDrawable bg = (StateListDrawable) mSelectButton.getBackground();
                        Drawable currentBg = bg.getCurrent();
                        if (currentBg instanceof AnimationDrawable) {
                            AnimationDrawable btnAnim = (AnimationDrawable) currentBg;
                            btnAnim.stop();
                            btnAnim.start();
                        }
                    }
                });

                // manual select mode; for multiple selects per tap/connect
                // does not select ppse for payment apps unless specifically configured
                xcvr = new ManualReaderXcvr(isoDep, aid, this);
            } else if (mApps.get(mSelectedAppPos).getType() == SmartcardApp.TYPE_PAYMENT) {
                // payment, ie. always selects ppse first
                xcvr = new PaymentReaderXcvr(isoDep, aid, this, TEST_MODE_AID_ROUTE);
            } else {
                // other/non-payment; auto select on each tap/connect
                xcvr = new OtherReaderXcvr(isoDep, aid, this);
            }

            new Thread(xcvr).start();
        }
    }

    @Override
    public void onMessageSend(final String raw, final String name) {
        mConsole.write(raw, MessageAdapter.MSG_SEND, name, null);
    }

    @Override
    public void onMessageRcv(final String raw, final String name, final String parsed) {
        mConsole.write(raw, MessageAdapter.MSG_RCV, name, parsed);
    }

    @Override
    public void onOkay(final String message) {
        mConsole.write(message, MessageAdapter.MSG_OKAY, null, null);
    }

    @Override
    public void onError(final String message) {
        mConsole.write(message, MessageAdapter.MSG_ERROR, null, null);
    }

    @Override
    public void onSeparator() {
        addMessageSeparator();
    }

    @Override
    public void clearMessages() {
        mConsole.clear();
    }

    @Override
    public void setUserSelectListener(final ReaderXcvr.UiListener callback) {
        mSelectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // haptic feedback
                if (mSelectHaptic) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                }
                // update console and do select transaction
                if (mAutoClear) {
                    clearMessages();
                    // short delay to show cleared messages
                    mHandler.postDelayed(new Runnable() {
                        public void run() {
                            callback.onUserSelect(mApps.get(mSelectedAppPos).getAid());
                        }
                    }, 50L);
                } else {
                    addMessageSeparator();
                    callback.onUserSelect(mApps.get(mSelectedAppPos).getAid());
                }
            }
        });
    }

    @Override
    public void onFinish() {
        // nothing yet! animation cleanup worked better elsewhere
    }

    private void stopSelectButtonAnim() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                StateListDrawable bg = (StateListDrawable) mSelectButton.getBackground();
                Drawable currentBg = bg.getCurrent();
                if (currentBg instanceof AnimationDrawable) {
                    AnimationDrawable btnAnim = (AnimationDrawable) currentBg;
                    btnAnim.stop();
                }
            }
        });        
    }

    private void addMessageSeparator() {
        if (mShowMsgSeparators) {
            mConsole.writeSeparator();
        }
    }

    private void writePrefs() {
        // serialize list of SmartcardApp
        Gson gson = new Gson();
        String json = gson.toJson(mApps);
        mEditor.putString("apps", json);

        mEditor.putInt("selected_aid_pos", mSelectedAppPos);
        mEditor.putInt("test_mode", TEST_MODE_AID_ROUTE);
        mEditor.putBoolean("manual", mManual);
        mEditor.commit();
    }

    private class writePrefsTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... v) {
            writePrefs();
            return null;
        }
    }
}